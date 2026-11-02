package com.convertia.app.data.engine.office

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

class Fb2WriterTest {

    /** Trecho no formato que o LibreOffice exporta como XHTML. */
    private val xhtml = """<!DOCTYPE html>
<html><head><meta http-equiv="content-type" content="text/html; charset=utf-8"/><title></title></head>
<body lang="pt-BR" text="#000000" dir="ltr">
<h1 class="western" align="left"> Relatório Trimestral</h1>
<p class="western">Este documento resume os <b>resultados de vendas</b> do terceiro trimestre, com <i>comentários</i>&nbsp;da equipe.</p>
<h2 class="western">Destaques</h2>
<ul><li><p class="western">Receita cresceu 12%.</p></li><li><p class="western">Novos clientes: 340.</p></li></ul>
<table width="223" cellpadding="2" cellspacing="0"><col width="61"/><tr><th><p align="center">Região</p></th><th><p>Q3</p></th></tr><tr><td><p>Sul</p></td><td><p>1.380</p></td></tr></table>
<p class="western"><img src="grafico.png" name="Image1" width="320" height="180"/></p>
<p class="western">Conclusão: positivo &amp; otimista.</p>
</body></html>"""

    @Test
    fun `secoes, paragrafos, enfase, lista, tabela e imagem`() {
        val fb2 = Fb2Writer.convert(xhtml, title = null, language = null) { src -> if (src == "grafico.png") byteArrayOf(1, 2, 3) else null }
        // XML válido.
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fb2.byteInputStream())
        assertEquals("FictionBook", doc.documentElement.tagName)
        assertEquals("Relatório Trimestral", doc.getElementsByTagName("book-title").item(0).textContent)
        assertEquals("pt", doc.getElementsByTagName("lang").item(0).textContent)
        assertEquals(2, doc.getElementsByTagName("section").length)
        assertEquals(2, doc.getElementsByTagName("title").length)
        assertTrue(fb2.contains("<strong>resultados de vendas</strong>"))
        assertTrue(fb2.contains("<emphasis>comentários</emphasis>"))
        assertTrue(fb2.contains("<p>• Receita cresceu 12%.</p>"))
        assertEquals(1, doc.getElementsByTagName("table").length)
        assertEquals(2, doc.getElementsByTagName("tr").length)
        assertTrue(fb2.contains("""<image l:href="#img1.png"/>"""))
        val binary = doc.getElementsByTagName("binary").item(0)
        assertEquals("image/png", binary.attributes.getNamedItem("content-type").textContent)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(Base64.getMimeDecoder().decode(binary.textContent.trim())))
        assertTrue(fb2.contains("positivo &amp; otimista"))
    }

    @Test
    fun `titulo e idioma informados prevalecem e documento vazio ainda e valido`() {
        val fb2 = Fb2Writer.convert("<html><body></body></html>", title = "Meu livro", language = "en") { null }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fb2.byteInputStream())
        assertEquals("Meu livro", doc.getElementsByTagName("book-title").item(0).textContent)
        assertEquals("en", doc.getElementsByTagName("lang").item(0).textContent)
        assertEquals(1, doc.getElementsByTagName("section").length)
    }

    @Test
    fun `imagem em data URI`() {
        val png = Base64.getEncoder().encodeToString(byteArrayOf(9, 9))
        val fb2 = Fb2Writer.convert("""<html><body><p>x</p><img src="data:image/jpeg;base64,$png"/></body></html>""", null, null) { null }
        assertTrue(fb2.contains("""content-type="image/jpeg""""))
        assertTrue(fb2.contains("""<image l:href="#img1.jpg"/>"""))
    }
}
