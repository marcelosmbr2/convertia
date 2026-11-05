package com.recast.app.data.engine.pdf

import com.recast.app.data.convert.ConversionJob
import com.recast.app.data.convert.ProgressListener
import com.recast.app.data.engine.office.OfficeClient
import com.recast.app.data.engine.office.OfficeExport
import java.io.File
import java.io.IOException

/** Do documento reconstruído ao arquivo pedido: TXT direto; o resto via ODT plano e LibreOffice. */
object DocumentPipeline {

    fun hasText(doc: DocModel): Boolean = doc.blocks.any { it is Paragraph || it is ListBlock || it is Table }

    suspend fun export(doc: DocModel, job: ConversionJob, office: OfficeClient?, progress: ProgressListener, from: Double): List<File> {
        val item = job.item
        if (item.target.ext == "TXT") {
            job.output.writeText(doc.plainText())
            progress.onProgress(100.0, null)
            return listOf(job.output)
        }
        val client = office ?: throw IOException("Este build não tem o LibreOffice")
        val fodt = File(job.outputDir, "reconstruido.fodt")
        fodt.writeText(FodtWriter.write(doc) { path -> File(path).takeIf { it.isFile }?.readBytes() })
        val mid = from + (100 - from) * 0.2
        progress.onProgress(mid, null)
        val title = doc.blocks.filterIsInstance<Paragraph>().firstOrNull { it.level in 1..2 }?.text ?: item.name.substringBeforeLast('.')
        return OfficeExport.export(client, fodt, job, progress, title, "pt", from = mid)
            .also { progress.onProgress(100.0, null) }
    }
}
