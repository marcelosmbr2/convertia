package com.convertia.app.data.engine.ocr

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import com.convertia.app.R
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

/**
 * PDF pesquisável: a imagem na página e, por cima, o texto reconhecido invisível
 * (modo de renderização 3), cada linha ajustada à caixa que o OCR achou. É o que leitores
 * de PDF e o "Localizar" do celular enxergam; a aparência fica igual à da foto.
 */
class SearchablePdfWriter(private val context: Context) {

    // A fonte do app (TTF em res/font) é lida como bytes para virar a fonte do PDF.
    @SuppressLint("ResourceType")
    fun write(pages: List<Pair<Bitmap, List<OcrLine>>>, output: File, jpegQuality: Int) {
        PDDocument().use { document ->
            val font = context.resources.openRawResource(R.font.inter_regular).use { PDType0Font.load(document, it, false) }
            for ((bitmap, lines) in pages) {
                val scale = OcrEngine.pageScale(bitmap.width)
                val pageWidth = bitmap.width * scale
                val pageHeight = bitmap.height * scale
                val page = PDPage(PDRectangle(pageWidth, pageHeight))
                document.addPage(page)
                val image = if (bitmap.hasAlpha()) LosslessFactory.createFromImage(document, bitmap) else JPEGFactory.createFromImage(document, bitmap, jpegQuality / 100f)
                PDPageContentStream(document, page).use { content ->
                    content.drawImage(image, 0f, 0f, pageWidth, pageHeight)
                    content.setRenderingMode(RenderingMode.NEITHER)
                    for (line in lines) {
                        val text = line.text.filter { it != '\n' && it != '\r' }
                        if (text.isBlank()) continue
                        val boxHeight = (line.bottom - line.top) * scale
                        val fontSize = (boxHeight / 1.25f).coerceAtLeast(2f)
                        val textWidth = runCatching { font.getStringWidth(text) / 1000f * fontSize }.getOrDefault(0f)
                        val boxWidth = (line.right - line.left) * scale
                        val stretch = if (textWidth > 0) boxWidth / textWidth else 1f
                        val x = line.left * scale
                        // Origem do PDF embaixo: y cresce para cima.
                        val baselineY = pageHeight - line.bottom * scale + boxHeight * 0.18f
                        content.beginText()
                        content.setFont(font, fontSize)
                        content.setTextMatrix(Matrix(stretch, 0f, 0f, 1f, x, baselineY))
                        runCatching { content.showText(text) }
                            .onFailure { runCatching { content.showText(text.filter { ch -> font.hasGlyph(ch.code) }) } }
                        content.endText()
                    }
                }
            }
            document.save(output)
        }
    }

}
