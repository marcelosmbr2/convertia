package com.convertia.app.share

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.convertia.app.ConvertiaApplication
import com.convertia.app.MainActivity
import com.convertia.app.R
import com.convertia.app.domain.firstUrlIn
import com.convertia.app.ui.components.SheetHost
import com.convertia.app.ui.components.ToastContent
import com.convertia.app.ui.components.ToastHost
import com.convertia.app.ui.theme.ConvertiaTheme
import kotlinx.coroutines.delay

/**
 * Alvo do menu Compartilhar: uma janela transparente com o sheet "Baixar este vídeo" por
 * cima do app de origem. O download entra na mesma fila do app e continua depois que esta
 * tela fecha.
 */
class ShareActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by viewModels {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }
        val container = (application as ConvertiaApplication).container
        viewModelFactory {
            initializer {
                ShareViewModel(firstUrlIn(text), subject, container.mediaInspector, container.downloads) {
                    container.settings.value.downloadOptions
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            ConvertiaTheme {
                ShareScreen(
                    viewModel = viewModel,
                    onMoreFormats = { url ->
                        startActivity(MainActivity.inspectIntent(this, url))
                        finish()
                    },
                    onOpenQueue = {
                        startActivity(MainActivity.queueIntent(this))
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }
}

/** Toast local: some com a tela, sem passar pela fila de mensagens do app. */
private sealed interface ShareToast {
    data class Queued(val formatLabel: String) : ShareToast
    data object NoLink : ShareToast
}

@Composable
private fun ShareScreen(
    viewModel: ShareViewModel,
    onMoreFormats: (String) -> Unit,
    onOpenQueue: () -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetVisible by remember { mutableStateOf(state != ShareState.NoLink) }
    var toast by remember { mutableStateOf<ShareToast?>(if (state == ShareState.NoLink) ShareToast.NoLink else null) }

    // Sem sheet nem toast: espera a animação de saída e fecha.
    LaunchedEffect(sheetVisible, toast) {
        if (!sheetVisible && toast == null) {
            delay(220)
            onClose()
        }
    }
    BackHandler { if (sheetVisible) sheetVisible = false else onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                // Toque fora com só o toast na tela: fecha na hora.
                if (!sheetVisible) onClose()
            },
    ) {
        SheetHost(
            value = state.takeIf { sheetVisible },
            onDismiss = { sheetVisible = false },
            paneTitle = stringResource(R.string.share_title),
        ) { current ->
            ShareSheetContent(
                state = current,
                onSelect = viewModel::select,
                onMoreFormats = {
                    val url = when (current) {
                        is ShareState.Ready -> current.media.url
                        is ShareState.Loading -> current.url
                        is ShareState.Failed -> current.url
                        ShareState.NoLink -> null
                    }
                    if (url != null) onMoreFormats(url) else onClose()
                },
                onDownload = {
                    viewModel.download()?.let { label ->
                        toast = ShareToast.Queued(label)
                        sheetVisible = false
                    }
                },
            )
        }

        val content = when (val t = toast) {
            is ShareToast.Queued -> ToastContent(1, stringResource(R.string.toast_shared_download_queued, t.formatLabel), stringResource(R.string.action_open))
            ShareToast.NoLink -> ToastContent(2, stringResource(R.string.toast_no_link_shared), null)
            null -> null
        }
        ToastHost(
            toast = content,
            onAction = { onOpenQueue() },
            onDismiss = { toast = null },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 20.dp),
        )
    }
}
