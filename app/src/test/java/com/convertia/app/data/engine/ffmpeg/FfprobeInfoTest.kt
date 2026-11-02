package com.convertia.app.data.engine.ffmpeg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saídas reais do `ffprobe -of json` de amostras geradas com `-f lavfi`. */
class FfprobeInfoTest {
    private fun fixture(name: String) = checkNotNull(javaClass.getResourceAsStream("/ffmpeg/$name")).bufferedReader().readText()

    @Test
    fun `video com audio`() {
        val probe = MediaProbe.parse(fixture("probe_video.json"))
        assertTrue(probe.hasVideo)
        assertTrue(probe.hasAudio)
        assertEquals(320, probe.width)
        assertEquals(180, probe.height)
        assertEquals(30.0, probe.fps!!, 0.01)
        assertEquals(2.0, probe.durationSeconds!!, 0.1)
        assertEquals("h264", probe.videoCodec)
        assertEquals("aac", probe.audioCodec)
    }

    @Test
    fun `so audio`() {
        val probe = MediaProbe.parse(fixture("probe_audio.json"))
        assertFalse(probe.hasVideo)
        assertTrue(probe.hasAudio)
        assertNull(probe.width)
        assertEquals(1, probe.audioChannels)
        assertEquals(2.0, probe.durationSeconds!!, 0.1)
    }

    @Test
    fun `capa embutida nao conta como video e duracao zero vira nula`() {
        val json = """
            {"streams":[
              {"codec_type":"video","codec_name":"mjpeg","width":500,"height":500,"disposition":{"attached_pic":1},"avg_frame_rate":"0/0"},
              {"codec_type":"audio","codec_name":"mp3","channels":2}
            ],"format":{"duration":"0.000000"}}
        """.trimIndent()
        val probe = MediaProbe.parse(json)
        assertFalse(probe.hasVideo)
        assertTrue(probe.hasAudio)
        assertNull(probe.durationSeconds)
        assertEquals(2, probe.audioChannels)
    }
}
