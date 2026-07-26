package com.lucent.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lucent.app.data.RichSpan
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A snapshot undo/redo stack for a single text field (the "previous step / next step" half of task
 * A7).
 *
 * ### Why snapshots rather than operations
 *
 * An operation-based stack (insert 3 chars at 41, delete 2 at 12) is smaller and is what a real
 * word processor uses. It is also a source of subtle bugs whenever anything writes to the field
 * that isn't the keyboard — and in Lucent plenty does: the assistant edits notes, a template fills
 * one in, a version restore replaces the whole body. Every one of those has to be expressible as an
 * operation or the stack silently desynchronises from the text, and a desynchronised undo stack
 * doesn't fail loudly, it corrupts the document one keystroke at a time.
 *
 * Whole-string snapshots cannot desynchronise. A note body is kilobytes and the depth is capped, so
 * the memory this "wastes" is a rounding error against the bitmap of a single attachment thumbnail.
 *
 * ### Coalescing
 *
 * [record] merges a change into the previous entry when both are small, contiguous typing within
 * [COALESCE_WINDOW_MS]. Without it, undo steps back one character at a time and is useless for the
 * thing people actually want it for, which is "put back the paragraph I just wiped".
 */
class TextUndoStack(initial: String = "") {

    private val entries = ArrayDeque<String>().apply { addLast(initial) }
    private var cursor = 0
    private var lastRecordedAt = 0L

    val canUndo: Boolean get() = cursor > 0
    val canRedo: Boolean get() = cursor < entries.size - 1

    /** The text as the stack currently believes it to be. */
    fun current(): String = entries[cursor]

    /**
     * Note that the text is now [value]. A no-op when nothing changed, so a recomposition that
     * merely re-reads the field cannot push a duplicate entry.
     */
    fun record(value: String, now: Long = System.currentTimeMillis()) {
        if (value == entries[cursor]) return

        // Anything recorded after an undo discards the redo branch — the standard rule, and the
        // only one that keeps "redo" meaning a single unambiguous thing.
        while (entries.size > cursor + 1) entries.removeLast()

        val coalesce = now - lastRecordedAt < COALESCE_WINDOW_MS &&
            kotlin.math.abs(value.length - entries[cursor].length) <= COALESCE_MAX_DELTA &&
            cursor > 0
        if (coalesce) {
            entries[cursor] = value
        } else {
            entries.addLast(value)
            cursor = entries.size - 1
            while (entries.size > MAX_DEPTH) {
                entries.removeFirst()
                cursor--
            }
        }
        lastRecordedAt = now
    }

    /** Step back one entry and return the text to show, or null when there is nothing to undo. */
    fun undo(): String? {
        if (!canUndo) return null
        cursor--
        // Break coalescing: text typed straight after an undo must start its own entry, or it would
        // be merged into the state we just stepped back to.
        lastRecordedAt = 0L
        return entries[cursor]
    }

    fun redo(): String? {
        if (!canRedo) return null
        cursor++
        lastRecordedAt = 0L
        return entries[cursor]
    }

    private companion object {
        const val MAX_DEPTH = 100
        const val COALESCE_WINDOW_MS = 900L
        const val COALESCE_MAX_DELTA = 3
    }
}

/**
 * The floating control in the bottom-right of a detail page (task A7).
 *
 * ### Three states, one button
 *
 * - **Scrolling up** → a "jump to top" arrow.
 * - **Scrolling down** → a "jump to bottom" arrow.
 * - **Idle** → a tool glyph that opens a small ring of editing actions.
 *
 * The task asks for a *fast scroll* rather than a jump, and that distinction is honoured by the
 * call site: it animates the scroll instead of snapping, so the page travels and the reader keeps
 * their bearings. A snap to an unfamiliar position is disorienting in a way that a fast scroll
 * isn't, which is the whole reason the request specified it.
 *
 * ### Why the actions are a ring and not a menu
 *
 * A dropdown would open upward into the text being edited and cover it. The ring expands around a
 * control that is already in the corner, into space that is empty by construction, and every action
 * stays within a thumb's arc of where the thumb already is. Tapping the centre again commits and
 * collapses — and, as the task requires, **stays on the current page**: this control never
 * navigates.
 */
@Composable
fun QuickActionFab(
    scrollingUp: Boolean,
    scrollingDown: Boolean,
    expanded: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onScrollTop: () -> Unit,
    onScrollBottom: () -> Unit,
    onToggleExpanded: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
    // ---- INTEGRATION: C-group task 20's toolbar lives here (see the block comment below) ----
    // Optional so every existing call site keeps compiling unchanged and keeps its two-satellite
    // ring; only the note and task editors pass these.
    richTextEnabled: Boolean = false,
    hasSelection: Boolean = false,
    onToggleStyle: (RichSpan.Kind, Int) -> Unit = { _, _ -> },
    onClearStyle: () -> Unit = {},
    onNeedSelection: () -> Unit = {}
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    // One driver for every satellite's position and fade, so they cannot disagree mid-animation.
    val reveal by animateFloatAsState(targetValue = if (expanded) 1f else 0f, label = "quickActionRing")

    // Which page of the ring is showing. The formatting actions are a SECOND page rather than more
    // satellites on the first: task 20 asks for three weights, italic and five highlighters, which
    // is nine controls, and nine satellites on a 72dp arc are smaller than a fingertip and no longer
    // distinguishable at a glance. Two pages of five keep every target at 40dp — the size the rest
    // of this ring already uses and the size a thumb can actually hit.
    var formatPage by remember { mutableStateOf(false) }
    var highlightOpen by remember { mutableStateOf(false) }
    // Collapsing the ring resets it, so re-opening never lands on a page the user did not choose.
    if (!expanded && (formatPage || highlightOpen)) {
        formatPage = false
        highlightOpen = false
    }

    Box(modifier = modifier.size(RING_DIAMETER), contentAlignment = Alignment.BottomEnd) {
        if (reveal > 0.01f) {
            when {
                // ---- Page 2b: the five highlighter colours ----
                highlightOpen -> {
                    val angles = RING_ANGLES_5
                    RichHighlightColors.forEachIndexed { index, swatch ->
                        RingSwatch(
                            color = swatch,
                            label = com.lucent.app.i18n.S.richTextHighlight,
                            angleDegrees = angles[index],
                            reveal = reveal,
                            enabled = hasSelection
                        ) {
                            onToggleStyle(RichSpan.Kind.HIGHLIGHT, index)
                            highlightOpen = false
                        }
                    }
                }
                // ---- Page 2a: weights, italic, highlighter, clear ----
                formatPage -> {
                    RingAction(Icons.Default.FormatBold, com.lucent.app.i18n.S.richTextBold, RING_ANGLES_5[0], reveal, hasSelection) {
                        if (hasSelection) onToggleStyle(RichSpan.Kind.BOLD, 0) else onNeedSelection()
                    }
                    RingAction(Icons.Default.FormatItalic, com.lucent.app.i18n.S.richTextItalic, RING_ANGLES_5[1], reveal, hasSelection) {
                        if (hasSelection) onToggleStyle(RichSpan.Kind.ITALIC, 0) else onNeedSelection()
                    }
                    RingAction(Icons.Default.FormatSize, com.lucent.app.i18n.S.richTextLight, RING_ANGLES_5[2], reveal, hasSelection) {
                        if (hasSelection) onToggleStyle(RichSpan.Kind.LIGHT, 0) else onNeedSelection()
                    }
                    RingAction(Icons.Default.Brush, com.lucent.app.i18n.S.richTextHighlight, RING_ANGLES_5[3], reveal, hasSelection) {
                        if (hasSelection) highlightOpen = true else onNeedSelection()
                    }
                    RingAction(Icons.Default.FormatClear, com.lucent.app.i18n.S.richTextClear, RING_ANGLES_5[4], reveal, hasSelection) {
                        if (hasSelection) onClearStyle() else onNeedSelection()
                    }
                }
                // ---- Page 1: the editing actions that were always here ----
                else -> {
                    // Laid out on an arc that opens up and to the left — the only quadrant that is
                    // free when the control sits in the bottom-right corner. See [RING_ANGLES_5].
                    RingAction(Icons.AutoMirrored.Filled.Undo, com.lucent.app.i18n.S.actionUndo, RING_ANGLES_3[0], reveal, canUndo, onUndo)
                    RingAction(Icons.AutoMirrored.Filled.Redo, com.lucent.app.i18n.S.actionRedo, RING_ANGLES_3[1], reveal, canRedo, onRedo)
                    // The formatting page is offered only when the switch is on, so a user who never
                    // turned rich text on sees the ring exactly as group A shipped it.
                    if (richTextEnabled) {
                        RingAction(
                            Icons.Default.TextFormat,
                            com.lucent.app.i18n.S.a11yRichTextToolbar,
                            RING_ANGLES_3[2], reveal, true
                        ) { formatPage = true }
                    }
                }
            }
        }

        val icon: ImageVector = when {
            expanded -> Icons.Default.Check
            scrollingUp -> Icons.Default.KeyboardArrowUp
            scrollingDown -> Icons.Default.KeyboardArrowDown
            else -> Icons.Default.Build
        }
        val label = when {
            expanded -> com.lucent.app.i18n.S.actionSave
            scrollingUp -> com.lucent.app.i18n.S.a11yScrollTop
            scrollingDown -> com.lucent.app.i18n.S.a11yScrollBottom
            else -> com.lucent.app.i18n.S.a11yQuickEdit
        }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(expanded, scrollingUp, scrollingDown) {
                    detectTapGestures(onTap = {
                        Haptics.tick(context)
                        when {
                            // On a formatting page the centre steps BACK a page instead of
                            // committing. Committing from there would mean the same tap sometimes
                            // ends the edit and sometimes closes a submenu, depending on a state the
                            // user cannot see from the button itself.
                            highlightOpen -> highlightOpen = false
                            formatPage -> formatPage = false
                            expanded -> onToggleExpanded()
                            scrollingUp -> onScrollTop()
                            scrollingDown -> onScrollBottom()
                            else -> onToggleExpanded()
                        }
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = onGradient, modifier = Modifier.size(26.dp))
        }
    }
}

/** One satellite on the ring: placed by angle, faded and pushed outward by [reveal]. */
@Composable
private fun RingAction(
    icon: ImageVector,
    label: String,
    angleDegrees: Float,
    reveal: Float,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val radians = Math.toRadians(angleDegrees.toDouble())
    val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { RING_RADIUS.toPx() }
    val dx = (cos(radians) * radiusPx * reveal).roundToInt()
    val dy = (sin(radians) * radiusPx * reveal).roundToInt()

    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(dx, dy) }
            .size(40.dp)
            .graphicsLayer { alpha = reveal * (if (enabled) 1f else 0.35f) }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.30f))
            .pointerInput(enabled) {
                detectTapGestures(onTap = { if (enabled) { Haptics.tick(context); onClick() } })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = onGradient, modifier = Modifier.size(20.dp))
    }
}


/**
 * One highlighter colour on the ring. A filled disc rather than an icon, because the thing being
 * chosen IS the colour — an icon tinted five ways is five icons nobody can tell apart at 20dp.
 */
@Composable
private fun RingSwatch(
    color: Color,
    label: String,
    angleDegrees: Float,
    reveal: Float,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val radians = Math.toRadians(angleDegrees.toDouble())
    val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { RING_RADIUS.toPx() }
    val dx = (cos(radians) * radiusPx * reveal).roundToInt()
    val dy = (sin(radians) * radiusPx * reveal).roundToInt()

    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(dx, dy) }
            .size(40.dp)
            .graphicsLayer { alpha = reveal * (if (enabled) 1f else 0.35f) }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.30f))
            .pointerInput(enabled) {
                detectTapGestures(onTap = { if (enabled) { Haptics.tick(context); onClick() } })
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(color)
                .semantics { contentDescription = label }
        )
    }
}

/**
 * The five highlighter colours the editor offers.
 *
 * Derived from [com.lucent.app.data.RichText.HIGHLIGHT_ARGB] rather than restated, so the swatch a
 * user taps, the highlight drawn in the field, the DOCX run and both PDF writers are the same
 * colour by construction. Two lists that are "obviously the same" drift the first time one of them
 * is adjusted.
 */
val RichHighlightColors: List<Color> = com.lucent.app.data.RichText.HIGHLIGHT_ARGB.map { Color(it) }

/**
 * Where the satellites sit, measured in SCREEN space: 180° is straight to the left of the centre
 * button, 270° is straight above it, and the values between sweep the up-and-left quadrant.
 *
 * This is deliberately not the mathematical convention, and the difference *was* the bug. Satellites
 * are placed with `dx = cos(a) * r`, `dy = sin(a) * r`, and on a canvas y grows DOWNWARD — so the
 * previous 120°–240° arc sent two of the five satellites BELOW the centre button. Below the button
 * is the bottom edge of the screen and the navigation capsule, and, more to the point, it is outside
 * this ring's own 160dp box. Compose does not deliver pointer events to a child laid out beyond its
 * parent's bounds, so those two controls — bold and italic — were drawn perfectly and could not be
 * tapped at all. That is the whole of the "italic and weight do nothing" report: the feature was
 * there, the buttons were not reachable.
 *
 * Confining every angle to 180°..270° keeps each satellite inside the box (the maximum excursion is
 * 72dp up or left from a 40dp control bottom-end-aligned in a 160dp box, which fits with 48dp to
 * spare) and inside the only quadrant guaranteed to be empty when the control sits in the
 * bottom-right corner.
 */
private val RING_ANGLES_5 = listOf(180f, 202.5f, 225f, 247.5f, 270f)

/** The same arc for the three-satellite first page: left, up-left, up. */
private val RING_ANGLES_3 = listOf(180f, 225f, 270f)

private val RING_DIAMETER = 160.dp
private val RING_RADIUS = 72.dp
