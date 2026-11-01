package com.convertia.app.data

import com.convertia.app.domain.HistoryEntry
import com.convertia.app.domain.LibraryItem
import com.convertia.app.domain.RecentLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Biblioteca (arquivos baixados), histórico de conversões e links recentes.
 * Em memória nesta fase; vira Room quando os arquivos forem reais.
 */
class LibraryRepository {
    private val _items = MutableStateFlow<List<LibraryItem>>(emptyList())
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val _recents = MutableStateFlow<List<RecentLink>>(emptyList())

    /** Mais recentes primeiro. */
    val items: StateFlow<List<LibraryItem>> = _items.asStateFlow()
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()
    val recents: StateFlow<List<RecentLink>> = _recents.asStateFlow()

    fun add(item: LibraryItem) = _items.update { listOf(item) + it }

    fun find(id: String): LibraryItem? = _items.value.firstOrNull { it.id == id }

    /** Remove e devolve o item com a posição, para o "Desfazer". */
    fun remove(id: String): Pair<LibraryItem, Int>? {
        val list = _items.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return null
        _items.value = list.toMutableList().apply { removeAt(index) }
        return list[index] to index
    }

    fun restore(item: LibraryItem, index: Int) = _items.update { list ->
        if (list.any { it.id == item.id }) list
        else list.toMutableList().apply { add(index.coerceIn(0, size), item) }
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

    private companion object {
        const val MAX_RECENTS = 3
    }
}
