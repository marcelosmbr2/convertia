package com.convertia.app.share

import com.convertia.app.MainDispatcherRule
import com.convertia.app.data.DownloadQueue
import com.convertia.app.data.FakeDownloadEngine
import com.convertia.app.data.LibraryRepository
import com.convertia.app.data.SampleData
import com.convertia.app.data.engine.SimulatedMediaInspector
import com.convertia.app.data.fixedClock
import com.convertia.app.domain.MediaKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun `opcoes rapidas sao 1080p, 720p e MP3 320 como no mockup`() {
        val options = quickOptions(SampleData.mediaInfo())
        assertEquals(listOf("v1080", "v720", "a3"), options.map { it.id })
        assertEquals(listOf(true, false, false), options.map { it.recommended })
        assertEquals(listOf("mp4", "mp4", "mp3"), options.map { it.container })
    }

    @Test
    fun `sem link nao inspeciona`() = runTest(mainRule.dispatcher) {
        val queue = DownloadQueue(backgroundScope, FakeDownloadEngine(), LibraryRepository(), fixedClock, MutableStateFlow(2))
        val vm = ShareViewModel(null, null, SimulatedMediaInspector(10), queue)
        assertEquals(ShareState.NoLink, vm.state.value)
    }

    @Test
    fun `inspeciona e baixa a opcao escolhida`() = runTest(mainRule.dispatcher) {
        val queue = DownloadQueue(backgroundScope, FakeDownloadEngine(), LibraryRepository(), fixedClock, MutableStateFlow(2))
        val vm = ShareViewModel(SampleData.DEFAULT_URL, "Teclado", SimulatedMediaInspector(1_000), queue)
        assertTrue(vm.state.value is ShareState.Loading)

        advanceTimeBy(1_100)
        runCurrent()
        assertTrue(vm.state.value is ShareState.Ready)

        vm.select("a3")
        assertEquals("MP3 · 320 kbps", vm.download())
        val job = queue.jobs.value.single()
        assertEquals(MediaKind.AUDIO, job.request.kind)
        assertEquals(42.8, job.request.sizeMb, 0.0)
    }
}
