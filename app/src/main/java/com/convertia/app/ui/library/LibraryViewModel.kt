package com.convertia.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.convertia.app.data.AppMessages
import com.convertia.app.data.LibraryRepository
import com.convertia.app.data.ToastEvent
import com.convertia.app.domain.HistoryEntry
import com.convertia.app.domain.LibraryItem
import com.convertia.app.ui.convertiaContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LibraryUiState(
    val items: List<LibraryItem> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val query: String = "",
) {
    /** Itens que batem com a busca (nome ou formato). */
    val visibleItems: List<LibraryItem>
        get() {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return items
            return items.filter { it.title.lowercase().contains(q) || it.format.lowercase().contains(q) }
        }

    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
}

class LibraryViewModel(
    private val library: LibraryRepository,
    private val messages: AppMessages,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<LibraryUiState> = combine(library.items, library.history, query) { items, history, q ->
        LibraryUiState(items, history, q)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState(library.items.value, library.history.value))

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun find(id: String): LibraryItem? = library.find(id)

    fun clearHistory() = library.clearHistory()

    /** Exclui e oferece "Desfazer". */
    fun delete(id: String) {
        val (item, index) = library.remove(id) ?: return
        messages.show(ToastEvent.LibraryItemDeleted(item, index))
    }

    /** Avisa que o arquivo não pôde ser aberto (apagado fora do app ou sem app compatível). */
    fun fileUnavailable() = messages.show(ToastEvent.FileUnavailable)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = convertiaContainer()
                LibraryViewModel(c.library, c.messages)
            }
        }
    }
}
