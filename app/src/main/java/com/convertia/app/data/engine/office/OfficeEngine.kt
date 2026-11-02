package com.convertia.app.data.engine.office

import com.convertia.app.data.convert.ConversionJob
import com.convertia.app.data.convert.FileConverter
import com.convertia.app.data.convert.ProgressListener
import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.MediaCategory
import java.io.File

/**
 * Documentos, planilhas, apresentações e e-books pelo LibreOffice embutido. HTML e EPUB
 * (via [EpubToHtml]) entram como ODT plano ([HtmlToFodt]), porque o LibreOffice do Android
 * não traz o Writer/Web; a gravação no formato pedido fica com [OfficeExport]. Progresso em
 * etapas, sem ETA: o LibreOffice não informa andamento.
 */
class OfficeEngine(private val client: OfficeClient) : FileConverter {

    override fun supports(item: ConversionItem): Boolean {
        if (item.ext == "pdf") return false
        if (item.category != MediaCategory.DOCUMENT && item.category != MediaCategory.EBOOK) return false
        return item.target.category in setOf(MediaCategory.DOCUMENT, MediaCategory.EBOOK) ||
            (item.target.category == MediaCategory.IMAGE && item.target.ext in OfficeExport.IMAGE_FORMATS)
    }

    override suspend fun convert(job: ConversionJob, progress: ProgressListener): List<File> {
        val item = job.item
        progress.onProgress(5.0, null)
        var title: String? = null
        var language: String? = null
        val input = when (item.ext) {
            "epub" -> {
                val book = EpubToHtml.convert(job.input, File(job.outputDir, "epub"))
                title = book.title
                language = book.language
                File(job.outputDir, "book.fodt").also { HtmlToFodt.convert(book.html, it, book.title, book.language) }
            }
            "html", "htm" -> File(job.outputDir, "page.fodt").also { HtmlToFodt.convert(job.input, it, null, null) }
            else -> job.input
        }
        progress.onProgress(15.0, null)
        return OfficeExport.export(client, input, job, progress, title, language, from = 15.0)
            .also { progress.onProgress(100.0, null) }
    }
}
