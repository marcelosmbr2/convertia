package com.recast.app.data.engine.pdf

/**
 * O que lemos de um PDF, página a página, em pontos (1/72 pol.) com a origem no canto
 * superior esquerdo. É a entrada da reconstrução ([PdfReflow]); quem preenche é o leitor
 * de PDF (PdfBox-Android no app, PDFBox de desktop nos testes).
 */
data class Glyph(
    val text: String,
    val x: Float,
    /** Topo da caixa do glifo. */
    val top: Float,
    val width: Float,
    /** Tamanho da fonte em pontos (altura nominal da linha). */
    val fontSize: Float,
    val baseline: Float,
    val font: String,
    val bold: Boolean,
    val italic: Boolean,
    /** 0xRRGGBB. */
    val color: Int = 0,
    /** Identificador de conteúdo marcado (PDF tagueado), se houver. */
    val mcid: Int? = null,
) {
    val right: Float get() = x + width
    val bottom: Float get() = top + fontSize
}

/** Segmento de reta desenhado (bordas de tabela, sublinhados, réguas). */
data class Stroke(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val width: Float) {
    val isHorizontal: Boolean get() = kotlin.math.abs(y1 - y2) < 1.5f && kotlin.math.abs(x1 - x2) > 2f
    val isVertical: Boolean get() = kotlin.math.abs(x1 - x2) < 1.5f && kotlin.math.abs(y1 - y2) > 2f
}

/** Retângulo preenchido (fundo de célula, destaque). */
data class Fill(val x: Float, val y: Float, val width: Float, val height: Float, val color: Int)

data class PdfImage(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    /** PNG gravado pelo leitor; nulo se a imagem não pôde ser decodificada. */
    val file: String?,
    val mcid: Int? = null,
)

data class PdfPage(
    val index: Int,
    val width: Float,
    val height: Float,
    val glyphs: List<Glyph>,
    val strokes: List<Stroke> = emptyList(),
    val fills: List<Fill> = emptyList(),
    val images: List<PdfImage> = emptyList(),
)

/** Nó da árvore de estrutura de um PDF tagueado (tipos padrão: P, H1, L, LI, Table, TD, Figure…). */
data class StructNode(
    val type: String,
    val children: List<StructNode> = emptyList(),
    /** Conteúdo marcado deste nó: (página, MCID). */
    val content: List<Pair<Int, Int>> = emptyList(),
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    /** Texto alternativo (ActualText/Alt), quando o PDF o traz. */
    val actualText: String? = null,
)

data class PdfModel(val pages: List<PdfPage>, val structure: StructNode? = null)

// Saída da reconstrução --------------------------------------------------------------------

data class Run(val text: String, val bold: Boolean = false, val italic: Boolean = false) {
    fun isBlank() = text.isBlank()
}

enum class Align { LEFT, CENTER, RIGHT, JUSTIFY }

sealed interface Block

/** Parágrafo comum ([level] 0) ou título ([level] 1–6). */
data class Paragraph(val runs: List<Run>, val level: Int = 0, val align: Align = Align.LEFT) : Block {
    val text: String get() = runs.joinToString("") { it.text }
}

data class ListBlock(val ordered: Boolean, val items: List<List<Run>>) : Block

data class Cell(val blocks: List<Block>, val colSpan: Int = 1, val rowSpan: Int = 1)

data class TableRow(val cells: List<Cell>)

/** [columnWidths] em pontos; [bordered] quando o PDF desenhava a grade. */
data class Table(val rows: List<TableRow>, val columnWidths: List<Float>, val bordered: Boolean) : Block

data class ImageBlock(val file: String, val widthPt: Float, val heightPt: Float) : Block

data object PageBreak : Block

/** Documento reconstruído: páginas, margens e blocos na ordem de leitura. */
data class DocModel(
    val pageWidth: Float,
    val pageHeight: Float,
    val marginLeft: Float,
    val marginRight: Float,
    val marginTop: Float,
    val marginBottom: Float,
    val bodyFontSize: Float,
    val bodyFont: String,
    val header: List<Run> = emptyList(),
    val footer: List<Run> = emptyList(),
    val blocks: List<Block>,
) {
    /** Texto corrido, para TXT e para os testes. */
    fun plainText(): String = buildString {
        blocks.forEach { block ->
            when (block) {
                is Paragraph -> appendLine(block.text)
                is ListBlock -> block.items.forEachIndexed { i, runs -> appendLine((if (block.ordered) "${i + 1}. " else "• ") + runs.joinToString("") { it.text }) }
                is Table -> block.rows.forEach { row -> appendLine(row.cells.joinToString("\t") { cell -> cell.blocks.filterIsInstance<Paragraph>().joinToString(" ") { it.text } }) }
                is ImageBlock -> Unit
                PageBreak -> appendLine()
            }
        }
    }
}
