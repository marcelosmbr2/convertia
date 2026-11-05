package com.recast.app.data.engine.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.markedcontent.BeginMarkedContentSequence
import com.tom_roush.pdfbox.contentstream.operator.markedcontent.BeginMarkedContentSequenceWithProperties
import com.tom_roush.pdfbox.contentstream.operator.markedcontent.EndMarkedContentSequence
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference
import com.tom_roush.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement
import com.tom_roush.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode
import com.tom_roush.pdfbox.pdmodel.documentinterchange.taggedpdf.PDTableAttributeObject
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.IOException
import java.io.Writer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Leitor de PDF do app, com o PdfBox-Android. */
class PdfBoxExtractor(context: Context) : PdfExtractor {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    override fun extract(pdf: File, imagesDir: File): PdfModel {
        imagesDir.mkdirs()
        val document = try {
            PDDocument.load(pdf)
        } catch (e: InvalidPasswordException) {
            throw IOException("PDF protegido por senha")
        }
        document.use { doc ->
            val pageIndex = java.util.IdentityHashMap<COSDictionary, Int>()
            doc.pages.forEachIndexed { i, page -> pageIndex[page.cosObject] = i }
            val pages = doc.pages.mapIndexed { index, page ->
                val box = page.cropBox
                val text = TextCollector(index, box.lowerLeftX, box.upperRightY)
                text.startPage = index + 1
                text.endPage = index + 1
                text.writeText(doc, NullWriter)
                val graphics = GraphicsCollector(page, index, box.lowerLeftX, box.upperRightY, imagesDir)
                runCatching { graphics.processPage(page) }.onFailure { Log.w(TAG, "Desenhos da página $index ignorados", it) }
                PdfPage(index, box.width, box.height, text.glyphs, graphics.strokes, graphics.fills, graphics.images)
            }
            val structure = doc.documentCatalog.structureTreeRoot?.let { root ->
                runCatching { StructNode("Document", children = root.kids.mapNotNull { structNode(it, pageIndex, null) }) }.getOrNull()
            }
            return PdfModel(pages, structure?.takeIf { it.children.isNotEmpty() })
        }
    }

    private companion object {
        private const val TAG = "PdfBoxExtractor"
    }

    private fun structNode(kid: Any?, pageIndex: Map<COSDictionary, Int>, inheritedPage: Int?): StructNode? {
        return when (kid) {
            is PDStructureElement -> {
                val page = kid.page?.let { pageIndex[it.cosObject] } ?: inheritedPage
                val content = mutableListOf<Pair<Int, Int>>()
                val children = mutableListOf<StructNode>()
                for (child in kid.kids) {
                    when (child) {
                        is PDStructureElement -> structNode(child, pageIndex, page)?.let(children::add)
                        is PDMarkedContentReference -> (child.page?.let { pageIndex[it.cosObject] } ?: page)?.let { content += it to child.mcid }
                        is Int -> page?.let { content += it to child }
                    }
                }
                var colSpan = 1
                var rowSpan = 1
                runCatching {
                    val attributes = kid.attributes
                    for (i in 0 until attributes.size()) {
                        (attributes.getObject(i) as? PDTableAttributeObject)?.let { colSpan = it.colSpan; rowSpan = it.rowSpan }
                    }
                }
                StructNode(
                    type = kid.standardStructureType ?: kid.structureType,
                    children = children,
                    content = content,
                    colSpan = colSpan,
                    rowSpan = rowSpan,
                    actualText = kid.actualText ?: kid.alternateDescription,
                )
            }
            is PDStructureNode -> StructNode("Div", children = kid.kids.mapNotNull { structNode(it, pageIndex, inheritedPage) })
            else -> null
        }
    }

    private object NullWriter : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) = Unit
        override fun flush() = Unit
        override fun close() = Unit
    }

    /** Coleta cada glifo com posição, fonte, estilo, cor e MCID. */
    private class TextCollector(private val page: Int, private val originX: Float, private val topY: Float) : PDFTextStripper() {
        val glyphs = mutableListOf<Glyph>()
        private val marked = ArrayDeque<Int?>()

        init {
            addOperator(BeginMarkedContentSequence())
            addOperator(BeginMarkedContentSequenceWithProperties())
            addOperator(EndMarkedContentSequence())
            sortByPosition = false
            setSuppressDuplicateOverlappingText(false)
        }

        override fun beginMarkedContentSequence(tag: COSName?, properties: COSDictionary?) {
            marked.addLast(properties?.getInt(COSName.MCID)?.takeIf { it >= 0 })
        }

        override fun endMarkedContentSequence() {
            marked.removeLastOrNull()
        }

        override fun processTextPosition(text: TextPosition) {
            val unicode = text.unicode ?: return
            val size = text.fontSizeInPt.takeIf { it > 0 } ?: text.fontSize
            val font = text.font
            val name = fontName(font)
            val color = runCatching { graphicsState.nonStrokingColor.toRGB() }.getOrDefault(0)
            val baseline = text.yDirAdj
            glyphs += Glyph(
                text = unicode,
                x = text.xDirAdj,
                top = baseline - size * ASCENT,
                width = text.widthDirAdj,
                fontSize = size,
                baseline = baseline,
                font = name,
                bold = isBold(font, name),
                italic = isItalic(font, name),
                color = color,
                mcid = marked.lastOrNull(),
            )
        }

        private fun fontName(font: PDFont?): String = font?.name ?: "Unknown"

        private fun isBold(font: PDFont?, name: String): Boolean {
            val lower = name.lowercase()
            if ("bold" in lower || lower.endsWith("-b") || lower.endsWith(",b") || "black" in lower || "heavy" in lower || "semibold" in lower) return true
            val descriptor = font?.fontDescriptor ?: return false
            return descriptor.isForceBold || descriptor.fontWeight >= 600f
        }

        private fun isItalic(font: PDFont?, name: String): Boolean {
            val lower = name.lowercase()
            if ("italic" in lower || "oblique" in lower || lower.endsWith("-i") || lower.endsWith(",i")) return true
            val descriptor = font?.fontDescriptor ?: return false
            return descriptor.isItalic || abs(descriptor.italicAngle) > 0.5f
        }

        private companion object {
            const val ASCENT = 0.78f
        }
    }

    /** Coleta linhas, retângulos e imagens, já na origem superior esquerda. */
    private class GraphicsCollector(
        page: PDPage,
        private val pageIndex: Int,
        private val originX: Float,
        private val topY: Float,
        private val imagesDir: File,
    ) : PDFGraphicsStreamEngine(page) {
        val strokes = mutableListOf<Stroke>()
        val fills = mutableListOf<Fill>()
        val images = mutableListOf<PdfImage>()
        private val subpaths = mutableListOf<MutableList<PointF>>()
        private var current = PointF()
        private val marked = ArrayDeque<Int?>()

        init {
            addOperator(BeginMarkedContentSequence())
            addOperator(BeginMarkedContentSequenceWithProperties())
            addOperator(EndMarkedContentSequence())
        }

        override fun beginMarkedContentSequence(tag: COSName?, properties: COSDictionary?) {
            marked.addLast(properties?.getInt(COSName.MCID)?.takeIf { it >= 0 })
        }

        override fun endMarkedContentSequence() {
            marked.removeLastOrNull()
        }

        private fun tx(x: Float) = x - originX
        private fun ty(y: Float) = topY - y

        override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {
            subpaths += mutableListOf(PointF(p0.x, p0.y), PointF(p1.x, p1.y), PointF(p2.x, p2.y), PointF(p3.x, p3.y), PointF(p0.x, p0.y))
            current = PointF(p0.x, p0.y)
        }

        override fun moveTo(x: Float, y: Float) {
            subpaths += mutableListOf(PointF(x, y))
            current = PointF(x, y)
        }

        override fun lineTo(x: Float, y: Float) {
            (subpaths.lastOrNull() ?: mutableListOf<PointF>().also { subpaths += it }) += PointF(x, y)
            current = PointF(x, y)
        }

        override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = lineTo(x3, y3)

        override fun getCurrentPoint(): PointF = current

        override fun closePath() {
            subpaths.lastOrNull()?.let { if (it.size > 1) it += PointF(it.first().x, it.first().y) }
        }

        override fun endPath() {
            subpaths.clear()
        }

        override fun clip(windingRule: Path.FillType) = Unit

        override fun strokePath() {
            val width = runCatching { graphicsState.lineWidth }.getOrDefault(1f)
            for (path in subpaths) {
                for (i in 1 until path.size) addStroke(path[i - 1], path[i], width)
            }
            subpaths.clear()
        }

        override fun fillPath(windingRule: Path.FillType) {
            val color = runCatching { graphicsState.nonStrokingColor.toRGB() }.getOrDefault(0)
            for (path in subpaths) {
                val xs = path.map { it.x }
                val ys = path.map { it.y }
                val w = xs.max() - xs.min()
                val h = ys.max() - ys.min()
                when {
                    path.size < 4 -> Unit
                    // Retângulo fino: é uma linha desenhada como preenchimento.
                    min(w, h) < THIN && max(w, h) > 2f -> {
                        if (w >= h) addStroke(PointF(xs.min(), (ys.min() + ys.max()) / 2), PointF(xs.max(), (ys.min() + ys.max()) / 2), h)
                        else addStroke(PointF((xs.min() + xs.max()) / 2, ys.min()), PointF((xs.min() + xs.max()) / 2, ys.max()), w)
                    }
                    else -> fills += Fill(tx(xs.min()), ty(ys.max()), w, h, color)
                }
            }
            subpaths.clear()
        }

        override fun fillAndStrokePath(windingRule: Path.FillType) {
            val copy = subpaths.map { it.toMutableList() }
            fillPath(windingRule)
            subpaths += copy
            strokePath()
        }

        override fun shadingFill(shadingName: COSName) = Unit

        private fun addStroke(a: PointF, b: PointF, width: Float) {
            val stroke = Stroke(tx(a.x), ty(a.y), tx(b.x), ty(b.y), max(width, 0.5f))
            if (stroke.isHorizontal || stroke.isVertical) strokes += stroke
        }

        override fun drawImage(image: PDImage) {
            val ctm = graphicsState.currentTransformationMatrix
            val corners = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).map { (ux, uy) -> ctm.transformPoint(ux, uy) }
            val xs = corners.map { it.x }
            val ys = corners.map { it.y }
            val left = tx(xs.min())
            val top = ty(ys.max())
            val width = xs.max() - xs.min()
            val height = ys.max() - ys.min()
            if (width < 4f || height < 4f) return
            val file = (image as? PDImageXObject)?.let { save(it) }
            images += PdfImage(left, top, width, height, file, marked.lastOrNull())
        }

        private fun save(image: PDImageXObject): String? = runCatching {
            val bitmap: Bitmap = image.image ?: return null
            val file = File(imagesDir, "img-$pageIndex-${images.size + 1}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            file.absolutePath
        }.getOrNull()

        private companion object {
            const val THIN = 2.5f
        }
    }
}
