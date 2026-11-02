package com.convertia.app.data.engine.office

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubToHtmlTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** EPUB mínimo com dois capítulos em OEBPS/, uma imagem e um CSS. */
    private fun sampleEpub(): File {
        val epub = File(folder.root, "livro.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            put("OEBPS/content.opf", """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Dom Casmurro</dc:title><dc:language>pt-BR</dc:language></metadata>
                <manifest><item id="c2" href="text/cap2.xhtml" media-type="application/xhtml+xml"/><item id="c1" href="text/cap1.xhtml" media-type="application/xhtml+xml"/><item id="css" href="style.css" media-type="text/css"/><item id="img" href="images/capa.png" media-type="image/png"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest>
                <spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""")
            put("OEBPS/text/cap1.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>1</title><link rel="stylesheet" href="../style.css"/></head><body><h1>Capítulo I</h1><p>Uma noite destas…</p><img src="../images/capa.png" alt=""/><script>alert(1)</script></body></html>""")
            put("OEBPS/text/cap2.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body class="x"><h1>Capítulo II</h1><p>Do <a href="cap1.xhtml#topo">livro</a> e de <a href="https://exemplo.org/x">fora</a>.</p></body></html>""")
            put("OEBPS/nav.xhtml", "<html><body><nav>…</nav></body></html>")
            put("OEBPS/style.css", "p { color: red }")
            put("OEBPS/images/capa.png", "PNG")
        }
        return epub
    }

    @Test
    fun `junta os capitulos na ordem do spine com caminhos relativos a raiz`() {
        val into = folder.newFolder("out")
        val book = EpubToHtml.convert(sampleEpub(), into)
        assertEquals("Dom Casmurro", book.title)
        assertEquals("pt-BR", book.language)
        val html = book.html.readText()
        assertTrue(html.indexOf("Capítulo I<") < html.indexOf("Capítulo II<"))
        assertTrue(html.contains("""<link rel="stylesheet" href="OEBPS/style.css">"""))
        assertTrue(html.contains("""src="OEBPS/images/capa.png""""))
        assertTrue(html.contains("""href="OEBPS/text/cap1.xhtml""""))
        assertTrue(html.contains("""href="https://exemplo.org/x""""))
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("<nav>"))
        assertTrue(html.contains("page-break-before"))
        assertTrue(File(into, "OEBPS/images/capa.png").isFile)
    }

    @Test
    fun `resolve caminhos relativos`() {
        assertEquals("images/a.png", EpubToHtml.resolve("OEBPS", "../images/a.png"))
        assertEquals("OEBPS/text/b.xhtml", EpubToHtml.resolve("OEBPS/text", "./b.xhtml"))
        assertEquals("x/c.css", EpubToHtml.resolve("", "x/c.css?v=1"))
        assertEquals("OEBPS/a.xhtml", EpubToHtml.resolve("OEBPS", "a.xhtml#sec"))
    }
}

class EpubToHtmlRealFileTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** EPUB gerado pelo exportador do LibreOffice a partir de um DOCX com título, lista e tabela. */
    @Test
    fun `epub do LibreOffice vira um HTML com o conteudo`() {
        val epub = folder.newFile("relatorio.epub").apply {
            checkNotNull(EpubToHtmlRealFileTest::class.java.getResourceAsStream("/office/relatorio.epub")).use { input -> outputStream().use { input.copyTo(it) } }
        }
        val book = EpubToHtml.convert(epub, folder.newFolder("out"))
        val html = book.html.readText()
        assertTrue(html.contains("Relatório Trimestral"))
        assertTrue(html.contains("Receita cresceu 12%"))
        assertTrue(html.contains("""href="OEBPS/styles/stylesheet.css""""))
        assertFalse(html.contains("toc.xhtml"))
    }
}

/**
 * Quando há um LibreOffice de desktop no PATH (contêiner de desenvolvimento e runners do
 * GitHub), confere que o HTML montado pelo [EpubToHtml] abre no LibreOffice e vira DOCX
 * com o conteúdo, e que o ODT plano que ele exporta passa pelo [Fb2Writer].
 */
class OfficeRoundTripSmokeTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `epub para html para docx, e docx para odt plano para fb2`() {
        org.junit.Assume.assumeTrue("soffice não está no PATH", sofficeAvailable())
        val epub = folder.newFile("relatorio.epub").apply {
            checkNotNull(OfficeRoundTripSmokeTest::class.java.getResourceAsStream("/office/relatorio.epub")).use { input -> outputStream().use { input.copyTo(it) } }
        }
        val out = folder.newFolder("epub")
        val book = EpubToHtml.convert(epub, out)
        soffice("--infilter=HTML (StarWriter)", "--convert-to", "docx", "--outdir", folder.root.path, book.html.path)
        val docx = File(folder.root, "book.docx")
        assertTrue(docx.isFile && docx.length() > 0)
        val text = java.util.zip.ZipFile(docx).use { zip -> zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText() }
        assertTrue(text.contains("Relatório Trimestral"))
        assertTrue(text.contains("<w:tbl>"))

        soffice("--convert-to", "fodt", "--outdir", folder.root.path, docx.path)
        val fodt = File(folder.root, "book.fodt")
        assertTrue(fodt.isFile)
        val fb2 = File(folder.root, "book.fb2")
        Fb2Writer.write(fodt, fb2, book.title, book.language)
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fb2)
        // O exportador de EPUB do LibreOffice troca títulos por parágrafos estilizados, então
        // a volta não recupera as seções; o conteúdo, sim.
        assertTrue(doc.getElementsByTagName("section").length >= 1)
        assertTrue(fb2.readText().contains("Receita cresceu 12%"))
        assertTrue(fb2.readText().contains("Relatório Trimestral"))
        // O LibreOffice lê FB2: o arquivo gerado tem que abrir nele.
        soffice("--convert-to", "odt", "--outdir", folder.newFolder("back").path, fb2.path)
        assertTrue(File(folder.root, "back/book.odt").isFile)
    }

    private fun soffice(vararg args: String) {
        val process = ProcessBuilder(listOf("soffice", "--headless", "--norestore") + args).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        process.waitFor(180, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(log, 0, process.exitValue())
    }

    private fun sofficeAvailable(): Boolean = runCatching {
        ProcessBuilder("soffice", "--version").redirectErrorStream(true).start().let { it.inputStream.readBytes(); it.waitFor() == 0 }
    }.getOrDefault(false)
}
