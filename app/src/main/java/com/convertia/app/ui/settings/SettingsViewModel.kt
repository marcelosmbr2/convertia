package com.convertia.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.convertia.app.data.AppMessages
import com.convertia.app.data.ToastEvent
import com.convertia.app.domain.AppSettings
import com.convertia.app.domain.Preset
import com.convertia.app.ui.convertiaContainer
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    val settings: StateFlow<AppSettings>,
    private val updateSettings: ((AppSettings) -> AppSettings) -> Unit,
    private val messages: AppMessages,
    val presets: List<Preset>,
) : ViewModel() {

    fun update(transform: (AppSettings) -> AppSettings) = updateSettings(transform)

    /** Simulado nesta fase: o motor real (Fase 2) informa a versão de verdade. */
    fun checkEngineUpdates() = messages.show(ToastEvent.EngineUpToDate("yt-dlp", YT_DLP_VERSION))

    companion object {
        const val YT_DLP_VERSION = "2026.10.10"
        const val FFMPEG_VERSION = "7.1"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = convertiaContainer()
                SettingsViewModel(c.settings, c::updateSettings, c.messages, c.presets)
            }
        }
    }
}
