package com.recast.app.data

import com.recast.app.domain.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppStateStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `grava e le de volta`() {
        val store = AppStateStore(folder.root.resolve("state.json"))
        assertNull(store.load())
        val snapshot = AppSnapshot(
            library = SampleData.libraryItems(fixedClock),
            history = SampleData.history(fixedClock),
            recents = SampleData.recents(fixedClock),
            jobs = SampleData.downloadJobs(fixedClock),
        )
        store.save(snapshot)
        assertEquals(snapshot, store.load())
    }

    @Test
    fun `downloads interrompidos voltam pausados`() {
        val restored = AppSnapshot(jobs = SampleData.downloadJobs(fixedClock)).withInterruptedJobsPaused()
        assertEquals(
            listOf(DownloadStatus.PAUSED, DownloadStatus.PAUSED, DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.DONE),
            restored.jobs.map { it.status },
        )
        assertEquals(0.0, restored.jobs.first().speedMbps, 0.0)
    }

    @Test
    fun `arquivo corrompido e ignorado`() {
        val file = folder.root.resolve("state.json").apply { writeText("{ não é json") }
        assertNull(AppStateStore(file).load())
    }
}
