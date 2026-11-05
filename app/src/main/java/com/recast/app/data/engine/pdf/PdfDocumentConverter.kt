package com.recast.app.data.engine.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.recast.app.data.convert.ConversionJob
import com.recast.app.data.convert.FileConverter
import com.recast.app.data.convert.ProgressListener
import com.recast.app.data.engine.ocr.OcrEngine
import com.recast.app.data.engine.office.OfficeClient
import com.recast.app.domain.ConversionItem
import com.recast.app.domain.MediaCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil

/**
 * PDF → documento editável: lê texto, desenhos e imagens com posição ([PdfExtractor]),
 * reconstrói parágrafos, títulos, listas e tabelas ([PdfReflow]) e deixa o LibreOffice
 * exportar no formato pedido ([DocumentPipeline]). Páginas sem texto (digitalizadas)
 * passam pelo OCR quando o ajuste está ligado.
 */
class PdfDocumentConverter(
    private val extractor: PdfExtractor,
    private val office: OfficeClient?,
    private val ocr: OcrEngine? = null,
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
        var model = withContext(Dispatchers.Default) { extractor.extract(job.input, File(job.outputDir, "images")) }
        progress.onProgress(25.0, null)
        val scanned = model.pages.filter { page -> page.glyphs.count { it.text.isNotBlank() } < MIN_GLYPHS_PER_PAGE }
        if (scanned.isNotEmpty() && item.options.ocr && ocr != null) {
            model = recognizePages(model, scanned.map { it.index }, job, progress)
        }
        val doc = withContext(Dispatchers.Default) { PdfReflow.reflow(model) }
        if (!DocumentPipeline.hasText(doc)) {
            throw IOException(
                if (ocr != null && !item.options.ocr) "PDF sem texto: ligue \"Reconhecer texto\" ou converta para imagens"
                else "PDF sem texto reconhecível: converta para imagens",
            )
        }
        progress.onProgress(55.0, null)
        return DocumentPipeline.export(doc, job, office, progress, from = 55.0)
    }

    /** Renderiza as páginas digitalizadas a 300 dpi e troca os (poucos) glifos pelos reconhecidos. */
    private suspend fun recognizePages(model: PdfModel, indices: List<Int>, job: ConversionJob, progress: ProgressListener): PdfModel {
        val engine = ocr ?: return model
        val recognized = mutableMapOf<Int, PdfPage>()
        val descriptor = ParcelFileDescriptor.open(job.input, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            PdfRenderer(descriptor).use { renderer ->
                indices.forEachIndexed { n, index ->
                    coroutineContext.ensureActive()
                    renderer.openPage(index).use { page ->
                        val scale = OCR_DPI / 72f
                        val bitmap = Bitmap.createBitmap(ceil(page.width * scale).toInt(), ceil(page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val lines = engine.recognize(bitmap)
                        val original = model.pages[index]
                        val ocrPage = OcrEngine.toPage(lines, index, bitmap.width, bitmap.height, OcrEngine.renderScale(OCR_DPI))
                        // Mantém desenhos e imagens da página; o texto passa a ser o reconhecido.
                        recognized[index] = original.copy(glyphs = ocrPage.glyphs, width = original.width, height = original.height)
                        bitmap.recycle()
                    }
                    progress.onProgress(25.0 + (n + 1) * 25.0 / indices.size, null)
                }
            }
        } finally {
            descriptor.close()
        }
        // A árvore de estrutura não vale para texto reconhecido: a geometria decide.
        return PdfModel(model.pages.map { recognized[it.index] ?: it }, structure = null)
    }

    private companion object {
        const val MIN_GLYPHS_PER_PAGE = 20
        const val OCR_DPI = 300f
    }
}
