package com.convertia.app.data.engine.office

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.IOException
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

/**
 * O LibreOffice lê FB2 mas não grava. Este escritor monta o FB2 (FictionBook 2.0) a partir
 * do XHTML que o LibreOffice exporta: títulos viram seções, parágrafos, ênfase, listas,
 * tabelas e imagens (em base64). Código puro, testado na JVM.
 */
object Fb2Writer {

    /** Converte [xhtml] (com as imagens ao lado dele) em FB2 escrito em [output]. */
    fun write(xhtml: File, output: File, title: String?, language: String?) {
        val fb2 = convert(xhtml.readText(), title, language) { src ->
            val file = File(xhtml.parentFile, src)
            if (file.isFile) file.readBytes() else null
        }
        output.writeText(fb2)
    }

    /** [loadImage] resolve um `src` relativo em bytes; `data:` URIs são tratadas aqui. */
    fun convert(xhtml: String, title: String?, language: String?, loadImage: (String) -> ByteArray?): String {
        val document = parse(xhtml)
        val body = document.getElementsByTagName("body").item(0) as? Element
            ?: throw IOException("XHTML sem <body>")
        val builder = Builder(loadImage)
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
                append(Base64.getMimeEncoder(76, "\n".toByteArray()).encodeToString(image.second)).append("</binary>\n")
            }
            append("</FictionBook>\n")
        }
    }

    private class Builder(private val loadImage: (String) -> ByteArray?) {
        val body = StringBuilder()
        val binaries = linkedMapOf<String, Pair<String, ByteArray>>()
        var firstHeading: String? = null
        private var sectionOpen = false
        private var paragraphs = 0

        fun walk(node: Node) {
            val children = node.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child !is Element) {
                    val text = child.textContent?.trim().orEmpty()
                    if (text.isNotEmpty() && child.nodeType == Node.TEXT_NODE) paragraph("<p>${escape(text)}</p>")
                    continue
                }
                when (child.tagName.lowercase()) {
                    "h1", "h2", "h3", "h4", "h5", "h6" -> heading(child)
                    "p", "div", "blockquote", "pre", "address", "dd", "dt" -> {
                        if (child.getElementsByTagName("p").length > 0 || child.getElementsByTagName("table").length > 0) walk(child)
                        else paragraph("<p>${inline(child)}</p>")
                    }
                    "ul", "ol" -> list(child)
                    "table" -> table(child)
                    "img" -> image(child)?.let { paragraph(it) }
                    "br", "hr" -> Unit
                    "script", "style", "head" -> Unit
                    else -> walk(child)
                }
            }
        }

        private fun heading(element: Element) {
            val text = inline(element).trim()
            if (text.isEmpty()) return
            if (firstHeading == null) firstHeading = element.textContent.trim()
            closeSection()
            body.append("<section>\n<title><p>").append(text).append("</p></title>\n")
            sectionOpen = true
        }

        private fun list(element: Element) {
            val items = element.childNodes
            var index = 1
            for (i in 0 until items.length) {
                val item = items.item(i) as? Element ?: continue
                if (item.tagName.lowercase() != "li") continue
                val marker = if (element.tagName.lowercase() == "ol") "${index++}. " else "• "
                paragraph("<p>${escape(marker)}${inline(item)}</p>")
            }
        }

        private fun table(element: Element) {
            val rows = element.getElementsByTagName("tr")
            if (rows.length == 0) return
            ensureSection()
            body.append("<table>\n")
            for (r in 0 until rows.length) {
                val row = rows.item(r) as Element
                body.append("<tr>")
                val cells = row.childNodes
                for (c in 0 until cells.length) {
                    val cell = cells.item(c) as? Element ?: continue
                    val tag = cell.tagName.lowercase()
                    if (tag != "td" && tag != "th") continue
                    body.append('<').append(tag)
                    cell.getAttribute("colspan").takeIf { it.isNotEmpty() }?.let { body.append(" colspan=\"").append(escape(it)).append('"') }
                    cell.getAttribute("rowspan").takeIf { it.isNotEmpty() }?.let { body.append(" rowspan=\"").append(escape(it)).append('"') }
                    body.append('>').append(inline(cell)).append("</").append(tag).append('>')
                }
                body.append("</tr>\n")
            }
            body.append("</table>\n")
            paragraphs++
        }

        private fun image(element: Element): String? {
            val src = element.getAttribute("src").ifEmpty { return null }
            val (type, bytes) = if (src.startsWith("data:")) {
                val header = src.substringBefore(',')
                val mime = header.removePrefix("data:").substringBefore(';').ifEmpty { "image/png" }
                mime to runCatching { Base64.getMimeDecoder().decode(src.substringAfter(',')) }.getOrNull()
            } else {
                mimeOf(src) to loadImage(src)
            }
            if (bytes == null || bytes.isEmpty()) return null
            val id = "img${binaries.size + 1}.${type.substringAfter('/').replace("jpeg", "jpg")}"
            binaries[id] = type to bytes
            return "<image l:href=\"#$id\"/>"
        }

        /** Texto com ênfase; imagens e quebras dentro do parágrafo viram texto simples. */
        private fun inline(element: Element): String {
            val out = StringBuilder()
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                when {
                    child.nodeType == Node.TEXT_NODE -> out.append(escape(child.textContent.replace(Regex("\\s+"), " ")))
                    child is Element -> when (child.tagName.lowercase()) {
                        "b", "strong" -> out.append("<strong>").append(inline(child)).append("</strong>")
                        "i", "em", "cite", "dfn" -> out.append("<emphasis>").append(inline(child)).append("</emphasis>")
                        "s", "strike", "del" -> out.append("<strikethrough>").append(inline(child)).append("</strikethrough>")
                        "sub" -> out.append("<sub>").append(inline(child)).append("</sub>")
                        "sup" -> out.append("<sup>").append(inline(child)).append("</sup>")
                        "code", "tt", "kbd", "samp" -> out.append("<code>").append(inline(child)).append("</code>")
                        "br" -> out.append(' ')
                        "img" -> image(child)?.let { paragraph(it) }
                        "script", "style" -> Unit
                        else -> out.append(inline(child))
                    }
                }
            }
            return out.toString().trim()
        }

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
    }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        else -> "image/png"
    }

    private fun parse(xhtml: String): org.w3c.dom.Document {
        // O XHTML do LibreOffice não declara entidades HTML; as poucas que aparecem viram numéricas.
        val cleaned = ENTITIES.fold(xhtml) { acc, (name, code) -> acc.replace("&$name;", "&#$code;") }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
        return try {
            builder.parse(cleaned.byteInputStream())
        } catch (e: org.xml.sax.SAXException) {
            throw IOException("Não foi possível ler o documento exportado (${e.message})")
        }
    }

    private val ENTITIES = listOf("nbsp" to 160, "copy" to 169, "reg" to 174, "laquo" to 171, "raquo" to 187, "ndash" to 8211, "mdash" to 8212, "hellip" to 8230, "lsquo" to 8216, "rsquo" to 8217, "ldquo" to 8220, "rdquo" to 8221, "bull" to 8226, "trade" to 8482)

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
