package com.convertia.app.data.engine.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Tabelas (com e sem grade), colunas, cabeçalho e rodapé, imagens — nos PDFs sem tags. */
class PdfLayoutTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun reflow(name: String): DocModel {
        val file = folder.newFile(name).apply {
            checkNotNull(PdfLayoutTest::class.java.getResourceAsStream("/pdf/$name")).use { input -> outputStream().use { input.copyTo(it) } }
        }
        return PdfReflow.reflow(ApachePdfBoxExtractor().extract(file, folder.newFolder("img-$name")))
    }

    private fun DocModel.dump(): String = "header=${header.joinToString("") { it.text }} footer=${footer.joinToString("") { it.text }}\n" + blocks.joinToString("\n") { block ->
        when (block) {
            is Paragraph -> "P${block.level}[${block.align}] ${block.text}"
            is ListBlock -> "L(${if (block.ordered) "1." else "•"}) " + block.items.joinToString(" | ") { it.joinToString("") { r -> r.text } }
            is Table -> "T ${block.rows.size}x${block.columnWidths.size}${if (block.bordered) " grade" else ""}" + block.rows.joinToString("") { r -> "\n   " + r.cells.joinToString(" | ") { c -> (if (c.colSpan > 1) "{${c.colSpan}}" else "") + c.blocks.filterIsInstance<Paragraph>().joinToString(" ") { it.text } } }
            is ImageBlock -> "IMG ${block.widthPt.toInt()}x${block.heightPt.toInt()}"
            PageBreak -> "---"
        }
    }

    private fun Cell.text() = blocks.filterIsInstance<Paragraph>().joinToString(" ") { it.text }.trim()

    @Test
    fun `tabela com grade e imagem pela geometria`() {
        val doc = reflow("simples-semtags.pdf")
        val dump = doc.dump()
        val table = doc.blocks.filterIsInstance<Table>().firstOrNull()
        assertNotNull(dump, table)
        assertTrue(dump, table!!.bordered)
        assertEquals(dump, 4, table.rows.size)
        assertEquals(dump, 4, table.columnWidths.size)
        assertEquals(dump, "Região", table.rows[0].cells[0].text())
        assertEquals(dump, "Sudeste", table.rows[2].cells[0].text())
        assertEquals(dump, "+17%", table.rows[3].cells[3].text())
        assertTrue(dump, table.rows[0].cells[0].blocks.filterIsInstance<Paragraph>().first().runs.first().bold)
        // A tabela fica entre o título "Tabela" e o título "Gráfico"; a imagem vem depois.
        val order = doc.blocks.map { b -> when (b) { is Paragraph -> b.text.trim(); is Table -> "TABLE"; is ImageBlock -> "IMG"; else -> "" } }
        assertTrue(dump, order.indexOf("Tabela") < order.indexOf("TABLE"))
        assertTrue(dump, order.indexOf("TABLE") < order.indexOf("Gráfico"))
        assertTrue(dump, order.indexOf("Gráfico") < order.indexOf("IMG"))
        assertTrue(dump, order.indexOf("IMG") < order.indexOfFirst { it.startsWith("Conclusão") })
        // Nada da tabela vazou para parágrafos soltos.
        assertTrue(dump, doc.blocks.filterIsInstance<Paragraph>().none { it.text.contains("Sudeste") })
    }

    @Test
    fun `tabela sem borda pelas colunas alinhadas`() {
        val doc = reflow("semborda-semtags.pdf")
        val dump = doc.dump()
        val table = doc.blocks.filterIsInstance<Table>().firstOrNull()
        assertNotNull(dump, table)
        assertTrue(dump, !table!!.bordered)
        assertEquals(dump, 5, table.rows.size)
        assertEquals(dump, 3, table.columnWidths.size)
        assertEquals(dump, "Produto", table.rows[0].cells[0].text())
        assertEquals(dump, "Caderno", table.rows[2].cells[0].text())
        assertEquals(dump, "R$ 149,00", table.rows[3].cells[2].text())
        val paragraphs = doc.blocks.filterIsInstance<Paragraph>()
        assertTrue(dump, paragraphs.any { it.text.trim() == "Texto depois da tabela." })
        assertTrue(dump, paragraphs.any { it.level == 1 && it.text.trim() == "Tabela sem borda" })
    }

    @Test
    fun `colunas, celulas mescladas, cabecalho e rodape`() {
        val doc = reflow("colunas-semtags.pdf")
        val dump = doc.dump()
        assertEquals(dump, "Relatório Anual 2026 — Convertia S.A.", doc.header.joinToString("") { it.text }.trim())
        assertTrue(dump, doc.footer.joinToString("") { it.text }.startsWith("Página"))
        val texts = doc.blocks.filterIsInstance<Paragraph>().map { it.text.trim() }
        assertTrue(dump, texts.none { it.contains("Relatório Anual") || it.startsWith("Página") })
        fun index(prefix: String) = texts.indexOfFirst { it.startsWith(prefix) }.also { assertTrue("$prefix\n$dump", it >= 0) }
        assertTrue(dump, index("Parágrafo de abertura") < index("Coluna esquerda, primeiro"))
        assertTrue(dump, index("Coluna esquerda, primeiro") < index("Coluna esquerda, segundo"))
        assertTrue(dump, index("Coluna esquerda, segundo") < index("Coluna direita começa"))
        assertTrue(dump, index("Coluna direita começa") < index("Último parágrafo"))
        assertTrue(dump, texts.any { it.startsWith("Coluna esquerda, primeiro") && it.endsWith("várias linhas de texto.") })
        val table = doc.blocks.filterIsInstance<Table>().firstOrNull()
        assertNotNull(dump, table)
        assertEquals(dump, 3, table!!.rows.size)
        assertEquals(dump, 3, table.columnWidths.size)
        assertEquals(dump, 1, table.rows[0].cells.size)
        assertEquals(dump, 3, table.rows[0].cells[0].colSpan)
        assertEquals(dump, "Cabeçalho mesclado", table.rows[0].cells[0].text())
        assertEquals(dump, "B2 com texto mais longo", table.rows[2].cells[1].text())
        assertTrue(dump, texts.any { it == "Segunda página" })
        assertTrue(dump, doc.blocks.filterIsInstance<Paragraph>().any { it.level == 1 && it.text.trim() == "Segunda página" })
    }

    @Test
    fun `com tags, as mesclagens vem dos atributos e cabecalho e rodape ficam de fora`() {
        val doc = reflow("colunas.pdf")
        val dump = doc.dump()
        val table = doc.blocks.filterIsInstance<Table>().firstOrNull()
        assertNotNull(dump, table)
        assertEquals(dump, 3, table!!.rows[0].cells[0].colSpan)
        val texts = doc.blocks.filterIsInstance<Paragraph>().map { it.text.trim() }
        assertTrue(dump, texts.none { it.contains("Relatório Anual") })
        assertTrue(dump, texts.indexOfFirst { it.startsWith("Coluna esquerda, segundo") } < texts.indexOfFirst { it.startsWith("Coluna direita") })
    }

    @Test
    fun `fodt com tabela mesclada, cabecalho e rodape e valido`() {
        val doc = reflow("colunas-semtags.pdf")
        val fodt = FodtWriter.write(doc) { path -> File(path).takeIf { it.isFile }?.readBytes() }
        javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(fodt.byteInputStream())
        assertTrue(fodt.contains("<style:header>"))
        assertTrue(fodt.contains("<text:page-number"))
        assertTrue(fodt.contains("""table:number-columns-spanned="3""""))
    }
}
