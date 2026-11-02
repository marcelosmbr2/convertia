package com.convertia.app.data.engine.office

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.IOException
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

/**
 * O LibreOffice lê FB2 mas não grava. Este escritor monta o FB2 (FictionBook 2.0) a partir
 * do ODT plano (`.fodt`) que o LibreOffice exporta: títulos viram seções; parágrafos,
 * ênfase (pelos estilos), listas, tabelas e imagens (base64) vêm junto. Código puro,
 * testado na JVM. (O exportador XHTML do LibreOffice precisa de XSLT, que o build Android
 * não traz; o ODT plano é XML direto.)
 */
object Fb2Writer {

    fun write(fodt: File, output: File, title: String?, language: String?) {
        val base = fodt.parentFile
        output.writeText(
            convert(fodt.readText(), title, language) { href ->
                File(base, href).takeIf { it.isFile && it.canonicalPath.startsWith(base.canonicalPath) }?.readBytes()
            },
        )
    }

    /** [loadLinked] lê imagens vinculadas (`xlink:href` relativo) quando não estão embutidas. */
    fun convert(fodt: String, title: String?, language: String?, loadLinked: (String) -> ByteArray? = { null }): String {
        val document = parse(fodt)
        val body = document.getElementsByTagName("office:text").item(0) as? Element
            ?: throw IOException("ODT sem corpo de texto")
        val styles = Styles(document)
        val builder = Builder(styles, loadLinked)
        builder.walk(body)
        builder.finish()
        val bookTitle = title?.takeIf { it.isNotBlank() } ?: builder.firstHeading ?: "Documento"
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\" xmlns:l=\"http://www.w3.org/1999/xlink\">\n")
            append("<description>\n<title-info>\n")
            append("<genre>nonfiction</genre>\n")
            append("<book-title>").append(escape(bookTitle)).append("</book-title>\n")
            append("<lang>").append(escape(language?.takeIf { it.isNotBlank() } ?: "pt")).append("</lang>\n")
            append("</title-info>\n<document-info>\n")
            append("<program-used>Convertia</program-used>\n")
            append("<id>convertia-").append(bookTitle.hashCode().toUInt()).append("</id>\n<version>1.0</version>\n")
            append("</document-info>\n</description>\n")
            append("<body>\n").append(builder.body).append("</body>\n")
            builder.binaries.forEach { (id, image) ->
                append("<binary id=\"").append(id).append("\" content-type=\"").append(image.first).append("\">")
                append(image.second).append("</binary>\n")
            }
            append("</FictionBook>\n")
        }
    }

    /** Negrito e itálico por estilo, seguindo a cadeia de estilos-pais. */
    private class Styles(document: org.w3c.dom.Document) {
        private data class Props(val bold: Boolean?, val italic: Boolean?, val parent: String?)
        private val styles = mutableMapOf<String, Props>()

        init {
            val nodes = document.getElementsByTagName("style:style")
            for (i in 0 until nodes.length) {
                val style = nodes.item(i) as Element
                val name = style.getAttribute("style:name").ifEmpty { continue }
                val text = style.getElementsByTagName("style:text-properties").item(0) as? Element
                val weight = text?.getAttribute("fo:font-weight")?.ifEmpty { null }
                val posture = text?.getAttribute("fo:font-style")?.ifEmpty { null }
                styles[name] = Props(
                    bold = weight?.let { it == "bold" || (it.toIntOrNull() ?: 0) >= 600 },
                    italic = posture?.let { it == "italic" || it == "oblique" },
                    parent = style.getAttribute("style:parent-style-name").ifEmpty { null },
                )
            }
        }

        fun bold(name: String?): Boolean = resolve(name, 0) { it.bold } ?: false
        fun italic(name: String?): Boolean = resolve(name, 0) { it.italic } ?: false

        private fun resolve(name: String?, depth: Int, pick: (Props) -> Boolean?): Boolean? {
            val props = styles[name ?: return null] ?: return null
            if (depth > 10) return null
            return pick(props) ?: resolve(props.parent, depth + 1, pick)
        }
    }

    private class Builder(private val styles: Styles, private val loadLinked: (String) -> ByteArray?) {
        val body = StringBuilder()
        val binaries = linkedMapOf<String, Pair<String, String>>()
        var firstHeading: String? = null
        private var sectionOpen = false
        private var paragraphs = 0

        fun walk(node: Node) {
            val children = node.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                when (child.tagName) {
                    "text:h" -> heading(child)
                    "text:p" -> paragraphOf(child)
                    "text:list" -> list(child)
                    "table:table" -> table(child)
                    "text:section", "office:text", "text:index-body", "text:table-of-content", "draw:text-box", "text:note-body" -> walk(child)
                    "text:sequence-decls", "text:tracked-changes", "text:variable-decls", "text:user-field-decls", "office:forms" -> Unit
                    "draw:frame" -> image(child)?.let { paragraph(it) }
                    else -> walk(child)
                }
            }
        }

        private fun heading(element: Element) {
            val text = inline(element, false, false).trim()
            if (text.isEmpty()) return
            if (firstHeading == null) firstHeading = element.textContent.trim()
            closeSection()
            body.append("<section>\n<title><p>").append(text).append("</p></title>\n")
            sectionOpen = true
        }

        private fun paragraphOf(element: Element) {
            val style = element.getAttribute("text:style-name").ifEmpty { null }
            val text = inline(element, styles.bold(style), styles.italic(style))
            if (text.isNotBlank()) paragraph("<p>$text</p>")
        }

        private fun list(element: Element) {
            val items = element.childNodes
            var index = 1
            val ordered = isOrdered(element)
            for (i in 0 until items.length) {
                val item = items.item(i) as? Element ?: continue
                if (item.tagName != "text:list-item" && item.tagName != "text:list-header") continue
                val marker = if (ordered) "${index++}. " else "• "
                val paragraphsInItem = item.childNodes
                var first = true
                for (j in 0 until paragraphsInItem.length) {
                    val p = paragraphsInItem.item(j) as? Element ?: continue
                    when (p.tagName) {
                        // Títulos numerados vêm dentro de listas (numeração de capítulos do Word): continuam títulos.
                        "text:h" -> heading(p)
                        "text:p" -> {
                            val style = p.getAttribute("text:style-name").ifEmpty { null }
                            val text = inline(p, styles.bold(style), styles.italic(style))
                            if (text.isNotBlank()) {
                                paragraph("<p>${if (first) escape(marker) else "  "}$text</p>")
                                first = false
                            }
                        }
                        "text:list" -> list(p)
                    }
                }
            }
        }

        /** Lista numerada se o estilo dela usa `list-level-style-number`; sem como saber, marcador. */
        private fun isOrdered(list: Element): Boolean {
            val styleName = list.getAttribute("text:style-name").ifEmpty { return false }
            val doc = list.ownerDocument
            val styles = doc.getElementsByTagName("text:list-style")
            for (i in 0 until styles.length) {
                val style = styles.item(i) as Element
                if (style.getAttribute("style:name") != styleName) continue
                val first = style.firstElementChild() ?: return false
                return first.tagName == "text:list-level-style-number"
            }
            return false
        }

        private fun table(element: Element) {
            val rows = mutableListOf<Element>()
            fun collectRows(n: Element) {
                val kids = n.childNodes
                for (i in 0 until kids.length) {
                    val k = kids.item(i) as? Element ?: continue
                    when (k.tagName) {
                        "table:table-row" -> rows += k
                        "table:table-header-rows", "table:table-rows", "table:table-row-group" -> collectRows(k)
                    }
                }
            }
            collectRows(element)
            if (rows.isEmpty()) return
            ensureSection()
            body.append("<table>\n")
            for (row in rows) {
                body.append("<tr>")
                val cells = row.childNodes
                for (c in 0 until cells.length) {
                    val cell = cells.item(c) as? Element ?: continue
                    if (cell.tagName != "table:table-cell") continue
                    body.append("<td")
                    cell.getAttribute("table:number-columns-spanned").takeIf { it.isNotEmpty() && it != "1" }?.let { body.append(" colspan=\"").append(escape(it)).append('"') }
                    cell.getAttribute("table:number-rows-spanned").takeIf { it.isNotEmpty() && it != "1" }?.let { body.append(" rowspan=\"").append(escape(it)).append('"') }
                    body.append('>')
                    val paragraphsInCell = cell.childNodes
                    val parts = mutableListOf<String>()
                    for (j in 0 until paragraphsInCell.length) {
                        val p = paragraphsInCell.item(j) as? Element ?: continue
                        if (p.tagName == "text:p" || p.tagName == "text:h") {
                            val style = p.getAttribute("text:style-name").ifEmpty { null }
                            parts += inline(p, styles.bold(style), styles.italic(style))
                        }
                    }
                    body.append(parts.filter { it.isNotBlank() }.joinToString(" "))
                    body.append("</td>")
                }
                body.append("</tr>\n")
            }
            body.append("</table>\n")
            paragraphs++
        }

        /** `draw:frame` com imagem embutida em base64, ou vinculada a um arquivo ao lado. */
        private fun image(frame: Element): String? {
            val image = frame.getElementsByTagName("draw:image").item(0) as? Element ?: return null
            val embedded = (image.getElementsByTagName("office:binary-data").item(0) as? Element)?.textContent?.replace(Regex("\\s+"), "")
            val bytes = if (!embedded.isNullOrEmpty()) {
                runCatching { Base64.getMimeDecoder().decode(embedded) }.getOrNull()
            } else {
                image.getAttribute("xlink:href").takeIf { it.isNotEmpty() && "://" !in it }?.let(loadLinked)
            } ?: return null
            if (bytes.isEmpty()) return null
            val encoded = Base64.getMimeEncoder(76, "\n".toByteArray()).encodeToString(bytes)
            val type = mimeOf(image.getAttribute("loext:mime-type").ifEmpty { image.getAttribute("draw:mime-type") }, encoded)
            val id = "img${binaries.size + 1}.${type.substringAfter('/').replace("jpeg", "jpg").replace("svg+xml", "svg")}"
            binaries[id] = type to encoded
            return "<image l:href=\"#$id\"/>"
        }

        /** Texto de um parágrafo com ênfase (herdada do parágrafo ou do `text:span`). */
        private fun inline(element: Element, bold: Boolean, italic: Boolean): String {
            val out = StringBuilder()
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                when {
                    child.nodeType == Node.TEXT_NODE -> out.append(styled(escape(child.textContent.replace(Regex("\\s+"), " ")), bold, italic))
                    child is Element -> when (child.tagName) {
                        "text:span" -> {
                            val style = child.getAttribute("text:style-name").ifEmpty { null }
                            out.append(inline(child, bold || styles.bold(style), italic || styles.italic(style)))
                        }
                        "text:s" -> out.append(" ".repeat(child.getAttribute("text:c").toIntOrNull() ?: 1))
                        "text:tab" -> out.append(' ')
                        "text:line-break" -> out.append(' ')
                        "text:a", "text:bookmark-ref", "text:sequence-ref", "text:reference-mark-start", "text:reference-mark-end" -> out.append(inline(child, bold, italic))
                        "text:note" -> Unit
                        "draw:frame" -> image(child)?.let { paragraph(it) }
                        "text:bookmark", "text:bookmark-start", "text:bookmark-end", "text:soft-page-break", "office:annotation" -> Unit
                        else -> out.append(inline(child, bold, italic))
                    }
                }
            }
            return mergeTags(out.toString()).trim()
        }

        private fun styled(text: String, bold: Boolean, italic: Boolean): String {
            if (text.isBlank()) return text
            var t = text
            if (italic) t = "<emphasis>$t</emphasis>"
            if (bold) t = "<strong>$t</strong>"
            return t
        }

        /** "</strong><strong>" entre palavras vizinhas vira um trecho só. */
        private fun mergeTags(text: String): String =
            text.replace("</strong><strong>", "").replace("</emphasis><emphasis>", "")
                .replace("</emphasis></strong><strong><emphasis>", "")

        private fun paragraph(xml: String) {
            if (xml == "<p></p>") return
            ensureSection()
            body.append(xml).append('\n')
            paragraphs++
        }

        private fun ensureSection() {
            if (!sectionOpen) {
                body.append("<section>\n")
                sectionOpen = true
            }
        }

        fun closeSection() {
            if (!sectionOpen) return
            if (paragraphs == 0) body.append("<empty-line/>\n")
            body.append("</section>\n")
            sectionOpen = false
            paragraphs = 0
        }

        /** Fim do documento: fecha o que estiver aberto; um FB2 precisa de ao menos uma seção. */
        fun finish() {
            closeSection()
            if (body.isEmpty()) body.append("<section><empty-line/></section>\n")
        }

        private fun Element.firstElementChild(): Element? {
            val kids = childNodes
            for (i in 0 until kids.length) (kids.item(i) as? Element)?.let { return it }
            return null
        }
    }

    private fun mimeOf(declared: String, base64: String): String = when {
        declared.startsWith("image/") -> declared
        base64.startsWith("/9j/") -> "image/jpeg"
        base64.startsWith("R0lG") -> "image/gif"
        else -> "image/png"
    }

    private fun parse(xml: String): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
        return try {
            builder.parse(xml.byteInputStream())
        } catch (e: org.xml.sax.SAXException) {
            throw IOException("Não foi possível ler o documento exportado (${e.message})")
        }
    }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
