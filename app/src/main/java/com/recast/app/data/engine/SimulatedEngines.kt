package com.recast.app.data.engine

import com.recast.app.data.SampleData
import com.recast.app.data.storage.MediaSaver
import com.recast.app.domain.ConversionItem
import com.recast.app.domain.DownloadJob
import com.recast.app.domain.Formatters
import com.recast.app.domain.MediaCategory
import com.recast.app.domain.MediaInfo
import com.recast.app.domain.SavedFile
import com.recast.app.domain.SizeEstimator
import com.recast.app.domain.siteOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Intervalo entre atualizações dos motores simulados (o `tick` do mockup). */
const val SIMULATION_TICK_MS = 300L

/** Devolve sempre os mesmos formatos de exemplo do mockup, depois de ~1,1 s. */
class SimulatedMediaInspector(private val delayMs: Long = 1_100) : MediaInspector {
    override suspend fun inspect(url: String): MediaInfo {
        delay(delayMs)
        return SampleData.mediaInfo(url = url, site = siteOf(url) ?: "youtube.com")
    }
}

/** Progresso com velocidade oscilante entre 1,5 e 14 MB/s, como no mockup. Não grava nada. */
class SimulatedDownloadEngine(private val random: Random) : DownloadEngine {
    override fun download(job: DownloadJob): Flow<DownloadEvent> = flow {
        var percent = job.progress
        var speed = if (job.speedMbps > 0) job.speedMbps else 5 + random.nextDouble() * 5
        val sizeMb = job.request.sizeMb.takeIf { it > 0 } ?: 100.0
        emit(DownloadEvent.Progress(percent, speed))
        while (percent < 100) {
            delay(SIMULATION_TICK_MS)
            speed = (speed + (random.nextDouble() - 0.5) * 1.6).coerceIn(1.5, 14.0)
            percent = min(100.0, percent + speed * 0.75 / sizeMb * 100)
            emit(DownloadEvent.Progress(percent, speed))
        }
        val request = job.request
        emit(
            DownloadEvent.Completed(
                listOf(SavedFile(uri = null, name = "${request.title}.${request.container}", sizeBytes = (sizeMb * 1e6).toLong(), mimeType = "")),
            ),
        )
    }
}

/** Avança mais rápido em arquivos pequenos e em vídeo com aceleração de hardware. */
class SimulatedConversionEngine : ConversionEngine {
    override fun convert(item: ConversionItem, hardwareAcceleration: Boolean): Flow<ConversionProgress> = flow {
        val step = stepFor(item, hardwareAcceleration)
        var percent = item.progress
        emit(ConversionProgress.Running(percent, etaSeconds(percent, step)))
        while (true) {
            delay(SIMULATION_TICK_MS)
            percent = min(100.0, percent + step)
            if (percent >= 100) {
                val bytes = SizeEstimator.estimatedBytes(item)
                emit(ConversionProgress.Finished(listOf(SavedFile(null, item.outputName, bytes, MediaSaver.mimeTypeOf(item.outputName)))))
                return@flow
            }
            emit(ConversionProgress.Running(percent, etaSeconds(percent, step)))
        }
    }

    private fun stepFor(item: ConversionItem, hardwareAcceleration: Boolean): Double {
        val base = max(0.5, 30 / sqrt(max(item.sizeBytes / 1e6, 1.0)))
        val boost = if (hardwareAcceleration && item.category == MediaCategory.VIDEO) 1.6 else 1.0
        return base * boost * 3
    }

    private fun etaSeconds(percent: Double, step: Double): Int =
        max(1L, Formatters.roundHalfUp((100 - percent) / step * SIMULATION_TICK_MS / 1000.0)).toInt()
}
