package com.convertia.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.convertia.app.data.convert.ConversionJob
import com.convertia.app.data.convert.FileConverter
import com.convertia.app.data.engine.archive.ArchiveConverter
import com.convertia.app.data.engine.archive.RarExtractor
import com.convertia.app.data.engine.ffmpeg.Ffmpeg
import com.convertia.app.data.engine.ffmpeg.FfmpegMediaConverter
import com.convertia.app.data.engine.image.ImageConverter
import com.convertia.app.data.engine.ocr.OcrDocumentConverter
import com.convertia.app.data.engine.ocr.OcrEngine
import com.convertia.app.data.engine.ocr.SearchablePdfWriter
import com.convertia.app.data.engine.pdf.PdfBoxExtractor
import com.convertia.app.data.engine.pdf.PdfDocumentConverter
import com.convertia.app.data.engine.office.OfficeAvailability
import com.convertia.app.data.engine.office.OfficeClient
import com.convertia.app.data.engine.office.OfficeEngine
import com.convertia.app.data.engine.ytdlp.YtDlp
import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.ConversionOptions
import com.convertia.app.domain.ConversionSource
import com.convertia.app.domain.EngineCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Roda cada motor de conversão de verdade no aparelho/emulador, direto nos
 * [FileConverter]s (sem UI): confirma que o ffmpeg empacotado executa, que as APIs de
 * imagem gravam, que a libarchive lê RAR e, quando o APK traz o LibreOffice, que ele
 * converte documentos no processo ":office".
 */
@RunWith(AndroidJUnit4::class)
class ConvertersTest {

    companion object {
        private lateinit var context: Context
        private lateinit var ffmpeg: Ffmpeg
        private lateinit var root: File

        @BeforeClass
        @JvmStatic
        fun prepare() = runBlocking {
            context = ApplicationProvider.getApplicationContext()
            val ytDlp = YtDlp(context)
            ytDlp.prepare()
            ffmpeg = Ffmpeg(context, ytDlp)
            root = File(context.cacheDir, "converters-test").apply { deleteRecursively(); mkdirs() }
        }
    }

    private fun job(input: File, target: String, options: ConversionOptions = ConversionOptions(), caps: EngineCapabilities = EngineCapabilities.All): ConversionJob {
        val item = checkNotNull(ConversionItem.create("t", input.name, input.length(), "p", ConversionSource.Sample, target, options, caps))
        val dir = File(root, "out-${input.name}-$target").apply { deleteRecursively(); mkdirs() }
        return ConversionJob(item, input, dir, hardwareAcceleration = false)
    }

    private fun run(converter: FileConverter, job: ConversionJob): List<File> = runBlocking {
        assertTrue("${job.item.ext} → ${job.item.target.ext} suportado", converter.supports(job.item))
        val outputs = converter.convert(job) { _, _ -> }
        assertTrue("gerou saída", outputs.isNotEmpty())
        outputs.forEach { assertTrue("${it.name} não vazio", it.isFile && it.length() > 0) }
        outputs
    }

    private fun asset(name: String): File = File(root, name).also { file ->
        context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
    }

    private fun sampleVideo(): File = File(root, "sample.mp4").also { out ->
        if (out.isFile) return@also
        runBlocking {
            ffmpeg.run(
                listOf("-f", "lavfi", "-i", "testsrc2=size=320x180:rate=30", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000", "-t", "1.5", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", out.path),
                root, {},
            )
        }
    }

    private fun sampleAudio(): File = File(root, "sample.mp3").also { out ->
        if (out.isFile) return@also
        runBlocking { ffmpeg.run(listOf("-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100", "-t", "1.5", "-c:a", "libmp3lame", out.path), root, {}) }
    }

    private fun samplePng(): File = File(root, "sample.png").also { out ->
        if (out.isFile) return@also
        val bitmap = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawRect(20f, 20f, 180f, 100f, Paint().apply { color = Color.rgb(240, 113, 82) })
        }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun videoParaWebmEOpus() {
        val converter = FfmpegMediaConverter(ffmpeg)
        val webm = run(converter, job(sampleVideo(), "WEBM")).first()
        assertEquals("sample.webm", webm.name)
        val opus = run(converter, job(sampleVideo(), "OPUS")).first()
        assertEquals("sample.opus", opus.name)
        run(converter, job(sampleAudio(), "FLAC"))
        run(converter, job(sampleVideo(), "JPG"))
    }

    @Test
    fun imagemParaAvifJpgEPdf() {
        val converter = ImageConverter(ffmpeg)
        run(converter, job(samplePng(), "AVIF"))
        run(converter, job(samplePng(), "JPG"))
        run(converter, job(samplePng(), "WEBP"))
        run(converter, job(samplePng(), "PDF"))
        val svg = File(root, "logo.svg").apply {
            writeText("""<svg xmlns="http://www.w3.org/2000/svg" width="100" height="60"><rect width="100" height="60" fill="#f07152"/></svg>""")
        }
        run(converter, job(svg, "PNG"))
    }

    @Test
    fun pdfParaImagens() {
        val outputs = run(ImageConverter(ffmpeg), job(asset("sample.pdf"), "PNG"))
        assertEquals(1, outputs.size)
        assertEquals("sample.png", outputs.first().name)
    }

    @Test
    fun zipPara7zERarParaZip() {
        val zip = File(root, "arquivos.zip").apply {
            ZipOutputStream(outputStream()).use { out ->
                out.putNextEntry(ZipEntry("a.txt")); out.write("olá".toByteArray()); out.closeEntry()
                out.putNextEntry(ZipEntry("pasta/b.bin")); out.write(ByteArray(10_000) { it.toByte() }); out.closeEntry()
            }
        }
        val converter = ArchiveConverter(rarExtractor = RarExtractor())
        assertEquals("arquivos.7z", run(converter, job(zip, "7Z")).first().name)
        assertEquals("sample.zip", run(converter, job(asset("sample.rar"), "ZIP")).first().name)
    }

    /** Imagem com texto desenhado: o OCR precisa achar as palavras e o TXT sai sem LibreOffice. */
    @Test
    fun imagemComTextoParaTxtEPdfPesquisavel() {
        val image = File(root, "texto.png").apply {
            val bitmap = Bitmap.createBitmap(1200, 400, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f }
                drawText("Relatorio anual de vendas", 60f, 140f, paint)
                drawText("Receita cresceu 12 por cento", 60f, 260f, paint)
            }
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val ocr = OcrEngine()
        val txt = run(OcrDocumentConverter(ocr, ffmpeg, null), job(image, "TXT")).first()
        val text = txt.readText()
        assertTrue(text, text.contains("Relatorio") && text.contains("vendas"))
        val pdf = run(ImageConverter(ffmpeg, ocr, SearchablePdfWriter(context)), job(image, "PDF", ConversionOptions(ocr = true))).first()
        assertTrue(pdf.length() > 1_000)
    }

    /** PDF digital → TXT pela reconstrução, sem LibreOffice. */
    @Test
    fun pdfParaTexto() {
        val txt = run(PdfDocumentConverter(PdfBoxExtractor(context), null), job(asset("sample.pdf"), "TXT")).first()
        val text = txt.readText()
        assertTrue(text, text.contains("Relatório Trimestral") && text.contains("Sudeste"))
    }

    @Test
    fun documentosPeloLibreOffice() {
        assumeTrue("APK sem LibreOffice", OfficeAvailability.isAvailable(context))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val engine = OfficeEngine(OfficeClient(context, scope))
        val docx = asset("sample.docx")
        assertEquals("sample.pdf", run(engine, job(docx, "PDF")).first().name)
        run(engine, job(docx, "ODT"))
        run(engine, job(docx, "FB2"))
        run(engine, job(docx, "PNG"))
        assertEquals("sample.pdf", run(engine, job(asset("sample.epub"), "PDF")).first().name)
        // PDF → DOCX: reconstrução + LibreOffice.
        val rebuilt = run(PdfDocumentConverter(PdfBoxExtractor(context), OfficeClient(context, scope)), job(asset("sample.pdf"), "DOCX")).first()
        assertEquals("sample.docx", rebuilt.name)
    }
}
