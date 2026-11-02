package com.convertia.app.ui.queue

import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.ConversionStatus
import com.convertia.app.domain.DownloadJob
import com.convertia.app.domain.DownloadStatus

/** Estado de uma linha da Fila, comum a downloads e conversões. */
enum class RowStatus { ACTIVE, QUEUED, PAUSED, FAILED, DONE }

sealed interface QueueRow {
    val key: String
    val status: RowStatus
    /** 0–100. */
    val progress: Double

    data class Download(val job: DownloadJob, override val status: RowStatus) : QueueRow {
        override val key get() = "download-${job.id}"
        override val progress get() = job.progress
    }

    data class Conversion(val item: ConversionItem, override val status: RowStatus) : QueueRow {
        override val key get() = "conversion-${item.id}"
        override val progress get() = item.progress
    }
}

enum class SectionKind { IN_PROGRESS, FAILED, DONE }

data class QueueSection(val kind: SectionKind, val rows: List<QueueRow>)

/** Tudo que a Fila mostra, derivado das duas filas (qsections e afins no mockup). */
data class QueueOverview(
    val sections: List<QueueSection>,
    val activeCount: Int,
    val doneCount: Int,
    /** Algo baixando, esperando vaga ou convertendo: decide "Pausar tudo" × "Retomar tudo". */
    val anyRunning: Boolean,
    val downloadingCount: Int,
    val totalSpeedMbps: Double,
    val convertingCount: Int,
    /** Há downloads na fila segurados por "Somente em Wi-Fi". */
    val waitingForWifi: Boolean = false,
) {
    val isEmpty: Boolean get() = sections.isEmpty()
    val isIdle: Boolean get() = downloadingCount == 0 && convertingCount == 0

    companion object {
        private val order = mapOf(
            RowStatus.ACTIVE to 0,
            RowStatus.QUEUED to 1,
            RowStatus.PAUSED to 2,
            RowStatus.FAILED to 3,
            RowStatus.DONE to 4,
        )

        fun build(
            jobs: List<DownloadJob>,
            items: List<ConversionItem>,
            conversionsRunning: Boolean,
            downloadsAllowed: Boolean = true,
        ): QueueOverview {
            // Arquivos prontos só aparecem enquanto a fila de conversão está ligada.
            val conversions = items
                .filter { it.status != ConversionStatus.READY || conversionsRunning }
                .map { item ->
                    val status = when (item.status) {
                        ConversionStatus.CONVERTING -> if (conversionsRunning) RowStatus.ACTIVE else RowStatus.PAUSED
                        ConversionStatus.READY -> RowStatus.QUEUED
                        ConversionStatus.DONE -> RowStatus.DONE
                        ConversionStatus.FAILED -> RowStatus.FAILED
                    }
                    QueueRow.Conversion(item, status)
                }
            val downloads = jobs.map { job ->
                val status = when (job.status) {
                    DownloadStatus.DOWNLOADING -> RowStatus.ACTIVE
                    DownloadStatus.QUEUED -> RowStatus.QUEUED
                    DownloadStatus.PAUSED -> RowStatus.PAUSED
                    DownloadStatus.FAILED -> RowStatus.FAILED
                    DownloadStatus.DONE -> RowStatus.DONE
                }
                QueueRow.Download(job, status)
            }
            val all = (conversions + downloads).sortedBy { order.getValue(it.status) }
            val active = all.filter { it.status in setOf(RowStatus.ACTIVE, RowStatus.QUEUED, RowStatus.PAUSED) }
            val failed = all.filter { it.status == RowStatus.FAILED }
            val done = all.filter { it.status == RowStatus.DONE }
            val downloading = jobs.filter { it.status == DownloadStatus.DOWNLOADING }
            return QueueOverview(
                sections = listOf(
                    QueueSection(SectionKind.IN_PROGRESS, active),
                    QueueSection(SectionKind.FAILED, failed),
                    QueueSection(SectionKind.DONE, done),
                ).filter { it.rows.isNotEmpty() },
                activeCount = active.size,
                doneCount = done.size,
                anyRunning = jobs.any { it.isActive } || conversionsRunning,
                downloadingCount = downloading.size,
                totalSpeedMbps = downloading.sumOf { it.speedMbps },
                convertingCount = if (conversionsRunning) items.count { it.status == ConversionStatus.CONVERTING } else 0,
                waitingForWifi = !downloadsAllowed && jobs.any { it.status == DownloadStatus.QUEUED },
            )
        }
    }
}
