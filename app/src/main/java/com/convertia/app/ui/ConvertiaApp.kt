package com.convertia.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.convertia.app.R
import com.convertia.app.ui.components.SheetHost
import com.convertia.app.ui.components.ToastHost
import com.convertia.app.ui.convert.AddFilesSheet
import com.convertia.app.ui.convert.ConvertNavigation
import com.convertia.app.ui.convert.ConvertScreen
import com.convertia.app.ui.convert.ConvertViewModel
import com.convertia.app.ui.convert.FormatPickerSheet
import com.convertia.app.ui.convert.InspectorActions
import com.convertia.app.ui.convert.InspectorSheet
import com.convertia.app.ui.convert.PickerActions
import com.convertia.app.ui.download.DownloadScreen
import com.convertia.app.ui.library.LibraryItemActions
import com.convertia.app.ui.library.LibraryItemSheet
import com.convertia.app.ui.library.LibraryScreen
import com.convertia.app.ui.library.LibraryViewModel
import com.convertia.app.ui.queue.QueueScreen
import com.convertia.app.ui.settings.PresetsScreen
import com.convertia.app.ui.settings.SettingsScreen
import com.convertia.app.ui.settings.SettingsViewModel
import com.convertia.app.ui.theme.Palette

/** App principal: aba atual, barra inferior, bottom sheet e toast. */
@Composable
fun ConvertiaApp(appViewModel: AppViewModel) {
    val nav by appViewModel.nav.collectAsStateWithLifecycle()
    val queueBadge by appViewModel.queueBadge.collectAsStateWithLifecycle()
    val toast by appViewModel.toast.collectAsStateWithLifecycle()

    BackHandler(enabled = nav.canGoBack) { appViewModel.goBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .semantics { if (nav.sheet != null) hideFromAccessibility() },
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .statusBarsPadding(),
            ) {
                TabContent(nav, appViewModel)
            }
            BottomNavBar(current = nav.tab, queueBadge = queueBadge, onSelect = appViewModel::selectTab)
        }

        SheetHost(
            value = nav.sheet,
            onDismiss = appViewModel::closeSheet,
            paneTitle = stringResource(R.string.app_name),
        ) { sheet ->
            SheetContent(sheet, appViewModel)
        }

        val toastInsets = if (nav.sheet != null) WindowInsets.navigationBars.union(WindowInsets.ime) else WindowInsets.navigationBars
        ToastHost(
            toast = toast?.toContent(),
            onAction = { id -> toast?.takeIf { it.id == id }?.let(appViewModel::onToastAction) },
            onDismiss = appViewModel::dismissToast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(toastInsets)
                .padding(bottom = if (nav.sheet != null) 16.dp else 92.dp),
        )
    }
}

@Composable
private fun TabContent(nav: NavState, appViewModel: AppViewModel) {
    when (nav.tab) {
        Tab.DOWNLOAD -> DownloadScreen()
        Tab.CONVERT -> ConvertScreen(
            navigation = ConvertNavigation(
                openItem = { appViewModel.openSheet(AppSheet.Inspector(it)) },
                openPicker = { appViewModel.openFormatPicker(it) },
                openAddFiles = { appViewModel.openSheet(AppSheet.AddFiles) },
            ),
        )
        Tab.QUEUE -> QueueScreen(onConvertDownload = appViewModel::convertDownload)
        Tab.LIBRARY -> LibraryScreen(
            view = nav.libraryView,
            onViewChange = appViewModel::showLibraryView,
            onOpenItem = { appViewModel.openSheet(AppSheet.LibraryActions(it)) },
        )
        Tab.SETTINGS -> if (nav.presetsOpen) {
            val convert: ConvertViewModel = viewModel(factory = ConvertViewModel.Factory)
            val settings: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            PresetsScreen(
                presets = settings.presets,
                onBack = appViewModel::closePresets,
                onApply = { preset ->
                    convert.applyPreset(preset)
                    appViewModel.selectTab(Tab.CONVERT)
                },
            )
        } else {
            SettingsScreen(
                onOpenPresets = appViewModel::openPresets,
                onRestartOnboarding = appViewModel::restartOnboarding,
            )
        }
    }
}

@Composable
private fun ColumnScope.SheetContent(sheet: AppSheet, appViewModel: AppViewModel) {
    val convert: ConvertViewModel = viewModel(factory = ConvertViewModel.Factory)
    val convertUi by convert.uiState.collectAsStateWithLifecycle()
    when (sheet) {
        AppSheet.AddFiles -> AddFilesSheet(
            recentDownloads = convertUi.recentDownloads,
            onPicked = { files, location ->
                if (files.isNotEmpty()) {
                    convert.addPicked(files, location)
                    appViewModel.selectTab(Tab.CONVERT)
                }
            },
            onAddDownload = appViewModel::convertLibraryItem,
        )
        is AppSheet.Inspector -> {
            val item = convertUi.items.firstOrNull { it.id == sheet.itemId } ?: return
            InspectorSheet(
                item = item,
                actions = InspectorActions(
                    onPickFormat = { appViewModel.openFormatPicker(item.id, fromInspector = true) },
                    onOptions = { transform -> convert.updateOptions(item.id, transform) },
                    onApplyToAll = {
                        convert.applyToSameCategory(item.id)
                        appViewModel.closeSheet()
                    },
                    onRemove = { convert.remove(item.id) },
                    onDone = appViewModel::closeSheet,
                ),
            )
        }
        is AppSheet.FormatPicker -> FormatPickerSheet(
            sheet = sheet,
            items = convertUi.items,
            actions = PickerActions(
                onBack = { appViewModel.goBack() },
                onQuery = appViewModel::updatePickerQuery,
                onCategory = appViewModel::selectPickerCategory,
                onChoose = { format ->
                    if (sheet.itemId != null) convert.setTarget(sheet.itemId, format) else convert.setTargetForAll(format)
                    appViewModel.finishPicker()
                },
            ),
        )
        is AppSheet.LibraryActions -> {
            val library: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
            val libraryUi by library.uiState.collectAsStateWithLifecycle()
            val item = libraryUi.items.firstOrNull { it.id == sheet.itemId } ?: return
            LibraryItemSheet(
                item = item,
                actions = LibraryItemActions(
                    onConvert = { appViewModel.convertLibraryItem(item.id) },
                    onPlay = {
                        appViewModel.closeSheet()
                        library.openInPlayer()
                    },
                    onShare = {
                        appViewModel.closeSheet()
                        library.share()
                    },
                    onDelete = { library.delete(item.id) },
                ),
            )
        }
    }
}
