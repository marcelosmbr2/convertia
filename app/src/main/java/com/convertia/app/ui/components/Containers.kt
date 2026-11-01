package com.convertia.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convertia.app.ui.theme.BaseTextStyle
import com.convertia.app.ui.theme.MonoTextStyle
import com.convertia.app.ui.theme.Palette
import com.convertia.app.ui.theme.TextStyles

/**
 * Cabeçalho das abas: título grande e subtítulo sobre a grade de pontos. Sangra até as
 * bordas da tela, como no mockup (margem −8 −16 0, padding 28 20 10).
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .dotGrid()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = TextStyles.ScreenTitle, modifier = Modifier.semantics { heading() })
        subtitle()
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    ScreenHeader(title, modifier) { Text(subtitle, style = TextStyles.ScreenSubtitle) }
}

/** Rótulo de seção em maiúsculas. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextTertiary) {
    Text(
        text = text.uppercase(),
        style = TextStyles.SectionLabel.copy(color = color),
        modifier = modifier
            .padding(horizontal = 4.dp)
            .semantics { heading() },
    )
}

/** Cartão padrão: fundo #141417, borda #222227, raio 16. */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    background: Color = Palette.Card,
    border: Color = Palette.Border,
    radius: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape),
        content = content,
    )
}

/** Barra de progresso fina (4 dp) com largura animada como o `transition: width .3s linear`. */
@Composable
fun ThinProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    track: Color = Palette.Track,
    height: Dp = 4.dp,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300, easing = LinearEasing), label = "progress")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track)
            .progressSemantics(progress.coerceIn(0f, 1f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .background(color),
        )
    }
}

/** Bloco com a extensão embaixo (arquivos do Converter, Fila, Histórico). */
@Composable
fun FileTypeTile(
    ext: String,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    radius: Dp = 7.dp,
    fontSize: TextUnit = 8.5.sp,
    bottomPadding: Dp = 5.dp,
    borderAlpha: Float = 0.28f,
    tint: Color = Palette.TextSecondary,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .size(width, height)
            .clip(shape)
            .background(tint.copy(alpha = 0.08f))
            .border(1.dp, tint.copy(alpha = borderAlpha), shape)
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = ext.uppercase(),
            style = BaseTextStyle.copy(fontSize = fontSize, fontWeight = FontWeight.Bold, letterSpacing = 0.02.sp, color = tint),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Miniatura listrada que o mockup usa no lugar de capas de vídeo. */
@Composable
fun StripedThumbnail(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    stripe: Dp = 8.dp,
    border: Color? = Palette.BorderSubtle,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Palette.StripeA)
            .drawBehind {
                // Listras a 135°: faixas alternadas de largura [stripe] medida na diagonal.
                val step = stripe.toPx() * 1.4142f
                val diagonal = size.width + size.height
                var x = -size.height
                var i = 0
                while (x < diagonal) {
                    if (i % 2 == 1) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(x, size.height)
                            lineTo(x + size.height, 0f)
                            lineTo(x + size.height + step, 0f)
                            lineTo(x + step, size.height)
                            close()
                        }
                        drawPath(path, Palette.StripeB)
                    }
                    x += step
                    i++
                }
            }
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier),
        content = content,
    )
}

/** Selo de duração sobre a miniatura ("18:42"). */
@Composable
fun BoxScope.DurationBadge(text: String, fontSize: TextUnit = 10.5.sp, inset: Dp = 5.dp) {
    Text(
        text = text,
        style = MonoTextStyle.copy(fontSize = fontSize, color = Palette.TextPrimary),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = inset, bottom = inset)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Barra fixa acima da navegação (#0c0c0e com linha no topo). */
@Composable
fun BottomActionBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.BackgroundDeep)
            .drawBehind { drawLine(Palette.DividerStrong, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) },
    ) { content() }
}
