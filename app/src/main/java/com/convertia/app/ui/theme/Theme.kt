package com.convertia.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// O design é só escuro e usa as cores da marca, então não há tema claro
// nem cores dinâmicas (Material You).
private val ConvertiaColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = Ink,
    onBackground = TextPrimary,
    surface = Ink,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    outline = Stroke,
    outlineVariant = Hairline,
    error = Danger,
    onError = TextPrimary,
)

@Composable
fun ConvertiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ConvertiaColors,
        typography = ConvertiaTypography,
        content = content,
    )
}
