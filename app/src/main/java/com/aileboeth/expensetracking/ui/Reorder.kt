package com.aileboeth.expensetracking.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A fixed-column grid whose cells can be long-pressed and dragged to a new place. The others slide out of
 * the way while dragging; [onReorder] gets the final order when the finger lifts. Taps still reach the cells.
 */
@Composable
fun <T> ReorderGrid(
    items: List<T>,
    key: (T) -> Any,
    columns: Int,
    cellHeight: Dp,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    cell: @Composable (item: T, dragging: Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf(items) }
    var dragging by remember { mutableStateOf<Any?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(items) { if (dragging == null) order = items }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val cellW = maxWidth / columns
        val cw = with(density) { cellW.toPx() }
        val ch = with(density) { cellHeight.toPx() }
        val rows = (order.size + columns - 1) / columns
        Box(Modifier.fillMaxWidth().height(cellHeight * rows.coerceAtLeast(1))) {
            order.forEachIndexed { index, item ->
                val k = key(item)
                key(k) {
                    val target = IntOffset(((index % columns) * cw).roundToInt(), ((index / columns) * ch).roundToInt())
                    val anim = remember { Animatable(target, IntOffset.VectorConverter) }
                    val isDragging = dragging == k
                    LaunchedEffect(target, isDragging) {
                        if (!isDragging) anim.animateTo(target, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                    }
                    val lift by animateFloatAsState(if (isDragging) 1f else 0f, label = "lift")
                    Box(
                        Modifier
                            .offset { if (isDragging) IntOffset(finger.x.roundToInt(), finger.y.roundToInt()) else anim.value }
                            .size(cellW, cellHeight)
                            .zIndex(if (isDragging) 2f else 0f)
                            .graphicsLayer {
                                scaleX = 1f + 0.1f * lift; scaleY = 1f + 0.1f * lift
                                rotationZ = 3f * lift
                            }
                            .pointerInput(k) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val i = order.indexOfFirst { key(it) == k }
                                        finger = Offset((i % columns) * cw, (i / columns) * ch)
                                        dragging = k
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val total = order.size
                                        val maxRow = (total - 1) / columns
                                        finger = Offset(
                                            (finger.x + amount.x).coerceIn(-cw * 0.3f, cw * (columns - 0.7f)),
                                            (finger.y + amount.y).coerceIn(-ch * 0.3f, ch * (maxRow + 0.3f)),
                                        )
                                        val col = ((finger.x + cw / 2) / cw).toInt().coerceIn(0, columns - 1)
                                        val row = ((finger.y + ch / 2) / ch).toInt().coerceIn(0, maxRow)
                                        val to = (row * columns + col).coerceIn(0, total - 1)
                                        val from = order.indexOfFirst { key(it) == k }
                                        if (from >= 0 && to != from) {
                                            order = order.toMutableList().apply { add(to, removeAt(from)) }
                                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        }
                                    },
                                    onDragEnd = {
                                        scope.launch { anim.snapTo(IntOffset(finger.x.roundToInt(), finger.y.roundToInt())) }
                                        dragging = null
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                        onReorder(order)
                                    },
                                    onDragCancel = {
                                        scope.launch { anim.snapTo(IntOffset(finger.x.roundToInt(), finger.y.roundToInt())) }
                                        dragging = null
                                        onReorder(order)
                                    },
                                )
                            },
                    ) { cell(item, isDragging) }
                }
            }
        }
    }
}
