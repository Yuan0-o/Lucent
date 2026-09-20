package com.lucent.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

class ReorderDragState internal constructor() {

    var dragging: Boolean by mutableStateOf(false)

    var draggingId: Long? by mutableStateOf(null)

    var dragOffset: Offset by mutableStateOf(Offset.Zero)

    var gapBeforeId: Long? by mutableStateOf(null)
    var gapAfterId: Long? by mutableStateOf(null)

    var settling: Boolean by mutableStateOf(false)
        private set

    var settleOrder: List<Long>? by mutableStateOf(null)
        private set

    internal var dragToken: Int by mutableStateOf(0)
        private set

    internal fun begin(id: Long) {
        dragToken += 1
        dragging = true
        settling = false
        settleOrder = null
        draggingId = id
        dragOffset = Offset.Zero
        gapBeforeId = null
        gapAfterId = null
    }

    internal fun finish(order: List<Long>?): Pair<Long?, Long?> {
        val landed = gapBeforeId to gapAfterId
        dragging = false
        settling = order != null
        settleOrder = order
        return landed
    }

    internal fun cancel() {
        dragging = false
        settling = true
        settleOrder = null
        gapBeforeId = null
        gapAfterId = null
    }

    internal fun landed() {
        dragging = false
        settling = false
        settleOrder = null
        draggingId = null
        dragOffset = Offset.Zero
        gapBeforeId = null
        gapAfterId = null
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

    fun offsetOf(key: Long?): IntOffset? = offsets.getOrNull(indexOfKey(key))

    fun sizeOf(key: Long?): IntSize? = sizes.getOrNull(indexOfKey(key))

    fun visibleKeys(): List<Long> = keys

    fun slotFor(dragging: Long?, gapBeforeId: Long?, gapAfterId: Long?): Pair<IntOffset, IntSize>? =
        boundsAt(landingIndex(dragging, gapBeforeId, gapAfterId))

    fun targetFor(key: Long, dragging: Long?, gapBeforeId: Long?, gapAfterId: Long?): IntOffset? {
        val me = indexOfKey(key)
        if (me < 0) return null
        if (dragging == null) return offsets[me]
        if (key == dragging) return slotFor(dragging, gapBeforeId, gapAfterId)?.first ?: offsets[me]
        val reflow = reflowFor(key, dragging, gapBeforeId, gapAfterId)
        return IntOffset(offsets[me].x + reflow.x, offsets[me].y + reflow.y)
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

    private fun landingIndex(dragging: Long?, gapBeforeId: Long?, gapAfterId: Long?): Int {
        if (dragging == null) return -1
        val after = indexOfKey(gapAfterId)
        val before = indexOfKey(gapBeforeId)
        if (after < 0 && before < 0) return -1
        val target = if (after >= 0) after else before + 1
        val from = indexOfKey(dragging)
        if (from < 0 || target == from) return -1
        val landing = if (from < target) target - 1 else target
        return if (landing in keys.indices) landing else -1
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
fun rememberListSlots(state: LazyListState): () -> ReorderSlots {
    val cached = remember(state) { derivedStateOf { state.visibleSlots() } }
    return remember(cached) { { cached.value } }
}

@Composable
fun rememberGridSlots(state: LazyGridState): () -> ReorderSlots {
    val cached = remember(state) { derivedStateOf { state.visibleSlots() } }
    return remember(cached) { { cached.value } }
}

private object ListScrollMemory {
    private val entries = HashMap<String, IntArray>()

    fun read(key: String): IntArray? = entries[key]

    fun write(key: String, index: Int, offset: Int) {
        val slot = entries[key]
        if (slot == null) {
            entries[key] = intArrayOf(index, offset)
        } else {
            slot[0] = index
            slot[1] = offset
        }
    }
}

@Composable
fun rememberRestoredListState(key: String): LazyListState {
    val restored = remember(key) { ListScrollMemory.read(key) }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = restored?.get(0) ?: 0,
        initialFirstVisibleItemScrollOffset = restored?.get(1) ?: 0
    )
    LaunchedEffect(state, key) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (state.layoutInfo.totalItemsCount > 1) ListScrollMemory.write(key, index, offset)
            }
    }
    return state
}

@Composable
fun rememberRestoredGridState(key: String): LazyGridState {
    val restored = remember(key) { ListScrollMemory.read(key) }
    val state = rememberLazyGridState(
        initialFirstVisibleItemIndex = restored?.get(0) ?: 0,
        initialFirstVisibleItemScrollOffset = restored?.get(1) ?: 0
    )
    LaunchedEffect(state, key) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (state.layoutInfo.totalItemsCount > 1) ListScrollMemory.write(key, index, offset)
            }
    }
    return state
}

private fun jellyPhase(raw: Float, seed: Float): Float {
    val shifted = (raw + seed) % 1f
    val up = if (shifted < 0f) shifted + 1f else shifted
    return if (up <= 0.5f) up * 2f else (1f - up) * 2f
}

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
                onDragEnd = {
                    val slots = listState.visibleSlots()
                    val from = slots.offsetOf(id)
                    val landing = slots.targetFor(id, id, state.gapBeforeId, state.gapAfterId)
                    val moved = landing != null && landing != from
                    val (b, a) = state.finish(if (moved) slots.visibleKeys() else null)
                    drop(b, a)
                },
                onDragCancel = { state.cancel() },
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
                onDragEnd = {
                    val slots = gridState.visibleSlots()
                    val from = slots.offsetOf(id)
                    val landing = slots.targetFor(id, id, state.gapBeforeId, state.gapAfterId)
                    val moved = landing != null && landing != from
                    val (b, a) = state.finish(if (moved) slots.visibleKeys() else null)
                    drop(b, a)
                },
                onDragCancel = { state.cancel() },
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
    val active = state.dragging || state.settling
    val motion = LocalBackgroundEnvironment.current.motionEnabled
    val token = state.dragToken
    val jelly = reorderJelly(state.dragging)
    val wobble by animateFloatAsState(
        targetValue = if (state.dragging) 1f else 0f,
        animationSpec = tween(durationMillis = JELLY_RAMP_MS),
        label = "reorderWobble"
    )
    val lift by animateFloatAsState(
        targetValue = if (lifted) 1f else 0f,
        animationSpec = if (motion) JELLY_SPRING else JELLY_STILL,
        label = "reorderLift"
    )
    val follow by animateFloatAsState(
        targetValue = if (state.dragging) JELLY_LEAD else 0f,
        animationSpec = tween(durationMillis = JELLY_LEAD_MS),
        label = "reorderFollow"
    )
    val target = if (active) slots().targetFor(id, state.draggingId, state.gapBeforeId, state.gapAfterId) else null
    val positioned = target != null
    val targetX = target?.x?.toFloat() ?: 0f
    val targetY = target?.y?.toFloat() ?: 0f
    val travelX = remember(token) { Animatable(targetX) }
    val travelY = remember(token) { Animatable(targetY) }
    val anchorX = remember(token) { targetX }
    val anchorY = remember(token) { targetY }
    val seed = (id % JELLY_SEEDS).toInt() * JELLY_SEED_STEP
    val travelSpec = if (motion) JELLY_SPRING else JELLY_STILL
    val density = LocalDensity.current
    val bob = with(density) { JELLY_BOB.toPx() }
    LaunchedEffect(token, targetX, targetY, positioned) {
        if (positioned) {
            launch { travelX.animateTo(targetX, travelSpec) }
            launch { travelY.animateTo(targetY, travelSpec) }
        }
    }
    this
        .zIndex(if (lifted) 2f else if (active) 1f else 0f)
        .graphicsLayer {
            val phase = jelly?.value ?: 0f
            if (positioned) {
                val here = slots().offsetOf(id)
                if (here != null) {
                    var dx = travelX.value - here.x
                    var dy = travelY.value - here.y
                    if (lifted) {
                        val size = slots().sizeOf(id)
                        val leadX = (size?.width ?: 0) * JELLY_LEAD_CAP
                        val leadY = (size?.height ?: 0) * JELLY_LEAD_CAP
                        dx += ((state.dragOffset.x - (travelX.value - anchorX)) * follow).coerceIn(-leadX, leadX)
                        dy += ((state.dragOffset.y - (travelY.value - anchorY)) * follow).coerceIn(-leadY, leadY)
                    } else {
                        dy += bob * jellyPhase(phase, seed) * wobble * JELLY_WOBBLE_GAIN
                    }
                    translationX = dx
                    translationY = dy
                }
            }
            val liftScale = 1f + (LIFT_SCALE - 1f) * lift
            if (lifted || lift > JELLY_LIFT_MIN) {
                val stretch = 1f + JELLY_STRETCH * phase
                scaleX = liftScale * stretch
                scaleY = liftScale / stretch
                alpha = 1f - (1f - LIFT_ALPHA) * lift
                shadowElevation = LIFT_ELEVATION * lift
            } else {
                val breathe = JELLY_BREATHE * phase * wobble * JELLY_WOBBLE_GAIN
                scaleX = 1f + breathe * 0.5f
                scaleY = 1f - breathe
            }
        }
}

@Composable
fun ReorderSettleEffect(state: ReorderDragState, slots: () -> ReorderSlots) {
    if (!state.settling) return
    val id = state.draggingId
    val order = state.settleOrder
    LaunchedEffect(state.settling, id, order) {
        if (id != null && order != null) {
            withTimeoutOrNull(JELLY_SETTLE_TIMEOUT_MS) {
                snapshotFlow { slots().visibleKeys() }.first { it != order }
            }
        } else {
            delay(JELLY_RETURN_MS)
        }
        state.landed()
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
private const val JELLY_WOBBLE_GAIN = 0.6f
private const val JELLY_RAMP_MS = 260
private const val JELLY_LIFT_MIN = 0.002f
private const val JELLY_LEAD = 0.18f
private const val JELLY_LEAD_MS = 240
private const val JELLY_LEAD_CAP = 0.12f
private const val JELLY_SEEDS = 5L
private const val JELLY_SEED_STEP = 0.16f
private const val JELLY_SETTLE_TIMEOUT_MS = 900L
private const val JELLY_RETURN_MS = 480L
private val JELLY_SPRING = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
private val JELLY_STILL = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

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
