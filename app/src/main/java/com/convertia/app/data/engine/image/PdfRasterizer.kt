package com.convertia.app.data.engine.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.convertia.app.data.convert.ConversionJob
import com.convertia.app.data.convert.ProgressListener
import com.convertia.app.domain.ImageResize
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil

/** PDF → uma imagem por página (JPG, PNG ou WEBP), a 150 dpi, com os nomes "nome-1.png"… */
object PdfRasterizer {

    suspend fun render(pdf: File, job: ConversionJob, progress: ProgressListener, from: Double = 0.0): List<File> {
        val item = job.item
        val descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (e: SecurityException) {
            descriptor.close()
            throw IOException("PDF protegido por senha")
        } catch (e: IOException) {
            descriptor.close()
            throw IOException("Não foi possível abrir o PDF (${e.message})")
        }
        val outputs = mutableListOf<File>()
        try {
            val count = renderer.pageCount
            if (count == 0) throw IOException("O PDF não tem páginas")
            val scale = when (item.options.resize) {
                ImageResize.ORIGINAL, ImageResize.MAX_2560 -> RENDER_DPI / 72f
                ImageResize.HALF -> RENDER_DPI / 72f / 2
            }
            for (index in 0 until count) {
                coroutineContext.ensureActive()
                renderer.openPage(index).use { page ->
                    var w = ceil(page.width * scale).toInt()
                    var h = ceil(page.height * scale).toInt()
                    if (item.options.resize == ImageResize.MAX_2560 && maxOf(w, h) > 2560) {
                        val k = 2560f / maxOf(w, h)
                        w = ceil(w * k).toInt()
                        h = ceil(h * k).toInt()
                    }
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val output = if (count == 1) job.output else job.output(index + 1)
                    ImageIo.encode(bitmap, item.target.ext, item.options.quality, output)
                    bitmap.recycle()
                    outputs += output
                }
                progress.onProgress(from + (index + 1) * (100.0 - from) / count, null)
            }
        } finally {
            renderer.close()
            descriptor.close()
        }
        return outputs
    }

    private const val RENDER_DPI = 150f
}
