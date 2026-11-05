package com.recast.app.ui.convert

import com.recast.app.MainDispatcherRule
import com.recast.app.data.AppMessages
import com.recast.app.data.ConversionQueue
import com.recast.app.data.FakeConversionEngine
import com.recast.app.data.LibraryRepository
import com.recast.app.data.SampleData
import com.recast.app.data.ToastEvent
import com.recast.app.data.fixedClock
import com.recast.app.domain.ConversionSource
import com.recast.app.domain.MediaCategory
import com.recast.app.domain.OutputFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConvertViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val library = LibraryRepository()
    private val messages = AppMessages()

    private fun TestScope.viewModel(): Pair<ConvertViewModel, ConversionQueue> {
        val queue = ConversionQueue(backgroundScope, FakeConversionEngine(), library, fixedClock, MutableStateFlow(2), MutableStateFlow(true))
        return ConvertViewModel(queue, library, messages, SampleData.presets) to queue
    }

    @Test
    fun `adicionar arquivos escolhidos avisa quantos`() = runTest(mainRule.dispatcher) {
        val (vm, queue) = viewModel()
        vm.addPicked(listOf(PickedFile("content://a", "foto.heic", 3_000_000), PickedFile("content://b", "nota.pdf", 1_000)), "Galeria")
        assertEquals(2, queue.items.value.size)
        assertEquals("JPG", queue.items.value.first().target.ext)
        assertEquals(ConversionSource.Document("content://a"), queue.items.value.first().source)
        assertEquals(ToastEvent.FilesAdded(2), messages.current.value?.event)
    }

    @Test
    fun `botao principal segue o estado da fila`() = runTest(mainRule.dispatcher) {
        val (vm, _) = viewModel()
        runCurrent()
        assertEquals(PrimaryAction.Idle, vm.uiState.value.primary)

        vm.addPicked(listOf(PickedFile("content://a", "a.png", 1_000_000), PickedFile("content://b", "b.png", 1_000_000)), "Arquivos")
        runCurrent()
        assertEquals(PrimaryAction.Convert(2), vm.uiState.value.primary)

        vm.onPrimary()
        runCurrent()
        assertEquals(PrimaryAction.Pause, vm.uiState.value.primary)

        advanceTimeBy(150)
        vm.onPrimary()
        runCurrent()
        assertEquals(PrimaryAction.Resume, vm.uiState.value.primary)
    }

    @Test
    fun `predefinicao sem arquivos correspondentes avisa`() = runTest(mainRule.dispatcher) {
        val (vm, _) = viewModel()
        vm.applyPreset(SampleData.presets.first())
        assertEquals(ToastEvent.PresetApplied("Fotos HEIC para JPG", 0, listOf("heic", "heif")), messages.current.value?.event)
    }

    @Test
    fun `tudo para um formato avisa quantos mudaram`() = runTest(mainRule.dispatcher) {
        val (vm, _) = viewModel()
        vm.addPicked(listOf(PickedFile("content://a", "a.png", 1), PickedFile("content://b", "b.mp3", 1)), "Arquivos")
        vm.setTargetForAll(OutputFormat("AVIF", MediaCategory.IMAGE))
        assertEquals(ToastEvent.TargetSetForAll(1, "AVIF"), messages.current.value?.event)
        runCurrent()
        assertEquals(null, vm.uiState.value.commonTarget)
    }
}
