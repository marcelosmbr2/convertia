package com.convertia.app.data.engine.ytdlp

import com.convertia.app.domain.DownloadOptions
import com.convertia.app.domain.DownloadRequest
import com.convertia.app.domain.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpArgsTest {

    private fun request(
        kind: MediaKind = MediaKind.VIDEO,
        formatId: String = "299",
        container: String = "mp4",
        codec: String? = "avc1",
        progressive: Boolean = false,
        source: String = "mp4",
        options: DownloadOptions = DownloadOptions(),
    ) = DownloadRequest(
        url = "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
        title = "Big Buck Bunny",
        kind = kind,
        formatId = formatId,
        container = container,
        quality = "1080p",
        resolution = "1920×1080",
        site = "youtube.com",
        sizeMb = 288.0,
        duration = "10:35",
        progressive = progressive,
        videoCodec = codec,
        sourceContainer = source,
        options = options,
    )

    private fun List<String>.valueOf(flag: String) = getOrNull(indexOf(flag) + 1)

    @Test
    fun `video mp4 junta com audio m4a e usa o modelo de nome`() {
        val args = YtDlpArgs.download(request(), "/cache/downloads/7")
        assertEquals("299+bestaudio[ext=m4a]/299+bestaudio/299", args.valueOf("-f"))
        assertEquals("mp4", args.valueOf("--merge-output-format"))
        assertEquals("/cache/downloads/7", args.valueOf("-P"))
        assertEquals("%(title).180B.%(ext)s", args.valueOf("-o"))
        assertTrue("--newline" in args)
        assertTrue("--embed-metadata" in args)
        assertTrue("--embed-thumbnail" in args)
        assertFalse("--recode-video" in args)
    }

    @Test
    fun `H264 em webm precisa recodificar e nao embute miniatura`() {
        val args = YtDlpArgs.download(request(container = "webm"), "/d")
        assertEquals("299+bestaudio[ext=webm]/299+bestaudio/299", args.valueOf("-f"))
        assertEquals("webm", args.valueOf("--recode-video"))
        assertFalse("--embed-thumbnail" in args)
    }

    @Test
    fun `VP9 em webm so junta`() {
        val args = YtDlpArgs.download(request(formatId = "315", codec = "vp9", container = "webm", source = "webm"), "/d")
        assertFalse("--recode-video" in args)
    }

    @Test
    fun `progressivo em outro conteiner remuxa`() {
        val args = YtDlpArgs.download(request(formatId = "http-540p", progressive = true, container = "mkv"), "/d")
        assertEquals("http-540p", args.valueOf("-f"))
        assertEquals("mkv", args.valueOf("--remux-video"))
        assertFalse("--merge-output-format" in args)
    }

    @Test
    fun `somente audio extrai no formato escolhido`() {
        val mp3 = YtDlpArgs.download(request(kind = MediaKind.AUDIO, formatId = "251", container = "mp3", codec = null), "/d")
        assertEquals("251", mp3.valueOf("-f"))
        assertEquals("mp3", mp3.valueOf("--audio-format"))
        assertEquals("0", mp3.valueOf("--audio-quality"))
        assertTrue("-x" in mp3)
        val wav = YtDlpArgs.download(request(kind = MediaKind.AUDIO, formatId = "140", container = "wav", codec = null), "/d")
        assertFalse("--embed-thumbnail" in wav)
        assertFalse("--audio-quality" in wav)
    }

    @Test
    fun `legendas e capitulos`() {
        val args = YtDlpArgs.download(request(options = DownloadOptions(subtitles = true, splitChapters = true, embedThumbnail = false)), "/d")
        assertEquals("en.*,en", args.valueOf("--sub-langs"))
        assertTrue("--embed-subs" in args)
        assertTrue("--split-chapters" in args)
        assertTrue(args.any { it.startsWith("chapter:") })
        assertFalse("--embed-thumbnail" in args)
    }
}
