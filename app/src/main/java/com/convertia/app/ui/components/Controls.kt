package com.convertia.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convertia.app.ui.theme.BaseTextStyle
import com.convertia.app.ui.theme.Palette
import com.convertia.app.ui.theme.TextStyles

/** Tamanhos do controle segmentado do mockup. */
enum class SegmentedSize(
    val height: Dp,
    val radius: Dp,
    val containerRadius: Dp,
    val fontSize: TextUnit,
    val containerColor: Color,
) {
    /** Vídeo/Somente áudio, Arquivos/Histórico. */
    Large(40.dp, 11.dp, 14.dp, 14.sp, Palette.Card),

    /** Opções em Ajustes e no inspetor. */
    Small(36.dp, 9.dp, 12.dp, 13.sp, Palette.Background),
}

/** Controle segmentado: item selecionado em #2a2a30, os outros transparentes. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    size: SegmentedSize = SegmentedSize.Small,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(size.containerRadius))
            .background(size.containerColor)
            .border(1.dp, Palette.BorderSubtle, RoundedCornerShape(size.containerRadius))
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(size.height)
                    .clip(RoundedCornerShape(size.radius))
                    .background(if (isSelected) Palette.BorderStrong else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = BaseTextStyle.copy(fontSize = size.fontSize, fontWeight = FontWeight.Medium),
                    color = if (isSelected) Palette.TextPrimary else Palette.TextMeta,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Chip selecionável: selecionado em branco com texto escuro ("Salvar como", categorias do
 * seletor de formato).
 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    radius: Dp = 10.dp,
    minWidth: Dp = 64.dp,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    unselectedBackground: Color = Palette.Card,
    unselectedText: Color = Palette.TextEmphasis,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minWidth)
            .height(height)
            .clip(shape)
            .background(if (selected) Palette.TextPrimary else unselectedBackground)
            .border(1.dp, if (selected) Palette.TextPrimary else Palette.BorderStrong, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = BaseTextStyle.copy(fontSize = fontSize, fontWeight = fontWeight),
            color = if (selected) Palette.Background else unselectedText,
            maxLines = 1,
        )
    }
}

/** Interruptor do mockup (44×26). Só desenha; a linha inteira é que recebe o toque. */
@Composable
fun ConvertiaSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) Palette.TextPrimary else Palette.BorderStrong, tween(150), label = "track")
    val knob by animateColorAsState(if (checked) Palette.Background else Palette.TextMeta, tween(150), label = "knob")
    val x by animateDpAsState(if (checked) 21.dp else 3.dp, tween(150), label = "knobX")
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(track),
    ) {
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 3.dp.roundToPx()) }
                .size(20.dp)
                .clip(CircleShape)
                .background(knob),
        )
    }
}

/** Linha com título, descrição opcional e interruptor; a linha toda alterna. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    titleSize: TextUnit = 14.5.sp,
    minHeight: Dp = 64.dp,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 10.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TextStyles.RowTitle.copy(fontSize = titleSize))
            if (description != null) {
                Text(description, style = BaseTextStyle.copy(fontSize = 12.5.sp, lineHeight = 17.sp, color = Palette.TextTertiary))
            }
        }
        ConvertiaSwitch(checked)
    }
}

/** Anel de rádio do mockup (20 dp; ponto de 10 dp quando selecionado). */
@Composable
fun RadioMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .border(2.dp, if (selected) Palette.TextPrimary else Palette.Gray44, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.TextPrimary))
    }
}

/** Selo pequeno ao lado de um título ("Recomendado", "Melhor"). */
@Composable
fun SmallBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = BaseTextStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Palette.Raised3)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        maxLines = 1,
    )
}

/**
 * Linha de lista com rádio (formatos do Baixar, opções do Compartilhar). Use dentro de
 * [ListCard]; [showDivider] desenha a linha fina acima a partir da segunda linha.
 */
@Composable
fun RadioRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    showDivider: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier) {
        if (showDivider) RowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .background(if (selected) Palette.Raised else Color.Transparent)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RadioMark(selected)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = TextStyles.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        SmallBadge(badge)
                    }
                }
                Text(
                    subtitle,
                    style = BaseTextStyle.copy(fontSize = 12.5.sp, color = Palette.TextTertiary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke(this)
        }
    }
}

/** Linha divisória fina entre itens de uma lista. */
@Composable
fun RowDivider(modifier: Modifier = Modifier, color: Color = Palette.Divider) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}
