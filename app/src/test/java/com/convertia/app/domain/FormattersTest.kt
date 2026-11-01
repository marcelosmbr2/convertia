package com.convertia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Valores esperados obtidos rodando fmtSize/fmtT do mockup no Node. */
class FormattersTest {

    @Test
    fun `size segue as faixas e a virgula do mockup`() {
        val cases = listOf(
            0.0 to "0 B",
            512.0 to "512 B",
            999.0 to "999 B",
            1000.0 to "1 KB",
            1500.0 to "2 KB",
            240e3 to "240 KB",
            999_999.0 to "1000 KB",
            1e6 to "1,0 MB",
            3.2e6 to "3,2 MB",
            21.4e6 to "21,4 MB",
            94e6 to "94,0 MB",
            99.96e6 to "100,0 MB",
            1e8 to "100 MB",
            412e6 to "412 MB",
            1e9 to "1,00 GB",
            1.24e9 to "1,24 GB",
            2.41e9 to "2,41 GB",
            3.08e9 to "3,08 GB",
        )
        cases.forEach { (bytes, expected) -> assertEquals("bytes=$bytes", expected, Formatters.size(bytes)) }
    }

    @Test
    fun `minutesSeconds arredonda meio para cima`() {
        val cases = listOf(0.0 to "0:00", 59.0 to "0:59", 59.5 to "1:00", 81.0 to "1:21", 600.4 to "10:00", 3599.0 to "59:59")
        cases.forEach { (seconds, expected) -> assertEquals(expected, Formatters.minutesSeconds(seconds)) }
    }

    @Test
    fun `decimal usa virgula`() {
        assertEquals("5,1", Formatters.decimal(5.1, 1))
        assertEquals("13,7", Formatters.decimal(13.66, 1))
    }

    @Test
    fun `sizeDelta usa sinal de menos tipografico`() {
        assertEquals("+28%", Formatters.sizeDelta(3_200_000, 4_100_000))
        assertEquals("−42%", Formatters.sizeDelta(1_000_000, 580_000))
        assertEquals("−0%", Formatters.sizeDelta(1_000_000, 1_000_000))
    }

    @Test
    fun `siteOf e firstUrlIn`() {
        assertEquals("youtube.com", siteOf("https://www.youtube.com/watch?v=k3yB0ard_fs"))
        assertEquals("vimeo.com", siteOf("https://vimeo.com/884213301"))
        assertEquals(
            "https://youtu.be/abc123",
            firstUrlIn("Olha este vídeo: https://youtu.be/abc123."),
        )
        assertEquals(null, firstUrlIn("sem link aqui"))
    }
}
