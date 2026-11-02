package com.convertia.app.ui.convert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.convertia.app.data.AppMessages
import com.convertia.app.data.ConversionQueue
import com.convertia.app.data.LibraryRepository
import com.convertia.app.data.ToastEvent
import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.ConversionOptions
import com.convertia.app.domain.ConversionSource
import com.convertia.app.domain.ConversionStatus
import com.convertia.app.domain.EngineCapabilities
import com.convertia.app.domain.LibraryItem
import com.convertia.app.domain.OutputFormat
import com.convertia.app.domain.Preset
import com.convertia.app.ui.convertiaContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Arquivo escolhido no seletor do sistema ou na galeria. */
data class PickedFile(val uri: String, val name: String, val sizeBytes: Long)

/** Estado do botão principal (Converter/Pausar/Retomar). */
sealed interface PrimaryAction {
    data object Pause : PrimaryAction
    data object Resume : PrimaryAction
    data class Convert(val count: Int) : PrimaryAction
    data object Idle : PrimaryAction
}

data class ConvertUiState(
    val items: List<ConversionItem> = emptyList(),
    val running: Boolean = false,
    val eta: Map<String, Int> = emptyMap(),
    val quickPresets: List<Preset> = emptyList(),
    /** Primeiros arquivos da Biblioteca, para o sheet "Adicionar arquivos". */
    val recentDownloads: List<LibraryItem> = emptyList(),
) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
    val hasDone: Boolean get() = items.any { it.status == ConversionStatus.DONE }
    private val pickable get() = items.filter { it.status != ConversionStatus.CONVERTING }
    val hasPickable: Boolean get() = pickable.isNotEmpty()

    /** Formato comum a todos os arquivos editáveis, ou `null` se forem vários. */
    val commonTarget: String?
        get() = pickable.map { it.target.ext }.distinct().singleOrNull()

    val primary: PrimaryAction
        get() {
            val pending = items.count { it.isPending }
            return when {
                running -> PrimaryAction.Pause
                items.any { it.status == ConversionStatus.CONVERTING } -> PrimaryAction.Resume
                pending > 0 -> PrimaryAction.Convert(pending)
                else -> PrimaryAction.Idle
            }
        }
}

class ConvertViewModel(
    private val conversions: ConversionQueue,
    private val library: LibraryRepository,
    private val messages: AppMessages,
    presets: List<Preset>,
    private val capabilities: EngineCapabilities = EngineCapabilities.All,
) : ViewModel() {

    val uiState: StateFlow<ConvertUiState> = combine(
        conversions.items,
        conversions.running,
        conversions.eta,
        library.items,
    ) { items, running, eta, libraryItems ->
        ConvertUiState(items, running, eta, presets.take(QUICK_PRESETS), libraryItems.take(RECENT_DOWNLOADS))
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ConvertUiState(
            conversions.items.value,
            conversions.running.value,
            conversions.eta.value,
            presets.take(QUICK_PRESETS),
            library.items.value.take(RECENT_DOWNLOADS),
        ),
    )

    fun find(id: String): ConversionItem? = conversions.find(id)

    fun onPrimary() {
        if (conversions.running.value) conversions.pause() else conversions.start()
    }

    fun clearDone() = conversions.clearDone()

    fun applyPreset(preset: Preset) {
        val count = conversions.applyPreset(preset)
        messages.show(ToastEvent.PresetApplied(preset.name, count, preset.sources))
    }

    /** Adiciona os arquivos que o app converte; avisa quantos ficaram de fora. */
    fun addPicked(files: List<PickedFile>, location: String) {
        if (files.isEmpty()) return
        val items = files.mapNotNull {
            ConversionItem.create(
                id = conversions.newId(),
                name = it.name,
                sizeBytes = it.sizeBytes,
                location = location,
                source = ConversionSource.Document(it.uri),
                capabilities = capabilities,
            )
        }
        conversions.add(items)
        val unsupported = files.size - items.size
        messages.show(if (unsupported > 0) ToastEvent.FilesUnsupported(unsupported) else ToastEvent.FilesAdded(items.size))
    }

    fun retry(itemId: String) = conversions.retry(itemId)

    fun setTarget(itemId: String, target: OutputFormat) = conversions.setTarget(itemId, target)

    fun setTargetForAll(target: OutputFormat) {
        val count = conversions.setTargetForAll(target)
        messages.show(ToastEvent.TargetSetForAll(count, target.ext))
    }

    fun updateOptions(itemId: String, transform: (ConversionOptions) -> ConversionOptions) =
        conversions.updateOptions(itemId, transform)

    fun applyToSameCategory(itemId: String) {
        val count = conversions.applyToSameCategory(itemId)
        messages.show(ToastEvent.OptionsApplied(count))
    }

    fun remove(itemId: String) = conversions.remove(itemId)

    companion object {
        private const val QUICK_PRESETS = 4
        private const val RECENT_DOWNLOADS = 4

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = convertiaContainer()
                ConvertViewModel(c.conversions, c.library, c.messages, c.presets, c.capabilities)
            }
        }
    }
}
