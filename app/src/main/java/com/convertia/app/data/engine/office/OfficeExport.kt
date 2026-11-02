package com.convertia.app.data.engine.office

import com.convertia.app.data.convert.ConversionJob
import com.convertia.app.data.convert.ProgressListener
import com.convertia.app.data.engine.image.PdfRasterizer
import com.convertia.app.domain.ConversionItem
import java.io.File
import java.io.IOException

/**
 * Grava um documento que o LibreOffice abre ([input]) no formato pedido pelo item:
 * direto (PDF, DOCX, ODT, RTF, TXT, HTML, MD, EPUB), por ODT plano → FB2, ou por PDF →
 * imagens. Compartilhado pelo motor de documentos e pelo de PDF → documento.
 */
object OfficeExport {

    val IMAGE_FORMATS = setOf("JPG", "PNG", "WEBP")

    suspend fun export(
        client: OfficeClient,
        input: File,
        job: ConversionJob,
        progress: ProgressListener,
        title: String?,
        language: String?,
        from: Double,
    ): List<File> {
        val item = job.item
        val target = item.target.ext
        return when {
            target in IMAGE_FORMATS -> {
                val pdf = File(job.outputDir, "pages.pdf")
                client.convert(input, pdf, "pdf", "")
                val mid = from + (100 - from) * 0.6
                progress.onProgress(mid, null)
                PdfRasterizer.render(pdf, job, progress, from = mid).also { pdf.delete() }
            }
            target == "FB2" -> {
                // ODT plano (XML único): o exportador XHTML precisa de XSLT, que o build Android não traz.
                val fodt = File(job.outputDir, "book.fodt")
                client.convert(input, fodt, "fodt", "")
                progress.onProgress(from + (100 - from) * 0.8, null)
                Fb2Writer.write(fodt, job.output, title ?: item.name.substringBeforeLast('.'), language)
                listOf(job.output)
            }
            else -> {
                val (format, options) = when (target) {
                    "PDF" -> "pdf" to pdfOptions(item)
                    "TXT" -> "txt" to "UTF8"
                    "DOCX" -> "docx" to ""
                    "ODT" -> "odt" to ""
                    "RTF" -> "rtf" to ""
                    "HTML" -> "html" to ""
                    "MD" -> "md" to ""
                    "EPUB" -> "epub" to ""
                    else -> throw IOException("O LibreOffice não grava $target")
                }
                client.convert(input, job.output, format, options)
                listOf(job.output)
            }
        }
    }

    /** "Comprimir imagens" do inspetor: imagens incorporadas a 150 dpi, JPEG com a qualidade do slider. */
    fun pdfOptions(item: ConversionItem): String {
        if (!item.options.compressImages) return ""
        val quality = item.options.quality.coerceIn(1, 100)
        return """{"ReduceImageResolution":{"type":"boolean","value":"true"},"MaxImageResolution":{"type":"long","value":"150"},"Quality":{"type":"long","value":"$quality"}}"""
    }
}
