package com.convertia.app.data.engine.ytdlp

import com.convertia.app.data.engine.DownloadEngine
import com.convertia.app.data.engine.DownloadEvent
import com.convertia.app.data.engine.MediaInspector
import com.convertia.app.data.storage.MediaSaver
import com.convertia.app.domain.DownloadJob
import com.convertia.app.domain.MediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/** Inspeciona links com o yt-dlp. */
class YtDlpMediaInspector(private val ytDlp: YtDlp) : MediaInspector {
    override suspend fun inspect(url: String): MediaInfo {
        val json = ytDlp.inspect(url)
        return withContext(Dispatchers.Default) { YtDlpInfoParser.parse(json, url) }
    }
}

/**
 * Baixa com o yt-dlp numa pasta temporária por job (o que permite retomar: os `.part`
 * ficam lá entre uma pausa e outra) e, ao terminar, grava em `Download/Convertia`.
 */
class YtDlpDownloadEngine(
    private val ytDlp: YtDlp,
    private val saver: MediaSaver,
    private val workRoot: File,
) : DownloadEngine {

    override fun download(job: DownloadJob): Flow<DownloadEvent> = channelFlow {
        val dir = workDir(job).apply { mkdirs() }
        val request = job.request
        val progress = YtDlpProgress(request.streamSizesMb, startPercent = job.progress)
        send(DownloadEvent.Progress(job.progress, 0.0, job.totalMb))

        ytDlp.download(request.url, YtDlpArgs.download(request, dir.absolutePath), "download-${job.id}") { line, percent ->
            progress.onLine(line, percent.takeIf { it > 0f })?.let { update ->
                trySend(DownloadEvent.Progress(update.percent, update.speedMbps, update.totalMb))
            }
        }

        val outputs = outputsIn(dir)
        if (outputs.isEmpty()) error("O yt-dlp terminou sem gerar arquivo")
        val saved = outputs.map { saver.save(it) }
        dir.deleteRecursively()
        send(DownloadEvent.Completed(saved))
    }.flowOn(Dispatchers.IO)

    override fun discard(job: DownloadJob) {
        workDir(job).deleteRecursively()
    }

    private fun workDir(job: DownloadJob) = File(workRoot, job.id.toString())

    /** Arquivos finais: sem temporários do yt-dlp; o maior (o vídeo inteiro) primeiro. */
    private fun outputsIn(dir: File): List<File> =
        dir.walkTopDown()
            .filter { file ->
                file.isFile && !file.name.startsWith(".") && ".part" !in file.name &&
                    TEMP_SUFFIXES.none { file.name.endsWith(it) }
            }
            .sortedByDescending { it.length() }
            .toList()

    private companion object {
        val TEMP_SUFFIXES = listOf(".ytdl", ".temp", ".tmp")
    }
}
