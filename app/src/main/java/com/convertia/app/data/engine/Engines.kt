package com.convertia.app.data.engine

import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.DownloadJob
import com.convertia.app.domain.MediaInfo
import kotlinx.coroutines.flow.Flow

/**
 * Lê os metadados e formatos de um link. Nesta fase é simulado; na Fase 2 vira o yt-dlp.
 */
interface MediaInspector {
    suspend fun inspect(url: String): MediaInfo
}

data class DownloadProgress(
    /** 0–100. */
    val percent: Double,
    val speedMbps: Double,
)

/**
 * Baixa um arquivo. O fluxo emite o progresso e termina quando o download acaba; um erro
 * no fluxo marca o job como falho. Cancelar a coleta é pausar: a fila guarda o progresso e
 * chama [download] de novo para retomar. Agendamento e concorrência ficam na fila.
 */
interface DownloadEngine {
    fun download(job: DownloadJob): Flow<DownloadProgress>
}

sealed interface ConversionProgress {
    data class Running(
        /** 0–100. */
        val percent: Double,
        /** Segundos restantes estimados, se o motor souber. */
        val etaSeconds: Int?,
    ) : ConversionProgress

    data class Finished(val outputBytes: Long) : ConversionProgress
}

/**
 * Converte um arquivo. Mesmo contrato do [DownloadEngine]: cancelar é pausar, e a fila
 * retoma a partir de [ConversionItem.progress].
 */
interface ConversionEngine {
    fun convert(item: ConversionItem, hardwareAcceleration: Boolean): Flow<ConversionProgress>
}
