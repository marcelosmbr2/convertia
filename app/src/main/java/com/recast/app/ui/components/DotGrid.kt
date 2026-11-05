package com.recast.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.recast.app.ui.theme.Palette
import kotlin.math.sqrt

/**
 * Fundo de grade de pontos do mockup (`radial-gradient(#24242a 1px, transparent 1.2px)`
 * em blocos de 18 px), apagado por uma máscara elíptica.
 *
 * A elipse tem centro em ([centerX], [centerY]) e raios ([radiusX], [radiusY]), tudo em
 * frações do tamanho do elemento. A opacidade vai de [innerAlpha] no centro a zero em
 * [fadeEnd] (fração do raio), com [solidUntil] mantendo a opacidade cheia até ali.
 */
fun Modifier.dotGrid(
    centerX: Float = 0.7f,
    centerY: Float = 0.4f,
    radiusX: Float = 0.9f,
    radiusY: Float = 0.8f,
    innerAlpha: Float = 0.9f,
    solidUntil: Float = 0f,
    fadeEnd: Float = 0.75f,
    color: Color = Palette.BorderSubtle,
): Modifier = drawWithCache {
    val spacing = 18.dp.toPx()
    val dotRadius = 1.1.dp.toPx()
    val width = size.width
    val height = size.height
    val cx = width * centerX
    val cy = height * centerY
    val rx = width * radiusX
    val ry = height * radiusY
    val dots = buildList {
        var y = spacing / 2
        while (y < height) {
            var x = spacing / 2
            while (x < width) {
                val dx = (x - cx) / rx
                val dy = (y - cy) / ry
                val d = sqrt(dx * dx + dy * dy)
                val alpha = when {
                    d <= solidUntil -> innerAlpha
                    d >= fadeEnd -> 0f
                    else -> innerAlpha * (1 - (d - solidUntil) / (fadeEnd - solidUntil))
                }
                if (alpha > 0.01f) add(Offset(x, y) to alpha)
                x += spacing
            }
            y += spacing
        }
    }
    onDrawBehind {
        dots.forEach { (center, alpha) -> drawCircle(color.copy(alpha = alpha), dotRadius, center) }
    }
}
