package com.convertia.app.ui.onboarding

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.convertia.app.R
import com.convertia.app.ui.components.dotGrid
import com.convertia.app.ui.theme.BaseTextStyle
import com.convertia.app.ui.theme.ConvertiaIcons
import com.convertia.app.ui.theme.CvIcon
import com.convertia.app.ui.theme.CvIconSpec
import com.convertia.app.ui.theme.MonoTextStyle
import com.convertia.app.ui.theme.Palette

/**
 * Área das ilustrações: grade de pontos com máscara elíptica e conteúdo posicionado como no
 * mockup (distâncias a partir do topo). Se a tela for mais baixa que [designHeight], a
 * ilustração encolhe para caber.
 */
@Composable
fun IllustrationArea(designHeight: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .dotGrid(centerX = 0.5f, centerY = 0.45f, radiusX = 0.75f, radiusY = 0.7f, innerAlpha = 1f, solidUntil = 0.4f, fadeEnd = 1f)
            .clearAndSetSemantics {},
    ) {
        val scale = (maxHeight / designHeight).coerceAtMost(1f)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
            content = content,
        )
    }
}

/** Passo 1: link → app → leque de formatos. */
@Composable
fun WelcomeIllustration(modifier: Modifier = Modifier) {
    val float = rememberFloat()
    IllustrationArea(designHeight = 410.dp, modifier = modifier) {
        // No mockup o brilho fica centrado 14 px abaixo do meio da área.
        Box(Modifier.align(Alignment.Center).offset(y = 14.dp)) { Glow(top = 0.dp, alpha = 0.22f) }
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 56.dp)
                .graphicsLayer { translationY = float.value * density }
                .shadow(20.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Card)
                .border(1.dp, Palette.BorderStrong, RoundedCornerShape(20.dp))
                .height(40.dp)
                .padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CvIcon(ConvertiaIcons.Link, contentDescription = null, size = 15.dp, tint = Palette.TextSecondary)
            Text("youtube.com/watch?v=k3yB…", style = MonoTextStyle.copy(fontSize = 12.sp, color = Palette.TextEmphasis), maxLines = 1)
        }
        DashedLine(top = 96.dp, length = 44.dp)
        AppTile(top = 142.dp, icon = ConvertiaIcons.Convert, iconSize = 40.dp, stroke = 2.2f)
        DashedLine(top = 228.dp, length = 22.dp)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 252.dp)
                .fillMaxWidth()
                .height(120.dp),
        ) {
            listOf(
                Triple("PDF", -30f, (-6).dp),
                Triple("JPG", -15f, 10.dp),
                Triple("MP3", 15f, 10.dp),
                Triple("EPUB", 30f, (-6).dp),
                Triple("MP4", 0f, 16.dp),
            ).forEach { (label, angle, bottom) ->
                val highlighted = label == "MP4"
                MiniCard(
                    label = label,
                    width = 60.dp,
                    height = 76.dp,
                    radius = 12.dp,
                    barWidth = 18.dp,
                    fontSize = 12.sp,
                    highlighted = highlighted,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = -bottom)
                        .zIndex(if (highlighted) 5f else if (angle == 15f || angle == -15f) 2f else 1f)
                        .graphicsLayer {
                            rotationZ = angle
                            transformOrigin = TransformOrigin(0.5f, 3f)
                        },
                )
            }
        }
    }
}

/** Passo 2: cadeado com formatos em órbita e "0 bytes enviados". */
@Composable
fun PrivacyIllustration(modifier: Modifier = Modifier) {
    IllustrationArea(designHeight = 350.dp, modifier = modifier) {
        Glow(top = 76.dp, alpha = 0.2f)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 80.dp)
                .size(250.dp)
                .drawBehind {
                    drawCircle(
                        color = Palette.Gray3A,
                        radius = size.minDimension / 2 - 0.75.dp.toPx(),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                    )
                },
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 120.dp)
                .size(170.dp)
                .clip(CircleShape)
                .background(Palette.Background.copy(alpha = 0.6f))
                .border(1.dp, Palette.Border, CircleShape),
        )
        AppTile(top = 163.dp, icon = ConvertiaIcons.Lock, iconSize = 36.dp, stroke = 2.1f)
        listOf(
            OrbitCard("MP4", (-112).dp, 106.dp, -14f),
            OrbitCard("PDF", 112.dp, 120.dp, 12f),
            OrbitCard("JPG", (-120).dp, 250.dp, 10f),
            OrbitCard("MP3", 116.dp, 258.dp, -10f),
        ).forEach { card ->
            MiniCard(
                label = card.label,
                width = 46.dp,
                height = 58.dp,
                radius = 10.dp,
                barWidth = 14.dp,
                fontSize = 10.5.sp,
                padding = 7.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = card.dx, y = card.top)
                    .graphicsLayer { rotationZ = card.angle },
            )
        }
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 312.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Palette.Card)
                .border(1.dp, Palette.BorderStrong, RoundedCornerShape(17.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Accent))
            Text(stringResource(R.string.onboarding_zero_bytes), style = MonoTextStyle.copy(fontSize = 12.sp, color = Palette.TextEmphasis))
        }
    }
}

private data class OrbitCard(val label: String, val dx: Dp, val top: Dp, val angle: Float)

/** Passo 3: notificação "Download concluído" e o Convertia no menu Compartilhar. */
@Composable
fun PermissionsIllustration(modifier: Modifier = Modifier) {
    val float = rememberFloat()
    IllustrationArea(designHeight = 250.dp, modifier = modifier) {
        Glow(top = 10.dp, alpha = 0.2f)
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 34.dp)
                .graphicsLayer { translationY = float.value * density }
                .width(300.dp)
                .shadow(28.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.Raised)
                .border(1.dp, Palette.BorderStrong, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Accent), contentAlignment = Alignment.Center) {
                CvIcon(ConvertiaIcons.Convert, contentDescription = null, size = 17.dp, tint = Palette.OnAccent, strokeWidth = 2.3f)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.onboarding_notification_app), style = BaseTextStyle.copy(fontSize = 11.5.sp, color = Palette.TextMeta))
                Text(stringResource(R.string.toast_download_finished), style = BaseTextStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold))
                Text(
                    stringResource(R.string.onboarding_notification_file),
                    style = BaseTextStyle.copy(fontSize = 12.sp, color = Palette.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 138.dp)
                .width(270.dp)
                .shadow(28.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.Card)
                .border(1.dp, Palette.Border, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.onboarding_share_with), style = BaseTextStyle.copy(fontSize = 11.5.sp, color = Palette.TextMeta))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Palette.Accent.copy(alpha = 0.18f))
                        .padding(4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    CvIcon(ConvertiaIcons.Convert, contentDescription = null, size = 21.dp, tint = Palette.OnAccent, strokeWidth = 2.3f)
                }
                listOf("M", "E", "D", "N").forEach { letter ->
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.Selected), contentAlignment = Alignment.Center) {
                        Text(letter, style = BaseTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextMeta2))
                    }
                }
            }
        }
    }
}

/** Sobe e desce 5 dp a cada 4 s (`cvFloat` no mockup). */
@Composable
private fun rememberFloat() = rememberInfiniteTransition(label = "float").animateFloat(
    initialValue = 0f,
    targetValue = -5f,
    animationSpec = infiniteRepeatable(tween(2_000, easing = EaseInOut), RepeatMode.Reverse),
    label = "floatY",
)

@Composable
private fun BoxScope.Glow(top: Dp, alpha: Float) {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .offset(y = top)
            .size(260.dp)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        0f to Palette.Accent.copy(alpha = alpha),
                        0.65f to Palette.Accent.copy(alpha = 0f),
                        center = center,
                        radius = size.minDimension / 2,
                    ),
                )
            },
    )
}

@Composable
private fun BoxScope.DashedLine(top: Dp, length: Dp) {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .offset(y = top)
            .size(width = 2.dp, height = length)
            .drawBehind {
                drawLine(
                    color = Palette.Gray3A,
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            },
    )
}

/** Ícone laranja do app (84 dp) com o anel claro e o brilho embaixo. */
@Composable
private fun BoxScope.AppTile(top: Dp, icon: CvIconSpec, iconSize: Dp, stroke: Float) {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .offset(y = top - 8.dp)
            .size(100.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Palette.Accent.copy(alpha = 0.08f)),
    )
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .offset(y = top)
            .size(84.dp)
            .shadow(30.dp, RoundedCornerShape(26.dp), ambientColor = Palette.Accent, spotColor = Palette.Accent)
            .clip(RoundedCornerShape(26.dp))
            .background(Palette.Accent),
        contentAlignment = Alignment.Center,
    ) {
        CvIcon(icon, contentDescription = null, size = iconSize, tint = Palette.OnAccent, strokeWidth = stroke)
    }
}

/** Cartãozinho de formato (barra no topo, extensão embaixo). */
@Composable
private fun MiniCard(
    label: String,
    width: Dp,
    height: Dp,
    radius: Dp,
    barWidth: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    padding: Dp = 8.dp,
    highlighted: Boolean = false,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .size(width, height)
            .shadow(20.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(if (highlighted) Palette.Raised2 else Palette.Card)
            .border(1.dp, if (highlighted) Palette.Accent else Palette.BorderStrong, shape)
            .padding(padding),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .size(width = barWidth, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (highlighted) Palette.Accent else Palette.Gray33),
        )
        Text(
            label,
            style = BaseTextStyle.copy(
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.24.sp,
                color = if (highlighted) Palette.TextPrimary else Palette.TextSecondary,
            ),
        )
    }
}
