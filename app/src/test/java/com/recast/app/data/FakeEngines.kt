package com.recast.app.data

import com.recast.app.data.engine.ConversionEngine
import com.recast.app.data.engine.ConversionProgress
import com.recast.app.data.engine.DownloadEngine
import com.recast.app.data.engine.DownloadEvent
import com.recast.app.domain.ConversionItem
import com.recast.app.domain.DownloadJob
import com.recast.app.domain.DownloadRequest
import com.recast.app.domain.MediaKind
import com.recast.app.domain.SavedFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.min

/** Avança [step]% a cada [tickMs] ms a partir do progresso atual do job. */
class FakeDownloadEngine(
    private val step: Double = 25.0,
    private val tickMs: Long = 100,
    private val failAt: Double? = null,
    private val extraFiles: Int = 0,
) : DownloadEngine {
    val discarded = mutableListOf<Long>()

    override fun download(job: DownloadJob): Flow<DownloadEvent> = flow {
        var percent = job.progress
        while (percent < 100) {
            delay(tickMs)
            percent = min(100.0, percent + step)
            if (failAt != null && percent >= failAt) error("HTTP 403")
            emit(DownloadEvent.Progress(percent, 5.0))
        }
        val files = buildList {
            add(SavedFile("content://media/${job.id}", "${job.request.title}.${job.request.container}", 1_000, "video/mp4"))
            repeat(extraFiles) { add(SavedFile("content://media/${job.id}-$it", "Capítulo $it.mp4", 500, "video/mp4")) }
        }
        emit(DownloadEvent.Completed(files))
    }

    override fun discard(job: DownloadJob) {
        discarded += job.id
    }
}

/** Avança [step]% a cada [tickMs] ms; o arquivo de saída tem metade do tamanho. */
class FakeConversionEngine(
    private val step: Double = 25.0,
    private val tickMs: Long = 100,
    /** Extensões de origem que falham no primeiro passo. */
    private val failing: Set<String> = emptySet(),
    /** Extensões cujo fluxo termina sem `Finished`. */
    private val silent: Set<String> = emptySet(),
) : ConversionEngine {
    override fun convert(item: ConversionItem, hardwareAcceleration: Boolean): Flow<ConversionProgress> = flow {
        var percent = item.progress
        if (item.ext in failing) {
            delay(tickMs)
            error("Motor recusou ${item.name}")
        }
        if (item.ext in silent) {
            delay(tickMs)
            return@flow
        }
        while (true) {
            delay(tickMs)
            percent = min(100.0, percent + step)
            if (percent >= 100) {
                emit(ConversionProgress.Finished(listOf(SavedFile("content://recast/${item.id}", item.outputName, item.sizeBytes / 2, "application/octet-stream"))))
                return@flow
            }
            emit(ConversionProgress.Running(percent, null))
        }
    }
}

val fixedClock: Clock = Clock.fixed(Instant.parse("2026-11-01T13:00:00Z"), ZoneOffset.UTC)

fun request(title: String, mb: Double = 100.0) = DownloadRequest(
    url = "https://youtube.com/watch?v=$title",
    title = title,
    kind = MediaKind.VIDEO,
    formatId = "v1080",
    container = "mp4",
    quality = "1080p",
    resolution = "1920×1080",
    site = "youtube.com",
    sizeMb = mb,
    duration = "1:00",
)
