package com.recast.app.ui.queue

import com.recast.app.data.SampleData
import com.recast.app.data.fixedClock
import com.recast.app.domain.ConversionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Números conferidos com o mockup rodando os dados de exemplo (JOBS0 + SAMPLES). */
class QueueOverviewTest {
    private val jobs = SampleData.downloadJobs(fixedClock)
    private val items = SampleData.conversionItems()

    @Test
    fun `secoes e contagens com a fila de conversao ligada`() {
        val overview = QueueOverview.build(jobs, items, conversionsRunning = true)
        assertEquals(listOf(SectionKind.IN_PROGRESS, SectionKind.FAILED, SectionKind.DONE), overview.sections.map { it.kind })
        // 4 conversões pendentes + 3 downloads (baixando, na fila, pausado)
        assertEquals(7, overview.activeCount)
        assertEquals(2, overview.doneCount)
        assertTrue(overview.anyRunning)
        assertEquals(1, overview.downloadingCount)
        assertEquals(5.1, overview.totalSpeedMbps, 0.0)
        assertEquals(1, overview.convertingCount)
        // Ordem: ativos, na fila, pausados; conversões antes de downloads no mesmo estado.
        val statuses = overview.sections.first().rows.map { it.status }
        assertEquals(statuses.sortedBy { it.ordinal }, statuses)
        assertTrue(overview.sections.first().rows.first() is QueueRow.Conversion)
    }

    @Test
    fun `com a fila de conversao desligada, prontos somem e convertendo vira pausado`() {
        val overview = QueueOverview.build(jobs, items, conversionsRunning = false)
        val conversions = overview.sections.flatMap { it.rows }.filterIsInstance<QueueRow.Conversion>()
        assertTrue(conversions.none { it.item.status == ConversionStatus.READY })
        assertEquals(RowStatus.PAUSED, conversions.first { it.item.status == ConversionStatus.CONVERTING }.status)
        assertEquals(0, overview.convertingCount)
    }

    @Test
    fun `fila vazia e ociosa`() {
        val overview = QueueOverview.build(emptyList(), emptyList(), conversionsRunning = false)
        assertTrue(overview.isEmpty)
        assertTrue(overview.isIdle)
        assertFalse(overview.anyRunning)
    }
}
