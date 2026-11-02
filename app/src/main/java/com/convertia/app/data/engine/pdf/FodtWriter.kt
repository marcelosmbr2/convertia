package com.convertia.app.data.engine.pdf

import java.util.Base64
import java.util.Locale

/**
 * Escreve o documento reconstruído como ODT plano (`.fodt`), que o LibreOffice abre e
 * exporta para DOCX, RTF, EPUB etc. Fontes do PDF viram as equivalentes métricas que o
 * LibreOffice embutido traz (Calibri → Carlito, Cambria → Caladea, Arial/Times → Liberation).
 */
object FodtWriter {

    /** [loadImage] lê os bytes PNG de um [ImageBlock.file]. */
    fun write(doc: DocModel, loadImage: (String) -> ByteArray?): String {
        val b = StringBuilder()
        val font = libreOfficeFont(doc.bodyFont)
        val body = doc.bodyFontSize.coerceIn(6f, 72f)
        b.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        b.append("""<office:document xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0" office:version="1.3" office:mimetype="application/vnd.oasis.opendocument.text">""").append('\n')
        b.append("<office:font-face-decls>")
        for (f in setOf(font, "Liberation Serif", "Liberation Sans", "Liberation Mono", "Carlito", "Caladea")) {
            b.append("""<style:font-face style:name="${esc(f)}" svg:font-family="&apos;${esc(f)}&apos;"/>""")
        }
        b.append("</office:font-face-decls>\n")

        b.append("<office:styles>\n")
        b.append("""<style:default-style style:family="paragraph"><style:paragraph-properties fo:margin-bottom="${pt(body * 0.5f)}" fo:line-height="115%"/><style:text-properties style:font-name="${esc(font)}" fo:font-size="${pt(body)}" fo:language="pt" fo:country="BR"/></style:default-style>""").append('\n')
        b.append("""<style:style style:name="Standard" style:family="paragraph"/>""").append('\n')
        val headingScale = listOf(1.7f, 1.4f, 1.2f, 1.1f, 1.05f, 1f)
        for (level in 1..6) {
            b.append("""<style:style style:name="Heading_${level}" style:display-name="Heading $level" style:family="paragraph" style:parent-style-name="Standard" style:default-outline-level="$level"><style:paragraph-properties fo:margin-top="${pt(body * 0.9f)}" fo:margin-bottom="${pt(body * 0.4f)}" fo:keep-with-next="always"/><style:text-properties fo:font-size="${pt(body * headingScale[level - 1])}" fo:font-weight="bold"/></style:style>""").append('\n')
        }
        b.append("""<style:style style:name="Header" style:family="paragraph" style:parent-style-name="Standard"/>""")
        b.append("""<style:style style:name="Footer" style:family="paragraph" style:parent-style-name="Standard"><style:paragraph-properties fo:text-align="center"/></style:style>""").append('\n')
        b.append("""<text:list-style style:name="L_bullet">""")
        for (level in 1..3) b.append("""<text:list-level-style-bullet text:level="$level" text:bullet-char="•"><style:list-level-properties text:list-level-position-and-space-mode="label-alignment"><style:list-level-label-alignment text:label-followed-by="listtab" text:list-tab-stop-position="${pt(body * 1.5f * level)}" fo:text-indent="-${pt(body * 0.8f)}" fo:margin-left="${pt(body * 1.5f * level)}"/></style:list-level-properties></text:list-level-style-bullet>""")
        b.append("</text:list-style>\n")
        b.append("""<text:list-style style:name="L_number">""")
        for (level in 1..3) b.append("""<text:list-level-style-number text:level="$level" style:num-suffix="." style:num-format="1"><style:list-level-properties text:list-level-position-and-space-mode="label-alignment"><style:list-level-label-alignment text:label-followed-by="listtab" text:list-tab-stop-position="${pt(body * 1.5f * level)}" fo:text-indent="-${pt(body * 0.8f)}" fo:margin-left="${pt(body * 1.5f * level)}"/></style:list-level-properties></text:list-level-style-number>""")
        b.append("</text:list-style>\n")
        b.append("</office:styles>\n")

        // Estilos automáticos: alinhamentos, ênfase, tabelas e molduras.
        val tables = doc.blocks.flatMap { collectTables(it) }
        b.append("<office:automatic-styles>\n")
        for (align in Align.entries) {
            b.append("""<style:style style:name="P_${align.name}" style:family="paragraph" style:parent-style-name="Standard"><style:paragraph-properties fo:text-align="${alignValue(align)}"/></style:style>""")
        }
        b.append("""<style:style style:name="T_b" style:family="text"><style:text-properties fo:font-weight="bold"/></style:style>""")
        b.append("""<style:style style:name="T_i" style:family="text"><style:text-properties fo:font-style="italic"/></style:style>""")
        b.append("""<style:style style:name="T_bi" style:family="text"><style:text-properties fo:font-weight="bold" fo:font-style="italic"/></style:style>""").append('\n')
        tables.forEachIndexed { index, table ->
            val total = table.columnWidths.sum().takeIf { it > 0 } ?: 1f
            b.append("""<style:style style:name="Tbl$index" style:family="table"><style:table-properties style:width="${pt(table.columnWidths.sum())}" table:align="left" fo:margin-top="${pt(body * 0.3f)}" fo:margin-bottom="${pt(body * 0.5f)}"/></style:style>""")
            table.columnWidths.forEachIndexed { c, width ->
                b.append("""<style:style style:name="Tbl${index}_C$c" style:family="table-column"><style:table-column-properties style:column-width="${pt(width)}" style:rel-column-width="${(width / total * 65535).toInt()}*"/></style:style>""")
            }
            val border = if (table.bordered) "0.5pt solid #000000" else "none"
            b.append("""<style:style style:name="Tbl${index}_Cell" style:family="table-cell"><style:table-cell-properties fo:border="$border" fo:padding="0.08cm"/></style:style>""").append('\n')
        }
        b.append("""<style:style style:name="Fr" style:family="graphic"><style:graphic-properties style:wrap="none" style:horizontal-pos="center" style:horizontal-rel="paragraph" fo:border="none"/></style:style>""")
        b.append("""<style:page-layout style:name="pm1"><style:page-layout-properties fo:page-width="${pt(doc.pageWidth)}" fo:page-height="${pt(doc.pageHeight)}" fo:margin-top="${pt(doc.marginTop)}" fo:margin-bottom="${pt(doc.marginBottom)}" fo:margin-left="${pt(doc.marginLeft)}" fo:margin-right="${pt(doc.marginRight)}" style:print-orientation="${if (doc.pageWidth > doc.pageHeight) "landscape" else "portrait"}"/>""")
        if (doc.header.isNotEmpty()) b.append("""<style:header-style><style:header-footer-properties fo:min-height="0cm" fo:margin-bottom="${pt(body * 0.6f)}"/></style:header-style>""")
        if (doc.footer.isNotEmpty()) b.append("""<style:footer-style><style:header-footer-properties fo:min-height="0cm" fo:margin-top="${pt(body * 0.6f)}"/></style:footer-style>""")
        b.append("</style:page-layout>\n")
        b.append("</office:automatic-styles>\n")

        b.append("""<office:master-styles><style:master-page style:name="Standard" style:page-layout-name="pm1">""")
        if (doc.header.isNotEmpty()) b.append("<style:header>").append(paragraph(doc.header, "Header")).append("</style:header>")
        if (doc.footer.isNotEmpty()) b.append("<style:footer>").append(footer(doc.footer)).append("</style:footer>")
        b.append("</style:master-page></office:master-styles>\n")

        b.append("<office:body><office:text>\n")
        var tableIndex = 0
        fun writeBlocks(blocks: List<Block>) {
            for (block in blocks) {
                when (block) {
                    is Paragraph -> b.append(paragraphBlock(block)).append('\n')
                    is ListBlock -> {
                        b.append("""<text:list text:style-name="${if (block.ordered) "L_number" else "L_bullet"}">""")
                        block.items.forEach { item -> b.append("<text:list-item>").append(paragraph(item, "Standard")).append("</text:list-item>") }
                        b.append("</text:list>\n")
                    }
                    is Table -> {
                        val index = tableIndex++
                        b.append("""<table:table table:name="Tabela${index + 1}" table:style-name="Tbl$index">""")
                        block.columnWidths.indices.forEach { c -> b.append("""<table:table-column table:style-name="Tbl${index}_C$c"/>""") }
                        for (row in block.rows) {
                            b.append("<table:table-row>")
                            for (cell in row.cells) {
                                b.append("""<table:table-cell table:style-name="Tbl${index}_Cell" office:value-type="string"""")
                                if (cell.colSpan > 1) b.append(""" table:number-columns-spanned="${cell.colSpan}"""")
                                if (cell.rowSpan > 1) b.append(""" table:number-rows-spanned="${cell.rowSpan}"""")
                                b.append('>')
                                if (cell.blocks.isEmpty()) b.append("<text:p/>") else writeBlocks(cell.blocks)
                                b.append("</table:table-cell>")
                                repeat(cell.colSpan - 1) { b.append("<table:covered-table-cell/>") }
                            }
                            b.append("</table:table-row>")
                        }
                        b.append("</table:table>\n")
                    }
                    is ImageBlock -> {
                        val bytes = loadImage(block.file) ?: continue
                        val maxWidth = doc.pageWidth - doc.marginLeft - doc.marginRight
                        val scale = if (block.widthPt > maxWidth) maxWidth / block.widthPt else 1f
                        b.append("""<text:p text:style-name="P_CENTER"><draw:frame draw:style-name="Fr" draw:name="Imagem${tableIndex}_${b.length}" text:anchor-type="as-char" svg:width="${pt(block.widthPt * scale)}" svg:height="${pt(block.heightPt * scale)}"><draw:image><office:binary-data>""")
                        b.append(Base64.getMimeEncoder(76, "\n".toByteArray()).encodeToString(bytes))
                        b.append("</office:binary-data></draw:image></draw:frame></text:p>\n")
                    }
                    PageBreak -> b.append("""<text:p text:style-name="Standard"><text:soft-page-break/></text:p>""").append('\n')
                }
            }
        }
        writeBlocks(doc.blocks)
        b.append("</office:text></office:body></office:document>\n")
        return b.toString()
    }

    private fun paragraphBlock(p: Paragraph): String =
        if (p.level in 1..6) {
            // O estilo de título já é negrito; marcar de novo só suja o documento.
            """<text:h text:style-name="Heading_${p.level}" text:outline-level="${p.level}">${runs(p.runs.map { it.copy(bold = false) })}</text:h>"""
        } else {
            paragraph(p.runs, if (p.align == Align.LEFT) "Standard" else "P_${p.align.name}")
        }

    private fun paragraph(runs: List<Run>, style: String) = """<text:p text:style-name="$style">${runs(runs)}</text:p>"""

    /** Rodapé: um número solto vira o campo de número de página. */
    private fun footer(runs: List<Run>): String {
        val text = runs.joinToString("") { it.text }
        val replaced = PAGE_NUMBER.replace(esc(text)) { m -> "${m.groupValues[1]}<text:page-number text:select-page=\"current\">1</text:page-number>" }
        return """<text:p text:style-name="Footer">$replaced</text:p>"""
    }

    private fun runs(runs: List<Run>): String = runs.joinToString("") { run ->
        val text = textContent(run.text)
        when {
            run.bold && run.italic -> """<text:span text:style-name="T_bi">$text</text:span>"""
            run.bold -> """<text:span text:style-name="T_b">$text</text:span>"""
            run.italic -> """<text:span text:style-name="T_i">$text</text:span>"""
            else -> text
        }
    }

    /** Texto com espaços repetidos e tabulações preservados como manda o ODF. */
    private fun textContent(text: String): String {
        val escaped = esc(text).replace("\t", "<text:tab/>")
        return Regex("  +").replace(escaped) { m -> " <text:s text:c=\"${m.value.length - 1}\"/>" }
    }

    private fun collectTables(block: Block): List<Table> = when (block) {
        is Table -> listOf(block) + block.rows.flatMap { r -> r.cells.flatMap { c -> c.blocks.flatMap(::collectTables) } }
        else -> emptyList()
    }

    private fun alignValue(align: Align) = when (align) {
        Align.LEFT -> "start"
        Align.CENTER -> "center"
        Align.RIGHT -> "end"
        Align.JUSTIFY -> "justify"
    }

    /** Pontos → "12.5pt". */
    private fun pt(value: Float) = String.format(Locale.ROOT, "%.2fpt", value)

    fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Nome de fonte do PDF (sem o prefixo de subconjunto) → fonte que o LibreOffice embutido tem. */
    fun libreOfficeFont(pdfFont: String): String {
        val name = pdfFont.substringAfter('+').lowercase()
        return when {
            "calibri" in name -> "Carlito"
            "cambria" in name -> "Caladea"
            listOf("courier", "mono", "consolas", "menlo").any { it in name } -> "Liberation Mono"
            listOf("times", "georgia", "garamond", "serif", "book", "roman", "minion", "palatino", "cambria").any { it in name } -> "Liberation Serif"
            listOf("arial", "helvetica", "verdana", "tahoma", "segoe", "roboto", "sans", "calibri", "open", "lato", "dejavu").any { it in name } -> "Liberation Sans"
            else -> "Liberation Serif"
        }
    }

    private val PAGE_NUMBER = Regex("""(^|\D)\d{1,4}(?!\d)""")
}
