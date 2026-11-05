package com.recast.app.ui.download

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Procura um link na área de transferência quando a janela ganha foco (o Android 10+ só
 * entrega o conteúdo com foco). Lê o conteúdo só quando o recorte mudou — cada leitura
 * dispara o aviso "colado" do Android 12+ — e, no 12+, pula recortes que o sistema já
 * classificou como sem link.
 */
@Composable
fun ClipboardLinkWatcher(
    enabled: Boolean,
    shouldRead: (stamp: Long) -> Boolean,
    onRead: (text: String?, stamp: Long) -> Unit,
) {
    val context = LocalContext.current
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(enabled, focused) {
        if (!enabled || !focused) return@LaunchedEffect
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@LaunchedEffect
        val description = clipboard.primaryClipDescription ?: return@LaunchedEffect
        val stamp = description.timestamp
        if (!shouldRead(stamp)) return@LaunchedEffect
        if (!description.mayContainText() || description.classifiedWithoutUrl()) {
            onRead(null, stamp)
            return@LaunchedEffect
        }
        onRead(readClipboardText(context), stamp)
    }
}

/** Texto atual da área de transferência (dispara o aviso "colado" do Android 12+). */
fun readClipboardText(context: Context): String? = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull()

private fun ClipDescription.mayContainText(): Boolean =
    hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
        hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) ||
        hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)

private fun ClipDescription.classifiedWithoutUrl(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE &&
        getConfidenceScore(TextClassifier.TYPE_URL) == 0f
