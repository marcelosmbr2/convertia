package com.convertia.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.convertia.app.ui.theme.BaseTextStyle
import com.convertia.app.ui.theme.ConvertiaIcons
import com.convertia.app.ui.theme.CvIcon
import com.convertia.app.ui.theme.CvIconSpec
import com.convertia.app.ui.theme.Palette

/**
 * Campo de texto em pílula do mockup (busca da Biblioteca, do seletor de formato e o campo
 * de link do Baixar). [trailing] ocupa o fim do campo (botão Colar/Limpar).
 */
@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: CvIconSpec? = ConvertiaIcons.Search,
    leadingIconSize: Dp = 17.dp,
    leadingIconStroke: Float = 1.8f,
    height: Dp = 48.dp,
    radius: Dp = 24.dp,
    background: Color = Palette.Card,
    border: Color? = Palette.BorderSheet,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp),
    textStyle: TextStyle = BaseTextStyle,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(radius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leadingIcon != null) {
            CvIcon(leadingIcon, contentDescription = null, size = leadingIconSize, tint = Palette.TextMeta2, strokeWidth = leadingIconStroke)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = textStyle.copy(color = Palette.TextMuted), maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle.copy(color = Palette.TextPrimary),
                cursorBrush = SolidColor(Palette.TextPrimary),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        trailing?.invoke()
    }
}
