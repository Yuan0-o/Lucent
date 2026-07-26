package com.lucent.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Doodle notes (task A22): a whiteboard note kind alongside plain text and checklists.
 *
 * ### The stroke model, and why coordinates are normalised
 *
 * A stroke is a colour, a width, and a list of points. The points are stored as **fractions of the
 * canvas (0..1), not pixels**, and that is the single most important decision in this file.
 * Lucent runs on a phone and on Windows, on screens whose widths differ by a factor of five, and a
 * doodle drawn on one and opened on the other has to be the same drawing. Pixel coordinates would
 * make a note that was legible on a desktop unreadable on a phone and vice versa; fractions make
 * the drawing resolution-independent by construction, and the canvas simply scales them.
 *
 * Widths are normalised the same way, against the canvas width, so a "thin" line stays visually
 * thin rather than becoming a hairline on a large screen.
 *
 * ### Why JSON in a text column
 *
 * The same reasoning `checklist` and `attachments` already follow in this codebase: a small,
 * self-describing blob in a TEXT column travels through the existing backup format, the existing
 * encryption, and the existing migration machinery without any of them needing to learn a new
 * shape. A binary format would be smaller and would need all three taught about it.
 */
object Doodle {

    /** The palette the task asks for: five colours. Black first because most notes are just ink. */
    val COLORS: List<Color> = listOf(
        Color(0xFF1B1B1F),
        Color(0xFFE53935),
        Color(0xFF1E88E5),
        Color(0xFF43A047),
        Color(0xFFFFB300)
    )

    /** Three widths, as fractions of the canvas width so they scale with the drawing. */
    val WIDTHS: List<Float> = listOf(0.004f, 0.010f, 0.022f)

    data class Stroke(
        val color: Int,
        val width: Float,
        val points: List<Offset>
    )

    /** Never throws: a corrupt or hand-edited blob yields an empty drawing rather than a crash. */
    fun parse(json: String?): List<Stroke> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val pts = o.optJSONArray("p") ?: return@mapNotNull null
                // Points are flattened [x0,y0,x1,y1,...]: half the JSON overhead of an array of
                // objects, and a doodle can easily carry a few thousand of them.
                val points = ArrayList<Offset>(pts.length() / 2)
                var k = 0
                while (k + 1 < pts.length()) {
                    points.add(Offset(pts.optDouble(k).toFloat(), pts.optDouble(k + 1).toFloat()))
                    k += 2
                }
                if (points.isEmpty()) null
                else Stroke(o.optInt("c", 0xFF1B1B1F.toInt()), o.optDouble("w", 0.01).toFloat(), points)
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun serialize(strokes: List<Stroke>): String {
        val arr = JSONArray()
        strokes.forEach { s ->
            val pts = JSONArray()
            s.points.forEach { pts.put(it.x.toDouble()); pts.put(it.y.toDouble()) }
            arr.put(JSONObject().put("c", s.color).put("w", s.width.toDouble()).put("p", pts))
        }
        return arr.toString()
    }

    fun isEmpty(json: String?): Boolean = parse(json).isEmpty()
}

/**
 * Multi-page whiteboards.
 *
 * ### Storage, and why it stays backwards compatible
 *
 * A doodle used to be one JSON array of strokes. It is now an object carrying an array of those
 * arrays — one per canvas — and [parse] still accepts the old bare array as a single page. Nothing
 * has to be migrated, no column changes, and a note written by an older build opens with its one
 * canvas intact and can grow a second one immediately.
 *
 * Pages are identified by position, not by a stored name. "Canvas 2" is simply the second canvas,
 * so deleting or reordering never leaves a name pointing at the wrong drawing — there is no name to
 * point anywhere.
 */
object DoodlePages {

    fun parse(json: String?): List<String> {
        val raw = json?.trim().orEmpty()
        if (raw.isEmpty()) return listOf("")
        // Legacy: a bare array of strokes is one page.
        if (raw.startsWith("[")) return listOf(raw)
        return try {
            val arr = JSONObject(raw).optJSONArray("pages") ?: return listOf("")
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) out.add(arr.optString(i, ""))
            if (out.isEmpty()) listOf("") else out
        } catch (_: Throwable) {
            // Unreadable container: fall back to treating the whole string as one page rather than
            // discarding a drawing we merely failed to understand.
            listOf(raw)
        }
    }

    fun serialize(pages: List<String>): String {
        val kept = if (pages.isEmpty()) listOf("") else pages
        // One page and nothing drawn on it is the empty doodle, and must serialise back to "" so
        // `Note.isDoodle` and every "is this blank?" check keep answering the way they always did.
        if (kept.size == 1 && Doodle.isEmpty(kept.first())) return ""
        if (kept.size == 1) return kept.first()
        val arr = JSONArray()
        kept.forEach { arr.put(it) }
        return JSONObject().put("pages", arr).toString()
    }

    /** How many canvases carry anything at all. */
    fun drawnCount(pages: List<String>): Int = pages.count { !Doodle.isEmpty(it) }
}

/**
 * The editable whiteboard.
 *
 * Drawing is a plain drag: touch down starts a stroke, movement extends it, lifting ends it. There
 * is no eraser tool and no save button — every stroke is committed as it is finished, which is what
 * "the canvas saves itself" means here. What the toolbar does carry is **undo and redo**, and they
 * work on whole-canvas snapshots rather than on individual strokes, so "clear the canvas" is
 * undoable like anything else. A destructive action you cannot take back is the one thing a drawing
 * surface must not have.
 */
@Composable
fun DoodleEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    // Task 12. When true the board fills whatever it is given instead of standing at a fixed 340dp,
    // and the corner control collapses rather than expands. The full-screen editor is this same
    // composable hosted in a Dialog, so there is exactly one whiteboard implementation and the two
    // sizes cannot drift apart in behaviour.
    fullScreen: Boolean = false,
    onToggleFullScreen: (() -> Unit)? = null,
    /** Adds a canvas after this one. Null hides the button (the preview host has no use for it). */
    onAddPage: (() -> Unit)? = null,
    /** False shows the drawing read-only — the preview mode pages open in until Edit is pressed. */
    editable: Boolean = true
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    // ---- Task 3.3: the toolbar has to be legible against ITS OWN backdrop ----
    //
    // In full screen the panel behind this toolbar is near-black, but the icons were tinted with the
    // app's on-gradient colour — which on every light palette is near-black too. The result was a
    // row of controls that were technically drawn and effectively invisible.
    val toolTint = if (fullScreen) Color(0xFFEDEDF2) else onGradient
    val toolMuted = if (fullScreen) Color(0xFFEDEDF2).copy(alpha = 0.60f) else onGradientMuted

    // ---- Task 3.5: why the stroke list is NOT keyed on [value] ----
    //
    // It used to be `remember(value) { mutableStateOf(...) }`. Every commit changes `value`, which
    // re-keys the remember and allocates a BRAND NEW MutableState — but the pointerInput block that
    // owns the drawing gesture is keyed only on the pen settings, so its still-running coroutine
    // keeps a reference to the OLD state object and the OLD `commit` closure.
    //
    // That is the "clear the canvas, draw one stroke, and everything comes back" bug, exactly: clear
    // wrote an empty list to the *new* state, and the next stroke was committed by the *old* closure
    // as `oldStrokes + newStroke`, resurrecting every stroke the user had just deleted.
    val strokesState = remember { mutableStateOf(Doodle.parse(value)) }
    var strokes by strokesState
    var lastKnownValue by remember { mutableStateOf(value) }
    if (value != lastKnownValue) {
        lastKnownValue = value
        strokes = Doodle.parse(value)
    }
    // Whole-canvas snapshots, for the same reason TextUndoStack uses them: an operation log has to
    // express every writer, and "clear" plus "load a different page" are writers that an
    // add-one-stroke log cannot describe without desynchronising from the drawing.
    val past = remember { mutableStateOf(listOf<List<Doodle.Stroke>>()) }
    val future = remember { mutableStateOf(listOf<List<Doodle.Stroke>>()) }
    var colorIndex by remember { mutableStateOf(0) }
    var widthIndex by remember { mutableStateOf(1) }
    // The stroke currently under the finger, kept separate so it can be drawn live without
    // re-serializing the whole drawing on every pointer sample.
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    val emitValue = androidx.compose.runtime.rememberUpdatedState(onValueChange)
    fun write(updated: List<Doodle.Stroke>) {
        strokes = updated
        val json = Doodle.serialize(updated)
        // Record what we wrote BEFORE emitting it, so the sync check above recognises the value
        // coming back as our own and leaves the list alone.
        lastKnownValue = json
        emitValue.value(json)
    }
    fun commit(updated: List<Doodle.Stroke>) {
        past.value = (past.value + listOf(strokes)).takeLast(UNDO_DEPTH)
        future.value = emptyList()   // a new mark discards the redo branch, as it must
        write(updated)
    }
    fun undo() {
        val prev = past.value.lastOrNull() ?: return
        future.value = future.value + listOf(strokes)
        past.value = past.value.dropLast(1)
        write(prev)
    }
    fun redo() {
        val next = future.value.lastOrNull() ?: return
        past.value = past.value + listOf(strokes)
        future.value = future.value.dropLast(1)
        write(next)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // ---- The board -----------------------------------------------------------------------
        Box(
            modifier = (if (fullScreen) Modifier.fillMaxWidth().weight(1f)
                        else Modifier.fillMaxWidth().height(340.dp))
                .clip(RoundedCornerShape(16.dp))
                // Opaque white, not glass: ink needs a surface with a known colour behind it, and
                // the animated gradient showing through would change what a drawing looks like
                // depending on where the background happened to be that second.
                .background(Color.White)
                .border(1.dp, toolMuted.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .pointerInput(colorIndex, widthIndex, editable) {
                    if (!editable) return@pointerInput
                    detectDragGestures(
                        onDragStart = { start ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            live = listOf(Offset(start.x / w, start.y / h))
                        },
                        onDragEnd = {
                            if (live.size > 1) {
                                commit(
                                    strokes + Doodle.Stroke(
                                        color = Doodle.COLORS[colorIndex].toArgb(),
                                        width = Doodle.WIDTHS[widthIndex],
                                        points = live
                                    )
                                )
                            }
                            live = emptyList()
                        },
                        onDragCancel = { live = emptyList() },
                        onDrag = { change, _ ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            live = live + Offset(change.position.x / w, change.position.y / h)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                canvasSize = size
                strokes.forEach { drawDoodleStroke(it.points, Color(it.color), it.width) }
                if (live.size > 1) {
                    drawDoodleStroke(live, Doodle.COLORS[colorIndex], Doodle.WIDTHS[widthIndex])
                }
            }
            if (strokes.isEmpty() && live.isEmpty()) {
                Text(
                    com.lucent.app.i18n.S.doodleEmpty,
                    color = Color(0xFF9E9E9E),
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            // Expand / collapse, top-right, mirroring where ExpandableGlassTextField puts its own.
            if (onToggleFullScreen != null) {
                IconButton(
                    onClick = onToggleFullScreen,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(36.dp)
                ) {
                    Icon(
                        if (fullScreen) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                        contentDescription = if (fullScreen) com.lucent.app.i18n.S.collapseTextBox
                                             else com.lucent.app.i18n.S.expandTextBox,
                        tint = Color(0xFF616161),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            // "Add a canvas", bottom-right, the same size as the expand control above it.
            if (onAddPage != null) {
                IconButton(
                    onClick = onAddPage,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = com.lucent.app.i18n.S.doodleAddPage,
                        tint = Color(0xFF616161),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (!editable) return@Column

        Spacer(modifier = Modifier.height(10.dp))

        // ---- Pen: five colours, three widths ---------------------------------------------------
        //
        // Task 12. This row carries five colours, three widths, undo, redo and clear — twelve
        // controls. On a phone they do not fit, and the previous answer was to squeeze them: undo
        // and clear were shrunk to 36dp, well under the 48dp a fingertip actually needs. Shrinking
        // controls to fit a row is the wrong trade every time; the row scrolls instead, and every
        // button is back at a size a thumb can land on.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Doodle.COLORS.forEachIndexed { index, c ->
                Box(
                    modifier = Modifier
                        .size(if (index == colorIndex) 30.dp else 24.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            if (index == colorIndex) 2.dp else 1.dp,
                            toolTint.copy(alpha = if (index == colorIndex) 0.9f else 0.3f),
                            CircleShape
                        )
                        .clickableNoRipple { colorIndex = index }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Doodle.WIDTHS.forEachIndexed { index, w ->
                // The swatch is drawn at the width it selects, so the control shows the outcome
                // rather than naming it.
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(toolTint.copy(alpha = if (index == widthIndex) 0.22f else 0.08f))
                        .clickableNoRipple { widthIndex = index },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size((4 + index * 5).dp)
                            .clip(CircleShape)
                            .background(toolTint)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            // A fixed gap rather than weight(1f): a horizontally scrolling Row has no bounded
            // width to hand out, so a weighted spacer cannot be measured here.
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(onClick = { undo() }, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = com.lucent.app.i18n.S.doodleUndoStroke,
                    tint = if (past.value.isEmpty()) toolMuted else toolTint
                )
            }
            IconButton(onClick = { redo() }, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = com.lucent.app.i18n.S.doodleRedoStroke,
                    tint = if (future.value.isEmpty()) toolMuted else toolTint
                )
            }
            IconButton(onClick = { commit(emptyList()) }, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = com.lucent.app.i18n.S.doodleClear,
                    tint = toolTint
                )
            }
        }
    }
}

private const val UNDO_DEPTH = 50

/**
 * The whiteboard, its full-screen mode, and its extra canvases.
 *
 * The expanded canvas is the *same* [DoodleEditor] hosted in a Dialog, not a second implementation:
 * a drawing surface that behaved even slightly differently at two sizes would be a bug factory, and
 * the state it edits is the caller's `value` either way.
 *
 * ### Pages
 *
 * "+" in the board's bottom corner appends a canvas and moves to it. Every canvas is listed as a bar
 * underneath — tapping one **previews** it, and previews are read-only until Edit is pressed. That
 * separation is deliberate: the bars sit directly under a live drawing surface, and a stray tap that
 * silently made a different canvas editable is exactly how someone draws on the wrong page.
 *
 * There is no save button anywhere, by design. Each stroke commits as it is finished, so a canvas is
 * saved the moment it exists, and the only way to lose work is to undo it on purpose.
 */
@Composable
fun ExpandableDoodleEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var expanded by remember { mutableStateOf(false) }
    var activeIndex by remember { mutableStateOf(0) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }

    val pages = remember(value) { DoodlePages.parse(value) }
    val index = activeIndex.coerceIn(0, pages.lastIndex)

    fun writePages(updated: List<String>) = onValueChange(DoodlePages.serialize(updated))
    fun updateActive(pageJson: String) {
        writePages(pages.toMutableList().also { it[index] = pageJson })
    }
    fun addPage() {
        writePages(pages + "")
        activeIndex = pages.size
    }

    // The board takes its Modifier from the call site rather than building one: the full-screen copy
    // needs `weight(1f)`, and `weight` is a ColumnScope extension that does not resolve inside a
    // lambda declared out here. Handing the modifier in keeps one board with two hosts.
    val board: @Composable (Boolean, Modifier) -> Unit = { full, boardModifier ->
        DoodleEditor(
            value = pages[index],
            onValueChange = { updateActive(it) },
            modifier = boardModifier,
            fullScreen = full,
            onToggleFullScreen = { expanded = !expanded },
            onAddPage = { addPage() }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        board(false, modifier)

        // ---- The canvas bars ----
        if (pages.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            pages.forEachIndexed { i, page ->
                val current = i == index
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(onGradient.copy(alpha = if (current) 0.16f else 0.07f))
                        .clickable { previewIndex = i }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        com.lucent.app.i18n.S.doodlePageName(i + 1),
                        color = onGradient,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (Doodle.isEmpty(page)) com.lucent.app.i18n.S.doodleEmpty else "",
                        color = onGradientMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    // ---- Full screen ----
    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFF101014)).padding(12.dp)
            ) {
                board(true, Modifier.weight(1f))
            }
        }
    }

    // ---- Preview, with an explicit way into editing ----
    previewIndex?.let { i ->
        if (i in pages.indices) {
            Dialog(
                onDismissRequest = { previewIndex = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF101014)).padding(16.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            com.lucent.app.i18n.S.doodlePageName(i + 1),
                            color = Color(0xFFEDEDF2),
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { previewIndex = null; activeIndex = i }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = com.lucent.app.i18n.S.actionEdit,
                                tint = Color(0xFFEDEDF2)
                            )
                        }
                        IconButton(onClick = { previewIndex = null }) {
                            Icon(
                                Icons.Default.CloseFullscreen,
                                contentDescription = com.lucent.app.i18n.S.collapseTextBox,
                                tint = Color(0xFFEDEDF2)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // Read-only on purpose — see the class comment.
                    DoodleView(value = pages[i], height = 460.dp)
                }
            }
        }
    }
}

/** Read-only rendering for a detail page or a card preview. */
@Composable
fun DoodleView(value: String, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 260.dp) {
    val strokes = remember(value) { Doodle.parse(value) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            strokes.forEach { drawDoodleStroke(it.points, Color(it.color), it.width) }
        }
    }
}

/**
 * Paint one stroke, converting the normalised points back into this canvas's pixels.
 *
 * Rounded caps and joins, and a quadratic smoothing between samples: a polyline through raw touch
 * events looks like a seismograph, because the sampling rate is nowhere near the speed of a hand.
 * Averaging consecutive points into control points costs nothing and is the difference between
 * "handwriting" and "a jagged trace".
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDoodleStroke(
    points: List<Offset>,
    color: Color,
    width: Float
) {
    if (points.size < 2) return
    val w = size.width
    val h = size.height
    val path = Path()
    val first = points.first()
    path.moveTo(first.x * w, first.y * h)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        val midX = (prev.x + cur.x) / 2f * w
        val midY = (prev.y + cur.y) / 2f * h
        path.quadraticTo(prev.x * w, prev.y * h, midX, midY)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = (width * w).coerceAtLeast(1f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

/** A tap target with no ripple — the swatches are their own visual feedback. */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        // First-compile fix (CI 2026-07-26): detectTapGestures is an EXTENSION on
        // PointerInputScope — Kotlin cannot call an extension through its package name; it must be
        // imported and called on the receiver, which pointerInput provides right here.
        detectTapGestures(onTap = { onClick() })
    }
