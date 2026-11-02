package com.convertia.app.ui.settings

import com.convertia.app.MainDispatcherRule
import com.convertia.app.data.AppMessages
import com.convertia.app.data.SampleData
import com.convertia.app.data.ToastEvent
import com.convertia.app.domain.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val messages = AppMessages()

    private fun viewModel(update: suspend () -> String?) =
        SettingsViewModel(MutableStateFlow(AppSettings()), {}, messages, SampleData.presets, MutableStateFlow("2025.11.12"), update)

    @Test
    fun `verificar avisa a versao nova`() = runTest(mainRule.dispatcher) {
        val vm = viewModel { "2026.10.10" }
        vm.checkEngineUpdates()
        runCurrent()
        assertEquals(ToastEvent.EngineUpdated("yt-dlp", "2026.10.10"), messages.current.value?.event)
        assertFalse(vm.checking.value)
    }

    @Test
    fun `verificar sem novidade avisa que esta em dia`() = runTest(mainRule.dispatcher) {
        val vm = viewModel { null }
        vm.checkEngineUpdates()
        runCurrent()
        assertEquals(ToastEvent.EngineUpToDate("yt-dlp", "2025.11.12"), messages.current.value?.event)
    }

    @Test
    fun `falha na verificacao vira aviso`() = runTest(mainRule.dispatcher) {
        val vm = viewModel { error("sem rede") }
        vm.checkEngineUpdates()
        runCurrent()
        assertEquals(ToastEvent.EngineUpdateFailed, messages.current.value?.event)
        assertFalse(vm.checking.value)
    }
}
