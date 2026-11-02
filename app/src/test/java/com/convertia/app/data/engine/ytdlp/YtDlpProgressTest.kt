package com.convertia.app.data.engine.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YtDlpProgressTest {

    @Test
    fun `soma as duas faixas pelo tamanho`() {
        val progress = YtDlpProgress(listOf(300.0, 100.0))
        progress.onLine("[download] Destination: /d/Big Buck Bunny.f299.mp4")
        val half = progress.onLine("[download]  50.0% of  286.10MiB at    5.10MiB/s ETA 00:28")!!
        // 286,10 MiB = 300 MB: metade da primeira faixa = 150 de 400 MB
        assertEquals(37.5, half.percent, 0.1)
        assertEquals(5.10 * 1.048576, half.speedMbps, 0.001)

        progress.onLine("[download] 100% of  286.10MiB in 00:00:58 at 4.9MiB/s")
        progress.onLine("[download] Destination: /d/Big Buck Bunny.f140.m4a")
        val audio = progress.onLine("[download]  50.0% of ~  95.37MiB at  2.00MiB/s ETA 00:24")!!
        assertEquals(87.5, audio.percent, 0.1)
        assertEquals(400.0, audio.totalMb!!, 0.1)
    }

    @Test
    fun `nao volta e nao chega a 100 antes do fim`() {
        val progress = YtDlpProgress(emptyList(), startPercent = 40.0)
        assertEquals(40.0, progress.onLine("[download]  10.0% of 10.00MiB at 1.00MiB/s ETA 00:09")!!.percent, 0.0)
        assertEquals(99.5, progress.onLine("[download] 100.0% of 10.00MiB at 1.00MiB/s ETA 00:00")!!.percent, 0.0)
        assertNull(progress.onLine("[Merger] Merging formats into \"x.mp4\""))
    }

    @Test
    fun `mensagem de erro sem prefixos`() {
        val stderr = """
            WARNING: [youtube] something
            ERROR: [youtube] aqz-KE-bpKQ: Video unavailable. This video is private
        """.trimIndent()
        assertEquals("Video unavailable. This video is private", YtDlpProgress.errorMessage(stderr))
        assertEquals("Unsupported URL: https://x.y", YtDlpProgress.errorMessage("ERROR: Unsupported URL: https://x.y"))
        assertNull(YtDlpProgress.errorMessage("tudo certo"))
    }
}
