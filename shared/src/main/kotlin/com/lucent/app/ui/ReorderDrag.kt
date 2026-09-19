package com.lucent.app.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

class ReorderDragState internal constructor() {

    var dragging: Boolean by mutableStateOf(false)

    var draggingId: Long? by mutableStateOf(null)

    var dragOffset: Offset by mutableStateOf(Offset.Zero)

    var gapBeforeId: Long? by mutableStateOf(null)
    var gapAfterId: Long? by mutableStateOf(null)

    internal fun begin(id: Long) {
        dragging = true
        draggingId = id
        dragOffset = Offset.Zero
        gapBeforeId = null
        gapAfterId = null
    }

    internal fun finish(): Pair<Long?, Long?> {
        val landed = gapBeforeId to gapAfterId
        dragging = false
        draggingId = null
        dragOffset = Offset.Zero
        gapBeforeId = null
        gapAfterId = null
        return landed
    }
}

@Composable
fun rememberReorderDragState(): ReorderDragState = remember { ReorderDragState() }

fun Modifier.reorderableItem(
    id: Long,
    enabled: Boolean,
    listState: LazyListState,
    state: ReorderDragState,
    onLongPress: () -> Unit,
    onDrop: (beforeId: Long?, afterId: Long?) -> Unit
): Modifier = composed {
    val press by rememberUpdatedState(onLongPress)
    val drop by rememberUpdatedState(onDrop)
    this
        .pointerInput(id, enabled) {
            if (!enabled) return@pointerInput
            var grabbedAt = Offset.Zero
            var travelled = Offset.Zero
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    grabbedAt = local
                    travelled = Offset.Zero
                    state.begin(id)
                    press()
                },
                onDragEnd = { val (b, a) = state.finish(); drop(b, a) },
                onDragCancel = { state.finish() },
                onDrag = { change, amount ->
                    change.consume()
                    travelled += amount
                    state.dragOffset = travelled
                    val top = listState.topOf(id)
                    val (b, a) = listState.gapAtY(top + grabbedAt.y + travelled.y, id)
                    state.gapBeforeId = b
                    state.gapAfterId = a
                }
            )
        }
}

fun Modifier.reorderableGridItem(
    id: Long,
    enabled: Boolean,
    gridState: LazyGridState,
    state: ReorderDragState,
    onLongPress: () -> Unit,
    onDrop: (beforeId: Long?, afterId: Long?) -> Unit
): Modifier = composed {
    val press by rememberUpdatedState(onLongPress)
    val drop by rememberUpdatedState(onDrop)
    this
        .pointerInput(id, enabled) {
            if (!enabled) return@pointerInput
            var grabbedAt = Offset.Zero
            var travelled = Offset.Zero
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    grabbedAt = local
                    travelled = Offset.Zero
                    state.begin(id)
                    press()
                },
                onDragEnd = { val (b, a) = state.finish(); drop(b, a) },
                onDragCancel = { state.finish() },
                onDrag = { change, amount ->
                    change.consume()
                    travelled += amount
                    state.dragOffset = travelled
                    val (b, a) = gridState.gapAt(gridState.originOf(id) + grabbedAt + travelled, id)
                    state.gapBeforeId = b
                    state.gapAfterId = a
                }
            )
        }
}

fun Modifier.reorderVisuals(id: Long, state: ReorderDragState): Modifier = composed {
    val lifted = state.draggingId == id
    val push = when {
        lifted || !state.dragging -> 0f
        state.gapBeforeId == id -> -1f
        state.gapAfterId == id -> 1f
        else -> 0f
    }
    val shift by animateFloatAsState(
        targetValue = push,
        animationSpec = spring(dampingRatio = 0.40f, stiffness = Spring.StiffnessMediumLow),
        label = "reorderGap"
    )
    val shiftPx = with(androidx.compose.ui.platform.LocalDensity.current) { GAP_SHIFT.toPx() }
    this
        .zIndex(if (lifted) 2f else if (shift != 0f) 1f else 0f)
        .graphicsLayer {
            if (state.draggingId == id) {
                translationX = state.dragOffset.x
                translationY = state.dragOffset.y
                scaleX = LIFT_SCALE
                scaleY = LIFT_SCALE
                alpha = LIFT_ALPHA
                shadowElevation = LIFT_ELEVATION
            } else if (shift != 0f) {
                translationY = shiftPx * shift
                val squash = SQUEEZE_AMOUNT * kotlin.math.abs(shift)
                scaleY = 1f - squash
                scaleX = 1f + squash * 0.5f
            }
        }
}

val REORDER_SETTLE: FiniteAnimationSpec<IntOffset> =
    spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))


@Composable
fun rememberReorderPlacementSpec(): FiniteAnimationSpec<IntOffset>? {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        armed = true
    }
    return if (armed) REORDER_SETTLE else null
}

private const val LIFT_SCALE = 1.05f
private const val LIFT_ALPHA = 0.93f
private const val LIFT_ELEVATION = 16f
private const val SQUEEZE_AMOUNT = 0.09f

private val GAP_SHIFT = 13.dp

private fun LazyListState.topOf(id: Long): Float =
    (layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset ?: 0).toFloat()

private fun LazyListState.gapAtY(y: Float, dragged: Long): Pair<Long?, Long?> {
    val cards = layoutInfo.visibleItemsInfo.filter { (it.key as? Long)?.let { k -> k != dragged } == true }
    if (cards.isEmpty()) return null to null
    var before: Long? = null
    var after: Long? = null
    for (item in cards) {
        val mid = item.offset + item.size / 2f
        if (y >= mid) before = item.key as Long else { after = item.key as Long; break }
    }
    return before to after
}

private fun LazyGridState.originOf(id: Long): Offset =
    layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset
        ?.let { Offset(it.x.toFloat(), it.y.toFloat()) } ?: Offset.Zero

private fun LazyGridState.gapAt(p: Offset, dragged: Long): Pair<Long?, Long?> {
    val cards = layoutInfo.visibleItemsInfo.filter { (it.key as? Long)?.let { k -> k != dragged } == true }
    if (cards.isEmpty()) return null to null
    var before: Long? = null
    var after: Long? = null
    for (item in cards) {
        val cx = item.offset.x + item.size.width / 2f
        val cy = item.offset.y + item.size.height / 2f
        val pastIt = p.y > cy + item.size.height / 2f || (kotlin.math.abs(p.y - cy) <= item.size.height / 2f && p.x >= cx)
        if (pastIt) before = item.key as Long else { after = item.key as Long; break }
    }
    return before to after
}

fun <T> reorderedAround(
    current: List<T>,
    moving: List<T>,
    beforeId: Long?,
    afterId: Long?,
    idOf: (T) -> Long
): List<T> {
    if (moving.isEmpty()) return current
    val movingIds = moving.map(idOf).toHashSet()
    val remainder = current.filterNot { idOf(it) in movingIds }
    val at = when {
        afterId != null && afterId !in movingIds -> remainder.indexOfFirst { idOf(it) == afterId }
        beforeId != null && beforeId !in movingIds -> remainder.indexOfFirst { idOf(it) == beforeId } + 1
        else -> -1
    }
    if (at < 0 || at > remainder.size) return current
    return remainder.toMutableList().also { it.addAll(at, moving) }
}
