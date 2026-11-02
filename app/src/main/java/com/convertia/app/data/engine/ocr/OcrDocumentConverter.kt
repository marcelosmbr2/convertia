package com.convertia.app.data.engine.ocr

import com.convertia.app.data.convert.ConversionJob
import com.convertia.app.data.convert.FileConverter
import com.convertia.app.data.convert.ProgressListener
import com.convertia.app.data.engine.ffmpeg.Ffmpeg
import com.convertia.app.data.engine.ffmpeg.FfmpegArgs
import com.convertia.app.data.engine.image.ImageIo
import com.convertia.app.data.engine.office.OfficeClient
import com.convertia.app.data.engine.pdf.DocumentPipeline
import com.convertia.app.data.engine.pdf.PdfModel
import com.convertia.app.data.engine.pdf.PdfReflow
import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.ImageResize
import com.convertia.app.domain.MediaCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Imagem (foto ou digitalização) → documento editável, pelo OCR e pela mesma reconstrução do PDF. */
class OcrDocumentConverter(
    private val ocr: OcrEngine,
    private val ffmpeg: Ffmpeg,
    private val office: OfficeClient?,
) : FileConverter {

    override fun supports(item: ConversionItem): Boolean =
        item.category == MediaCategory.IMAGE && item.target.category == MediaCategory.DOCUMENT && item.target.ext != "PDF" &&
            (item.target.ext == "TXT" || office != null)

    override suspend fun convert(job: ConversionJob, progress: ProgressListener): List<File> {
        val item = job.item
        progress.onProgress(5.0, null)
        val bitmap = if (ImageIo.canDecode(item.ext)) {
            ImageIo.decode(job.input, item.ext, ImageResize.ORIGINAL)
        } else {
            val png = File(job.outputDir, "decoded.png")
            ffmpeg.run(FfmpegArgs.decodeToPng(job.input.path, png.path), job.outputDir, {})
            try { ImageIo.decode(png, "png", ImageResize.ORIGINAL) } finally { png.delete() }
        }
        progress.onProgress(15.0, null)
        val lines = ocr.recognize(bitmap)
        val page = OcrEngine.toPage(lines, 0, bitmap.width, bitmap.height, OcrEngine.pageScale(bitmap.width))
        bitmap.recycle()
        progress.onProgress(50.0, null)
        if (page.glyphs.isEmpty()) throw IOException("Nenhum texto reconhecido na imagem")
        val doc = withContext(Dispatchers.Default) { PdfReflow.reflow(PdfModel(listOf(page))) }
        return DocumentPipeline.export(doc, job, office, progress, from = 55.0)
    }
}
