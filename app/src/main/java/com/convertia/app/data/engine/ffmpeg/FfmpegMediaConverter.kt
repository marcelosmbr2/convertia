package com.convertia.app.data.engine.ffmpeg

import com.convertia.app.data.convert.ConversionJob
import com.convertia.app.data.convert.FileConverter
import com.convertia.app.data.convert.ProgressListener
import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.MediaCategory
import com.convertia.app.domain.VideoCodec
import java.io.File
import java.util.Collections

/**
 * Áudio e vídeo com o ffmpeg: vídeo → vídeo/áudio/quadro e áudio → áudio. Com a
 * aceleração por hardware ligada tenta o encoder MediaCodec e, se ele falhar antes de
 * produzir progresso, refaz em software. Um codec que o aparelho declara não ter deixa de
 * ser tentado até o app fechar.
 */
class FfmpegMediaConverter(private val ffmpeg: Ffmpeg) : FileConverter {

    private val unsupportedHardware: MutableSet<VideoCodec> = Collections.synchronizedSet(mutableSetOf())

    override fun supports(item: ConversionItem): Boolean = when (item.category) {
        MediaCategory.VIDEO -> item.target.category in setOf(MediaCategory.VIDEO, MediaCategory.AUDIO, MediaCategory.IMAGE)
        MediaCategory.AUDIO -> item.target.category == MediaCategory.AUDIO
        else -> false
    }

    override suspend fun convert(job: ConversionJob, progress: ProgressListener): List<File> {
        val item = job.item
        val probe = ffmpeg.probe(job.input)
        when {
            item.category == MediaCategory.VIDEO && !probe.hasVideo -> throw FfmpegException("O arquivo não tem faixa de vídeo")
            item.target.category == MediaCategory.AUDIO && !probe.hasAudio -> throw FfmpegException("O arquivo não tem faixa de áudio")
        }
        val output = job.output
        val hardware = job.hardwareAcceleration && item.options.codec !in unsupportedHardware
        val seek = if (item.target.category == MediaCategory.IMAGE) FfmpegArgs.frameSeekSeconds(probe) else null
        var args = prefix(seek) + FfmpegArgs.convert(job.input.absolutePath, output.absolutePath, item.target, item.options, probe, hardware)
        var started = false
        val duration = probe.durationSeconds?.takeIf { item.target.category != MediaCategory.IMAGE }
        val onProgress: (FfmpegProgress.Update) -> Unit = { update ->
            started = true
            progress.onProgress(update.percent, update.etaSeconds)
        }
        try {
            ffmpeg.run(args, job.outputDir, onProgress, duration)
        } catch (e: FfmpegException) {
            if (!FfmpegArgs.usesHardware(args) || started) throw e
            // O MediaCodec não aceitou: refaz em software (e lembra se o codec não existe).
            if (isUnsupportedCodec(e.message)) unsupportedHardware += item.options.codec
            output.delete()
            args = prefix(seek) + FfmpegArgs.convert(job.input.absolutePath, output.absolutePath, item.target, item.options, probe, hardware = false)
            ffmpeg.run(args, job.outputDir, onProgress, duration)
        }
        if (!output.isFile || output.length() == 0L) throw FfmpegException("O ffmpeg terminou sem gerar arquivo")
        return listOf(output)
    }

    /** Para um quadro: começa um pouco adiante, para não cair na tela preta inicial. */
    private fun prefix(seek: Double?): List<String> = if (seek != null) listOf("-ss", String.format(java.util.Locale.ROOT, "%.2f", seek)) else emptyList()

    private fun isUnsupportedCodec(message: String?): Boolean {
        val text = message?.lowercase() ?: return false
        return listOf("unknown encoder", "not found", "not supported", "no such", "unsupported").any { it in text }
    }
}
