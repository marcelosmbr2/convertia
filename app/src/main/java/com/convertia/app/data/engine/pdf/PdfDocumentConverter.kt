package com.convertia.app.data.engine.pdf

import com.convertia.app.data.convert.ConversionJob
import com.convertia.app.data.convert.FileConverter
import com.convertia.app.data.convert.ProgressListener
import com.convertia.app.data.engine.office.OfficeClient
import com.convertia.app.data.engine.office.OfficeExport
import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.MediaCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * PDF → documento editável: lê texto, desenhos e imagens com posição ([PdfExtractor]),
 * reconstrói parágrafos, títulos, listas e tabelas ([PdfReflow]), grava um ODT plano
 * ([FodtWriter]) e deixa o LibreOffice exportar no formato pedido. TXT sai direto, sem
 * LibreOffice.
 */
class PdfDocumentConverter(
    private val extractor: PdfExtractor,
    private val office: OfficeClient?,
) : FileConverter {

    override fun supports(item: ConversionItem): Boolean {
        if (item.ext != "pdf") return false
        return when (item.target.category) {
            MediaCategory.DOCUMENT -> item.target.ext == "TXT" || (office != null && item.target.ext != "PDF")
            MediaCategory.EBOOK -> office != null
            else -> false
        }
    }

    override suspend fun convert(job: ConversionJob, progress: ProgressListener): List<File> {
        val item = job.item
        progress.onProgress(3.0, null)
        val doc = withContext(Dispatchers.Default) {
            val model = extractor.extract(job.input, File(job.outputDir, "images"))
            progress.onProgress(35.0, null)
            PdfReflow.reflow(model)
        }
        if (doc.blocks.none { it is Paragraph || it is ListBlock || it is Table }) {
            throw IOException("PDF sem texto: ligue o OCR ou converta para imagens")
        }
        progress.onProgress(45.0, null)
        if (item.target.ext == "TXT") {
            job.output.writeText(doc.plainText())
            progress.onProgress(100.0, null)
            return listOf(job.output)
        }
        val client = office ?: throw IOException("Este build não tem o LibreOffice")
        val fodt = File(job.outputDir, "reconstruido.fodt")
        fodt.writeText(FodtWriter.write(doc) { path -> File(path).takeIf { it.isFile }?.readBytes() })
        progress.onProgress(55.0, null)
        val title = doc.blocks.filterIsInstance<Paragraph>().firstOrNull { it.level in 1..2 }?.text ?: item.name.substringBeforeLast('.')
        return OfficeExport.export(client, fodt, job, progress, title, "pt", from = 55.0)
            .also { progress.onProgress(100.0, null) }
    }
}
