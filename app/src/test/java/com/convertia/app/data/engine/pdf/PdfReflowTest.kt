package com.convertia.app.data.engine.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * PDFs gerados pelo LibreOffice a partir de documentos conhecidos (`src/test/resources/pdf`),
 * com e sem a árvore de estrutura. A versão sem tags exercita a reconstrução geométrica.
 */
class PdfReflowTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun fixture(name: String): File = folder.newFile(name).apply {
        checkNotNull(PdfReflowTest::class.java.getResourceAsStream("/pdf/$name")).use { input -> outputStream().use { input.copyTo(it) } }
    }

    private fun reflow(name: String): DocModel {
        val model = ApachePdfBoxExtractor().extract(fixture(name), folder.newFolder("img-$name"))
        return PdfReflow.reflow(model)
    }

    private fun DocModel.dump(): String = blocks.joinToString("\n") { block ->
        when (block) {
            is Paragraph -> "P${block.level}[${block.align}] ${block.text}"
            is ListBlock -> "L(${if (block.ordered) "1." else "•"}) " + block.items.joinToString(" | ") { it.joinToString("") { r -> r.text } }
            is Table -> "T ${block.rows.size}x${block.columnWidths.size}" + block.rows.joinToString("") { r -> "\n   " + r.cells.joinToString(" | ") { c -> c.blocks.filterIsInstance<Paragraph>().joinToString(" ") { it.text } } }
            is ImageBlock -> "IMG ${block.widthPt}x${block.heightPt}"
            PageBreak -> "---"
        }
    }

    @Test
    fun `sem tags - titulos, paragrafos e listas pela geometria`() {
        val doc = reflow("simples-semtags.pdf")
        val dump = doc.dump()
        val paragraphs = doc.blocks.filterIsInstance<Paragraph>()
        val h1 = paragraphs.firstOrNull { it.level == 1 }
        assertNotNull(dump, h1)
        assertEquals(dump, "Relatório Trimestral", h1!!.text.trim())
        assertTrue(dump, paragraphs.any { it.level == 2 && it.text.trim() == "Destaques" })
        assertTrue(dump, paragraphs.any { it.level == 2 && it.text.trim() == "Tabela" })
        // Quatro linhas do PDF viram um parágrafo só, com a ênfase preservada.
        val long = paragraphs.first { it.text.startsWith("Este documento") }
        assertTrue(dump, long.text.endsWith("parágrafo só."))
        assertTrue(dump, long.runs.any { it.bold && it.text.contains("resultados de vendas") })
        assertTrue(dump, long.runs.any { it.italic && it.text.contains("comentários") })
        assertTrue(dump, paragraphs.any { it.level == 0 && it.text.trim() == "Segundo parágrafo, curto." })
        val lists = doc.blocks.filterIsInstance<ListBlock>()
        val bullets = lists.firstOrNull { !it.ordered }
        assertNotNull(dump, bullets)
        assertEquals(dump, 3, bullets!!.items.size)
        assertEquals(dump, "Receita cresceu 12% em relação ao trimestre anterior.", bullets.items[0].joinToString("") { it.text })
        val numbered = lists.firstOrNull { it.ordered }
        assertNotNull(dump, numbered)
        assertEquals(dump, listOf("Primeiro passo", "Segundo passo"), numbered!!.items.map { it.joinToString("") { r -> r.text } })
        assertTrue(dump, doc.plainText().contains("Sudeste"))
        assertTrue(dump, paragraphs.last().text.startsWith("Conclusão"))
        assertTrue(dump, doc.bodyFontSize in 11f..13f)
    }

    @Test
    fun `com tags - a arvore de estrutura da a hierarquia e a tabela`() {
        val doc = reflow("simples.pdf")
        val dump = doc.dump()
        val paragraphs = doc.blocks.filterIsInstance<Paragraph>()
        assertEquals(dump, "Relatório Trimestral", paragraphs.first { it.level == 1 }.text.trim())
        assertTrue(dump, paragraphs.any { it.level == 2 && it.text.trim() == "Destaques" })
        val table = doc.blocks.filterIsInstance<Table>().firstOrNull()
        assertNotNull(dump, table)
        assertEquals(dump, 4, table!!.rows.size)
        assertEquals(dump, 4, table.rows.first().cells.size)
        assertEquals(dump, "Sudeste", table.rows[2].cells[0].blocks.filterIsInstance<Paragraph>().first().text.trim())
        val bullets = doc.blocks.filterIsInstance<ListBlock>().first { !it.ordered }
        assertEquals(dump, 3, bullets.items.size)
        val long = paragraphs.first { it.text.startsWith("Este documento") }
        assertTrue(dump, long.text.endsWith("parágrafo só."))
        assertTrue(dump, long.runs.any { it.bold })
    }

    @Test
    fun `fodt gerado e XML valido e abre no LibreOffice`() {
        val doc = reflow("simples.pdf")
        val fodt = FodtWriter.write(doc) { null }
        javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(fodt.byteInputStream())
        assertTrue(fodt.contains("""<text:h text:style-name="Heading_1" text:outline-level="1">Relatório Trimestral</text:h>"""))
        assertTrue(fodt.contains("<table:table "))
        assertTrue(fodt.contains("""<text:span text:style-name="T_b">resultados de vendas"""))

        assumeTrue("soffice não está no PATH", sofficeAvailable())
        val file = File(folder.root, "reconstruido.fodt").apply { writeText(fodt) }
        val process = ProcessBuilder("soffice", "--headless", "--norestore", "--convert-to", "docx", "--outdir", folder.root.path, file.path).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        process.waitFor(180, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(log, 0, process.exitValue())
        val docx = File(folder.root, "reconstruido.docx")
        assertTrue(log, docx.isFile && docx.length() > 0)
        val xml = java.util.zip.ZipFile(docx).use { zip -> zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText() }
        assertTrue(xml.contains("Relatório Trimestral"))
        assertTrue(xml.contains("<w:tbl>"))
        assertTrue(xml.contains("Heading1") || xml.contains("Heading_1") || xml.contains("Ttulo1") || xml.contains("Heading"))
    }

    @Test
    fun `runs juntam linhas e desfazem hifenizacao`() {
        fun w(t: String, bold: Boolean = false) = Word(t, 0f, 10f, 11f, "F", bold, false)
        val runs = Runs.of(listOf(listOf(w("A"), w("cons-")), listOf(w("tru"), w("ção", bold = true))))
        assertEquals(listOf(Run("A constru "), Run("ção", bold = true)), runs)
        val plain = Runs.of(listOf(listOf(w("fim")), listOf(w("Começo"))))
        assertEquals("fim Começo", plain.joinToString("") { it.text })
    }

    @Test
    fun `marcadores de lista`() {
        fun w(t: String) = Word(t, 0f, 10f, 11f, "F", false, false)
        assertEquals(false to listOf(w("Item")), ListMarkers.strip(listOf(w("•"), w("Item"))))
        assertEquals(true to listOf(w("Item")), ListMarkers.strip(listOf(w("1."), w("Item"))))
        assertEquals(true to listOf(w("Item")), ListMarkers.strip(listOf(w("a)"), w("Item"))))
        assertEquals(null, ListMarkers.strip(listOf(w("Texto"), w("normal"))))
        assertEquals(null, ListMarkers.strip(listOf(w("2024."))))
    }

    private fun sofficeAvailable(): Boolean = runCatching {
        ProcessBuilder("soffice", "--version").redirectErrorStream(true).start().let { it.inputStream.readBytes(); it.waitFor() == 0 }
    }.getOrDefault(false)
}
