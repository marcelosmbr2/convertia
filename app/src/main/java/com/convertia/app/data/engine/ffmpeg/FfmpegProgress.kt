package com.convertia.app.data.engine.ffmpeg

import kotlin.math.roundToInt

/**
 * Lê a saída de `ffmpeg -progress pipe:1` (pares `chave=valor`, um bloco por segundo
 * terminado em `progress=continue|end`) e converte em percentual e tempo restante.
 */
class FfmpegProgress(private val durationSeconds: Double?) {
    data class Update(val percent: Double, val etaSeconds: Int?)

    private var outTimeSeconds = 0.0
    private var speed: Double? = null
    private var best = 0.0

    /** Uma linha da saída; devolve algo só ao fim de cada bloco (`progress=`). */
    fun onLine(line: String): Update? {
        val (key, value) = line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } ?: return null
        when (key) {
            "out_time_us" -> value.toLongOrNull()?.let { outTimeSeconds = it / 1_000_000.0 }
            "out_time_ms" -> if (outTimeSeconds == 0.0) value.toLongOrNull()?.let { outTimeSeconds = it / 1_000_000.0 }
            "speed" -> speed = value.removeSuffix("x").toDoubleOrNull()?.takeIf { it > 0 }
            "progress" -> return if (value == "end") Update(100.0, 0) else current()
        }
        return null
    }

    private fun current(): Update? {
        val duration = durationSeconds ?: return null
        val fraction = (outTimeSeconds / duration).coerceIn(0.0, 1.0)
        best = maxOf(best, (fraction * 100).coerceAtMost(MAX_BEFORE_DONE))
        val eta = speed?.let { ((duration - outTimeSeconds) / it).roundToInt().coerceAtLeast(1) }
        return Update(best, eta)
    }

    private companion object {
        /** O arquivo ainda está sendo fechado quando o último bloco chega. */
        const val MAX_BEFORE_DONE = 99.5
    }
}
