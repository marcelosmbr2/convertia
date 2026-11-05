package com.recast.app.data.engine.ffmpeg

import com.recast.app.domain.ConversionItem
import com.recast.app.domain.ConversionOptions
import com.recast.app.domain.ConversionSource
import com.recast.app.domain.VideoCodec
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Roda os argumentos gerados num ffmpeg de verdade, quando houver um no PATH (há no
 * contêiner de desenvolvimento e nos runners do GitHub). Garante que os filtros, codecs e
 * muxers existem e aceitam o que mandamos; o build Android traz os mesmos encoders.
 */
class FfmpegArgsSmokeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var video: File
    private lateinit var audio: File
    private lateinit var png: File

    @Before
    fun samples() {
        assumeTrue("ffmpeg não está no PATH", ffmpegAvailable())
        video = folder.newFile("sample.mp4").also {
            run("-f", "lavfi", "-i", "testsrc2=size=320x180:rate=30", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000", "-t", "1.5", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", it.path)
        }
        audio = folder.newFile("sample.mp3").also {
            run("-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100", "-t", "1.5", "-c:a", "libmp3lame", it.path)
        }
        png = folder.newFile("sample.png").also { run("-f", "lavfi", "-i", "testsrc2=size=64x64:rate=1", "-frames:v", "1", it.path) }
    }

    @Test
    fun `video para cada conteiner e codec em software`() {
        val cases = listOf(
            "MP4" to VideoCodec.H264, "MOV" to VideoCodec.HEVC, "MKV" to VideoCodec.AV1,
            "WEBM" to VideoCodec.VP9, "WEBM" to VideoCodec.AV1, "AVI" to VideoCodec.H264, "GIF" to VideoCodec.H264,
        )
        for ((ext, codec) in cases) {
            val out = convert(video, "sample.mp4", ext, ConversionOptions(codec = codec).withQuality(55))
            assertTrue("$ext/$codec", out.length() > 0)
            val probe = probe(out)
            assertTrue("$ext/$codec tem vídeo", probe.hasVideo)
            if (ext != "GIF") assertTrue("$ext/$codec tem áudio", probe.hasAudio)
        }
    }

    @Test
    fun `audio para cada formato, a partir de video e de audio`() {
        for (ext in listOf("MP3", "AAC", "M4A", "OPUS", "OGG", "FLAC", "WAV", "AIFF")) {
            for (source in listOf(video to "sample.mp4", audio to "sample.mp3")) {
                val out = convert(source.first, source.second, ext, ConversionOptions())
                val probe = probe(out)
                assertTrue("$ext de ${source.second}", probe.hasAudio && !probe.hasVideo)
            }
        }
    }

    @Test
    fun `quadro de video e imagens pelo ffmpeg`() {
        for (ext in listOf("JPG", "PNG", "WEBP")) {
            val out = convert(video, "sample.mp4", ext, ConversionOptions(), seek = true)
            assertEquals("$ext", 1, probeStreams(out))
        }
        for (ext in listOf("AVIF", "TIFF", "BMP", "GIF", "ICO")) {
            val out = folder.newFile("img.${ext.lowercase()}")
            run(*FfmpegArgs.image(png.path, out.path, ext, ConversionOptions()).toTypedArray())
            assertTrue(ext, out.length() > 0)
        }
    }

    private fun convert(input: File, name: String, ext: String, options: ConversionOptions, seek: Boolean = false): File {
        val item = ConversionItem.create("id", name, input.length(), "p", ConversionSource.Sample, ext, options)!!
        val probe = probe(input)
        val out = File(folder.root, "out-${System.nanoTime()}.${ext.lowercase()}")
        val prefix = if (seek) FfmpegArgs.frameSeekSeconds(probe)?.let { listOf("-ss", it.toString()) }.orEmpty() else emptyList()
        run(*(prefix + FfmpegArgs.convert(input.path, out.path, item.target, item.options, probe, hardware = false)).toTypedArray())
        return out
    }

    private fun probe(file: File): MediaProbe {
        val process = ProcessBuilder("ffprobe", "-v", "error", "-of", "json", "-show_format", "-show_streams", file.path)
            .redirectErrorStream(false).start()
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor(30, TimeUnit.SECONDS)
        return MediaProbe.parse(out)
    }

    private fun probeStreams(file: File): Int {
        val process = ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "stream=codec_type", "-of", "csv=p=0", file.path).start()
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor(30, TimeUnit.SECONDS)
        return out.lines().count { it.isNotBlank() }
    }

    private fun run(vararg args: String) {
        val process = ProcessBuilder(listOf("ffmpeg", "-nostdin", "-y", "-hide_banner", "-loglevel", "error") + args).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        process.waitFor(120, TimeUnit.SECONDS)
        assertEquals("ffmpeg ${args.joinToString(" ")}\n$log", 0, process.exitValue())
    }

    private fun ffmpegAvailable(): Boolean = runCatching {
        ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start().let { it.inputStream.readBytes(); it.waitFor() == 0 }
    }.getOrDefault(false)
}
