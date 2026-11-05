package com.recast.app.data.engine.image

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.recast.app.data.convert.ConversionJob
import com.recast.app.data.convert.FileConverter
import com.recast.app.data.convert.ProgressListener
import com.recast.app.data.engine.ffmpeg.Ffmpeg
import com.recast.app.data.engine.ffmpeg.FfmpegArgs
import com.recast.app.data.engine.ocr.OcrEngine
import com.recast.app.data.engine.ocr.SearchablePdfWriter
import com.recast.app.domain.ConversionItem
import com.recast.app.domain.ImageResize
import com.recast.app.domain.MediaCategory
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil

/**
 * Imagens: imagem → imagem (Android, com o ffmpeg para o que ele não lê ou não grava),
 * imagem → PDF (uma página do tamanho da imagem; SVG fica vetorial) e PDF → imagens (uma
 * por página, a 150 dpi).
 */
class ImageConverter(
    private val ffmpeg: Ffmpeg,
    private val ocr: OcrEngine? = null,
    private val searchablePdf: SearchablePdfWriter? = null,
) : FileConverter {

    override fun supports(item: ConversionItem): Boolean = when {
        item.category == MediaCategory.IMAGE -> item.target.category == MediaCategory.IMAGE || item.target.ext == "PDF"
        item.ext == "pdf" -> item.target.category == MediaCategory.IMAGE && item.target.ext in BITMAP_FORMATS
        else -> false
    }

    override suspend fun convert(job: ConversionJob, progress: ProgressListener): List<File> {
        val item = job.item
        return when {
            item.ext == "pdf" -> pdfToImages(job, progress)
            item.target.ext == "PDF" -> listOf(imageToPdf(job, progress))
            else -> listOf(imageToImage(job, progress))
        }
    }

    private suspend fun imageToImage(job: ConversionJob, progress: ProgressListener): File {
        val item = job.item
        val target = item.target.ext
        val bitmap = decode(job, item.options.resize)
        progress.onProgress(40.0, null)
        coroutineContext.ensureActive()
        val output = job.output
        if (target in BITMAP_FORMATS || target == "HEIC") {
            ImageIo.encode(bitmap, target, item.options.quality, output)
            if (!item.options.stripMetadata && target in BITMAP_FORMATS) ImageIo.copyExif(job.input, output)
        } else {
            // AVIF, TIFF, BMP, GIF e ICO: PNG temporário → ffmpeg.
            val png = File(job.outputDir, "frame.png")
            ImageIo.encode(bitmap, "PNG", 100, png)
            progress.onProgress(60.0, null)
            ffmpeg.run(FfmpegArgs.image(png.path, output.path, target, item.options), job.outputDir, {})
            png.delete()
        }
        bitmap.recycle()
        if (!output.isFile || output.length() == 0L) throw IOException("Não foi possível gravar a imagem")
        return output
    }

    /** Decodifica pelo Android; o que ele não lê passa pelo ffmpeg e vira PNG antes. */
    private suspend fun decode(job: ConversionJob, resize: ImageResize): Bitmap {
        val ext = job.item.ext
        if (ImageIo.canDecode(ext)) return ImageIo.decode(job.input, ext, resize)
        if (ext in setOf("heic", "heif")) throw IOException("Este aparelho não lê HEIC (precisa do Android 9)")
        val png = File(job.outputDir, "decoded.png")
        ffmpeg.run(FfmpegArgs.decodeToPng(job.input.path, png.path), job.outputDir, {})
        return try {
            ImageIo.decode(png, "png", resize)
        } finally {
            png.delete()
        }
    }

    private suspend fun imageToPdf(job: ConversionJob, progress: ProgressListener): File {
        val item = job.item
        val output = job.output
        if (item.options.ocr && ocr != null && searchablePdf != null && item.ext != "svg") {
            // PDF pesquisável: a imagem com o texto reconhecido invisível por cima.
            val bitmap = decode(job, item.options.resize)
            progress.onProgress(30.0, null)
            val lines = ocr.recognize(bitmap)
            progress.onProgress(70.0, null)
            searchablePdf.write(listOf(bitmap to lines), output, item.options.quality)
            bitmap.recycle()
            return output
        }
        val document = PdfDocument()
        try {
            if (item.ext == "svg") {
                val (w, h) = ImageIo.svgSize(job.input)
                val (pageW, pageH) = pageSize(w, h)
                val page = document.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
                ImageIo.drawSvg(job.input, page.canvas, pageW.toFloat(), pageH.toFloat())
                document.finishPage(page)
            } else {
                val bitmap = decode(job, item.options.resize)
                progress.onProgress(50.0, null)
                coroutineContext.ensureActive()
                val (pageW, pageH) = pageSize(bitmap.width.toFloat(), bitmap.height.toFloat())
                val page = document.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                document.finishPage(page)
                bitmap.recycle()
            }
            output.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return output
    }

    /** Página na proporção da imagem, com o lado maior em A4 (842 pt). */
    private fun pageSize(width: Float, height: Float): Pair<Int, Int> {
        val scale = PAGE_LONG_SIDE_PT / maxOf(width, height)
        return maxOf(1, ceil(width * scale).toInt()) to maxOf(1, ceil(height * scale).toInt())
    }

    private suspend fun pdfToImages(job: ConversionJob, progress: ProgressListener): List<File> =
        PdfRasterizer.render(job.input, job, progress)

    private companion object {
        val BITMAP_FORMATS = setOf("JPG", "PNG", "WEBP")
        const val PAGE_LONG_SIDE_PT = 842f
    }
}

