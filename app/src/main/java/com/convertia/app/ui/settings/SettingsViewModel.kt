package com.convertia.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.convertia.app.data.AppMessages
import com.convertia.app.data.ToastEvent
import com.convertia.app.domain.AppSettings
import com.convertia.app.domain.Preset
import com.convertia.app.ui.convertiaContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    val settings: StateFlow<AppSettings>,
    private val updateSettings: ((AppSettings) -> AppSettings) -> Unit,
    private val messages: AppMessages,
    val presets: List<Preset>,
    /** Versão do yt-dlp em uso; nula enquanto o motor prepara. */
    val ytDlpVersion: StateFlow<String?>,
    /** Atualiza o yt-dlp; devolve a versão nova ou `null` se já estava em dia. */
    private val updateEngine: suspend () -> String?,
) : ViewModel() {

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) = updateSettings(transform)

    /** "Verificar": busca a versão estável mais nova do yt-dlp no GitHub. */
    fun checkEngineUpdates() {
        if (_checking.value) return
        _checking.value = true
        viewModelScope.launch {
            try {
                val updated = updateEngine()
                messages.show(
                    if (updated != null) ToastEvent.EngineUpdated(YT_DLP, updated)
                    else ToastEvent.EngineUpToDate(YT_DLP, ytDlpVersion.value.orEmpty()),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                messages.show(ToastEvent.EngineUpdateFailed)
            } finally {
                _checking.value = false
            }
        }
    }

    companion object {
        private const val YT_DLP = "yt-dlp"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = convertiaContainer()
                SettingsViewModel(c.settings, c::updateSettings, c.messages, c.presets, c.ytDlp.version) { c.ytDlp.update() }
            }
        }
    }
}
