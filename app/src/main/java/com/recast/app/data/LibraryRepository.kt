package com.recast.app.data

import com.recast.app.domain.HistoryEntry
import com.recast.app.domain.LibraryItem
import com.recast.app.domain.RecentLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Biblioteca (arquivos baixados), histórico de conversões e links recentes. O
 * [AppContainer][com.recast.app.AppContainer] grava tudo em disco a cada mudança.
 *
 * Excluir tira o item da lista na hora, mas só apaga o arquivo ([deleteFile]) depois de
 * [deleteDelayMs], para o "Desfazer" do toast funcionar.
 */
class LibraryRepository(
    private val scope: CoroutineScope? = null,
    private val deleteFile: (uri: String) -> Unit = {},
    private val deleteDelayMs: Long = DELETE_DELAY_MS,
) {
    private val pendingDeletes = mutableMapOf<String, Job>()
    private val _items = MutableStateFlow<List<LibraryItem>>(emptyList())
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val _recents = MutableStateFlow<List<RecentLink>>(emptyList())

    /** Mais recentes primeiro. */
    val items: StateFlow<List<LibraryItem>> = _items.asStateFlow()
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()
    val recents: StateFlow<List<RecentLink>> = _recents.asStateFlow()

    fun add(item: LibraryItem) = _items.update { listOf(item) + it }

    fun find(id: String): LibraryItem? = _items.value.firstOrNull { it.id == id }

    /** Remove e devolve o item com a posição, para o "Desfazer"; o arquivo some depois. */
    fun remove(id: String): Pair<LibraryItem, Int>? {
        val list = _items.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return null
        _items.value = list.toMutableList().apply { removeAt(index) }
        val item = list[index]
        item.uri?.let { uri ->
            pendingDeletes[id] = scope?.launch {
                delay(deleteDelayMs)
                pendingDeletes.remove(id)
                deleteFile(uri)
            } ?: return@let
        }
        return item to index
    }

    /** "Desfazer": devolve o item à lista e cancela a exclusão do arquivo. */
    fun restore(item: LibraryItem, index: Int) {
        pendingDeletes.remove(item.id)?.cancel()
        _items.update { list ->
            if (list.any { it.id == item.id }) list
            else list.toMutableList().apply { add(index.coerceIn(0, size), item) }
        }
    }

    fun addHistory(entry: HistoryEntry) = _history.update { listOf(entry) + it }

    fun clearHistory() {
        _history.value = emptyList()
    }

    /** Guarda o link no topo dos recentes, sem repetir. */
    fun addRecent(link: RecentLink) = _recents.update { list ->
        (listOf(link) + list.filterNot { it.url == link.url }).take(MAX_RECENTS)
    }

    internal fun seed(items: List<LibraryItem>, history: List<HistoryEntry>, recents: List<RecentLink>) {
        _items.value = items
        _history.value = history
        _recents.value = recents
    }

    companion object {
        private const val MAX_RECENTS = 3

        /** Mais que o toast (3,6 s, ou mais com as opções de acessibilidade). */
        const val DELETE_DELAY_MS = 10_000L
    }
}
