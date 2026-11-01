package com.convertia.app.ui.download

import com.convertia.app.MainDispatcherRule
import com.convertia.app.data.AppMessages
import com.convertia.app.data.DownloadQueue
import com.convertia.app.data.FakeDownloadEngine
import com.convertia.app.data.LibraryRepository
import com.convertia.app.data.ToastEvent
import com.convertia.app.data.engine.SimulatedMediaInspector
import com.convertia.app.data.fixedClock
import com.convertia.app.domain.AppSettings
import com.convertia.app.domain.MediaKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val library = LibraryRepository()
    private val messages = AppMessages()
    private val settings = MutableStateFlow(AppSettings())
    private val pendingLink = MutableStateFlow<String?>(null)

    private fun TestScope.viewModel(): Pair<DownloadViewModel, DownloadQueue> {
        val queue = DownloadQueue(backgroundScope, FakeDownloadEngine(), library, fixedClock, MutableStateFlow(2))
        val vm = DownloadViewModel(
            inspector = SimulatedMediaInspector(delayMs = 1_000),
            downloads = queue,
            library = library,
            messages = messages,
            settings = settings,
            updateSettings = { transform -> settings.value = transform(settings.value) },
            clock = fixedClock,
            pendingLink = pendingLink,
            consumeLink = { pendingLink.value.also { pendingLink.value = null } },
        )
        return vm to queue
    }

    @Test
    fun `vazio, carregando e pronto`() = runTest(mainRule.dispatcher) {
        val (vm, _) = viewModel()
        assertEquals(InspectPhase.EMPTY, vm.uiState.value.phase)

        vm.onUrlChange("https://www.youtube.com/watch?v=k3yB0ard_fs")
        runCurrent()
        assertTrue(vm.uiState.value.showInspectButton)

        vm.inspectCurrent()
        runCurrent()
        assertEquals(InspectPhase.LOADING, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.showInspectButton)

        advanceTimeBy(1_100)
        runCurrent()
        val ui = vm.uiState.value
        assertEquals(InspectPhase.READY, ui.phase)
        assertEquals("v1080", ui.selectedFormat?.id)
        assertEquals(3, ui.visibleFormats.size)
        assertTrue(ui.showFormatsToggle)
        assertEquals("youtube.com", library.recents.value.single().site)
    }

    @Test
    fun `editar o link depois de inspecionar mostra Ver formatos de novo`() = runTest(mainRule.dispatcher) {
        val (vm, _) = viewModel()
        vm.inspect("https://vimeo.com/884213301")
        advanceTimeBy(1_100)
        runCurrent()
        assertFalse(vm.uiState.value.showInspectButton)

        vm.onUrlChange("https://vimeo.com/1")
        runCurrent()
        assertTrue(vm.uiState.value.showInspectButton)
    }

    @Test
    fun `somente audio troca formato e conteiner`() = runTest(mainRule.dispatcher) {
        val (vm, _) = viewModel()
        vm.inspect("https://www.youtube.com/watch?v=k3yB0ard_fs")
        advanceTimeBy(1_100)
        vm.selectKind(MediaKind.AUDIO)
        runCurrent()
        assertEquals("a1", vm.uiState.value.selectedFormat?.id)
        assertEquals("mp3", vm.uiState.value.state.container)
        assertEquals(listOf("mp3", "m4a", "opus", "wav"), vm.uiState.value.containers)
    }

    @Test
    fun `escolher formato fora dos tres primeiros abre a lista`() = runTest(mainRule.dispatcher) {
        val (vm, _) = viewModel()
        vm.inspect("https://www.youtube.com/watch?v=k3yB0ard_fs")
        advanceTimeBy(1_100)
        vm.selectFormat("v720")
        runCurrent()
        assertTrue(vm.uiState.value.formatsExpanded)
        assertFalse(vm.uiState.value.showFormatsToggle)
        assertEquals(6, vm.uiState.value.visibleFormats.size)
    }

    @Test
    fun `baixar enfileira e avisa`() = runTest(mainRule.dispatcher) {
        val (vm, queue) = viewModel()
        vm.inspect("https://www.youtube.com/watch?v=k3yB0ard_fs")
        advanceTimeBy(1_100)
        vm.selectContainer("mkv")
        vm.download()
        runCurrent()
        val job = queue.jobs.value.single()
        assertEquals("1080p · MKV", job.request.formatLabel)
        assertEquals(412.0, job.request.sizeMb, 0.0)
        assertEquals(ToastEvent.DownloadQueued("1080p · MKV"), messages.current.value?.event)
    }

    @Test
    fun `colar sem link avisa e colar com link inspeciona`() = runTest(mainRule.dispatcher) {
        val (vm, _) = viewModel()
        vm.onPaste("só texto")
        assertEquals(ToastEvent.NoLinkInClipboard, messages.current.value?.event)

        vm.onPaste("veja https://vimeo.com/884213301")
        runCurrent()
        assertEquals("https://vimeo.com/884213301", vm.uiState.value.url)
        assertEquals(InspectPhase.LOADING, vm.uiState.value.phase)
    }

    @Test
    fun `link vindo do menu Compartilhar e inspecionado`() = runTest(mainRule.dispatcher) {
        val (vm, _) = viewModel()
        runCurrent()
        pendingLink.value = "https://www.youtube.com/watch?v=la4_vectors"
        runCurrent()
        assertEquals(InspectPhase.LOADING, vm.uiState.value.phase)
        assertNull(pendingLink.value)
    }

    @Test
    fun `cartao da area de transferencia respeita o ajuste`() = runTest(mainRule.dispatcher) {
        val (vm, _) = viewModel()
        vm.onClipboardChecked("https://youtu.be/abc", stamp = 1)
        runCurrent()
        assertEquals("https://youtu.be/abc", vm.uiState.value.clipboardSuggestion)
        assertFalse(vm.shouldReadClipboard(1))

        settings.value = settings.value.copy(detectClipboardLinks = false)
        runCurrent()
        assertNull(vm.uiState.value.clipboardSuggestion)
    }
}
