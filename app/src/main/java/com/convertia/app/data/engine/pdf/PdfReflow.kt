package com.convertia.app.data.engine.pdf

import kotlin.math.abs
import kotlin.math.max

/**
 * Reconstrói um documento editável a partir do que foi lido do PDF. Com árvore de
 * estrutura (PDF tagueado) a hierarquia vem dela ([TaggedReflow]); sem ela, a geometria
 * decide ([GeometricReflow]). Funções puras, testadas na JVM.
 */
object PdfReflow {

    fun reflow(model: PdfModel): DocModel {
        if (model.pages.isEmpty()) throw IllegalArgumentException("PDF sem páginas")
        val layout = PageLayout.of(model)
        val tagged = model.structure?.let { TaggedReflow(model, layout).reflow(it) }
        if (tagged != null) return tagged
        return GeometricReflow(model, layout).reflow()
    }
}

/** Métricas comuns às duas estratégias: margens, tamanho e fonte do corpo. */
internal class PageLayout private constructor(
    val pageWidth: Float,
    val pageHeight: Float,
    val textLeft: Float,
    val textRight: Float,
    val textTop: Float,
    val textBottom: Float,
    val bodyFontSize: Float,
    val bodyFont: String,
) {
    val textWidth: Float get() = textRight - textLeft

    fun toDoc(blocks: List<Block>, header: List<Run> = emptyList(), footer: List<Run> = emptyList()) = DocModel(
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        marginLeft = textLeft.coerceIn(MIN_MARGIN, pageWidth / 3),
        marginRight = (pageWidth - textRight).coerceIn(MIN_MARGIN, pageWidth / 3),
        marginTop = textTop.coerceIn(MIN_MARGIN, pageHeight / 3),
        marginBottom = (pageHeight - textBottom).coerceIn(MIN_MARGIN, pageHeight / 3),
        bodyFontSize = bodyFontSize,
        bodyFont = bodyFont,
        header = header,
        footer = footer,
        blocks = blocks,
    )

    companion object {
        private const val MIN_MARGIN = 20f

        fun of(model: PdfModel): PageLayout {
            val first = model.pages.first()
            val glyphs = model.pages.flatMap { it.glyphs }.filter { it.text.isNotBlank() }
            // Tamanho do corpo: o mais frequente, pesado pelo número de caracteres.
            val sizes = glyphs.groupingBy { (it.fontSize * 2).toInt() / 2f }.eachCount()
            val bodySize = sizes.maxByOrNull { it.value }?.key ?: 11f
            val fonts = glyphs.filter { abs(it.fontSize - bodySize) < 0.6f }.groupingBy { it.font }.eachCount()
            val bodyFont = fonts.maxByOrNull { it.value }?.key ?: glyphs.firstOrNull()?.font ?: "Liberation Serif"
            val lefts = glyphs.map { it.x }.sorted()
            val rights = glyphs.map { it.right }.sorted()
            val tops = glyphs.map { it.top }.sorted()
            val bottoms = glyphs.map { it.bottom }.sorted()
            fun percentile(list: List<Float>, p: Float, fallback: Float) =
                if (list.isEmpty()) fallback else list[(list.lastIndex * p).toInt().coerceIn(0, list.lastIndex)]
            return PageLayout(
                pageWidth = first.width,
                pageHeight = first.height,
                textLeft = percentile(lefts, 0.02f, 56f),
                textRight = percentile(rights, 0.98f, first.width - 56f),
                textTop = percentile(tops, 0.01f, 56f),
                textBottom = percentile(bottoms, 0.99f, first.height - 56f),
                bodyFontSize = bodySize,
                bodyFont = bodyFont,
            )
        }
    }
}

/** Palavras → runs (negrito/itálico), juntando linhas com hifenização no fim. */
internal object Runs {
    fun of(lines: List<List<Word>>): List<Run> {
        val runs = mutableListOf<Run>()
        var pendingHyphen = false
        lines.forEachIndexed { lineIndex, words ->
            words.forEachIndexed { wordIndex, word ->
                var text = word.text
                val first = wordIndex == 0
                val separator = when {
                    lineIndex == 0 && first -> ""
                    first && pendingHyphen -> ""
                    else -> " "
                }
                pendingHyphen = false
                val isLast = wordIndex == words.lastIndex
                val nextLine = lines.getOrNull(lineIndex + 1)
                if (isLast && text.length > 1 && text.endsWith("-") && nextLine?.firstOrNull()?.text?.firstOrNull()?.isLowerCase() == true) {
                    text = text.dropLast(1)
                    pendingHyphen = true
                }
                val last = runs.lastOrNull()
                if (last != null && last.bold == word.bold && last.italic == word.italic) {
                    runs[runs.lastIndex] = last.copy(text = last.text + separator + text)
                } else {
                    if (last != null && separator.isNotEmpty()) runs[runs.lastIndex] = last.copy(text = last.text + separator)
                    runs += Run(text, word.bold, word.italic)
                }
            }
        }
        return runs.filterNot { it.text.isEmpty() }
    }
}

/** Marcadores de lista no começo de uma linha. */
internal object ListMarkers {
    private val bullet = Regex("""^[•◦▪■●○‣⁃\-–—*·]$""")
    private val number = Regex("""^(\d{1,3}|[a-zA-Z]|[ivxlcIVXLC]{1,5})[.)]$""")
    private val inline = Regex("""^([•◦▪■●○‣⁃·]|(\d{1,3}|[a-zA-Z]|[ivxlcIVXLC]{1,5})[.)])\s+""")

    /** Devolve (ordenada?, palavras sem o marcador) quando a linha começa com marcador. */
    fun strip(words: List<Word>): Pair<Boolean, List<Word>>? {
        val first = words.firstOrNull() ?: return null
        if (words.size >= 2 && (bullet.matches(first.text) || number.matches(first.text))) {
            return number.matches(first.text) to words.drop(1)
        }
        val match = inline.find(first.text) ?: return null
        val rest = first.text.substring(match.value.length)
        val ordered = match.groupValues[2].isNotEmpty()
        val remaining = if (rest.isEmpty()) words.drop(1) else listOf(first.copy(text = rest)) + words.drop(1)
        return if (remaining.isEmpty()) null else ordered to remaining
    }
}

/** Sem tags: linhas → títulos, listas e parágrafos pela geometria. */
internal class GeometricReflow(private val model: PdfModel, private val layout: PageLayout) {

    fun reflow(): DocModel {
        val blocks = mutableListOf<Block>()
        for (page in model.pages) {
            val lines = TextLines.build(page).sortedWith(compareBy({ it.top }, { it.x }))
            blocks += blocksOf(page, lines)
        }
        return layout.toDoc(blocks)
    }

    private fun blocksOf(page: PdfPage, lines: List<Line>): List<Block> {
        val body = layout.bodyFontSize
        val headingSizes = lines.filter { isHeading(it) }.map { (it.fontSize * 2).toInt() / 2f }.distinct().sortedDescending()
        val blocks = mutableListOf<Block>()
        val images = page.images.filter { it.file != null }.sortedBy { it.y }.toMutableList()
        var paragraph: MutableList<Line>? = null
        var list: Pair<Boolean, MutableList<MutableList<Word>>>? = null
        var listItemIndent = 0f

        fun flushParagraph() {
            paragraph?.let { blocks += paragraphOf(it) }
            paragraph = null
        }

        fun flushList() {
            list?.let { (ordered, items) -> blocks += ListBlock(ordered, items.map { Runs.of(listOf(it)) }) }
            list = null
        }

        fun flushImagesAbove(top: Float) {
            while (images.isNotEmpty() && images.first().y + images.first().height / 2 < top) {
                val image = images.removeAt(0)
                flushParagraph(); flushList()
                blocks += ImageBlock(image.file!!, image.width, image.height)
            }
        }

        var previous: Line? = null
        for (line in lines) {
            flushImagesAbove(line.top)
            val words = line.words
            val marker = ListMarkers.strip(words)
            when {
                isHeading(line) -> {
                    flushParagraph(); flushList()
                    val level = (headingSizes.indexOf((line.fontSize * 2).toInt() / 2f) + 1).coerceIn(1, 6)
                    blocks += Paragraph(Runs.of(listOf(words)), level, alignmentOf(listOf(line)))
                }
                marker != null -> {
                    flushParagraph()
                    val (ordered, rest) = marker
                    if (list == null || list!!.first != ordered) { flushList(); list = ordered to mutableListOf() }
                    list!!.second += rest.toMutableList()
                    listItemIndent = rest.first().x
                }
                list != null && previous != null && continuesList(previous, line, listItemIndent) -> {
                    // A segunda linha de um item: continua o item, não vira parágrafo.
                    list!!.second.last() += words
                }
                else -> {
                    flushList()
                    val current = paragraph
                    if (current != null && previous != null && continuesParagraph(previous, line, current)) {
                        current += line
                    } else {
                        flushParagraph()
                        paragraph = mutableListOf(line)
                    }
                }
            }
            previous = line
        }
        flushParagraph(); flushList()
        flushImagesAbove(Float.MAX_VALUE)
        return blocks
    }

    private fun isHeading(line: Line): Boolean {
        val body = layout.bodyFontSize
        if (line.text.length > 120) return false
        if (line.fontSize >= body * 1.18f) return true
        val short = (line.right - line.x) < layout.textWidth * 0.75f
        return line.chunks.all { it.bold } && line.fontSize >= body * 1.02f && short && !line.text.trimEnd().endsWith(".")
    }

    private fun continuesList(previous: Line, line: Line, indent: Float): Boolean {
        val gap = line.top - previous.bottom
        return gap < layout.bodyFontSize * 0.9f && line.x >= indent - 2f && abs(line.fontSize - previous.fontSize) < 1f
    }

    private fun continuesParagraph(previous: Line, line: Line, paragraph: List<Line>): Boolean {
        val body = layout.bodyFontSize
        val gap = line.top - previous.bottom
        if (gap > body * 0.95f || gap < -body) return false
        if (abs(line.fontSize - previous.fontSize) > 1f) return false
        // Recuo de primeira linha: o parágrafo anterior acabou.
        val paragraphLeft = paragraph.minOf { it.x }
        if (line.x > paragraphLeft + body * 0.8f && paragraph.size >= 1 && line.x > previous.x + body * 0.8f) return false
        // Linha anterior curta (acabou bem antes da margem direita): fim de parágrafo, a não
        // ser que a primeira palavra da linha seguinte simplesmente não coubesse lá.
        val rightEdge = max(layout.textRight, paragraph.maxOf { it.right })
        val remaining = rightEdge - previous.right
        val firstWord = line.words.firstOrNull()?.width ?: 0f
        if (remaining > body * 1.6f && remaining > firstWord + body * 0.4f && line.x <= paragraphLeft + body * 0.8f) return false
        return true
    }

    private fun paragraphOf(lines: List<Line>): Paragraph =
        Paragraph(Runs.of(lines.map { it.words }), 0, alignmentOf(lines))

    private fun alignmentOf(lines: List<Line>): Align {
        val center = (layout.textLeft + layout.textRight) / 2
        val centered = lines.all { abs((it.x + it.right) / 2 - center) < 4f && (it.right - it.x) < layout.textWidth * 0.9f }
        if (centered && lines.all { it.x > layout.textLeft + 8f }) return Align.CENTER
        val rightAligned = lines.all { abs(it.right - layout.textRight) < 3f && it.x > layout.textLeft + layout.bodyFontSize * 2 }
        if (rightAligned) return Align.RIGHT
        if (lines.size >= 2 && lines.dropLast(1).all { abs(it.right - layout.textRight) < 3f }) return Align.JUSTIFY
        return Align.LEFT
    }
}

/** Com tags: a hierarquia vem da árvore de estrutura; o texto, dos glifos marcados. */
internal class TaggedReflow(private val model: PdfModel, private val layout: PageLayout) {

    private val glyphsByContent: Map<Pair<Int, Int>, List<Glyph>> = model.pages.flatMap { page ->
        page.glyphs.filter { it.mcid != null }.map { (page.index to it.mcid!!) to it }
    }.groupBy({ it.first }, { it.second })

    private val imagesByContent: Map<Pair<Int, Int>, PdfImage> = model.pages.flatMap { page ->
        page.images.filter { it.mcid != null && it.file != null }.map { (page.index to it.mcid!!) to it }
    }.toMap()

    private var covered = 0

    /** `null` quando a árvore cobre pouco do texto (tags quebradas): a geometria assume. */
    fun reflow(root: StructNode): DocModel? {
        val blocks = blocksOf(root)
        val total = model.pages.sumOf { page -> page.glyphs.count { it.text.isNotBlank() } }
        if (total == 0 || covered < total * MIN_COVERAGE) return null
        return layout.toDoc(blocks)
    }

    private fun blocksOf(node: StructNode): List<Block> {
        val blocks = mutableListOf<Block>()
        for (child in node.children) blocks += blockOf(child)
        return blocks
    }

    private fun blockOf(node: StructNode): List<Block> = when (node.type) {
        "P", "Note", "Quote", "BlockQuote", "Caption", "Code", "Formula", "TOCI", "BibEntry", "Index" -> paragraphs(node, 0)
        "H", "Title" -> paragraphs(node, 1)
        "H1", "H2", "H3", "H4", "H5", "H6" -> paragraphs(node, node.type.last().digitToInt())
        "L" -> listOf(list(node))
        "Table" -> listOf(table(node))
        "Figure" -> figure(node)
        "Span", "Link", "Reference", "Annot", "Form", "Lbl", "LBody", "LI", "TR", "TD", "TH" -> paragraphs(node, 0)
        else -> if (node.children.isEmpty() && node.content.isNotEmpty()) paragraphs(node, 0) else blocksOf(node)
    }

    /** Um nó de texto: parágrafo com o conteúdo dele e dos filhos em linha; filhos de bloco vêm depois. */
    private fun paragraphs(node: StructNode, level: Int): List<Block> {
        val inline = collectInline(node)
        val result = mutableListOf<Block>()
        val runs = runsOf(inline)
        // Títulos marcados como parágrafo comum (acontece em PDFs vindos de HTML): a fonte denuncia.
        val effectiveLevel = if (level == 0) promotedHeadingLevel(inline, runs) else level
        if (runs.isNotEmpty()) result += Paragraph(runs, effectiveLevel, Align.LEFT)
        node.children.filterNot { isInline(it) }.forEach { result += blockOf(it) }
        return result
    }

    private fun promotedHeadingLevel(content: List<Pair<Int, Int>>, runs: List<Run>): Int {
        val glyphs = content.flatMap { glyphsByContent[it].orEmpty() }.filter { it.text.isNotBlank() }
        if (glyphs.isEmpty() || runs.sumOf { it.text.length } > 120) return 0
        val size = glyphs.map { it.fontSize }.average().toFloat()
        val ratio = size / layout.bodyFontSize
        val bold = glyphs.count { it.bold } * 2 > glyphs.size
        return when {
            ratio >= 1.45f -> 1
            ratio >= 1.18f -> 2
            bold && ratio >= 1.02f && runs.sumOf { it.text.length } < 80 && !runs.last().text.trimEnd().endsWith(".") -> 3
            else -> 0
        }
    }

    private fun isInline(node: StructNode) = node.type in INLINE_TYPES || (node.children.isEmpty() && node.type !in setOf("Figure", "Table", "L"))

    private fun collectInline(node: StructNode): List<Pair<Int, Int>> =
        node.content + node.children.filter { isInline(it) }.flatMap { collectInline(it) }

    private fun runsOf(content: List<Pair<Int, Int>>): List<Run> {
        val glyphs = content.flatMap { glyphsByContent[it].orEmpty() }
        if (glyphs.isEmpty()) return emptyList()
        covered += glyphs.count { it.text.isNotBlank() }
        val lines = glyphs.groupBy { it.mcid }.let { model.pages.filter { p -> content.any { it.first == p.index } } }
            .flatMap { page -> TextLines.build(page, glyphs.filter { g -> page.glyphs.any { it === g } }) }
            .sortedWith(compareBy({ it.page }, { it.top }, { it.x }))
        return Runs.of(lines.map { it.words })
    }

    private fun list(node: StructNode): Block {
        val items = mutableListOf<List<Run>>()
        var ordered = false
        for (item in node.children) {
            if (item.type != "LI") { items += runsOf(collectInline(item)); continue }
            val label = item.children.firstOrNull { it.type == "Lbl" }
            val body = item.children.filter { it.type != "Lbl" }
            label?.let { l -> runsOf(collectInline(l)).joinToString("") { it.text }.trim() }
                ?.let { text -> if (text.firstOrNull()?.isLetterOrDigit() == true) ordered = true }
            val runs = body.flatMap { b -> paragraphs(b, 0) }.filterIsInstance<Paragraph>().flatMap { it.runs }
            if (runs.isNotEmpty()) items += runs else if (label != null) items += runsOf(collectInline(label))
        }
        return ListBlock(ordered, items)
    }

    private fun table(node: StructNode): Block {
        val rows = mutableListOf<TableRow>()
        fun visitRows(n: StructNode) {
            for (child in n.children) {
                when (child.type) {
                    "TR" -> rows += TableRow(child.children.filter { it.type == "TD" || it.type == "TH" }.map { cell ->
                        Cell(blocksOf(cell).ifEmpty { paragraphs(cell, 0) }, cell.colSpan.coerceAtLeast(1), cell.rowSpan.coerceAtLeast(1))
                    })
                    "THead", "TBody", "TFoot" -> visitRows(child)
                }
            }
        }
        visitRows(node)
        val columns = rows.maxOfOrNull { row -> row.cells.sumOf { it.colSpan } } ?: 1
        val widths = List(columns) { layout.textWidth / columns }
        return Table(rows, widths, bordered = true)
    }

    private fun figure(node: StructNode): List<Block> {
        val images = collectInline(node).mapNotNull { imagesByContent[it] }
        if (images.isNotEmpty()) return images.map { ImageBlock(it.file!!, it.width, it.height) }
        val alt = node.actualText?.takeIf { it.isNotBlank() }
        return if (alt != null) listOf(Paragraph(listOf(Run(alt, italic = true)))) else paragraphs(node, 0)
    }

    private companion object {
        const val MIN_COVERAGE = 0.6
        val INLINE_TYPES = setOf("Span", "Link", "Reference", "Annot", "Form", "Lbl", "Code", "Quote")
    }
}

