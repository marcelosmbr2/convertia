package com.convertia.app.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.convertia.app.data.DownloadQueue
import com.convertia.app.data.engine.MediaInspector
import com.convertia.app.domain.DownloadFormat
import com.convertia.app.domain.DownloadOptions
import com.convertia.app.domain.MediaInfo
import com.convertia.app.domain.MediaKind
import com.convertia.app.domain.requestFor
import com.convertia.app.ui.download.defaultFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Uma das opções rápidas do sheet (SHARE_OPTS no mockup). */
data class QuickOption(val format: DownloadFormat, val container: String, val recommended: Boolean) {
    val id: String get() = format.id
    val kind: MediaKind get() = format.kind
}

/**
 * Até três opções: o vídeo recomendado, um 720p e o áudio em MP3 de maior taxa — como
 * "Vídeo · 1080p", "Vídeo · 720p" e "Somente áudio" no mockup.
 */
fun quickOptions(media: MediaInfo): List<QuickOption> {
    val videos = media.videoFormats
    val recommended = videos.defaultFormat()
    val smaller = videos.firstOrNull { it != recommended && it.quality == "720p" && it.container == recommended?.container }
        ?: videos.firstOrNull { it != recommended && it.sizeMb < (recommended?.sizeMb ?: 0.0) }
    val audio = media.audioFormats.filter { it.container == "mp3" }.maxByOrNull { it.sizeMb }
        ?: media.audioFormats.defaultFormat()
    return listOfNotNull(
        recommended?.let { QuickOption(it, it.container, recommended = true) },
        smaller?.let { QuickOption(it, it.container, recommended = false) },
        audio?.let { QuickOption(it, "mp3", recommended = false) },
    )
}

sealed interface ShareState {
    /** O texto compartilhado não tinha link. */
    data object NoLink : ShareState

    data class Loading(val url: String, val subject: String?) : ShareState

    data class Ready(val media: MediaInfo, val options: List<QuickOption>, val selectedId: String) : ShareState {
        val selected: QuickOption? get() = options.firstOrNull { it.id == selectedId }
    }

    data class Failed(val url: String) : ShareState
}

class ShareViewModel(
    url: String?,
    subject: String?,
    private val inspector: MediaInspector,
    private val downloads: DownloadQueue,
    /** Opções de "Mais opções" salvas nos ajustes. */
    private val options: () -> DownloadOptions = { DownloadOptions() },
) : ViewModel() {

    private val _state = MutableStateFlow<ShareState>(if (url == null) ShareState.NoLink else ShareState.Loading(url, subject))
    val state: StateFlow<ShareState> = _state.asStateFlow()

    init {
        if (url != null) {
            viewModelScope.launch {
                _state.value = try {
                    val media = inspector.inspect(url)
                    val options = quickOptions(media)
                    if (options.isEmpty()) ShareState.Failed(url) else ShareState.Ready(media, options, options.first().id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ShareState.Failed(url)
                }
            }
        }
    }

    fun select(id: String) {
        val ready = _state.value as? ShareState.Ready ?: return
        _state.value = ready.copy(selectedId = id)
    }

    /** Enfileira na fila do app e devolve o rótulo do formato ("1080p · MP4"). */
    fun download(): String? {
        val ready = _state.value as? ShareState.Ready ?: return null
        val option = ready.selected ?: return null
        val media = ready.media
        val request = media.requestFor(option.format, option.container, options())
        downloads.enqueue(request)
        return request.formatLabel
    }
}
