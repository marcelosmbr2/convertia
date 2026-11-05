package com.recast.app.data.engine.office

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

class Fb2WriterTest {

    private fun fodt(name: String) = checkNotNull(Fb2WriterTest::class.java.getResourceAsStream("/office/$name")).bufferedReader().readText()

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())

    /** ODT plano exportado pelo LibreOffice a partir do DOCX de teste (título, lista, tabela, imagem). */
    @Test
    fun `odt plano do LibreOffice vira FB2 com secoes, lista, tabela e imagem`() {
        val fb2 = Fb2Writer.convert(fodt("relatorio.fodt"), title = null, language = "pt-BR")
        val doc = parse(fb2)
        assertEquals("FictionBook", doc.documentElement.tagName)
        assertEquals("Relatório Trimestral", doc.getElementsByTagName("book-title").item(0).textContent)
        assertEquals("pt-BR", doc.getElementsByTagName("lang").item(0).textContent)
        // Título principal + Destaques + Tabela + Gráfico.
        assertEquals(4, doc.getElementsByTagName("section").length)
        assertTrue(fb2, fb2.contains("<strong>resultados de vendas</strong>"))
        assertTrue(fb2, fb2.contains("<emphasis>comentários</emphasis>"))
        assertTrue(fb2, fb2.contains("<p>• Receita cresceu 12% em relação ao trimestre anterior.</p>"))
        assertEquals(1, doc.getElementsByTagName("table").length)
        assertEquals(4, doc.getElementsByTagName("tr").length)
        assertTrue(fb2, fb2.contains("<td><strong>Região</strong></td>"))
        assertEquals(1, doc.getElementsByTagName("binary").length)
        assertTrue(fb2, fb2.contains("""<image l:href="#img1.png"/>"""))
        val binary = doc.getElementsByTagName("binary").item(0)
        assertEquals("image/png", binary.attributes.getNamedItem("content-type").textContent)
        val bytes = Base64.getMimeDecoder().decode(binary.textContent.trim())
        assertTrue(bytes.size > 100 && bytes[1].toInt().toChar() == 'P')
        assertTrue(fb2, fb2.contains("Conclusão: o trimestre foi positivo"))
    }

    @Test
    fun `titulo informado prevalece, listas numeradas e documento vazio valido`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<office:document xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0">
<office:styles><text:list-style style:name="LN"><text:list-level-style-number text:level="1"/></text:list-style>
<style:style style:name="Bold" style:family="paragraph"><style:text-properties fo:font-weight="bold"/></style:style></office:styles>
<office:body><office:text>
<text:p text:style-name="Bold">Negrito &amp; cia</text:p>
<text:list text:style-name="LN"><text:list-item><text:p>Um</text:p></text:list-item><text:list-item><text:p>Dois</text:p></text:list-item></text:list>
</office:text></office:body></office:document>"""
        val fb2 = Fb2Writer.convert(xml, title = "Meu livro", language = null)
        val doc = parse(fb2)
        assertEquals("Meu livro", doc.getElementsByTagName("book-title").item(0).textContent)
        assertEquals("pt", doc.getElementsByTagName("lang").item(0).textContent)
        assertTrue(fb2, fb2.contains("<p><strong>Negrito &amp; cia</strong></p>"))
        assertTrue(fb2, fb2.contains("<p>1. Um</p>") && fb2.contains("<p>2. Dois</p>"))

        val empty = Fb2Writer.convert("""<office:document xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"><office:body><office:text/></office:body></office:document>""", null, null)
        assertEquals(1, parse(empty).getElementsByTagName("section").length)
    }
}
