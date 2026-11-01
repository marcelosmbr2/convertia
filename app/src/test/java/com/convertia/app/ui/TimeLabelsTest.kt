package com.convertia.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TimeLabelsTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val now = Instant.parse("2026-11-01T15:00:00Z") // 12:00 em São Paulo
    private val clock = Clock.fixed(now, zone)

    private fun minutesAgo(m: Long) = now.minusSeconds(m * 60).toEpochMilli()

    @Test
    fun `recentes usam tempo relativo`() {
        assertEquals(TimeLabel.JustNow, TimeLabels.of(minutesAgo(0), clock, relative = true))
        assertEquals(TimeLabel.MinutesAgo(5), TimeLabels.of(minutesAgo(5), clock, relative = true))
        assertEquals(TimeLabel.HoursAgo(2), TimeLabels.of(minutesAgo(120), clock, relative = true))
        assertEquals(TimeLabel.Yesterday, TimeLabels.of(minutesAgo(24 * 60), clock, relative = true))
    }

    @Test
    fun `fila e historico usam hora de hoje e data curta`() {
        assertEquals(TimeLabel.Today("10:00"), TimeLabels.of(minutesAgo(120), clock, relative = false))
        val label = TimeLabels.of(minutesAgo(8 * 24 * 60), clock, relative = false) as TimeLabel.Date
        assertEquals(true, label.date.startsWith("24 out"))
    }
}
