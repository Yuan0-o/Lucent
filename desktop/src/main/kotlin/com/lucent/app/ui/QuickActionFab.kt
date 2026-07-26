package com.lucent.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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
    onNeedSelection: () -> Unit = {},
    // Task 1. Which attributes are currently in force — the styles covering the selection when there
    // is one, and otherwise the styles the user has ARMED for whatever they type next. The panel
    // lights its buttons from these, which is the only way "press bold, then type" is discoverable:
    // without it the button would look identical before and after being pressed.
    activeKinds: Set<RichSpan.Kind> = emptySet(),
    activeHighlight: Int? = null
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    // One driver for every satellite's position and fade, so they cannot disagree mid-animation.
    val reveal by animateFloatAsState(targetValue = if (expanded) 1f else 0f, label = "quickActionRing")

    // Which page is showing. The formatting actions are a second page rather than more satellites
    // on the first, and that second page is a GRID rather than an arc — see [FormatPanel] for the
    // arithmetic that rules the ring out past about three controls.
    var formatPage by remember { mutableStateOf(false) }
    var highlightOpen by remember { mutableStateOf(false) }
    // Collapsing the ring resets it, so re-opening never lands on a page the user did not choose.
    if (!expanded && (formatPage || highlightOpen)) {
        formatPage = false
        highlightOpen = false
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {

        // ---- The formatting panel (task 1) --------------------------------------------------
        //
        // This used to be a second and third PAGE OF THE RING, and the ring could not hold them.
        // Five satellites on a 90° arc at 72dp put their centres 28dp apart while each control is
        // 40dp across, so they overlapped by a third of their own width; the task asks for seven,
        // which on the same arc would be 19dp apart. There is no radius that fixes this on a phone:
        // a 40dp target every 44dp needs an arc 170dp in radius, which is off the side of the
        // screen. A ring is the wrong container for more than about three things.
        //
        // So the formatting controls are a grid instead — four columns, laid out downward from the
        // top-right, every cell a full 52dp with room to spare between them. It also solves the
        // *other* half of the report ("two of them, I don't know what they do"): a grid has room for
        // a label under each icon, and the ring never did.
        if (reveal > 0.01f && (formatPage || highlightOpen)) {
            FormatPanel(
                highlightOpen = highlightOpen,
                canUndo = canUndo,
                canRedo = canRedo,
                activeKinds = activeKinds,
                activeHighlight = activeHighlight,
                onUndo = onUndo,
                onRedo = onRedo,
                onToggleStyle = onToggleStyle,
                onClearStyle = onClearStyle,
                onOpenHighlights = { highlightOpen = true },
                onCloseHighlights = { highlightOpen = false }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Box(modifier = Modifier.size(RING_DIAMETER), contentAlignment = Alignment.BottomEnd) {
            // Page 1 keeps the ring: three satellites at 45° apart are 55dp between centres, which
            // is the one case the arc genuinely fits.
            if (reveal > 0.01f && !formatPage && !highlightOpen) {
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

/**
 * The formatting toolbar (task 1) — a grid, deliberately, and not another ring.
 *
 * Seven 40dp controls cannot share a 72dp arc: adjacent centres would be 19dp apart. Four columns
 * of 54dp cells hold all seven with clear space between every pair, and — the part the ring could
 * never do — leave room for a **label under each icon**, which is the whole answer to "I don't know
 * what these two do". A brush and a "TT" are not self-explanatory; "Highlight" and "Light" are.
 *
 * A lit cell means the attribute is IN FORCE: applied to the current selection if there is one, or
 * armed for whatever gets typed next if there isn't. Without that feedback, arming a style before
 * typing is invisible and therefore not a feature.
 */
@Composable
private fun FormatPanel(
    highlightOpen: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    activeKinds: Set<RichSpan.Kind>,
    activeHighlight: Int?,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleStyle: (RichSpan.Kind, Int) -> Unit,
    onClearStyle: () -> Unit,
    onOpenHighlights: () -> Unit,
    onCloseHighlights: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.End
    ) {
        if (highlightOpen) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RichHighlightColors.forEachIndexed { index, swatch ->
                    PanelCell(
                        label = com.lucent.app.i18n.S.richTextHighlight,
                        enabled = true,
                        active = activeHighlight == index,
                        onClick = { onToggleStyle(RichSpan.Kind.HIGHLIGHT, index); onCloseHighlights() }
                    ) {
                        Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(swatch))
                    }
                }
                PanelCell(
                    label = com.lucent.app.i18n.S.actionBack,
                    enabled = true,
                    active = false,
                    onClick = onCloseHighlights
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            return@Column
        }

        // Seven controls, four to a row. The order is the order of use: the three that change how
        // text looks, then the highlighter, then the two that take a change back, then clear.
        val cells: List<PanelItem> = listOf(
            PanelItem(Icons.Default.FormatBold, com.lucent.app.i18n.S.richTextBold, true,
                RichSpan.Kind.BOLD in activeKinds) { onToggleStyle(RichSpan.Kind.BOLD, 0) },
            PanelItem(Icons.Default.FormatItalic, com.lucent.app.i18n.S.richTextItalic, true,
                RichSpan.Kind.ITALIC in activeKinds) { onToggleStyle(RichSpan.Kind.ITALIC, 0) },
            PanelItem(Icons.Default.FormatSize, com.lucent.app.i18n.S.richTextLight, true,
                RichSpan.Kind.LIGHT in activeKinds) { onToggleStyle(RichSpan.Kind.LIGHT, 0) },
            PanelItem(Icons.Default.Brush, com.lucent.app.i18n.S.richTextHighlight, true,
                activeHighlight != null) { onOpenHighlights() },
            PanelItem(Icons.AutoMirrored.Filled.Undo, com.lucent.app.i18n.S.actionUndo, canUndo, false) { onUndo() },
            PanelItem(Icons.AutoMirrored.Filled.Redo, com.lucent.app.i18n.S.actionRedo, canRedo, false) { onRedo() },
            PanelItem(Icons.Default.FormatClear, com.lucent.app.i18n.S.richTextClear, true, false) { onClearStyle() }
        )
        cells.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { item ->
                    PanelCell(item.label, item.enabled, item.active, item.onClick) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private data class PanelItem(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
    val active: Boolean,
    val onClick: () -> Unit
)

/** One 54dp cell: a round icon well with its name underneath. */
@Composable
private fun PanelCell(
    label: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color.White.copy(alpha = 0.26f) else Color.Transparent)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .pointerInput(enabled, active) {
                detectTapGestures(onTap = { if (enabled) { Haptics.tick(context); onClick() } })
            }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) { content() }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 9.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
