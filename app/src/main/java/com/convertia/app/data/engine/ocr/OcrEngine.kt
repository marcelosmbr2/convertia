package com.convertia.app.data.engine.ocr

import android.graphics.Bitmap
import com.convertia.app.data.engine.pdf.Glyph
import com.convertia.app.data.engine.pdf.PdfPage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Uma palavra reconhecida, em pixels da imagem. */
data class OcrWord(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Uma linha reconhecida com as palavras dela. */
data class OcrLine(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float, val words: List<OcrWord>)

/**
 * Reconhecimento de texto no aparelho com o ML Kit (modelo latino embutido no APK). O
 * resultado vira o mesmo modelo de página do leitor de PDF, então a reconstrução de
 * parágrafos, títulos, listas e tabelas serve igual para texto fotografado ou digitalizado.
 */
class OcrEngine {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bitmap: Bitmap): List<OcrLine> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = suspendCancellableCoroutine<Text> { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(IOException("O reconhecimento de texto falhou (${it.message})")) }
        }
        return text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val words = line.elements.mapNotNull { element ->
                    val b = element.boundingBox ?: return@mapNotNull null
                    OcrWord(element.text, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
                }
                if (words.isEmpty()) null else OcrLine(line.text, box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat(), words)
            }
        }
    }

    fun close() = runCatching { recognizer.close() }

    companion object {
        /**
         * Converte as linhas reconhecidas numa página do modelo de PDF, em pontos: cada
         * palavra vira um glifo (a reconstrução só precisa de posições de palavra).
         * [scale] é pontos por pixel.
         */
        fun toPage(lines: List<OcrLine>, index: Int, widthPx: Int, heightPx: Int, scale: Float): PdfPage {
            val glyphs = lines.flatMap { line ->
                val lineHeight = (line.bottom - line.top) * scale
                val fontSize = (lineHeight / LINE_HEIGHT_FACTOR).coerceIn(4f, 200f)
                val baseline = line.bottom * scale - lineHeight * DESCENT_FRACTION
                line.words.map { word ->
                    Glyph(
                        text = word.text,
                        x = word.left * scale,
                        top = baseline - fontSize * 0.78f,
                        width = (word.right - word.left) * scale,
                        fontSize = fontSize,
                        baseline = baseline,
                        font = "OCR",
                        bold = false,
                        italic = false,
                    )
                }
            }
            return PdfPage(index, widthPx * scale, heightPx * scale, glyphs)
        }

        /** Escala para uma imagem solta virar uma página de largura A4. */
        fun pageScale(widthPx: Int): Float = A4_WIDTH_PT / widthPx.coerceAtLeast(1)

        /** Pixels → pontos para uma página renderizada a [dpi]. */
        fun renderScale(dpi: Float): Float = 72f / dpi

        private const val A4_WIDTH_PT = 595f
        private const val LINE_HEIGHT_FACTOR = 1.25f
        private const val DESCENT_FRACTION = 0.18f
    }
}
