package com.convertia.app.data.engine.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import androidx.heifwriter.HeifWriter
import com.caverock.androidsvg.SVG
import com.convertia.app.domain.ImageResize
import java.io.File
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Decodificação e gravação de imagens com as APIs do Android (sem ffmpeg). */
object ImageIo {

    /** Limite de pixels decodificados (≈ 120 MB em ARGB); acima disso a imagem é reduzida. */
    private const val MAX_PIXELS = 30_000_000L

    /** Formatos que o Android decodifica direto; o resto passa pelo ffmpeg antes. */
    fun canDecode(ext: String): Boolean = when (ext) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "ico", "dng" -> true
        "heic", "heif" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        "avif" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        "svg" -> true
        else -> false
    }

    /** Decodifica já orientada pelo EXIF e, se pedido, reduzida. */
    fun decode(file: File, ext: String, resize: ImageResize): Bitmap {
        if (ext == "svg") return renderSvg(file, resize)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeModern(file, resize) else decodeLegacy(file, resize)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeModern(file: File, resize: ImageResize): Bitmap {
        val source = ImageDecoder.createSource(file)
        return try {
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val (w, h) = targetSize(info.size.width, info.size.height, resize)
                if (w != info.size.width || h != info.size.height) decoder.setTargetSize(w, h)
            }
        } catch (e: ImageDecoder.DecodeException) {
            throw IOException("Não foi possível ler a imagem (${e.message})")
        }
    }

    private fun decodeLegacy(file: File, resize: ImageResize): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) throw IOException("Não foi possível ler a imagem")
        val (w, h) = targetSize(bounds.outWidth, bounds.outHeight, resize)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, w)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = BitmapFactory.decodeFile(file.path, options) ?: throw IOException("Não foi possível ler a imagem")
        if (bitmap.width != w || bitmap.height != h) bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val orientation = runCatching { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return applyOrientation(bitmap, orientation)
    }

    private fun renderSvg(file: File, resize: ImageResize): Bitmap {
        val svg = try {
            file.inputStream().use { SVG.getFromInputStream(it) }
        } catch (e: Exception) {
            throw IOException("Não foi possível ler o SVG (${e.message})")
        }
        val docWidth = svg.documentWidth.takeIf { it > 0 } ?: svg.documentViewBox?.width() ?: 0f
        val docHeight = svg.documentHeight.takeIf { it > 0 } ?: svg.documentViewBox?.height() ?: 0f
        val baseWidth = if (docWidth > 0) docWidth else DEFAULT_SVG_SIZE
        val baseHeight = if (docHeight > 0) docHeight else DEFAULT_SVG_SIZE
        val (w, h) = targetSize(ceil(baseWidth).toInt(), ceil(baseHeight).toInt(), resize)
        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bitmap))
        return bitmap
    }

    /** Desenha um SVG, vetorial, num canvas de [width]×[height]. */
    fun drawSvg(file: File, canvas: Canvas, width: Float, height: Float) {
        val svg = file.inputStream().use { SVG.getFromInputStream(it) }
        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")
        svg.renderToCanvas(canvas, android.graphics.RectF(0f, 0f, width, height))
    }

    /** Tamanho natural de um SVG em pontos (72/pol.), para a página do PDF. */
    fun svgSize(file: File): Pair<Float, Float> {
        val svg = file.inputStream().use { SVG.getFromInputStream(it) }
        val w = svg.documentWidth.takeIf { it > 0 } ?: svg.documentViewBox?.width()?.takeIf { it > 0 } ?: DEFAULT_SVG_SIZE
        val h = svg.documentHeight.takeIf { it > 0 } ?: svg.documentViewBox?.height()?.takeIf { it > 0 } ?: DEFAULT_SVG_SIZE
        return w to h
    }

    private const val DEFAULT_SVG_SIZE = 1024f

    /** Tamanho final pelo ajuste de redimensionar e pelo limite de memória. */
    fun targetSize(width: Int, height: Int, resize: ImageResize): Pair<Int, Int> {
        var scale = when (resize) {
            ImageResize.ORIGINAL -> 1.0
            ImageResize.HALF -> 0.5
            ImageResize.MAX_2560 -> minOf(1.0, 2560.0 / max(width, height))
        }
        val pixels = width.toLong() * height * scale * scale
        if (pixels > MAX_PIXELS) scale *= sqrt(MAX_PIXELS / pixels)
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    private fun sampleSize(width: Int, target: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= target) sample *= 2
        return sample
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Grava em JPG, PNG ou WEBP com `Bitmap.compress`; HEIC com o HeifWriter. */
    fun encode(bitmap: Bitmap, ext: String, quality: Int, output: File) {
        when (ext) {
            "JPG" -> compress(flatten(bitmap), Bitmap.CompressFormat.JPEG, quality, output)
            "PNG" -> compress(bitmap, Bitmap.CompressFormat.PNG, 100, output)
            "WEBP" -> {
                val format = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                    quality >= 100 -> Bitmap.CompressFormat.WEBP_LOSSLESS
                    else -> Bitmap.CompressFormat.WEBP_LOSSY
                }
                compress(bitmap, format, quality, output)
            }
            "HEIC" -> encodeHeif(bitmap, quality, output)
            else -> throw IOException("O Android não grava $ext")
        }
    }

    private fun compress(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int, output: File) {
        output.outputStream().use { out ->
            if (!bitmap.compress(format, quality.coerceIn(1, 100), out)) throw IOException("Não foi possível gravar a imagem")
        }
    }

    private fun encodeHeif(bitmap: Bitmap, quality: Int, output: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) throw IOException("Este aparelho não grava HEIC (precisa do Android 9)")
        encodeHeifModern(bitmap, quality, output)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun encodeHeifModern(bitmap: Bitmap, quality: Int, output: File) {
        try {
            val writer = HeifWriter.Builder(output.path, bitmap.width, bitmap.height, HeifWriter.INPUT_MODE_BITMAP)
                .setQuality(quality.coerceIn(1, 100))
                .setMaxImages(1)
                .build()
            writer.use {
                it.start()
                it.addBitmap(bitmap)
                it.stop(HEIF_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            output.delete()
            throw IOException("Este aparelho não grava HEIC (${e.message ?: "sem encoder HEVC"})")
        }
    }

    private const val HEIF_TIMEOUT_MS = 60_000L

    /** JPG não tem transparência: compõe sobre branco. */
    private fun flatten(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return flat
    }

    /** Copia os metadados EXIF da origem para a saída (JPG, PNG ou WEBP), já com a orientação normal. */
    fun copyExif(source: File, output: File) {
        val from = runCatching { ExifInterface(source) }.getOrNull() ?: return
        val to = runCatching { ExifInterface(output) }.getOrNull() ?: return
        var any = false
        for (tag in EXIF_TAGS) {
            from.getAttribute(tag)?.let { to.setAttribute(tag, it); any = true }
        }
        if (!any) return
        to.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        runCatching { to.saveAttributes() }
    }

    private val EXIF_TAGS = listOf(
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL, ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT, ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_TIMESTAMP,
    )
}
