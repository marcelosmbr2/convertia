package com.convertia.app.data.engine.pdf

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Palavra numa linha: texto já com os glifos juntos e o estilo predominante. */
data class Word(
    val text: String,
    val x: Float,
    val right: Float,
    val fontSize: Float,
    val font: String,
    val bold: Boolean,
    val italic: Boolean,
) {
    val width: Float get() = right - x
}

/**
 * Trecho contínuo de uma linha: palavras separadas por espaços normais. Uma linha com um
 * vão grande no meio (colunas, células de tabela) vira mais de um trecho.
 */
data class Chunk(val words: List<Word>) {
    val x: Float get() = words.first().x
    val right: Float get() = words.last().right
    val text: String get() = words.joinToString(" ") { it.text }
    val fontSize: Float get() = words.maxOf { it.fontSize }
    val bold: Boolean get() = words.count { it.bold } * 2 > words.size
    val italic: Boolean get() = words.count { it.italic } * 2 > words.size
}

/** Linha de texto: todos os glifos com a mesma linha de base, da esquerda para a direita. */
data class Line(val page: Int, val baseline: Float, val top: Float, val chunks: List<Chunk>) {
    val x: Float get() = chunks.first().x
    val right: Float get() = chunks.last().right
    val fontSize: Float get() = chunks.maxOf { it.fontSize }
    val bottom: Float get() = top + fontSize
    val text: String get() = chunks.joinToString("   ") { it.text }
    val words: List<Word> get() = chunks.flatMap { it.words }
}

/** Agrupa glifos em palavras, trechos e linhas. Função pura. */
object TextLines {

    fun build(page: PdfPage, glyphs: List<Glyph> = page.glyphs): List<Line> {
        val visible = glyphs.filter { it.text.isNotEmpty() && it.fontSize > 0 }
        if (visible.isEmpty()) return emptyList()
        val sorted = visible.sortedWith(compareBy({ it.baseline }, { it.x }))
        val lines = mutableListOf<MutableList<Glyph>>()
        for (glyph in sorted) {
            val current = lines.lastOrNull()
            if (current != null && sameLine(current, glyph)) current += glyph else lines += mutableListOf(glyph)
        }
        return lines.map { toLine(page.index, it.sortedBy { g -> g.x }) }
    }

    /** Mesma linha: linhas de base próximas (tolerância pela fonte menor), sem sobreposição horizontal estranha. */
    private fun sameLine(line: List<Glyph>, glyph: Glyph): Boolean {
        val reference = line.last()
        val tolerance = min(reference.fontSize, glyph.fontSize) * BASELINE_TOLERANCE
        return abs(reference.baseline - glyph.baseline) <= max(tolerance, 1f)
    }

    private fun toLine(page: Int, glyphs: List<Glyph>): Line {
        val chunks = mutableListOf<MutableList<Word>>()
        var current = StringBuilder()
        var wordStart = 0f
        var wordEnd = 0f
        var wordGlyphs = mutableListOf<Glyph>()
        var words = mutableListOf<Word>()

        fun flushWord() {
            if (current.isNotEmpty()) {
                val text = current.toString()
                val bold = wordGlyphs.count { it.bold } * 2 > wordGlyphs.size
                val italic = wordGlyphs.count { it.italic } * 2 > wordGlyphs.size
                val size = wordGlyphs.maxOf { it.fontSize }
                val font = wordGlyphs.groupingBy { it.font }.eachCount().maxByOrNull { it.value }!!.key
                words += Word(text, wordStart, wordEnd, size, font, bold, italic)
            }
            current = StringBuilder()
            wordGlyphs = mutableListOf()
        }

        fun flushChunk() {
            flushWord()
            if (words.isNotEmpty()) chunks += words
            words = mutableListOf()
        }

        var previous: Glyph? = null
        for (glyph in glyphs) {
            val isSpace = glyph.text.isBlank()
            if (previous != null) {
                val gap = glyph.x - previous.right
                val space = max(previous.fontSize, glyph.fontSize) * SPACE_FRACTION
                when {
                    gap > max(previous.fontSize, glyph.fontSize) * CHUNK_GAP_FRACTION -> flushChunk()
                    isSpace || gap > space -> flushWord()
                }
            }
            if (isSpace) {
                previous = glyph
                continue
            }
            if (current.isEmpty()) wordStart = glyph.x
            current.append(glyph.text)
            wordEnd = max(wordEnd, glyph.right)
            if (current.length == glyph.text.length) wordEnd = glyph.right
            wordGlyphs += glyph
            previous = glyph
        }
        flushChunk()
        val top = glyphs.minOf { it.top }
        return Line(page, glyphs.first().baseline, top, chunks.map(::Chunk))
    }

    private const val BASELINE_TOLERANCE = 0.45f

    /** Vão maior que isto (em "em") separa palavras. */
    private const val SPACE_FRACTION = 0.18f

    /** Vão maior que isto (em "em") separa trechos (colunas ou células). */
    private const val CHUNK_GAP_FRACTION = 2.2f
}
