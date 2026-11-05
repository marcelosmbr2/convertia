package com.recast.app.data.engine.office

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * O LibreOffice não importa EPUB. Um EPUB é um ZIP com capítulos em XHTML listados num
 * arquivo OPF: extraímos tudo e juntamos os capítulos, na ordem do `spine`, num único HTML
 * (com os caminhos das imagens e folhas de estilo ajustados) que o LibreOffice abre.
 * Código puro, testado na JVM.
 */
object EpubToHtml {

    data class Book(val html: File, val title: String?, val language: String?)

    /** Extrai [epub] em [into] e devolve o HTML unificado (`into/book.html`). */
    fun convert(epub: File, into: File): Book {
        into.mkdirs()
        val entries = mutableMapOf<String, ByteArray>()
        ZipFile(epub).use { zip ->
            for (entry in zip.entries().asSequence()) {
                if (entry.isDirectory) continue
                val clean = entry.name.trimStart('/')
                val target = File(into, clean)
                if (!target.canonicalPath.startsWith(into.canonicalPath + File.separator)) continue
                target.parentFile?.mkdirs()
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                target.writeBytes(bytes)
                entries[clean] = bytes
            }
        }
        val containerXml = entries["META-INF/container.xml"] ?: throw IOException("EPUB sem META-INF/container.xml")
        val opfPath = parse(containerXml).getElementsByTagName("rootfile").item(0)?.attributes?.getNamedItem("full-path")?.nodeValue
            ?: throw IOException("EPUB sem arquivo OPF")
        val opfBytes = entries[opfPath] ?: throw IOException("EPUB sem $opfPath")
        val opf = parse(opfBytes)
        val opfDir = opfPath.substringBeforeLast('/', "")

        val manifest = opf.elements("item").associate { item ->
            item.getAttribute("id") to (resolve(opfDir, item.getAttribute("href")) to item.getAttribute("media-type"))
        }
        val spine = opf.elements("itemref").mapNotNull { manifest[it.getAttribute("idref")] }
            .filter { (_, type) -> "html" in type || "xml" in type }
        if (spine.isEmpty()) throw IOException("EPUB sem capítulos legíveis")

        val title = opf.text("dc:title") ?: opf.text("title")
        val language = opf.text("dc:language") ?: opf.text("language")
        val styles = manifest.values.filter { (_, type) -> type == "text/css" }.map { it.first }

        val html = buildString {
            append("<!DOCTYPE html>\n<html")
            language?.let { append(" lang=\"").append(escape(it)).append('"') }
            append("><head><meta charset=\"utf-8\"><title>").append(escape(title.orEmpty())).append("</title>\n")
            styles.forEach { append("<link rel=\"stylesheet\" href=\"").append(escape(it)).append("\">\n") }
            append("</head><body>\n")
            spine.forEachIndexed { index, (path, _) ->
                val bytes = entries[path] ?: return@forEachIndexed
                val chapterDir = path.substringBeforeLast('/', "")
                if (index > 0) append("<div style=\"page-break-before: always\"></div>\n")
                append(rewritePaths(bodyOf(String(bytes, Charsets.UTF_8)), chapterDir)).append('\n')
            }
            append("</body></html>\n")
        }
        val out = File(into, "book.html")
        out.writeText(html)
        return Book(out, title, language)
    }

    /** Só o conteúdo de `<body>`; sem `<script>`. */
    internal fun bodyOf(xhtml: String): String {
        val body = BODY.find(xhtml)?.groupValues?.get(1) ?: xhtml
        return SCRIPT.replace(body, "")
    }

    /** Caminhos relativos ao capítulo passam a ser relativos à raiz do EPUB (onde fica o book.html). */
    internal fun rewritePaths(html: String, chapterDir: String): String = PATH_ATTR.replace(html) { match ->
        val value = match.groupValues[3]
        if (value.startsWith("#") || "://" in value || value.startsWith("data:") || value.startsWith("mailto:")) {
            match.value
        } else {
            "${match.groupValues[1]}=${match.groupValues[2]}${resolve(chapterDir, value)}${match.groupValues[2]}"
        }
    }

    /** "OEBPS" + "../images/a.png" → "images/a.png". */
    internal fun resolve(baseDir: String, relative: String): String {
        val decoded = relative.substringBefore('#').substringBefore('?')
        val parts = (if (baseDir.isEmpty()) emptyList() else baseDir.split('/')).toMutableList()
        for (segment in decoded.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += segment
            }
        }
        return parts.joinToString("/")
    }

    private fun parse(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
        return bytes.inputStream().use { builder.parse(it) }
    }

    private fun Document.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Document.text(tag: String): String? =
        getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")

    private val BODY = Regex("<body[^>]*>(.*)</body>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val SCRIPT = Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val PATH_ATTR = Regex("""\b(src|href|xlink:href)=(["'])([^"']+)\2""", RegexOption.IGNORE_CASE)
}
