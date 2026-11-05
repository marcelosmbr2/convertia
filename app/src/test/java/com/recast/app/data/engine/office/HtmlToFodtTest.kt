package com.recast.app.data.engine.office

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

class HtmlToFodtTest {

    private val html = """<!DOCTYPE html><html><head><title>Relatório</title><link rel="stylesheet" href="x.css"></head><body>
<h1>Relatório Trimestral</h1>
<p>Este documento resume os <b>resultados de vendas</b> do trimestre, com <i>comentários</i> e um <a href="https://exemplo.org">link</a>.<br>Nova linha.</p>
Texto solto fora de parágrafo.
<h2 style="text-align:center">Destaques</h2>
<ul><li>Receita cresceu 12%</li><li>Novos clientes <ul><li>340 no total</li></ul></li></ul>
<ol><li>Primeiro</li><li>Segundo</li></ol>
<table><tr><th colspan="2">Cabeçalho</th></tr><tr><td>Sul</td><td>1.200</td></tr></table>
<p><img src="grafico.png" width="320" height="180"></p>
<div style="page-break-before: always"></div>
<pre>linha 1
linha 2</pre>
<blockquote>Citação curta.</blockquote>
<script>alert(1)</script>
</body></html>"""

    @Test
    fun `html vira odt plano valido com titulos, listas, tabela e imagem`() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + ByteArray(12) + byteArrayOf(0, 0, 1, 64, 0, 0, 0, 180.toByte()) + ByteArray(8)
        val fodt = HtmlToFodt.convert(html, title = null, language = "pt-BR") { src -> if (src == "grafico.png") png else null }
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(fodt.byteInputStream())
        assertEquals("office:document", doc.documentElement.tagName)
        assertTrue(fodt, fodt.contains("<dc:title>Relatório</dc:title>"))
        assertTrue(fodt, fodt.contains("""<text:h text:style-name="Heading_1" text:outline-level="1">Relatório Trimestral</text:h>"""))
        assertTrue(fodt, fodt.contains("""<text:h text:style-name="Heading_2" text:outline-level="2">Destaques</text:h>"""))
        assertTrue(fodt, fodt.contains("""<text:span text:style-name="T_b">resultados de vendas</text:span>"""))
        assertTrue(fodt, fodt.contains("""<text:span text:style-name="T_i">comentários</text:span>"""))
        assertTrue(fodt, fodt.contains("""<text:a xlink:type="simple" xlink:href="https://exemplo.org">link</text:a>"""))
        assertTrue(fodt, fodt.contains("<text:line-break/>Nova linha."))
        assertTrue(fodt, fodt.contains("""<text:p text:style-name="Standard">Texto solto fora de parágrafo.</text:p>"""))
        assertEquals(2, Regex("""<text:list text:style-name="L_bullet">""").findAll(fodt).count())
        assertEquals(1, Regex("""<text:list text:style-name="L_number">""").findAll(fodt).count())
        assertTrue(fodt, fodt.contains("""table:number-columns-spanned="2""""))
        assertTrue(fodt, fodt.contains("""<text:span text:style-name="T_b">Cabeçalho</text:span>"""))
        assertTrue(fodt, fodt.contains("<table:covered-table-cell/>"))
        assertTrue(fodt, fodt.contains("<office:binary-data>" + Base64.getMimeEncoder(76, "\n".toByteArray()).encodeToString(png).take(20)))
        assertTrue(fodt, fodt.contains("""svg:width="240.00pt" svg:height="135.00pt""""))
        assertTrue(fodt, fodt.contains("""<text:p text:style-name="P_break"/>"""))
        assertTrue(fodt, fodt.contains("""<text:p text:style-name="Code">linha 1</text:p>"""))
        assertTrue(fodt, fodt.contains("""<text:p text:style-name="Quote">Citação curta.</text:p>"""))
        assertTrue(fodt, !fodt.contains("alert(1)"))
    }

    @Test
    fun `html quebrado ainda vira documento`() {
        val fodt = HtmlToFodt.convert("<p>Sem fechar <b>negrito <i>e itálico</p><p>Outro", title = "T", language = null) { null }
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fodt.byteInputStream())
        assertTrue(fodt, fodt.contains("Outro"))
        assertTrue(fodt, fodt.contains("""<text:span text:style-name="T_bi">e itálico</text:span>"""))
    }
}
