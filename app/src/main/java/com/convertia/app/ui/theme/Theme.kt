package com.convertia.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// O design é só escuro e usa as cores da marca, então não há tema claro
// nem cores dinâmicas (Material You). Os componentes próprios leem `Palette`
// direto; o esquema abaixo cobre o que vier pronto do Material 3.
private val ConvertiaColors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.OnAccent,
    secondary = Palette.AccentText,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Background,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.Raised2,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainerLowest = Palette.Background,
    surfaceContainerLow = Palette.Card,
    surfaceContainer = Palette.Card,
    surfaceContainerHigh = Palette.Sheet,
    surfaceContainerHighest = Palette.Raised2,
    outline = Palette.BorderStrong,
    outlineVariant = Palette.DividerStrong,
    error = Palette.Danger,
    onError = Palette.TextPrimary,
)

@Composable
fun ConvertiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ConvertiaColors,
        typography = ConvertiaTypography,
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides BaseTextStyle,
            LocalContentColor provides Palette.TextPrimary,
            content = content,
        )
    }
}
