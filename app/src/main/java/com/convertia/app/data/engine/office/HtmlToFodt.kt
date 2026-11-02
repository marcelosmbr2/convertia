package com.convertia.app.data.engine.office

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.File
import java.util.Base64
import java.util.Locale

/**
 * HTML → ODT plano (`.fodt`). O LibreOffice do Android não abre HTML (o módulo Writer/Web
 * não entra no build), mas abre ODT plano; então páginas HTML e os capítulos de EPUB
 * passam por aqui antes. Mantém títulos, parágrafos, ênfase, links, listas, tabelas,
 * imagens (embutidas em base64) e quebras de página. Código puro (jsoup), testado na JVM.
 */
object HtmlToFodt {

    /** [loadImage] resolve o `src` de uma imagem (relativo ao HTML) em bytes. */
    fun convert(html: String, title: String?, language: String?, loadImage: (String) -> ByteArray?): String {
        val document = Jsoup.parse(html)
        val builder = Builder(loadImage)
        builder.walkBlocks(document.body())
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<office:document xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0" office:version="1.3" office:mimetype="application/vnd.oasis.opendocument.text">""").append('\n')
            append("<office:meta>")
            (title ?: document.title().takeIf { it.isNotBlank() })?.let { append("<dc:title>").append(esc(it)).append("</dc:title>") }
            language?.let { append("<dc:language>").append(esc(it)).append("</dc:language>") }
            append("</office:meta>\n")
            append("<office:font-face-decls><style:font-face style:name=\"Liberation Serif\" svg:font-family=\"&apos;Liberation Serif&apos;\"/><style:font-face style:name=\"Liberation Mono\" svg:font-family=\"&apos;Liberation Mono&apos;\"/></office:font-face-decls>\n")
            append("<office:styles>\n")
            append("""<style:default-style style:family="paragraph"><style:paragraph-properties fo:margin-bottom="6pt" fo:line-height="120%"/><style:text-properties style:font-name="Liberation Serif" fo:font-size="12pt"/></style:default-style>""")
            append("""<style:style style:name="Standard" style:family="paragraph"/>""")
            val scale = listOf(1.7f, 1.4f, 1.2f, 1.1f, 1.05f, 1f)
            for (level in 1..6) {
                append("""<style:style style:name="Heading_$level" style:display-name="Heading $level" style:family="paragraph" style:parent-style-name="Standard" style:default-outline-level="$level"><style:paragraph-properties fo:margin-top="${pt(12 * 0.9f)}" fo:margin-bottom="${pt(12 * 0.4f)}" fo:keep-with-next="always"/><style:text-properties fo:font-size="${pt(12 * scale[level - 1])}" fo:font-weight="bold"/></style:style>""")
            }
            append("""<style:style style:name="Quote" style:family="paragraph" style:parent-style-name="Standard"><style:paragraph-properties fo:margin-left="1cm" fo:margin-right="1cm"/><style:text-properties fo:font-style="italic"/></style:style>""")
            append("""<style:style style:name="Code" style:family="paragraph" style:parent-style-name="Standard"><style:text-properties style:font-name="Liberation Mono" fo:font-size="10pt"/></style:style>""")
            append("""<text:list-style style:name="L_bullet">""")
            for (level in 1..5) append("""<text:list-level-style-bullet text:level="$level" text:bullet-char="•"><style:list-level-properties text:list-level-position-and-space-mode="label-alignment"><style:list-level-label-alignment text:label-followed-by="listtab" text:list-tab-stop-position="${pt(18f * level)}" fo:text-indent="-${pt(9f)}" fo:margin-left="${pt(18f * level)}"/></style:list-level-properties></text:list-level-style-bullet>""")
            append("</text:list-style>")
            append("""<text:list-style style:name="L_number">""")
            for (level in 1..5) append("""<text:list-level-style-number text:level="$level" style:num-suffix="." style:num-format="1"><style:list-level-properties text:list-level-position-and-space-mode="label-alignment"><style:list-level-label-alignment text:label-followed-by="listtab" text:list-tab-stop-position="${pt(18f * level)}" fo:text-indent="-${pt(9f)}" fo:margin-left="${pt(18f * level)}"/></style:list-level-properties></text:list-level-style-number>""")
            append("</text:list-style>\n")
            append("</office:styles>\n")
            append("<office:automatic-styles>\n")
            append("""<style:style style:name="T_b" style:family="text"><style:text-properties fo:font-weight="bold"/></style:style>""")
            append("""<style:style style:name="T_i" style:family="text"><style:text-properties fo:font-style="italic"/></style:style>""")
            append("""<style:style style:name="T_bi" style:family="text"><style:text-properties fo:font-weight="bold" fo:font-style="italic"/></style:style>""")
            append("""<style:style style:name="T_u" style:family="text"><style:text-properties style:text-underline-style="solid" style:text-underline-width="auto" style:text-underline-color="font-color"/></style:style>""")
            append("""<style:style style:name="T_s" style:family="text"><style:text-properties style:text-line-through-style="solid"/></style:style>""")
            append("""<style:style style:name="T_code" style:family="text"><style:text-properties style:font-name="Liberation Mono"/></style:style>""")
            append("""<style:style style:name="T_sup" style:family="text"><style:text-properties style:text-position="super 58%"/></style:style>""")
            append("""<style:style style:name="T_sub" style:family="text"><style:text-properties style:text-position="sub 58%"/></style:style>""")
            append("""<style:style style:name="P_break" style:family="paragraph" style:parent-style-name="Standard"><style:paragraph-properties fo:break-before="page"/></style:style>""")
            append("""<style:style style:name="P_center" style:family="paragraph" style:parent-style-name="Standard"><style:paragraph-properties fo:text-align="center"/></style:style>""")
            append("""<style:style style:name="P_right" style:family="paragraph" style:parent-style-name="Standard"><style:paragraph-properties fo:text-align="end"/></style:style>""")
            append("""<style:style style:name="Cell" style:family="table-cell"><style:table-cell-properties fo:border="0.5pt solid #808080" fo:padding="0.08cm"/></style:style>""")
            append("""<style:style style:name="Fr" style:family="graphic"><style:graphic-properties style:wrap="none" style:horizontal-pos="center" style:horizontal-rel="paragraph"/></style:style>""")
            append("""<style:page-layout style:name="pm1"><style:page-layout-properties fo:page-width="21cm" fo:page-height="29.7cm" fo:margin-top="2cm" fo:margin-bottom="2cm" fo:margin-left="2cm" fo:margin-right="2cm"/></style:page-layout>""").append('\n')
            append("</office:automatic-styles>\n")
            append("""<office:master-styles><style:master-page style:name="Standard" style:page-layout-name="pm1"/></office:master-styles>""").append('\n')
            append("<office:body><office:text>\n").append(builder.body).append("</office:text></office:body></office:document>\n")
        }
    }

    /** Converte o arquivo HTML e grava o `.fodt`; imagens relativas são lidas ao lado do HTML. */
    fun convert(html: File, output: File, title: String?, language: String?) {
        val base = html.parentFile
        output.writeText(
            convert(html.readText(), title, language) { src ->
                val file = File(base, src)
                if (file.isFile && file.canonicalPath.startsWith(base.canonicalPath)) file.readBytes() else null
            },
        )
    }

    private class Builder(private val loadImage: (String) -> ByteArray?) {
        val body = StringBuilder()
        private var imageCount = 0

        /** Elementos de bloco em sequência; texto solto vira parágrafo. */
        fun walkBlocks(element: Element) {
            var pending = StringBuilder()
            fun flushPending() {
                val text = pending.toString()
                if (text.isNotBlank()) body.append("<text:p text:style-name=\"Standard\">").append(text.trim()).append("</text:p>\n")
                pending = StringBuilder()
            }
            for (node in element.childNodes()) {
                if (node is TextNode) {
                    pending.append(esc(normalize(node.text())))
                    continue
                }
                val child = node as? Element ?: continue
                val tag = child.tagName().lowercase()
                if (!isBlock(tag)) {
                    pending.append(inline(child, Style()))
                    continue
                }
                flushPending()
                if (pageBreakBefore(child)) body.append("""<text:p text:style-name="P_break"/>""").append('\n')
                when (tag) {
                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        val level = tag[1].digitToInt()
                        body.append("""<text:h text:style-name="Heading_$level" text:outline-level="$level">""").append(inlineChildren(child, Style())).append("</text:h>\n")
                    }
                    "p", "address", "dt", "dd", "figcaption", "summary", "caption", "li" -> paragraph(child, paragraphStyle(child))
                    "div", "section", "article", "main", "header", "footer", "nav", "aside", "figure", "details", "body", "center", "form", "fieldset", "span" ->
                        if (child.children().any { isBlock(it.tagName().lowercase()) }) walkBlocks(child) else paragraph(child, paragraphStyle(child))
                    "blockquote" -> if (child.children().any { isBlock(it.tagName().lowercase()) }) walkBlocks(child) else paragraph(child, "Quote")
                    "pre" -> child.wholeText().split('\n').forEach { line -> body.append("""<text:p text:style-name="Code">""").append(esc(line)).append("</text:p>\n") }
                    "ul", "ol" -> list(child)
                    "table" -> table(child)
                    "img" -> image(child)?.let { body.append("""<text:p text:style-name="P_center">""").append(it).append("</text:p>\n") }
                    "hr" -> body.append("""<text:p text:style-name="P_center">* * *</text:p>""").append('\n')
                    "br" -> Unit
                    "script", "style", "noscript", "template", "head", "svg", "iframe", "object" -> Unit
                    else -> walkBlocks(child)
                }
            }
            flushPending()
        }

        private fun paragraph(element: Element, style: String) {
            val text = inlineChildren(element, Style())
            if (text.isBlank() && !text.contains("<draw:frame")) return
            body.append("""<text:p text:style-name="$style">""").append(text.trim()).append("</text:p>\n")
        }

        private fun paragraphStyle(element: Element): String {
            val align = (element.attr("align").ifEmpty { cssValue(element, "text-align") }).lowercase()
            return when (align) {
                "center" -> "P_center"
                "right", "end" -> "P_right"
                else -> "Standard"
            }
        }

        private fun pageBreakBefore(element: Element): Boolean {
            val style = element.attr("style").lowercase().replace(" ", "")
            return "page-break-before:always" in style || "break-before:page" in style || element.hasClass("page-break")
        }

        private fun list(element: Element) {
            val ordered = element.tagName().equals("ol", ignoreCase = true)
            body.append("""<text:list text:style-name="${if (ordered) "L_number" else "L_bullet"}">""").append('\n')
            for (item in element.children()) {
                if (!item.tagName().equals("li", ignoreCase = true)) continue
                body.append("<text:list-item>")
                val nested = item.children().filter { it.tagName().lowercase() in setOf("ul", "ol") }
                val own = item.clone().apply { children().filter { it.tagName().lowercase() in setOf("ul", "ol") }.forEach { it.remove() } }
                if (own.children().any { isBlock(it.tagName().lowercase()) }) {
                    val before = body.length
                    walkBlocks(own)
                    if (body.length == before) body.append("<text:p/>")
                } else {
                    body.append("<text:p>").append(inlineChildren(own, Style()).trim()).append("</text:p>")
                }
                nested.forEach { list(it) }
                body.append("</text:list-item>\n")
            }
            body.append("</text:list>\n")
        }

        private fun table(element: Element) {
            val rows = element.select("> tr, > thead > tr, > tbody > tr, > tfoot > tr")
            if (rows.isEmpty()) return
            val columns = rows.maxOf { row -> row.children().filter { it.tagName().lowercase() in setOf("td", "th") }.sumOf { it.attr("colspan").toIntOrNull() ?: 1 } }
            body.append("""<table:table>""")
            repeat(columns) { body.append("<table:table-column/>") }
            body.append('\n')
            for (row in rows) {
                body.append("<table:table-row>")
                for (cell in row.children()) {
                    val tag = cell.tagName().lowercase()
                    if (tag != "td" && tag != "th") continue
                    val colSpan = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val rowSpan = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                    body.append("""<table:table-cell table:style-name="Cell" office:value-type="string"""")
                    if (colSpan > 1) body.append(""" table:number-columns-spanned="$colSpan"""")
                    if (rowSpan > 1) body.append(""" table:number-rows-spanned="$rowSpan"""")
                    body.append('>')
                    if (cell.children().any { isBlock(it.tagName().lowercase()) }) {
                        val before = body.length
                        walkBlocks(cell)
                        if (body.length == before) body.append("<text:p/>")
                    } else {
                        val text = inlineChildren(cell, Style(bold = tag == "th"))
                        body.append("<text:p>").append(text.trim()).append("</text:p>")
                    }
                    body.append("</table:table-cell>")
                    repeat(colSpan - 1) { body.append("<table:covered-table-cell/>") }
                }
                body.append("</table:table-row>\n")
            }
            body.append("</table:table>\n")
        }

        private data class Style(val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false, val strike: Boolean = false, val code: Boolean = false, val sup: Boolean = false, val sub: Boolean = false)

        private fun inlineChildren(element: Element, style: Style): String {
            val out = StringBuilder()
            for (node in element.childNodes()) {
                when (node) {
                    is TextNode -> out.append(span(esc(normalize(node.text())), style))
                    is Element -> out.append(inline(node, style))
                }
            }
            return out.toString()
        }

        private fun inline(element: Element, style: Style): String = when (val tag = element.tagName().lowercase()) {
            "b", "strong" -> inlineChildren(element, style.copy(bold = true))
            "i", "em", "cite", "dfn", "var" -> inlineChildren(element, style.copy(italic = true))
            "u", "ins" -> inlineChildren(element, style.copy(underline = true))
            "s", "strike", "del" -> inlineChildren(element, style.copy(strike = true))
            "code", "kbd", "samp", "tt" -> inlineChildren(element, style.copy(code = true))
            "sup" -> inlineChildren(element, style.copy(sup = true))
            "sub" -> inlineChildren(element, style.copy(sub = true))
            "br" -> "<text:line-break/>"
            "img" -> image(element).orEmpty()
            "a" -> {
                val href = element.attr("href")
                val inner = inlineChildren(element, style)
                if (href.isNotEmpty() && "://" in href || href.startsWith("mailto:")) """<text:a xlink:type="simple" xlink:href="${esc(href)}">$inner</text:a>""" else inner
            }
            "script", "style", "noscript", "svg", "template" -> ""
            else -> if (isBlock(tag)) {
                // Bloco dentro de linha (HTML malformado): entra como texto corrido.
                inlineChildren(element, style)
            } else {
                inlineChildren(element, style)
            }
        }

        private fun span(text: String, style: Style): String {
            if (text.isEmpty()) return text
            val name = when {
                style.code -> "T_code"
                style.sup -> "T_sup"
                style.sub -> "T_sub"
                style.bold && style.italic -> "T_bi"
                style.bold -> "T_b"
                style.italic -> "T_i"
                style.underline -> "T_u"
                style.strike -> "T_s"
                else -> return text
            }
            return """<text:span text:style-name="$name">$text</text:span>"""
        }

        private fun image(element: Element): String? {
            val src = element.attr("src").ifEmpty { return null }
            val bytes: ByteArray = when {
                src.startsWith("data:") -> runCatching { Base64.getMimeDecoder().decode(src.substringAfter(',')) }.getOrNull()
                "://" in src -> null
                else -> loadImage(java.net.URLDecoder.decode(src.substringBefore('#').substringBefore('?'), "UTF-8"))
            } ?: return null
            if (bytes.isEmpty()) return null
            val (w, h) = imageSize(bytes, element)
            imageCount++
            return """<draw:frame draw:style-name="Fr" draw:name="Imagem$imageCount" text:anchor-type="as-char" svg:width="${pt(w)}" svg:height="${pt(h)}"><draw:image><office:binary-data>""" +
                Base64.getMimeEncoder(76, "\n".toByteArray()).encodeToString(bytes) + "</office:binary-data></draw:image></draw:frame>"
        }

        /** Tamanho em pontos: pelos atributos width/height (px → pt a 96 dpi) ou pelo cabeçalho PNG/JPEG, limitado à largura útil. */
        private fun imageSize(bytes: ByteArray, element: Element): Pair<Float, Float> {
            val attrW = element.attr("width").filter { it.isDigit() }.toFloatOrNull()
            val attrH = element.attr("height").filter { it.isDigit() }.toFloatOrNull()
            var (w, h) = if (attrW != null && attrH != null && attrW > 0 && attrH > 0) attrW to attrH else (pixelSize(bytes) ?: (400f to 300f))
            val maxWidth = 480f * 96f / 72f
            if (w > maxWidth) { h *= maxWidth / w; w = maxWidth }
            return (w * 72f / 96f) to (h * 72f / 96f)
        }

        private fun pixelSize(bytes: ByteArray): Pair<Float, Float>? {
            if (bytes.size > 24 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) {
                fun int(at: Int) = ((bytes[at].toInt() and 0xff) shl 24) or ((bytes[at + 1].toInt() and 0xff) shl 16) or ((bytes[at + 2].toInt() and 0xff) shl 8) or (bytes[at + 3].toInt() and 0xff)
                return int(16).toFloat() to int(20).toFloat()
            }
            if (bytes.size > 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                var i = 2
                while (i + 9 < bytes.size) {
                    if (bytes[i] != 0xFF.toByte()) { i++; continue }
                    val marker = bytes[i + 1].toInt() and 0xff
                    val length = ((bytes[i + 2].toInt() and 0xff) shl 8) or (bytes[i + 3].toInt() and 0xff)
                    if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                        val h = ((bytes[i + 5].toInt() and 0xff) shl 8) or (bytes[i + 6].toInt() and 0xff)
                        val w = ((bytes[i + 7].toInt() and 0xff) shl 8) or (bytes[i + 8].toInt() and 0xff)
                        return w.toFloat() to h.toFloat()
                    }
                    i += 2 + length
                }
            }
            return null
        }

        private fun isBlock(tag: String) = tag in BLOCKS

        private fun cssValue(element: Element, property: String): String {
            val style = element.attr("style")
            val match = Regex("""(?:^|;)\s*${Regex.escape(property)}\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE).find(style) ?: return ""
            return match.groupValues[1].trim()
        }

        private fun normalize(text: String) = text.replace(Regex("\\s+"), " ")
    }

    private val BLOCKS = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6", "p", "div", "section", "article", "main", "header", "footer", "nav", "aside", "figure", "figcaption",
        "details", "summary", "blockquote", "pre", "ul", "ol", "li", "table", "img", "hr", "address", "dl", "dt", "dd", "body", "center", "form", "fieldset",
        "script", "style", "noscript", "template", "head", "svg", "iframe", "object", "caption",
    )

    private fun pt(value: Float) = String.format(Locale.ROOT, "%.2fpt", value)

    private fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
