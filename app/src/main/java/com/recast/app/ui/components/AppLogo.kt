package com.recast.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.recast.app.ui.theme.CvIcon
import com.recast.app.ui.theme.Palette
import com.recast.app.ui.theme.RecastIcons

/**
 * Logo do Recast nas proporções do desktop (`app_icon_image` em recast/ui/icons.py): as
 * setas de conversão ocupam 67% da placa, com traço 1,6 e cantos de 24%.
 */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.24f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Palette.LogoTile)
            .border(1.dp, Palette.LogoBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        CvIcon(RecastIcons.Convert, contentDescription = null, size = size * 0.67f, tint = Palette.LogoArrows, strokeWidth = 1.6f)
    }
}
