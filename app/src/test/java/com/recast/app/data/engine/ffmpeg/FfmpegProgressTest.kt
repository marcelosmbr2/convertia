package com.recast.app.data.engine.ffmpeg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FfmpegProgressTest {

    private fun feed(progress: FfmpegProgress, block: String) =
        block.trimIndent().lines().mapNotNull(progress::onLine).lastOrNull()

    @Test
    fun `percentual pela duracao e ETA pela velocidade`() {
        val progress = FfmpegProgress(durationSeconds = 100.0)
        val update = feed(
            progress,
            """
            frame=300
            fps=60.0
            out_time_us=25000000
            out_time_ms=25000000
            out_time=00:00:25.000000
            speed=2.5x
            progress=continue
            """,
        )
        assertEquals(25.0, update!!.percent, 0.01)
        assertEquals(30, update.etaSeconds)
    }

    @Test
    fun `nunca volta, fica abaixo de 100 ate o fim e termina em 100`() {
        val progress = FfmpegProgress(durationSeconds = 10.0)
        feed(progress, "out_time_us=9000000\nspeed=1x\nprogress=continue")
        val stuck = feed(progress, "out_time_us=5000000\nspeed=1x\nprogress=continue")
        assertEquals(90.0, stuck!!.percent, 0.01)
        val almost = feed(progress, "out_time_us=12000000\nspeed=1x\nprogress=continue")
        assertEquals(99.5, almost!!.percent, 0.01)
        val end = feed(progress, "progress=end")
        assertEquals(100.0, end!!.percent, 0.0)
        assertEquals(0, end.etaSeconds)
    }

    @Test
    fun `sem duracao conhecida so avisa o fim`() {
        val progress = FfmpegProgress(durationSeconds = null)
        assertNull(feed(progress, "out_time_us=5000000\nspeed=1x\nprogress=continue"))
        assertEquals(100.0, feed(progress, "progress=end")!!.percent, 0.0)
    }

    @Test
    fun `speed N A nao quebra`() {
        val progress = FfmpegProgress(durationSeconds = 10.0)
        val update = feed(progress, "out_time_us=1000000\nspeed=N/A\nprogress=continue")
        assertEquals(10.0, update!!.percent, 0.01)
        assertNull(update.etaSeconds)
    }
}
