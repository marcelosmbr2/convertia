package com.convertia.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convertia.app.ui.theme.BaseTextStyle
import com.convertia.app.ui.theme.Palette
import kotlinx.coroutines.delay

/** Conteúdo pronto para exibir: texto e, opcionalmente, o rótulo da ação. */
data class ToastContent(val id: Long, val text: String, val actionLabel: String?)

private const val TOAST_DURATION_MS = 3_600L

/**
 * Toast claro do mockup (fundo #e6e6ea, raio 10). Some sozinho depois de 3,6 s — ou mais,
 * se o usuário pediu mais tempo nas opções de acessibilidade.
 */
@Composable
fun ToastHost(
    toast: ToastContent?,
    onAction: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var last by remember { mutableStateOf(toast) }
    if (toast != null) last = toast
    val accessibility = LocalAccessibilityManager.current

    if (toast != null) {
        LaunchedEffect(toast.id) {
            val timeout = accessibility?.calculateRecommendedTimeoutMillis(
                TOAST_DURATION_MS,
                containsIcons = false,
                containsText = true,
                containsControls = toast.actionLabel != null,
            ) ?: TOAST_DURATION_MS
            delay(timeout)
            onDismiss(toast.id)
        }
    }

    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 },
        exit = fadeOut(tween(150)),
    ) {
        val current = last ?: return@AnimatedVisibility
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .shadow(24.dp, RoundedCornerShape(10.dp), ambientColor = Palette.Background, spotColor = Palette.Background)
                .clip(RoundedCornerShape(10.dp))
                .background(Palette.ToastBackground)
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = current.text,
                style = BaseTextStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Palette.ToastText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (current.actionLabel != null) {
                TextPillButton(
                    text = current.actionLabel,
                    onClick = { onAction(current.id) },
                    color = Palette.ToastText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    height = 40.dp,
                    underline = true,
                )
            }
        }
    }
}
