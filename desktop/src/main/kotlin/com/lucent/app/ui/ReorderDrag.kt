package com.lucent.app.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Long-press-then-drag reordering for the Notes and Tasks home lists (task A16).
 *
 * ### The interaction, and why it is one gesture rather than two
 *
 * Long-pressing a card already meant "select this one", and that is kept: the press enters
 * selection mode and ticks the card. The addition is that **the finger doesn't have to come up**.
 * Keep holding and move, and the same gesture becomes a drag; the whole selection travels to
 * wherever the finger lets go.
 *
 * That continuity is the point. The alternative — long-press to select, lift, then press again to
 * drag — asks the user to perform two gestures to express one intention, and the second one is
 * indistinguishable from the first until it has already moved.
 *
 * ### Moving several at once
 *
 * The dragged block is **every selected item**, in *selection order* — the order the user ticked
 * them in, not their order in the list. Two people can disagree about which is more natural, but
 * selection order is the one the user actually authored: they chose those items one at a time, and
 * arriving in that sequence is the only outcome they could have predicted.
 *
 * The block is removed from the list and re-inserted in front of whatever card the finger is over
 * on release. Dropping onto a card that is itself selected is a no-op rather than an error — the
 * block is already there.
 *
 * ### Where "position" is stored
 *
 * Nowhere in this file. Reordering rewrites `manualOrder` on the affected rows (see the call
 * sites), and that column only decides anything under the "Custom order" sort — which is why the
 * gesture is enabled by that sort and inert under every other one. Dragging a card in "Title A–Z"
 * would be a request the next recomposition is obliged to ignore, so it is not offered.
 */
class ReorderDragState internal constructor(
    /** True while a drag is in flight; call sites can use it to suppress scroll-driven work. */
    var dragging: Boolean = false
)

@Composable
fun rememberReorderDragState(): ReorderDragState = remember { ReorderDragState() }

/**
 * Attach the gesture to one card in a [LazyColumn]-backed list.
 *
 * @param id this card's stable key — the same value passed to `items(key = ...)`.
 * @param enabled false disables reordering entirely (any sort other than Custom).
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
): Modifier = this.pointerInput(id, enabled) {
    if (!enabled) return@pointerInput
    // Where the finger is, in the list's own viewport coordinates. Derived from the item's laid-out
    // offset rather than tracked in window space, so it stays correct even as the list scrolls or
    // the items around it change height mid-drag.
    var pointerInViewport = 0f
    detectDragGesturesAfterLongPress(
        onDragStart = { local ->
            onLongPress()
            state.dragging = true
            val origin = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset ?: 0
            pointerInViewport = origin + local.y
        },
        onDragEnd = {
            state.dragging = false
            onDrop(listState.keyAtViewportY(pointerInViewport))
        },
        onDragCancel = { state.dragging = false },
        onDrag = { change, dragAmount ->
            change.consume()
            pointerInViewport += dragAmount.y
        }
    )
}

/** The grid twin, for the Notes home page. Same contract; the hit test is two-dimensional. */
fun Modifier.reorderableGridItem(
    id: Long,
    enabled: Boolean,
    gridState: LazyGridState,
    state: ReorderDragState,
    onLongPress: () -> Unit,
    onDrop: (targetId: Long?) -> Unit
): Modifier = this.pointerInput(id, enabled) {
    if (!enabled) return@pointerInput
    var pointer = Offset.Zero
    detectDragGesturesAfterLongPress(
        onDragStart = { local ->
            onLongPress()
            state.dragging = true
            val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
            val origin = info?.offset
            pointer = Offset(
                (origin?.x ?: 0) + local.x,
                (origin?.y ?: 0) + local.y
            )
        },
        onDragEnd = {
            state.dragging = false
            onDrop(gridState.keyAtViewportOffset(pointer))
        },
        onDragCancel = { state.dragging = false },
        onDrag = { change, dragAmount ->
            change.consume()
            pointer += dragAmount
        }
    )
}

/**
 * Which visible item contains [y].
 *
 * Uses the laid-out offsets rather than assuming a uniform row height, because the cards are not a
 * uniform height — a note with a checklist preview is taller than a bare one, and "drag distance
 * divided by row height" would drift further out of step the further the finger travelled.
 */
private fun LazyListState.keyAtViewportY(y: Float): Long? =
    layoutInfo.visibleItemsInfo
        .firstOrNull { y >= it.offset && y < it.offset + it.size }
        ?.key as? Long

private fun LazyGridState.keyAtViewportOffset(p: Offset): Long? =
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
