package com.convertia.app.ui.library

import com.convertia.app.MainDispatcherRule
import com.convertia.app.data.AppMessages
import com.convertia.app.data.LibraryRepository
import com.convertia.app.data.SampleData
import com.convertia.app.data.ToastEvent
import com.convertia.app.data.fixedClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val library = LibraryRepository().apply {
        seed(SampleData.libraryItems(fixedClock), SampleData.history(fixedClock), emptyList())
    }
    private val messages = AppMessages()

    @Test
    fun `busca por nome ou formato`() = runTest(mainRule.dispatcher) {
        val vm = LibraryViewModel(library, messages)
        vm.onQueryChange("flac")
        runCurrent()
        assertEquals(listOf("Synth Jam 04"), vm.uiState.value.visibleItems.map { it.title })
        vm.onQueryChange("aula")
        runCurrent()
        assertEquals(1, vm.uiState.value.visibleItems.size)
        assertEquals(7, vm.uiState.value.items.size)
    }

    @Test
    fun `excluir oferece desfazer na mesma posicao`() = runTest(mainRule.dispatcher) {
        val vm = LibraryViewModel(library, messages)
        vm.delete("sample-lib-3")
        val event = messages.current.value?.event as ToastEvent.LibraryItemDeleted
        assertEquals(2, event.index)
        assertEquals(6, library.items.value.size)

        library.restore(event.item, event.index)
        assertEquals("sample-lib-3", library.items.value[2].id)
    }
}
