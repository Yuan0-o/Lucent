package com.lucent.app.ui

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

    /** The card the finger is over right now, or null when it is over a gap or a header. */
    var targetId: Long? by mutableStateOf(null)

    internal fun begin(id: Long) {
        dragging = true
        draggingId = id
        dragOffset = Offset.Zero
        targetId = null
    }

    internal fun finish(): Long? {
        val landed = targetId
        dragging = false
        draggingId = null
        dragOffset = Offset.Zero
        targetId = null
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
    onDrop: (targetId: Long?) -> Unit
): Modifier = composed {
    val press by rememberUpdatedState(onLongPress)
    val drop by rememberUpdatedState(onDrop)
    val lifted = state.draggingId == id
    this
        .zIndex(if (lifted) 1f else 0f)
        .graphicsLayer {
            if (state.draggingId == id) {
                translationY = state.dragOffset.y
                translationX = state.dragOffset.x
                scaleX = LIFT_SCALE
                scaleY = LIFT_SCALE
                alpha = LIFT_ALPHA
                shadowElevation = LIFT_ELEVATION
            }
        }
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
                onDragEnd = { drop(state.finish()) },
                onDragCancel = { state.finish() },
                onDrag = { change, amount ->
                    change.consume()
                    travelled += amount
                    state.dragOffset = travelled
                    // Read the card's position from the CURRENT layout rather than from a value
                    // captured at drag start, so scrolling mid-drag cannot desynchronise the two.
                    val top = listState.topOf(id)
                    state.targetId = listState.keyAtY(top + grabbedAt.y + travelled.y)
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
    onDrop: (targetId: Long?) -> Unit
): Modifier = composed {
    val press by rememberUpdatedState(onLongPress)
    val drop by rememberUpdatedState(onDrop)
    val lifted = state.draggingId == id
    this
        .zIndex(if (lifted) 1f else 0f)
        .graphicsLayer {
            if (state.draggingId == id) {
                translationX = state.dragOffset.x
                translationY = state.dragOffset.y
                scaleX = LIFT_SCALE
                scaleY = LIFT_SCALE
                alpha = LIFT_ALPHA
                shadowElevation = LIFT_ELEVATION
            }
        }
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
                onDragEnd = { drop(state.finish()) },
                onDragCancel = { state.finish() },
                onDrag = { change, amount ->
                    change.consume()
                    travelled += amount
                    state.dragOffset = travelled
                    state.targetId = gridState.keyAtOffset(gridState.originOf(id) + grabbedAt + travelled)
                }
            )
        }
}

private const val LIFT_SCALE = 1.05f
private const val LIFT_ALPHA = 0.93f
private const val LIFT_ELEVATION = 16f

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
private fun LazyListState.keyAtY(y: Float): Long? =
    layoutInfo.visibleItemsInfo
        .firstOrNull { y >= it.offset && y < it.offset + it.size }
        ?.key as? Long

private fun LazyGridState.originOf(id: Long): Offset =
    layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset
        ?.let { Offset(it.x.toFloat(), it.y.toFloat()) } ?: Offset.Zero

private fun LazyGridState.keyAtOffset(p: Offset): Long? =
    layoutInfo.visibleItemsInfo
        .firstOrNull {
            p.x >= it.offset.x && p.x < it.offset.x + it.size.width &&
                p.y >= it.offset.y && p.y < it.offset.y + it.size.height
        }
        ?.key as? Long

/**
 * Compute the new ordering after a drop: [moving] (in the order given) lifted out of [current] and
 * re-inserted in front of [targetId].
 *
 * Returned as a full list rather than a pair of indices because the caller has to write
 * `manualOrder` on every row anyway — positions are only meaningful relative to their neighbours,
 * so a move is always a renumbering of the sequence, not an edit to one row.
 */
fun <T> reorderedBy(
    current: List<T>,
    moving: List<T>,
    targetId: Long?,
    idOf: (T) -> Long
): List<T> {
    if (moving.isEmpty()) return current
    val movingIds = moving.map(idOf).toHashSet()
    val remainder = current.filterNot { idOf(it) in movingIds }
    // Dropped onto one of the dragged cards: the block is already where it was asked to go.
    if (targetId == null || targetId in movingIds) return current
    val at = remainder.indexOfFirst { idOf(it) == targetId }
    if (at < 0) return current
    return remainder.toMutableList().also { it.addAll(at, moving) }
}
