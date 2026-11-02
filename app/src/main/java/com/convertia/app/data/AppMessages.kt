package com.convertia.app.data

import com.convertia.app.domain.DownloadJob
import com.convertia.app.domain.LibraryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * O que um toast comunica. A interface decide o texto e a ação de cada tipo; aqui só há
 * dados, para que nada guarde referências a telas ou ViewModels.
 */
sealed interface ToastEvent {
    /** "Download concluído" · Converter. */
    data class DownloadFinished(val jobId: Long) : ToastEvent

    /** "Conversões concluídas" · Histórico. */
    data object ConversionsFinished : ToastEvent

    /** "Na fila · 1080p · MP4" · Ver fila. */
    data class DownloadQueued(val formatLabel: String) : ToastEvent

    /** "Baixando no Convertia · 1080p · MP4" · Abrir (menu Compartilhar). */
    data class SharedDownloadQueued(val formatLabel: String) : ToastEvent

    /** "Arquivo excluído" · Desfazer. */
    data class LibraryItemDeleted(val item: LibraryItem, val index: Int) : ToastEvent

    data class FilesAdded(val count: Int) : ToastEvent

    /** "N arquivos definidos como EXT", ou "Nenhum arquivo compatível" com zero. */
    data class TargetSetForAll(val count: Int, val ext: String) : ToastEvent

    /** "“Nome” aplicada a N arquivos", ou "Nenhum arquivo X/Y na lista" com zero. */
    data class PresetApplied(val name: String, val count: Int, val sources: List<String>) : ToastEvent

    data class OptionsApplied(val count: Int) : ToastEvent

    /** Arquivo da Biblioteca que não abre (apagado fora do app ou sem app compatível). */
    data object FileUnavailable : ToastEvent

    data class EngineUpToDate(val engine: String, val version: String) : ToastEvent

    data class EngineUpdated(val engine: String, val version: String) : ToastEvent

    data object EngineUpdateFailed : ToastEvent

    /** O texto compartilhado não tinha link. */
    data object NoLinkShared : ToastEvent

    /** "Colar" sem link na área de transferência. */
    data object NoLinkInClipboard : ToastEvent
}

data class ToastMessage(val id: Long, val event: ToastEvent)

/** Fila de um toast só: um novo substitui o anterior, como no mockup. */
class AppMessages {
    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()
    private var nextId = 1L

    fun show(event: ToastEvent) {
        _current.value = ToastMessage(nextId++, event)
    }

    fun dismiss(id: Long) {
        if (_current.value?.id == id) _current.value = null
    }
}

/**
 * Avisa quando downloads e conversões terminam, se o ajuste Notificações estiver ligado:
 * toast com o app aberto, notificação do sistema com ele fechado.
 */
class CompletionNotifier(
    scope: CoroutineScope,
    downloads: DownloadQueue,
    conversions: ConversionQueue,
    notificationsEnabled: StateFlow<Boolean>,
    messages: AppMessages,
    isForeground: () -> Boolean = { true },
    notifyDownload: (DownloadJob) -> Unit = {},
    notifyConversions: () -> Unit = {},
) {
    init {
        scope.launch {
            downloads.finished.collect { job ->
                if (!notificationsEnabled.value) return@collect
                if (isForeground()) messages.show(ToastEvent.DownloadFinished(job.id)) else notifyDownload(job)
            }
        }
        scope.launch {
            conversions.allFinished.collect {
                if (!notificationsEnabled.value) return@collect
                if (isForeground()) messages.show(ToastEvent.ConversionsFinished) else notifyConversions()
            }
        }
    }
}
