package com.convertia.app.data.engine.ffmpeg

import com.convertia.app.domain.AudioBitrate
import com.convertia.app.domain.AudioChannels
import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.ConversionOptions
import com.convertia.app.domain.ConversionSource
import com.convertia.app.domain.ImageResize
import com.convertia.app.domain.MediaCategory
import com.convertia.app.domain.OutputFormat
import com.convertia.app.domain.VideoCodec
import com.convertia.app.domain.VideoResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegArgsTest {
    private val video = MediaProbe(120.0, hasVideo = true, hasAudio = true, 1920, 1080, 30.0, "h264", "aac", 2)
    private val monoAudio = MediaProbe(60.0, hasVideo = false, hasAudio = true, null, null, null, null, "mp3", 1)

    private fun item(name: String, target: String, options: ConversionOptions = ConversionOptions()) =
        ConversionItem.create("id", name, 1, "p", ConversionSource.Sample, target, options)!!

    private fun args(item: ConversionItem, probe: MediaProbe = video, hardware: Boolean = false) =
        FfmpegArgs.convert("in.bin", "out.bin", item.target, item.options, probe, hardware)

    private fun List<String>.valueOf(flag: String): String? = indexOf(flag).takeIf { it >= 0 }?.let { getOrNull(it + 1) }

    @Test
    fun `mp4 em software usa x264 com CRF pela qualidade e faststart`() {
        val a = args(item("a.mov", "MP4"))
        assertEquals("libx264", a.valueOf("-c:v"))
        assertEquals("23", a.valueOf("-crf"))
        assertEquals("aac", a.valueOf("-c:a"))
        assertEquals("+faststart", a.valueOf("-movflags"))
        assertEquals("mp4", a.valueOf("-f"))
        assertEquals("out.bin", a.last())
        assertFalse(FfmpegArgs.usesHardware(a))
    }

    @Test
    fun `hardware usa MediaCodec com taxa de bits pela resolucao`() {
        val a = args(item("a.mov", "MP4", ConversionOptions(codec = VideoCodec.HEVC, resolution = VideoResolution.P720)), hardware = true)
        assertEquals("hevc_mediacodec", a.valueOf("-c:v"))
        assertTrue(FfmpegArgs.usesHardware(a))
        assertEquals("hvc1", a.valueOf("-tag:v"))
        // 1280×720 × 30 fps × 0,13 bpp ≈ 3,6 Mbps.
        assertEquals("3594k", a.valueOf("-b:v"))
        assertTrue(a.valueOf("-vf")!!.startsWith("scale=-2:min(ih\\,720)"))
        // AV1 não tem encoder por hardware: cai no SVT mesmo com a opção ligada.
        val av1 = args(item("a.mov", "MKV", ConversionOptions(codec = VideoCodec.AV1)), hardware = true)
        assertEquals("libsvtav1", av1.valueOf("-c:v"))
    }

    @Test
    fun `webm usa VP9 ou AV1 com opus a 48 kHz`() {
        val a = args(item("a.mp4", "WEBM"))
        assertEquals("libvpx-vp9", a.valueOf("-c:v"))
        assertEquals("libopus", a.valueOf("-c:a"))
        assertEquals("48000", a.valueOf("-ar"))
        assertEquals("webm", a.valueOf("-f"))
        // Codec que o contêiner não aceita cai no primeiro válido.
        val forced = FfmpegArgs.convert("i", "o", OutputFormat("WEBM", MediaCategory.VIDEO), ConversionOptions(codec = VideoCodec.H264), video, false)
        assertEquals("libvpx-vp9", forced.valueOf("-c:v"))
    }

    @Test
    fun `video sem audio nao mapeia faixa de audio`() {
        val a = args(item("a.mp4", "MKV"), video.copy(hasAudio = false))
        assertEquals(1, a.count { it == "-map" })
        assertFalse("-c:a" in a)
    }

    @Test
    fun `gif usa paleta e largura limitada`() {
        val a = args(item("a.mp4", "GIF"))
        val filter = a.valueOf("-filter_complex")!!
        assertTrue(filter.startsWith("fps=12,scale=min(iw\\,480)"))
        assertTrue("palettegen" in filter && "paletteuse" in filter)
        assertEquals("0", a.valueOf("-loop"))
        assertTrue("-an" in a)
        assertFalse("-map" in a)
    }

    @Test
    fun `audio por formato`() {
        val mp3 = args(item("a.wav", "MP3", ConversionOptions(bitrate = AudioBitrate.KBPS_192)), monoAudio)
        assertEquals("libmp3lame", mp3.valueOf("-c:a"))
        assertEquals("192k", mp3.valueOf("-b:a"))
        assertTrue("-vn" in mp3)
        // Origem mono não vira estéreo à força.
        assertFalse("-ac" in mp3)

        val mono = args(item("a.flac", "OPUS", ConversionOptions(channels = AudioChannels.MONO)), monoAudio.copy(audioChannels = 2))
        assertEquals("libopus", mono.valueOf("-c:a"))
        assertEquals("1", mono.valueOf("-ac"))
        assertEquals("256k", mono.valueOf("-b:a"))

        assertEquals("pcm_s16le", args(item("a.mp3", "WAV"), monoAudio).valueOf("-c:a"))
        assertEquals("pcm_s16be", args(item("a.mp3", "AIFF"), monoAudio).valueOf("-c:a"))
        assertEquals("flac", args(item("a.mp3", "FLAC"), monoAudio).valueOf("-c:a"))
        assertEquals("ipod", args(item("a.mp3", "M4A"), monoAudio).valueOf("-f"))
        assertEquals("adts", args(item("a.mp3", "AAC"), monoAudio).valueOf("-f"))
        args(item("a.mp3", "OGG"), monoAudio).let { ogg ->
            assertEquals("libvorbis", ogg.valueOf("-c:a"))
            assertEquals("9", ogg.valueOf("-q:a"))
        }
    }

    @Test
    fun `quadro de video`() {
        val a = args(item("a.mp4", "JPG", ConversionOptions(resize = ImageResize.MAX_2560)))
        assertEquals("mjpeg", a.valueOf("-c:v"))
        assertEquals("8", a.valueOf("-q:v"))
        assertEquals("1", a.valueOf("-frames:v"))
        assertEquals("thumbnail=100,scale=min(iw\\,2560):-2", a.valueOf("-vf"))
        assertEquals(12.0, FfmpegArgs.frameSeekSeconds(video)!!, 0.01)
        assertEquals(30.0, FfmpegArgs.frameSeekSeconds(video.copy(durationSeconds = 3_600.0))!!, 0.01)
    }

    @Test
    fun `mapeamentos de qualidade`() {
        assertEquals(29, FfmpegArgs.crf(55))
        assertEquals(23, FfmpegArgs.crf(80))
        assertEquals(20, FfmpegArgs.crf(95))
        assertEquals(15, FfmpegArgs.mjpegQuality(55))
        assertEquals(3, FfmpegArgs.mjpegQuality(95))
        assertEquals(30, FfmpegArgs.avifCrf(80))
    }

    @Test
    fun `imagens pelo ffmpeg`() {
        val avif = FfmpegArgs.image("in.png", "out.avif", "AVIF", ConversionOptions())
        assertEquals("libaom-av1", avif.valueOf("-c:v"))
        assertEquals("1", avif.valueOf("-still-picture"))
        val ico = FfmpegArgs.image("in.png", "out.ico", "ICO", ConversionOptions())
        assertEquals("ico", ico.valueOf("-f"))
        assertEquals("tiff", FfmpegArgs.image("i", "o", "TIFF", ConversionOptions()).valueOf("-c:v"))
    }
}
