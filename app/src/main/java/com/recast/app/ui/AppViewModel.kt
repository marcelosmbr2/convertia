package com.recast.app.ui

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.recast.app.AppContainer
import com.recast.app.R
import com.recast.app.RecastApplication
import com.recast.app.data.ToastEvent
import com.recast.app.data.ToastMessage
import com.recast.app.domain.ConversionItem
import com.recast.app.domain.ConversionSource
import com.recast.app.domain.ConversionStatus
import com.recast.app.domain.DownloadStatus
import com.recast.app.domain.MediaCategory
import com.recast.app.domain.MediaKind
import com.recast.app.ui.theme.CvIconSpec
import com.recast.app.ui.theme.RecastIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Abas da barra inferior, na ordem em que aparecem. */
enum class Tab(@param:StringRes val label: Int, val icon: CvIconSpec) {
    DOWNLOAD(R.string.tab_download, RecastIcons.Download),
    CONVERT(R.string.tab_convert, RecastIcons.Convert),
    QUEUE(R.string.tab_queue, RecastIcons.Queue),
    LIBRARY(R.string.tab_library, RecastIcons.Folder),
    SETTINGS(R.string.tab_settings, RecastIcons.Sliders),
}

enum class LibraryView { FILES, HISTORY }

/** Bottom sheets do app. Referenciam itens por id: se o item sumir, o sheet fecha. */
sealed interface AppSheet {
    /** "Adicionar arquivos" (Galeria, Arquivos, Dos seus downloads). */
    data object AddFiles : AppSheet

    /** Inspetor de um arquivo do Converter. */
    data class Inspector(val itemId: String) : AppSheet

    /**
     * "Converter para": [itemId] nulo aplica a todos os arquivos compatíveis.
     * [backToInspector] mostra a seta de voltar quando veio do inspetor.
     */
    data class FormatPicker(
        val itemId: String?,
        val category: MediaCategory,
        val query: String = "",
        val backToInspector: Boolean = false,
    ) : AppSheet

    /** Ações de um arquivo da Biblioteca. */
    data class LibraryActions(val itemId: String) : AppSheet
}

data class NavState(
    val tab: Tab = Tab.DOWNLOAD,
    /** Ajustes › Predefinições aberto. */
    val presetsOpen: Boolean = false,
    val libraryView: LibraryView = LibraryView.FILES,
    val sheet: AppSheet? = null,
) {
    /** Se o botão voltar tem algo a fazer dentro do app. */
    val canGoBack: Boolean get() = sheet != null || presetsOpen || tab != Tab.DOWNLOAD
}

/**
 * Estado da navegação, compartilhado pela activity: fluxos que cruzam abas (o "Converter"
 * do toast abre o inspetor no Converter, por exemplo) passam por aqui.
 */
class AppViewModel(
    private val container: AppContainer,
    private val saved: SavedStateHandle,
) : ViewModel() {

    private val _nav = MutableStateFlow(restore())
    val nav: StateFlow<NavState> = _nav.asStateFlow()

    /** `null` enquanto os ajustes carregam. */
    val onboarded: StateFlow<Boolean?> =
        container.settingsOrNull.map { it?.onboarded }.stateIn(viewModelScope, SharingStarted.Eagerly, container.settingsOrNull.value?.onboarded)

    val toast: StateFlow<ToastMessage?> = container.messages.current

    /** Número no ícone da Fila (activeCount no mockup). */
    val queueBadge: StateFlow<Int> = combine(
        container.downloads.jobs,
        container.conversions.items,
        container.conversions.running,
    ) { jobs, items, running ->
        val downloads = jobs.count { it.isActive }
        val conversions = if (running) items.count { it.isPending } else 0
        downloads + conversions
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        // Fecha o sheet se o item a que ele se refere deixou de existir.
        viewModelScope.launch {
            combine(_nav, container.conversions.items, container.library.items) { nav, items, library ->
                when (val sheet = nav.sheet) {
                    is AppSheet.Inspector -> items.none { it.id == sheet.itemId }
                    is AppSheet.FormatPicker -> sheet.itemId != null && items.none { it.id == sheet.itemId }
                    is AppSheet.LibraryActions -> library.none { it.id == sheet.itemId }
                    else -> false
                }
            }.collect { stale -> if (stale) closeSheet() }
        }
    }

    fun selectTab(tab: Tab) = setNav { NavState(tab = tab, libraryView = it.libraryView) }

    fun showLibraryView(view: LibraryView) = setNav { it.copy(libraryView = view) }

    /** Abre a Biblioteca já na visão pedida (notificação de conclusão). */
    fun openLibrary(history: Boolean) =
        setNav { NavState(tab = Tab.LIBRARY, libraryView = if (history) LibraryView.HISTORY else LibraryView.FILES) }

    fun openPresets() = setNav { it.copy(presetsOpen = true, sheet = null) }

    fun closePresets() = setNav { it.copy(presetsOpen = false) }

    fun openSheet(sheet: AppSheet) = setNav { it.copy(sheet = sheet) }

    fun closeSheet() = setNav { it.copy(sheet = null) }

    /** Abre o seletor de formato para um arquivo (ou para todos, com [itemId] nulo). */
    fun openFormatPicker(itemId: String?, fromInspector: Boolean = false) {
        val items = container.conversions.items.value
        val category = if (itemId != null) {
            val item = items.firstOrNull { it.id == itemId } ?: return
            if (item.status == ConversionStatus.CONVERTING) return
            item.target.category
        } else {
            val pickable = items.filter { it.status != ConversionStatus.CONVERTING }
            // Categoria de saída mais comum entre os arquivos editáveis.
            pickable.flatMap { item -> item.targets.map { it.category }.distinct() }
                .groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key ?: MediaCategory.IMAGE
        }
        openSheet(AppSheet.FormatPicker(itemId, category, backToInspector = fromInspector))
    }

    fun updatePickerQuery(query: String) = setNav { nav ->
        val sheet = nav.sheet as? AppSheet.FormatPicker ?: return@setNav nav
        nav.copy(sheet = sheet.copy(query = query))
    }

    fun selectPickerCategory(category: MediaCategory) = setNav { nav ->
        val sheet = nav.sheet as? AppSheet.FormatPicker ?: return@setNav nav
        nav.copy(sheet = sheet.copy(category = category, query = ""))
    }

    /** Depois de escolher um formato: volta ao inspetor se veio de lá; senão fecha. */
    fun finishPicker() = setNav { nav ->
        val sheet = nav.sheet as? AppSheet.FormatPicker
        val back = sheet?.takeIf { it.backToInspector }?.itemId
        nav.copy(sheet = back?.let { AppSheet.Inspector(it) })
    }

    /** Volta um nível: seletor → inspetor → fecha sheet → sai de Predefinições → aba Baixar. */
    fun goBack(): Boolean {
        val nav = _nav.value
        val sheet = nav.sheet
        when {
            sheet is AppSheet.FormatPicker && sheet.backToInspector && sheet.itemId != null ->
                openSheet(AppSheet.Inspector(sheet.itemId))
            sheet != null -> closeSheet()
            nav.presetsOpen -> closePresets()
            nav.tab != Tab.DOWNLOAD -> selectTab(Tab.DOWNLOAD)
            else -> return false
        }
        return true
    }

    fun dismissToast(id: Long) = container.messages.dismiss(id)

    /** Ação do toast (o botão sublinhado à direita). */
    fun onToastAction(message: ToastMessage) {
        container.messages.dismiss(message.id)
        when (val event = message.event) {
            is ToastEvent.DownloadFinished -> convertDownload(event.jobId)
            ToastEvent.ConversionsFinished -> setNav { NavState(tab = Tab.LIBRARY, libraryView = LibraryView.HISTORY) }
            is ToastEvent.DownloadQueued, is ToastEvent.SharedDownloadQueued -> selectTab(Tab.QUEUE)
            is ToastEvent.LibraryItemDeleted -> container.library.restore(event.item, event.index)
            else -> Unit
        }
    }

    /** "Converter" num download concluído: manda o arquivo para o Converter e abre o inspetor. */
    fun convertDownload(jobId: Long) {
        val job = container.downloads.find(jobId) ?: return
        if (job.status != DownloadStatus.DONE) return
        val request = job.request
        val source = job.libraryItemId?.let { ConversionSource.Library(it) } ?: ConversionSource.Sample
        sendToConverter(
            name = "${request.title}.${request.container}",
            sizeBytes = (request.sizeMb * 1e6).toLong(),
            kind = request.kind,
            source = source,
        )
    }

    /** "Converter" num arquivo da Biblioteca. Legendas e miniaturas não têm para onde ir. */
    fun convertLibraryItem(itemId: String) {
        val item = container.library.find(itemId) ?: return
        if (!sendToConverter(item.fileName, item.sizeBytes, item.kind, ConversionSource.Library(item.id))) {
            container.messages.show(ToastEvent.FilesUnsupported(1))
        }
    }

    fun completeOnboarding() = container.updateSettings { it.copy(onboarded = true) }

    fun restartOnboarding() = container.updateSettings { it.copy(onboarded = false) }

    /** Link vindo de fora (menu Compartilhar): abre o Baixar já inspecionando. */
    fun openLink(url: String) {
        container.offerLink(url)
        selectTab(Tab.DOWNLOAD)
    }

    private fun sendToConverter(name: String, sizeBytes: Long, kind: MediaKind, source: ConversionSource): Boolean {
        val conversions = container.conversions
        // Vídeo baixado vai para MP3 por padrão, como no mockup.
        val item = ConversionItem.create(
            id = conversions.newId(),
            name = name,
            sizeBytes = sizeBytes,
            location = DOWNLOAD_FOLDER,
            source = source,
            targetExt = if (kind == MediaKind.VIDEO) "MP3" else null,
            capabilities = container.capabilities,
        ) ?: return false
        conversions.add(listOf(item))
        setNav { NavState(tab = Tab.CONVERT, libraryView = it.libraryView, sheet = AppSheet.Inspector(item.id)) }
        return true
    }

    private inline fun setNav(transform: (NavState) -> NavState) {
        val updated = transform(_nav.value)
        _nav.value = updated
        save(updated)
    }

    private fun save(nav: NavState) {
        saved[KEY_TAB] = nav.tab.name
        saved[KEY_PRESETS] = nav.presetsOpen
        saved[KEY_LIBRARY_VIEW] = nav.libraryView.name
        saved[KEY_SHEET] = nav.sheet?.let(::encode)
    }

    private fun restore(): NavState = NavState(
        tab = saved.get<String>(KEY_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } } ?: Tab.DOWNLOAD,
        presetsOpen = saved.get<Boolean>(KEY_PRESETS) ?: false,
        libraryView = saved.get<String>(KEY_LIBRARY_VIEW)?.let { name -> LibraryView.entries.firstOrNull { it.name == name } }
            ?: LibraryView.FILES,
        sheet = saved.get<String>(KEY_SHEET)?.let(::decode),
    )

    private fun encode(sheet: AppSheet): String = when (sheet) {
        AppSheet.AddFiles -> "add"
        is AppSheet.Inspector -> "inspector|${sheet.itemId}"
        is AppSheet.FormatPicker -> "picker|${sheet.itemId.orEmpty()}|${sheet.category.name}|${sheet.backToInspector}"
        is AppSheet.LibraryActions -> "library|${sheet.itemId}"
    }

    private fun decode(value: String): AppSheet? {
        val parts = value.split('|')
        return when (parts.first()) {
            "add" -> AppSheet.AddFiles
            "inspector" -> parts.getOrNull(1)?.let { AppSheet.Inspector(it) }
            "picker" -> runCatching {
                AppSheet.FormatPicker(
                    itemId = parts[1].ifEmpty { null },
                    category = MediaCategory.valueOf(parts[2]),
                    backToInspector = parts[3].toBoolean(),
                )
            }.getOrNull()
            "library" -> parts.getOrNull(1)?.let { AppSheet.LibraryActions(it) }
            else -> null
        }
    }

    companion object {
        const val DOWNLOAD_FOLDER = "Download/Recast"
        private const val KEY_TAB = "nav.tab"
        private const val KEY_PRESETS = "nav.presets"
        private const val KEY_LIBRARY_VIEW = "nav.libraryView"
        private const val KEY_SHEET = "nav.sheet"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(recastContainer(), createSavedStateHandle()) }
        }
    }
}

/** O [AppContainer] do app, para as fábricas de ViewModel. */
fun CreationExtras.recastContainer(): AppContainer =
    (this[APPLICATION_KEY] as RecastApplication).container
