package com.recast.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.recast.app.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Curva do `cvUp` do mockup. */
private val SheetEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * Bottom sheet desenhado na própria tela (não em outra janela, como o `ModalBottomSheet`),
 * para o toast poder ficar por cima, como no mockup. Mantém o último [value] não nulo
 * durante a animação de saída. Fecha no véu, arrastando a alça para baixo e — por conta de
 * quem chama — no botão voltar.
 */
@Composable
fun <T : Any> SheetHost(
    value: T?,
    onDismiss: () -> Unit,
    paneTitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    val visible = value != null

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Palette.Scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(260, easing = SheetEasing)) { it },
            exit = slideOutVertically(tween(200, easing = SheetEasing)) { it },
        ) {
            last?.let { current ->
                SheetSurface(onDismiss = onDismiss, paneTitle = paneTitle) { content(current) }
            }
        }
    }
}

@Composable
private fun SheetSurface(
    onDismiss: () -> Unit,
    paneTitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxHeight = this.maxHeight
        Column(
            modifier = Modifier
                .offset { IntOffset(0, drag.value.roundToInt()) }
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.9f)
                .clip(SheetShape)
                .background(Palette.Sheet)
                .drawWithContent {
                    drawContent()
                    // Só a borda de cima, como o `border-top` do mockup.
                    clipRect(bottom = 28.dp.toPx()) {
                        drawOutline(
                            SheetShape.createOutline(size, layoutDirection, this),
                            Palette.BorderSheet,
                            style = Stroke(1.dp.toPx()),
                        )
                    }
                }
                .semantics { this.paneTitle = paneTitle }
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                // Engole toques para não fecharem o sheet pelo véu.
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                        },
                        onDragStopped = { velocity ->
                            if (drag.value > 120f || velocity > 1500f) onDismiss()
                            else drag.animateTo(0f)
                        },
                    )
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Palette.Gray44),
                )
            }
            content()
        }
    }
}
