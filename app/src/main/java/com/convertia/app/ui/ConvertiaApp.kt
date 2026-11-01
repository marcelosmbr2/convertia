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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.convertia.app.R
import com.convertia.app.ui.components.ScreenHeader
import com.convertia.app.ui.download.DownloadScreen
import com.convertia.app.ui.components.SheetHost
import com.convertia.app.ui.components.ToastHost
import com.convertia.app.ui.theme.Palette
import com.convertia.app.ui.theme.TextStyles

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
        Tab.CONVERT -> PlaceholderScreen(R.string.tab_convert, R.string.convert_subtitle)
        Tab.QUEUE -> PlaceholderScreen(R.string.tab_queue, R.string.queue_subtitle)
        Tab.LIBRARY -> PlaceholderScreen(R.string.tab_library, R.string.library_subtitle)
        Tab.SETTINGS -> PlaceholderScreen(R.string.tab_settings, R.string.settings_subtitle)
    }
}

@Composable
private fun ColumnScope.SheetContent(sheet: AppSheet, appViewModel: AppViewModel) {
    Text(sheet.toString(), style = TextStyles.RowTitle, modifier = Modifier.padding(20.dp))
}

/** Tela provisória, substituída tela a tela. */
@Composable
private fun PlaceholderScreen(title: Int, subtitle: Int) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(stringResource(title), stringResource(subtitle))
    }
}
