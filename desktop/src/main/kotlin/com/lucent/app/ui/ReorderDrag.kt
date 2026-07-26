package com.lucent.app.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Long-press-then-drag reordering for the Notes and Tasks home lists.
 *
 * ### The interaction
 *
 * Long-pressing a card already meant "select this one", and that is kept: the press enters selection
 * mode and ticks the card. The addition is that **the finger doesn't have to come up**. Keep holding
 * and move, and the same gesture becomes a drag; the whole selection travels to wherever the finger
 * lets go. The dragged card lifts — it grows slightly, gains a shadow and rises above its
 * neighbours — because a gesture with no feedback is indistinguishable from a gesture that isn't
 * working, which is precisely how this feature was experienced before.
 *
 * ### Moving several at once
 *
 * The dragged block is **every selected item**, in *selection order* — the order the user ticked
 * them in, not their order in the list. They chose those items one at a time, and arriving in that
 * sequence is the only outcome they could have predicted.
 *
 * ### Three things this rewrite fixes
 *
 *  1. **The drop target is recomputed from live layout, every frame.** It used to be derived from
 *     the item's offset captured once at drag start, so any scroll or reflow during the drag left
 *     the finger pointing at the wrong card — or at nothing, which silently cancelled the whole
 *     operation.
 *  2. **The callbacks can no longer go stale.** `pointerInput` is keyed on the id and the enabled
 *     flag, neither of which changes in practice, so its coroutine outlives every recomposition and
 *     kept whichever lambdas it was created with. They are read through [rememberUpdatedState] now.
 *  3. **The gesture is visible.** [ReorderDragState] carries what is being dragged and where it has
 *     been dragged to, and the modifier renders that.
 *
 * ### Where "position" is stored
 *
 * Nowhere in this file. Reordering rewrites `manualOrder` on the affected rows (see the call sites).
 * That column only decides anything under the **Custom** sort — which is why a drop under any other
 * sort adopts Custom, and why the home screens switch their Recent/Today/Older sectioning off there.
 * Automatic grouping would otherwise regroup the cards the moment they landed and throw the user's
 * arrangement away in front of them.
 */
class ReorderDragState internal constructor() {

    /** True while a drag is in flight; call sites use it to suppress scroll-driven work. */
    var dragging: Boolean by mutableStateOf(false)

    /** The card the finger currently owns, or null. Drives the lift. */
    var draggingId: Long? by mutableStateOf(null)

    /** How far that card has travelled, in pixels. */
    var dragOffset: Offset by mutableStateOf(Offset.Zero)

    /**
     * The gap the finger is currently pointing at, named by the two cards that flank it.
     *
     * This replaces "which card is under the finger", and the difference is the whole of task 9. A
     * target card can only ever mean "put it before this one", so the position after the LAST card
     * was unreachable — there was no card there to aim at, `keyAtY` returned null outside every
     * item, and the drop was discarded. A gap has one more slot than there are cards, which is
     * exactly the number of places a card can go.
     *
     * At the top of the list [gapBeforeId] is null; at the bottom [gapAfterId] is. That is also what
     * makes the preview do the right thing at the ends: only one card has a neighbour to move away
     * from, so only one moves.
     */
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

/**
 * Attach the gesture to one card in a `LazyColumn`-backed list.
 *
 * @param id this card's stable key — the same value passed to `items(key = ...)`.
 * @param enabled false disables reordering entirely.
 * @param onLongPress enter selection and tick this card, exactly as the old long-press did.
 * @param onDrop called with the key of the card the finger was over on release, or null when it was
 *   over nothing droppable. The call site turns that into new `manualOrder` values.
 */
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
                    // Read the card's position from the CURRENT layout rather than from a value
                    // captured at drag start, so scrolling mid-drag cannot desynchronise the two.
                    val top = listState.topOf(id)
                    val (b, a) = listState.gapAtY(top + grabbedAt.y + travelled.y, id)
                    state.gapBeforeId = b
                    state.gapAfterId = a
                }
            )
        }
}

/** The grid twin, for the Notes home page. Same contract; the hit test is two-dimensional. */
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

/**
 * The **look** of a drag, kept separate from the gesture that drives it — and applied at the very
 * top of a card's modifier chain, which is the entire fix for "it leaves an empty frame behind".
 *
 * A `graphicsLayer` only transforms what is drawn *inside* it. Sitting where it used to — after
 * `frostedGlass()` — it moved the card's contents and left the glass panel itself standing in the
 * old slot, so a drag produced a travelling block of text and an abandoned empty box. Declared
 * first, it wraps the whole card: the panel, its blur, its border and its contents all leave
 * together, and nothing is left in the original position.
 *
 * `zIndex` here for the same reason: the lifted card has to be drawn above its neighbours or it
 * slides *under* them and disappears the moment it overlaps one.
 *
 * The card the finger is hovering over squeezes — shorter and a touch wider, on an underdamped
 * spring, so it compresses and rebounds like something soft being leaned on. That is the position
 * preview: the gap that is about to open is shown by the card that is about to move, rather than by
 * a separate placeholder that would need its own layout pass.
 */
fun Modifier.reorderVisuals(id: Long, state: ReorderDragState): Modifier = composed {
    val lifted = state.draggingId == id
    // -1 = this card sits just ABOVE the gap and moves up; +1 = just below, and moves down.
    val push = when {
        lifted || !state.dragging -> 0f
        state.gapBeforeId == id -> -1f
        state.gapAfterId == id -> 1f
        else -> 0f
    }
    // Underdamped on purpose: a dampingRatio below 1 overshoots and settles back, which is the
    // wobble. A critically damped spring would just slide open, and the gap would look like a hole
    // rather than like two soft things being pushed apart.
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
                // A touch of compression along the way, so the two cards read as being *pushed*
                // apart by something rather than simply sliding.
                val squash = SQUEEZE_AMOUNT * kotlin.math.abs(shift)
                scaleY = 1f - squash
                scaleX = 1f + squash * 0.5f
            }
        }
}

/**
 * How a card that was displaced by a drop travels to its new slot.
 *
 * Handed to `Modifier.animateItem` by both home lists. Underdamped, so a card that has to move
 * overshoots very slightly and settles — the "micro-bounce". Without this the list simply redraws in
 * the new order and the reordering is over before the eye can follow what moved where.
 */
val REORDER_SETTLE: FiniteAnimationSpec<IntOffset> =
    spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))

private const val LIFT_SCALE = 1.05f
private const val LIFT_ALPHA = 0.93f
private const val LIFT_ELEVATION = 16f
private const val SQUEEZE_AMOUNT = 0.09f

/** How far apart the two cards flanking the gap are pushed while it is being previewed. */
private val GAP_SHIFT = 13.dp

/** This card's current top edge in the list's own viewport coordinates. */
private fun LazyListState.topOf(id: Long): Float =
    (layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset ?: 0).toFloat()

/**
 * Which visible item contains [y].
 *
 * Uses the laid-out offsets rather than assuming a uniform row height, because the cards are not a
 * uniform height — a note with a checklist preview is taller than a bare one, and "drag distance
 * divided by row height" would drift further out of step the further the finger travelled.
 *
 * Section headers are in this list too and their keys are Strings, so `as? Long` returns null for
 * them: dropping onto a header is treated as dropping onto nothing, which is the honest answer.
 */
private fun LazyListState.gapAtY(y: Float, dragged: Long): Pair<Long?, Long?> {
    // Only real cards are candidates: section headers carry String keys, and the dragged card is
    // excluded so it can never be treated as its own neighbour.
    val cards = layoutInfo.visibleItemsInfo.filter { (it.key as? Long)?.let { k -> k != dragged } == true }
    if (cards.isEmpty()) return null to null
    // The gap is decided by MIDPOINTS, not by bounds. Past the middle of a card the finger is
    // asking to go after it; before the middle, in front of it. Bounds would leave the space
    // between two cards ambiguous and the space past the last card unreachable.
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
    // Reading order: down a row, then across. Comparing against each card's centre in that order
    // gives the same one-more-slot-than-cards behaviour the list version has, in two dimensions.
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

/**
 * Compute the new ordering after a drop: [moving] (in the order given) lifted out of [current] and
 * re-inserted in front of [targetId].
 *
 * Returned as a full list rather than a pair of indices because the caller has to write
 * `manualOrder` on every row anyway — positions are only meaningful relative to their neighbours,
 * so a move is always a renumbering of the sequence, not an edit to one row.
 */
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
    // Prefer the card BELOW the gap ("insert in front of it"); fall back to the one above ("insert
    // after it"), which is the only anchor available at the very bottom of the list.
    val at = when {
        afterId != null && afterId !in movingIds -> remainder.indexOfFirst { idOf(it) == afterId }
        beforeId != null && beforeId !in movingIds -> remainder.indexOfFirst { idOf(it) == beforeId } + 1
        else -> -1
    }
    if (at < 0 || at > remainder.size) return current
    return remainder.toMutableList().also { it.addAll(at, moving) }
}
