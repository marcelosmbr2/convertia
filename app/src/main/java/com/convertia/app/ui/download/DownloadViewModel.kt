package com.convertia.app.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.convertia.app.data.AppMessages
import com.convertia.app.data.DownloadQueue
import com.convertia.app.data.LibraryRepository
import com.convertia.app.data.ToastEvent
import com.convertia.app.data.engine.MediaInspector
import com.convertia.app.domain.AppSettings
import com.convertia.app.domain.DownloadFormat
import com.convertia.app.domain.FormatBadge
import com.convertia.app.domain.MediaInfo
import com.convertia.app.domain.MediaKind
import com.convertia.app.domain.RecentLink
import com.convertia.app.domain.SaveContainers
import com.convertia.app.domain.firstUrlIn
import com.convertia.app.domain.requestFor
import com.convertia.app.ui.convertiaContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

enum class InspectPhase { EMPTY, LOADING, READY }

/** Estado editável da tela Baixar. */
data class DownloadState(
    val url: String = "",
    val phase: InspectPhase = InspectPhase.EMPTY,
    val inspectedUrl: String = "",
    val media: MediaInfo? = null,
    val failed: Boolean = false,
    val kind: MediaKind = MediaKind.VIDEO,
    val selectedFormatId: String? = null,
    val container: String = SaveContainers.defaultFor(MediaKind.VIDEO),
    val showAllFormats: Boolean = false,
    val moreOpen: Boolean = false,
    /** Link encontrado na área de transferência. */
    val clipboardLink: String? = null,
)

/** O que a tela desenha: estado + ajustes + valores derivados. */
data class DownloadUiState(
    val state: DownloadState,
    val settings: AppSettings,
    val recents: List<RecentLink>,
) {
    val url get() = state.url
    val phase get() = state.phase
    val media get() = state.media

    /** "Ver formatos": há texto, não está carregando e o link mudou desde a última inspeção. */
    val showInspectButton: Boolean
        get() = state.url.isNotBlank() && state.phase != InspectPhase.LOADING &&
            (state.phase == InspectPhase.EMPTY || state.url.trim() != state.inspectedUrl)

    val formats: List<DownloadFormat>
        get() = media?.let { if (state.kind == MediaKind.VIDEO) it.videoFormats else it.audioFormats }.orEmpty()

    val selectedFormat: DownloadFormat?
        get() = formats.firstOrNull { it.id == state.selectedFormatId } ?: formats.defaultFormat()

    private val selectedIndex: Int get() = formats.indexOf(selectedFormat)

    /** A lista abre sozinha se o formato escolhido estiver fora dos três primeiros. */
    val formatsExpanded: Boolean get() = state.showAllFormats || selectedIndex >= COLLAPSED_FORMATS

    val visibleFormats: List<DownloadFormat>
        get() = if (formatsExpanded) formats else formats.take(COLLAPSED_FORMATS)

    val showFormatsToggle: Boolean get() = formats.size > COLLAPSED_FORMATS && selectedIndex < COLLAPSED_FORMATS

    val containers: List<String> get() = SaveContainers.forKind(state.kind)

    /** Cartão "Link copiado": só no estado vazio, com o ajuste ligado. */
    val clipboardSuggestion: String?
        get() = state.clipboardLink.takeIf { settings.detectClipboardLinks && state.phase == InspectPhase.EMPTY }

    val fileNamePreview: String get() = "${media?.title.orEmpty()}.${state.container}"

    companion object {
        const val COLLAPSED_FORMATS = 3
    }
}

/** Formato marcado como recomendado/melhor, ou o primeiro. */
fun List<DownloadFormat>.defaultFormat(): DownloadFormat? =
    firstOrNull { it.badge == FormatBadge.RECOMMENDED || it.badge == FormatBadge.BEST } ?: firstOrNull()

class DownloadViewModel(
    private val inspector: MediaInspector,
    private val downloads: DownloadQueue,
    private val library: LibraryRepository,
    private val messages: AppMessages,
    private val settings: StateFlow<AppSettings>,
    private val updateSettings: ((AppSettings) -> AppSettings) -> Unit,
    private val clock: Clock,
    pendingLink: StateFlow<String?>,
    private val consumeLink: () -> String?,
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadState())
    private var inspection: Job? = null
    private var lastClipboardStamp: Long? = null

    val uiState: StateFlow<DownloadUiState> = combine(_state, settings, library.recents) { state, s, recents ->
        DownloadUiState(state, s, recents)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DownloadUiState(_state.value, settings.value, library.recents.value))

    init {
        viewModelScope.launch {
            pendingLink.filterNotNull().collect { consumeLink()?.let(::inspect) }
        }
    }

    fun onUrlChange(url: String) = _state.update { it.copy(url = url) }

    fun clearUrl() {
        inspection?.cancel()
        _state.update { it.copy(url = "", phase = InspectPhase.EMPTY, failed = false) }
    }

    /** Inspeciona o link do campo. */
    fun inspectCurrent() = inspect(_state.value.url)

    fun inspect(url: String) {
        val link = url.trim()
        if (link.isEmpty()) return
        inspection?.cancel()
        _state.update {
            it.copy(url = link, inspectedUrl = link, phase = InspectPhase.LOADING, failed = false, showAllFormats = false)
        }
        inspection = viewModelScope.launch {
            try {
                val info = inspector.inspect(link)
                _state.update { state ->
                    val formats = if (state.kind == MediaKind.VIDEO) info.videoFormats else info.audioFormats
                    val keep = formats.any { it.id == state.selectedFormatId }
                    state.copy(
                        phase = InspectPhase.READY,
                        media = info,
                        selectedFormatId = if (keep) state.selectedFormatId else formats.defaultFormat()?.id,
                    )
                }
                library.addRecent(RecentLink(link, info.title, info.site, clock.millis()))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(phase = InspectPhase.EMPTY, failed = true) }
            }
        }
    }

    /** Texto lido da área de transferência pelo botão Colar. */
    fun onPaste(text: String?) {
        val link = text?.let(::firstUrlIn)
        if (link == null) {
            messages.show(ToastEvent.NoLinkInClipboard)
            return
        }
        inspect(link)
    }

    /**
     * Resultado da leitura automática da área de transferência. [stamp] identifica o
     * conteúdo (timestamp do ClipDescription) para não ler de novo o mesmo recorte.
     */
    fun onClipboardChecked(text: String?, stamp: Long) {
        lastClipboardStamp = stamp
        _state.update { it.copy(clipboardLink = text?.let(::firstUrlIn)) }
    }

    fun shouldReadClipboard(stamp: Long): Boolean = stamp != lastClipboardStamp

    fun selectKind(kind: MediaKind) = _state.update { state ->
        if (state.kind == kind) return@update state
        val formats = state.media?.let { if (kind == MediaKind.VIDEO) it.videoFormats else it.audioFormats }.orEmpty()
        state.copy(kind = kind, selectedFormatId = formats.defaultFormat()?.id, container = SaveContainers.defaultFor(kind))
    }

    fun selectFormat(id: String) = _state.update { it.copy(selectedFormatId = id) }

    fun selectContainer(container: String) = _state.update { it.copy(container = container) }

    fun toggleShowAllFormats() = _state.update { it.copy(showAllFormats = !currentUi().formatsExpanded) }

    fun toggleMoreOptions() = _state.update { it.copy(moreOpen = !it.moreOpen) }

    fun setEmbedThumbnail(on: Boolean) = updateSettings { it.copy(embedThumbnail = on) }
    fun setEmbedMetadata(on: Boolean) = updateSettings { it.copy(embedMetadata = on) }
    fun setSubtitles(on: Boolean) = updateSettings { it.copy(subtitles = on) }
    fun setSplitChapters(on: Boolean) = updateSettings { it.copy(splitChapters = on) }

    /** "Baixar": põe na fila e avisa com "Na fila · …". */
    fun download() {
        val ui = currentUi()
        val media = ui.media ?: return
        val format = ui.selectedFormat ?: return
        val request = media.requestFor(format, ui.state.container, ui.settings.downloadOptions)
        downloads.enqueue(request)
        messages.show(ToastEvent.DownloadQueued(request.formatLabel))
    }

    /** Estado derivado montado na hora (o [uiState] pode ainda não ter recebido a última mudança). */
    private fun currentUi() = DownloadUiState(_state.value, settings.value, library.recents.value)

    private inline fun MutableStateFlow<DownloadState>.update(transform: (DownloadState) -> DownloadState) {
        value = transform(value)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = convertiaContainer()
                DownloadViewModel(
                    inspector = c.mediaInspector,
                    downloads = c.downloads,
                    library = c.library,
                    messages = c.messages,
                    settings = c.settings,
                    updateSettings = c::updateSettings,
                    clock = c.clock,
                    pendingLink = c.pendingLink,
                    consumeLink = c::consumeLink,
                )
            }
        }
    }
}
