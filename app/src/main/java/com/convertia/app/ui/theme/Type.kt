package com.convertia.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.convertia.app.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/** Recursos do Inter usados no mockup: "a" de um andar e dígitos abertos. */
private const val InterFeatures = "'cv11', 'ss01'"

/**
 * Números tabulares, para textos com números que mudam (progresso, velocidade). Não vai no
 * estilo base porque, no Inter, o `tnum` também alarga o hífen ("e - books").
 */
const val TabularFeatures = "'cv11', 'ss01', 'tnum'"

private val CenteredLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** Estilo base: Inter 14 em texto principal, como o corpo do mockup. */
val BaseTextStyle = TextStyle(
    fontFamily = Inter,
    fontSize = 14.sp,
    fontWeight = FontWeight.Normal,
    color = Palette.TextPrimary,
    fontFeatureSettings = InterFeatures,
    lineHeightStyle = CenteredLineHeight,
)

/** Estilo base para URLs, durações e extensões. */
val MonoTextStyle = BaseTextStyle.copy(fontFamily = JetBrainsMono, fontFeatureSettings = null)

/** Estilos que se repetem em várias telas do mockup. */
object TextStyles {
    /** Título grande das abas ("Baixar", "Converter"…). */
    val ScreenTitle = BaseTextStyle.copy(
        fontSize = 30.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.03).em,
    )

    /** Subtítulo sob o título das abas. */
    val ScreenSubtitle = BaseTextStyle.copy(
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = Palette.TextSecondary,
    )

    /** Rótulo de seção em maiúsculas ("RECENTES", "SALVAR COMO"). */
    val SectionLabel = BaseTextStyle.copy(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.06.em,
        color = Palette.TextTertiary,
    )

    /** Título de bottom sheet. */
    val SheetTitle = BaseTextStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

    /** Título de linha de lista. */
    val RowTitle = BaseTextStyle.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Medium)

    /** Texto secundário de linha de lista. */
    val RowMeta = BaseTextStyle.copy(fontSize = 12.sp, color = Palette.TextTertiary)

    /** Texto de botão. */
    val Button = BaseTextStyle.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
}

internal val ConvertiaTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Inter),
        displayMedium = displayMedium.copy(fontFamily = Inter),
        displaySmall = displaySmall.copy(fontFamily = Inter),
        headlineLarge = TextStyles.ScreenTitle,
        headlineMedium = headlineMedium.copy(fontFamily = Inter),
        headlineSmall = headlineSmall.copy(fontFamily = Inter),
        titleLarge = titleLarge.copy(fontFamily = Inter),
        titleMedium = titleMedium.copy(fontFamily = Inter),
        titleSmall = titleSmall.copy(fontFamily = Inter),
        bodyLarge = bodyLarge.copy(fontFamily = Inter),
        bodyMedium = BaseTextStyle,
        bodySmall = bodySmall.copy(fontFamily = Inter),
        labelLarge = labelLarge.copy(fontFamily = Inter),
        labelMedium = labelMedium.copy(fontFamily = Inter),
        labelSmall = labelSmall.copy(fontFamily = Inter),
    )
}
