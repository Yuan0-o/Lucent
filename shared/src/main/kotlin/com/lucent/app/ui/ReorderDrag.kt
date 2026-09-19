package com.lucent.app.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs

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

class ReorderSlots internal constructor(
    private val keys: List<Long>,
    private val offsets: List<IntOffset>,
    private val sizes: List<IntSize>
) {
    val count: Int get() = keys.size

    fun indexOfKey(key: Long?): Int = if (key == null) -1 else keys.indexOf(key)

    fun boundsAt(index: Int): Pair<IntOffset, IntSize>? =
        if (index in keys.indices) offsets[index] to sizes[index] else null

    fun slotFor(gapBeforeId: Long?, gapAfterId: Long?): Pair<IntOffset, IntSize>? {
        boundsAt(indexOfKey(gapAfterId))?.let { return it }
        val (offset, size) = boundsAt(indexOfKey(gapBeforeId)) ?: return null
        return IntOffset(offset.x, offset.y + size.height) to size
    }

    fun reflowFor(key: Long, dragging: Long?, gapBeforeId: Long?, gapAfterId: Long?): IntOffset {
        if (dragging == null) return IntOffset.Zero
        val after = indexOfKey(gapAfterId)
        val before = indexOfKey(gapBeforeId)
        if (after < 0 && before < 0) return IntOffset.Zero
        val target = if (after >= 0) after else before + 1
        val from = indexOfKey(dragging)
        val me = indexOfKey(key)
        if (from < 0 || me < 0 || me == from) return IntOffset.Zero
        val neighbour = when {
            from < target && me in (from + 1) until target -> me - 1
            target < from && me in target until from -> me + 1
            else -> return IntOffset.Zero
        }
        val destination = boundsAt(neighbour)?.first ?: return IntOffset.Zero
        return destination - offsets[me]
    }

    companion object {
        val EMPTY = ReorderSlots(emptyList(), emptyList(), emptyList())
    }
}

private operator fun IntOffset.minus(other: IntOffset) = IntOffset(x - other.x, y - other.y)

private fun LazyListState.visibleSlots(): ReorderSlots {
    val cards = layoutInfo.visibleItemsInfo.filter { it.key is Long }
    val width = layoutInfo.viewportSize.width
    return ReorderSlots(
        keys = cards.map { it.key as Long },
        offsets = cards.map { IntOffset(0, it.offset) },
        sizes = cards.map { IntSize(width, it.size) }
    )
}

private fun LazyGridState.visibleSlots(): ReorderSlots {
    val cards = layoutInfo.visibleItemsInfo.filter { it.key is Long }
    return ReorderSlots(
        keys = cards.map { it.key as Long },
        offsets = cards.map { IntOffset(it.offset.x, it.offset.y) },
        sizes = cards.map { IntSize(it.size.width, it.size.height) }
    )
}

@Composable
fun rememberListSlots(state: LazyListState): () -> ReorderSlots =
    remember(state) { { state.visibleSlots() } }

@Composable
fun rememberGridSlots(state: LazyGridState): () -> ReorderSlots =
    remember(state) { { state.visibleSlots() } }

@Composable
private fun reorderJelly(active: Boolean): State<Float>? {
    if (!active || !LocalBackgroundEnvironment.current.motionEnabled) return null
    val transition = rememberInfiniteTransition(label = "reorderJelly")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = JELLY_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reorderJellyPhase"
    )
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

fun Modifier.reorderVisuals(
    id: Long,
    state: ReorderDragState,
    slots: () -> ReorderSlots = { ReorderSlots.EMPTY }
): Modifier = composed {
    val lifted = state.draggingId == id
    val jelly = reorderJelly(state.dragging)
    val density = LocalDensity.current
    val bob = with(density) { JELLY_BOB.toPx() }
    this
        .zIndex(if (lifted) 2f else if (state.dragging) 1f else 0f)
        .graphicsLayer {
            val phase = jelly?.value ?: 0f
            if (state.draggingId == id) {
                val stretch = 1f + JELLY_STRETCH * phase
                translationX = state.dragOffset.x
                translationY = state.dragOffset.y
                scaleX = LIFT_SCALE * stretch
                scaleY = LIFT_SCALE / stretch
                alpha = LIFT_ALPHA
                shadowElevation = LIFT_ELEVATION
            } else if (state.dragging) {
                val reflow = slots().reflowFor(id, state.draggingId, state.gapBeforeId, state.gapAfterId)
                if (reflow != IntOffset.Zero) {
                    val direction = if (reflow.y < 0) -1f else 1f
                    translationX = reflow.x.toFloat()
                    translationY = reflow.y.toFloat() + bob * phase * direction
                    val breathe = JELLY_BREATHE * phase
                    scaleX = 1f + breathe * 0.5f
                    scaleY = 1f - breathe
                }
            }
        }
}

@Composable
fun ReorderDropSlot(state: ReorderDragState, slots: () -> ReorderSlots, modifier: Modifier = Modifier) {
    if (!state.dragging) return
    val jelly = reorderJelly(true)
    val ink = LocalOnGradient.current
    val density = LocalDensity.current
    val corner = with(density) { SLOT_CORNER.toPx() }
    val stroke = with(density) { 1.5.dp.toPx() }
    Canvas(modifier = modifier) {
        val bounds = slots().slotFor(state.gapBeforeId, state.gapAfterId) ?: return@Canvas
        val phase = jelly?.value ?: 0f
        val shrink = SLOT_BREATHE * phase
        val width = bounds.second.width * (1f - shrink)
        val height = bounds.second.height * (1f - shrink)
        val left = bounds.first.x + (bounds.second.width - width) / 2f
        val top = bounds.first.y + (bounds.second.height - height) / 2f
        val topLeft = Offset(left, top)
        drawRoundRect(
            color = ink.copy(alpha = SLOT_FILL + SLOT_FILL_SWING * phase),
            topLeft = topLeft,
            size = Size(width, height),
            cornerRadius = CornerRadius(corner, corner)
        )
        drawRoundRect(
            color = ink.copy(alpha = SLOT_RIM),
            topLeft = topLeft,
            size = Size(width, height),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(
                width = stroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 7f, stroke * 5f), 0f)
            )
        )
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
private const val JELLY_PERIOD_MS = 620
private const val JELLY_STRETCH = 0.022f
private const val JELLY_BREATHE = 0.016f
private val JELLY_BOB = 3.5.dp
private val SLOT_CORNER = 20.dp
private const val SLOT_BREATHE = 0.05f
private const val SLOT_FILL = 0.05f
private const val SLOT_FILL_SWING = 0.05f
private const val SLOT_RIM = 0.32f

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
        val pastIt = p.y > cy + item.size.height / 2f ||
            (abs(p.y - cy) <= item.size.height / 2f && p.x >= cx)
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
