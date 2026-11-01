package com.convertia.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor
import kotlin.math.max

/**
 * Formatação de números como no mockup: unidades decimais e vírgula como separador
 * (fmtSize, fmtT e dec em mockup/Convertia v3.dc.html).
 */
object Formatters {

    /** Arredonda como o `Math.round` do JavaScript (meio para cima). */
    fun roundHalfUp(value: Double): Long = floor(value + 0.5).toLong()

    /** Número com [decimals] casas e vírgula decimal: 5.1 → "5,1". */
    fun decimal(value: Double, decimals: Int): String =
        BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

    /** "2,41 GB", "412 MB", "21,4 MB", "240 KB", "512 B". */
    fun size(bytes: Double): String = when {
        bytes >= 1e9 -> decimal(bytes / 1e9, 2) + " GB"
        bytes >= 1e8 -> "${roundHalfUp(bytes / 1e6)} MB"
        bytes >= 1e6 -> decimal(bytes / 1e6, 1) + " MB"
        bytes >= 1e3 -> "${roundHalfUp(bytes / 1e3)} KB"
        else -> "${roundHalfUp(bytes)} B"
    }

    fun size(bytes: Long): String = size(bytes.toDouble())

    /** Minutos e segundos: 81 → "1:21". */
    fun minutesSeconds(seconds: Double): String {
        val total = max(0L, roundHalfUp(seconds))
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }

    /** Variação percentual de tamanho com sinal tipográfico: "+28%", "−42%". */
    fun sizeDelta(inputBytes: Long, outputBytes: Long): String {
        if (inputBytes <= 0) return "+0%"
        val delta = roundHalfUp((outputBytes - inputBytes).toDouble() / inputBytes * 100)
        return (if (delta <= 0) "−" else "+") + kotlin.math.abs(delta) + "%"
    }
}
