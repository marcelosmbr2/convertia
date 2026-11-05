package com.recast.app.data.engine.ytdlp

import com.recast.app.domain.FormatBadge
import com.recast.app.domain.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpInfoParserTest {

    private fun fixture(name: String) = javaClass.getResource("/ytdlp/$name")!!.readText()

    /** Saída real do yt-dlp 2025.11.12 para "Big Buck Bunny 60fps 4K" (Blender, CC BY). */
    private val youtube = YtDlpInfoParser.parse(fixture("youtube-big-buck-bunny.json"), "https://youtu.be/aqz-KE-bpKQ")

    @Test
    fun `metadados do video`() {
        assertEquals("Big Buck Bunny 60fps 4K - Official Blender Foundation Short Film", youtube.title)
        assertEquals("Blender", youtube.channel)
        assertEquals("youtube.com", youtube.site)
        assertEquals("10:35", youtube.duration)
        assertEquals("Creative Commons", youtube.license)
        assertEquals("https://www.youtube.com/watch?v=aqz-KE-bpKQ", youtube.url)
        assertEquals(0, youtube.chapterCount)
        assertTrue(youtube.subtitleLanguages.isEmpty())
    }

    @Test
    fun `uma opcao de video por altura, sem storyboards e a partir de 360p`() {
        assertEquals(
            listOf("2160p", "1440p", "1080p", "720p", "480p", "360p"),
            youtube.videoFormats.map { it.quality },
        )
        // Até 1080p prefere H.264; acima, VP9.
        assertEquals(listOf("315", "308", "299", "298", "135", "134"), youtube.videoFormats.map { it.id })
        assertEquals("VP9 + Opus", youtube.videoFormats[0].codec)
        assertEquals("H.264 + AAC", youtube.videoFormats[2].codec)
        assertEquals("60", youtube.videoFormats[2].detail)
        assertEquals("mp4", youtube.videoFormats[2].container)
        assertEquals("1920×1080", youtube.videoFormats[2].resolution)
        assertFalse(youtube.videoFormats[2].progressive)
        assertEquals("avc1", youtube.videoFormats[2].videoCodec)
    }

    @Test
    fun `tamanho soma video e o audio que vai junto`() {
        val f1080 = youtube.videoFormats.first { it.id == "299" }
        // 299 (257,6 MB) + melhor m4a (258: 30,8 MB)
        assertEquals(257.619653 + 30.767611, f1080.sizeMb, 0.001)
        assertEquals(listOf(257.619653, 30.767611), f1080.streamSizesMb.map { Math.round(it * 1e6) / 1e6 })
        val f2160 = youtube.videoFormats.first { it.id == "315" }
        // webm junta com o melhor opus (251: 10,2 MB)
        assertEquals(1362.269481 + 10.20221, f2160.sizeMb, 0.001)
    }

    @Test
    fun `recomendado e o H264 1080p`() {
        assertEquals(listOf("299"), youtube.videoFormats.filter { it.badge == FormatBadge.RECOMMENDED }.map { it.id })
    }

    @Test
    fun `audio sem DRC nem surround, da maior taxa para a menor`() {
        val audio = youtube.audioFormats
        assertTrue(audio.all { it.kind == MediaKind.AUDIO })
        assertEquals(listOf("258", "256", "140", "251"), audio.map { it.id })
        assertEquals("388 kbps", audio[0].quality)
        assertEquals("AAC", audio[0].codec)
        assertEquals("48 kHz", audio[0].detail)
        assertEquals("44,1 kHz", audio[2].detail)
        assertEquals(FormatBadge.BEST, audio[0].badge)
        assertNull(audio[1].badge)
    }

    @Test
    fun `site generico com formatos progressivos, capitulos e legendas`() {
        val info = YtDlpInfoParser.parse(fixture("generic-progressive.json"), "https://vimeo.com/884213301")
        assertEquals("Field Notes", info.channel)
        assertEquals("2:00:12", info.duration)
        assertEquals(2, info.chapterCount)
        assertEquals(listOf("EN", "PT", "DE"), info.subtitleLanguages)
        assertEquals(listOf("1080p", "720p", "540p"), info.videoFormats.map { it.quality })
        assertTrue(info.videoFormats.all { it.progressive })
        assertEquals("H.264 + AAC", info.videoFormats[0].codec)
        // Sem tamanho no JSON: estima pela taxa (5100 kbps × 7212,4 s).
        assertEquals(5100 * 1000 / 8.0 * 7212.4 / 1e6, info.videoFormats[0].sizeMb, 0.01)
        assertEquals(612.0, info.videoFormats[2].sizeMb, 0.001)
        assertEquals(FormatBadge.RECOMMENDED, info.videoFormats[0].badge)
        assertEquals(listOf("192 kbps"), info.audioFormats.map { it.quality })
    }
}
