package com.convertia.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.convertia.app.ui.AppViewModel
import com.convertia.app.ui.ConvertiaApp
import com.convertia.app.ui.Tab
import com.convertia.app.ui.onboarding.OnboardingScreen
import com.convertia.app.ui.theme.ConvertiaTheme
import com.convertia.app.ui.theme.Palette

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // O app é sempre escuro, então os ícones das barras do sistema ficam claros
        // independentemente do tema do aparelho.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleIntent(intent)
        addOnNewIntentListener(::handleIntent)
        setContent {
            ConvertiaTheme {
                ConvertiaRoot(appViewModel)
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        intent.getStringExtra(EXTRA_INSPECT_URL)?.let(appViewModel::openLink)
        if (intent.getBooleanExtra(EXTRA_OPEN_QUEUE, false)) appViewModel.selectTab(Tab.QUEUE)
        if (intent.hasExtra(EXTRA_OPEN_LIBRARY)) {
            appViewModel.openLibrary(history = intent.getBooleanExtra(EXTRA_OPEN_LIBRARY, false))
        }
    }

    companion object {
        private const val EXTRA_INSPECT_URL = "com.convertia.app.extra.INSPECT_URL"
        private const val EXTRA_OPEN_QUEUE = "com.convertia.app.extra.OPEN_QUEUE"
        private const val EXTRA_OPEN_LIBRARY = "com.convertia.app.extra.OPEN_LIBRARY"

        /** Abre o app na aba Baixar já inspecionando [url]. */
        fun inspectIntent(context: Context, url: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_INSPECT_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Abre o app na Biblioteca (em Histórico, com [history]). */
        fun libraryIntent(context: Context, history: Boolean): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_LIBRARY, history)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Abre o app na Fila. */
        fun queueIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_QUEUE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

/** Onboarding ou app; nada (só o fundo) enquanto os ajustes carregam. */
@Composable
private fun ConvertiaRoot(appViewModel: AppViewModel) {
    val onboarded by appViewModel.onboarded.collectAsStateWithLifecycle()
    when (onboarded) {
        null -> Box(Modifier.fillMaxSize().background(Palette.Background))
        false -> OnboardingScreen(onFinish = appViewModel::completeOnboarding)
        true -> ConvertiaApp(appViewModel)
    }
}
