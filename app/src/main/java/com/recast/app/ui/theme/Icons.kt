package com.recast.app.ui.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ícone de traço do mockup: paths SVG num viewBox 24×24. A espessura do traço varia de
 * um uso para outro no mockup, então ela é escolhida na hora de desenhar ([CvIcon]).
 */
@Immutable
class CvIconSpec internal constructor(val paths: List<String>, val filled: Boolean = false)

private fun stroke(vararg paths: String) = CvIconSpec(paths.toList())
private fun fill(vararg paths: String) = CvIconSpec(paths.toList(), filled = true)

/** `<rect>` do SVG como path. */
private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,${r}v${h - 2 * r}a$r,$r 0 0 1 -$r,${r}" +
        "h-${w - 2 * r}a$r,$r 0 0 1 -$r,-${r}v-${h - 2 * r}a$r,$r 0 0 1 $r,-${r}z"

/** `<circle>` do SVG como path. */
private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r},${cy}a$r,$r 0 1 0 ${2 * r},0a$r,$r 0 1 0 -${2 * r},0z"

/** Ícones de mockup/Recast v3.dc.html. */
object RecastIcons {
    val Download = stroke("M12 3v12M7 10l5 5 5-5M5 20h14")
    val Convert = stroke("M4 8h14l-3.5-3.5M20 16H6l3.5 3.5")
    val Queue = stroke("M9 6h12M9 12h12M9 18h12M4 6h0.01M4 12h0.01M4 18h0.01")
    val Folder = stroke("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val Sliders = stroke("M4 20v-6M4 10V4M12 20v-8M12 8V4M20 20v-4M20 12V4M2 14h4M10 8h4M18 16h4")

    val Link = stroke(
        "M10 13a5 5 0 0 0 7.5 0.5l3-3a5 5 0 0 0-7-7l-1.7 1.7M14 11a5 5 0 0 0-7.5-0.5l-3 3a5 5 0 0 0 7 7l1.7-1.7",
    )
    val Lock = stroke(rect(5f, 11f, 14f, 9f, 2f), "M8 11V8a4 4 0 0 1 8 0v3")
    val Bell = stroke("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.9 1.9 0 0 0 3.4 0")
    val ArrowLeft = stroke("M19 12H5M11 6l-6 6 6 6")
    val ArrowRight = stroke("M5 12h14M13 6l6 6-6 6")
    val Share = stroke("M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M16 6l-4-4-4 4M12 2v13")
    val Copy = stroke(rect(9f, 9f, 12f, 12f, 2f), "M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1")
    val Close = stroke("M6 6l12 12M18 6L6 18")
    val Clipboard = stroke(
        rect(8f, 2f, 8f, 4f, 1f),
        "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2",
    )
    val Clock = stroke("M12 8v4l3 2", circle(12f, 12f, 9f))
    val Check = stroke("M20 6L9 17l-5-5")
    val CheckThin = stroke("M5 12.5l4.5 4.5L19 7.5")
    val ChevronDown = stroke("M6 9l6 6 6-6")
    val ChevronRight = stroke("M9 6l6 6-6 6")
    val Plus = stroke("M12 5v14M5 12h14")
    val Music = stroke("M9 18V5l12-2v13", circle(6f, 18f, 3f), circle(18f, 16f, 3f))
    val Video = stroke(rect(3f, 5f, 18f, 14f, 2f), "M10 9.5v5l4-2.5z")
    val Pause = stroke("M9 5v14M15 5v14")
    val Play = stroke("M7 5l12 7-12 7z")
    val Retry = stroke("M21 12a9 9 0 1 1-3-6.7L21 8M21 3v5h-5")
    val Search = stroke(circle(11f, 11f, 7f), "M21 21l-4.3-4.3")
    val Image = stroke(rect(3f, 4f, 18f, 16f, 2f), circle(9f, 10f, 2f), "M21 16l-5-5-9 9")
    val Trash = stroke("M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3")

    val PlayFilled = fill("M8 5v14l11-7z")
    val PlayFilledWide = fill("M7 4.5v15l12.5-7.5z")
    val PauseFilled = fill(rect(6f, 5f, 4f, 14f, 1f), rect(14f, 5f, 4f, 14f, 1f))
    val MoreFilled = fill(circle(5f, 12f, 1.8f), circle(12f, 12f, 1.8f), circle(19f, 12f, 1.8f))

    /** Todos, para testes e previews. */
    internal val all: List<CvIconSpec>
        get() = listOf(
            Download, Convert, Queue, Folder, Sliders, Link, Lock, Bell, ArrowLeft, ArrowRight,
            Share, Copy, Close, Clipboard, Clock, Check, CheckThin, ChevronDown, ChevronRight,
            Plus, Music, Video, Pause, Play, Retry, Search, Image, Trash, PlayFilled,
            PlayFilledWide, PauseFilled, MoreFilled,
        )
}

fun CvIconSpec.toImageVector(strokeWidth: Float): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = if (filled) null else SolidColor(Color.Black),
                strokeLineWidth = if (filled) 0f else strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/**
 * Desenha um ícone do mockup. [strokeWidth] está em unidades do viewBox (24), como o
 * `stroke-width` do SVG: 1,8 é o padrão da barra de navegação.
 */
@Composable
fun CvIcon(
    icon: CvIconSpec,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    strokeWidth: Float = 1.8f,
) {
    val vector = remember(icon, strokeWidth) { icon.toImageVector(strokeWidth) }
    Icon(vector, contentDescription, modifier.size(size), tint)
}
