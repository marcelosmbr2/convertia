package com.convertia.app.data.engine.office

import com.convertia.app.data.convert.ConversionJob
import com.convertia.app.data.convert.FileConverter
import com.convertia.app.data.convert.ProgressListener
import com.convertia.app.data.engine.image.PdfRasterizer
import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.MediaCategory
import java.io.File
import java.io.IOException

/**
 * Documentos, planilhas, apresentações e e-books pelo LibreOffice embutido. EPUB entra
 * convertido em HTML ([EpubToHtml]); FB2 sai do XHTML exportado ([Fb2Writer]); imagens
 * saem do PDF exportado ([PdfRasterizer]). Progresso em etapas, sem ETA: o LibreOffice
 * não informa andamento.
 */
class OfficeEngine(private val client: OfficeClient) : FileConverter {

    override fun supports(item: ConversionItem): Boolean {
        if (item.ext == "pdf") return false
        if (item.category != MediaCategory.DOCUMENT && item.category != MediaCategory.EBOOK) return false
        return item.target.category in setOf(MediaCategory.DOCUMENT, MediaCategory.EBOOK) ||
            (item.target.category == MediaCategory.IMAGE && item.target.ext in IMAGE_FORMATS)
    }

    override suspend fun convert(job: ConversionJob, progress: ProgressListener): List<File> {
        val item = job.item
        progress.onProgress(5.0, null)
        var title: String? = null
        var language: String? = null
        val input = if (item.ext == "epub") {
            val book = EpubToHtml.convert(job.input, File(job.outputDir, "epub"))
            title = book.title
            language = book.language
            book.html
        } else {
            job.input
        }
        progress.onProgress(15.0, null)

        val target = item.target.ext
        return when {
            target in IMAGE_FORMATS -> {
                val pdf = File(job.outputDir, "pages.pdf")
                client.convert(input, pdf, "pdf", pdfOptions(item, forImages = true))
                progress.onProgress(60.0, null)
                PdfRasterizer.render(pdf, job, progress, from = 60.0).also { pdf.delete() }
            }
            target == "FB2" -> {
                val xhtml = File(job.outputDir, "book.xhtml")
                client.convert(input, xhtml, "xhtml")
                progress.onProgress(80.0, null)
                Fb2Writer.write(xhtml, job.output, title ?: item.name.substringBeforeLast('.'), language)
                listOf(job.output)
            }
            else -> {
                val (format, options) = when (target) {
                    "PDF" -> "pdf" to pdfOptions(item, forImages = false)
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
        }.also { progress.onProgress(100.0, null) }
    }

    /** "Comprimir imagens" do inspetor: imagens incorporadas a 150 dpi, JPEG com a qualidade do slider. */
    private fun pdfOptions(item: ConversionItem, forImages: Boolean): String {
        if (forImages || !item.options.compressImages) return ""
        val quality = item.options.quality.coerceIn(1, 100)
        return """{"ReduceImageResolution":{"type":"boolean","value":"true"},"MaxImageResolution":{"type":"long","value":"150"},"Quality":{"type":"long","value":"$quality"}}"""
    }

    private companion object {
        val IMAGE_FORMATS = setOf("JPG", "PNG", "WEBP")
    }
}
