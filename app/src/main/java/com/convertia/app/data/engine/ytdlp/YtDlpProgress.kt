package com.convertia.app.data.engine.ytdlp

/**
 * Lê as linhas de progresso do yt-dlp
 * (`[download]  45.3% of ~ 412.00MiB at  5.10MiB/s ETA 00:20`) e soma o progresso das
 * faixas: um vídeo com áudio separado baixa duas faixas, cada uma de 0 a 100%.
 */
class YtDlpProgress(
    /** Tamanho esperado de cada faixa, em ordem; vazio se desconhecido. */
    private val streamSizesMb: List<Double>,
    /** Progresso já feito antes de retomar, para a barra não voltar. */
    startPercent: Double = 0.0,
) {
    data class Update(val percent: Double, val speedMbps: Double, val totalMb: Double?)

    private var stream = -1
    private var best = startPercent
    private val totals = mutableMapOf<Int, Double>()

    /**
     * Processa uma linha. Devolve uma atualização quando a linha traz progresso; o
     * percentual nunca diminui e fica abaixo de 100 até o fim do pós-processamento.
     */
    fun onLine(line: String, streamPercent: Float? = null): Update? {
        if (DESTINATION.containsMatchIn(line)) {
            stream++
            return null
        }
        val match = PROGRESS.find(line) ?: return null
        if (stream < 0) stream = 0
        val percent = streamPercent?.toDouble() ?: match.groupValues[1].toDouble()
        val total = sizeMb(match.groupValues[2], match.groupValues[3])
        if (total != null) totals[stream] = total
        val speed = SPEED.find(line)?.let { sizeMb(it.groupValues[1], it.groupValues[2]) } ?: 0.0

        val combined = combine(percent)
        best = maxOf(best, combined.coerceAtMost(MAX_BEFORE_DONE))
        return Update(best, speed, knownTotal())
    }

    private fun combine(percent: Double): Double {
        val sizes = sizesForWeights()
        if (sizes.size <= 1) return percent
        val current = stream.coerceIn(0, sizes.lastIndex)
        val done = sizes.take(current).sum()
        val total = sizes.sum()
        return if (total <= 0) percent else (done + sizes[current] * percent / 100) / total * 100
    }

    /** Tamanhos reais quando o yt-dlp já informou; senão os esperados; senão pesos iguais. */
    private fun sizesForWeights(): List<Double> {
        val count = maxOf(streamSizesMb.size, stream + 1)
        return (0 until count).map { i -> totals[i] ?: streamSizesMb.getOrNull(i)?.takeIf { it > 0 } ?: 1.0 }
    }

    private fun knownTotal(): Double? {
        val count = maxOf(streamSizesMb.size, stream + 1)
        val sizes = (0 until count).map { i -> totals[i] ?: streamSizesMb.getOrNull(i)?.takeIf { it > 0 } }
        return if (sizes.all { it != null }) sizes.sumOf { it!! } else null
    }

    companion object {
        /** Teto até o arquivo final ficar pronto (juntar faixas, embutir miniatura). */
        const val MAX_BEFORE_DONE = 99.5

        private val DESTINATION = Regex("""^\[download] Destination:""")
        private val PROGRESS = Regex("""^\[download]\s+([\d.]+)% of\s+~?\s*([\d.]+)\s*([KMGT]?i?B)""")
        private val SPEED = Regex("""\bat\s+([\d.]+)\s*([KMGT]?i?B)/s""")

        /** Converte para megabytes decimais (como o resto do app). */
        fun sizeMb(value: String, unit: String): Double? {
            val number = value.toDoubleOrNull() ?: return null
            val bytes = when (unit) {
                "B" -> 1.0
                "KiB" -> 1024.0
                "MiB" -> 1024.0 * 1024
                "GiB" -> 1024.0 * 1024 * 1024
                "TiB" -> 1024.0 * 1024 * 1024 * 1024
                "KB" -> 1e3
                "MB" -> 1e6
                "GB" -> 1e9
                "TB" -> 1e12
                else -> return null
            }
            return number * bytes / 1e6
        }

        /** Última linha "ERROR: …" da saída, sem o prefixo, para mostrar na Fila. */
        fun errorMessage(output: String?): String? =
            output?.lineSequence()
                ?.map { it.trim() }
                ?.lastOrNull { it.startsWith("ERROR:") }
                ?.removePrefix("ERROR:")
                ?.trim()
                ?.replace(Regex("""^\[[\w:]+]\s*[\w-]*:\s*"""), "")
                ?.takeIf { it.isNotEmpty() }
    }
}
