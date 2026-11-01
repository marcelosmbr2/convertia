package com.convertia.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convertia.app.ui.theme.CvIcon
import com.convertia.app.ui.theme.CvIconSpec
import com.convertia.app.ui.theme.Palette
import com.convertia.app.ui.theme.TextStyles

/** Base dos botões em pílula do mockup. */
@Composable
fun PillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(height / 2),
    background: Color = Color.Transparent,
    pressedBackground: Color = background,
    border: BorderStroke? = null,
    contentColor: Color = Palette.TextPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp),
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(height)
            .clip(shape)
            .background(if (pressed) pressedBackground else background)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = { content() })
    }
}

/** Botão principal laranja (48 dp, raio 24). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: CvIconSpec? = null,
    iconSize: Dp = 17.dp,
    iconStroke: Float = 2.2f,
    height: Dp = 48.dp,
    textStyle: TextStyle = TextStyles.Button,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp),
) {
    PillButton(
        onClick = onClick,
        modifier = modifier.alpha(if (enabled) 1f else 0.45f),
        height = height,
        background = Palette.Accent,
        pressedBackground = Palette.AccentPressed,
        contentColor = Palette.OnAccent,
        contentPadding = contentPadding,
        enabled = enabled,
    ) {
        if (icon != null) CvIcon(icon, contentDescription = null, size = iconSize, strokeWidth = iconStroke)
        Text(text, style = textStyle, color = Palette.OnAccent, maxLines = 1)
    }
}

/** Botão cinza com borda ("Mais formatos", "Aplicar a todas as imagens"). */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    background: Color = Palette.Raised2,
    textStyle: TextStyle = TextStyles.Button.copy(fontSize = 14.sp),
    icon: CvIconSpec? = null,
) {
    PillButton(
        onClick = onClick,
        modifier = modifier,
        height = height,
        background = background,
        pressedBackground = Palette.Raised3,
        border = BorderStroke(1.dp, Palette.BorderStrong),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        if (icon != null) CvIcon(icon, contentDescription = null, size = 15.dp)
        Text(text, style = textStyle, maxLines = 1)
    }
}

/** Botão pequeno com borda ("Verificar", "Aplicar"). */
@Composable
fun SmallOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PillButton(
        onClick = onClick,
        modifier = modifier,
        height = 40.dp,
        background = Palette.Raised,
        pressedBackground = Palette.Raised2,
        border = BorderStroke(1.dp, Palette.BorderStrong),
    ) {
        Text(text, style = TextStyles.Button.copy(fontSize = 13.sp), maxLines = 1)
    }
}

/** Botão de texto sem fundo ("Limpar", "Pausar tudo", "Inspecionar"). */
@Composable
fun TextPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Palette.TextPrimary,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    height: Dp = 36.dp,
    horizontalPadding: Dp = 12.dp,
    underline: Boolean = false,
) {
    PillButton(
        onClick = onClick,
        modifier = modifier,
        height = height,
        contentColor = color,
        contentPadding = PaddingValues(horizontal = horizontalPadding),
    ) {
        Text(
            text,
            style = TextStyles.Button.copy(
                fontSize = fontSize,
                fontWeight = fontWeight,
                textDecoration = if (underline) TextDecoration.Underline else null,
            ),
            color = color,
            maxLines = 1,
        )
    }
}

/** Botão redondo só com ícone; [contentDescription] é obrigatório para o TalkBack. */
@Composable
fun IconCircleButton(
    icon: CvIconSpec,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 16.dp,
    strokeWidth: Float = 1.8f,
    tint: Color = Palette.TextSecondary,
    background: Color = Color.Transparent,
    border: BorderStroke? = null,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        CvIcon(icon, contentDescription = null, size = iconSize, tint = tint, strokeWidth = strokeWidth)
    }
}
