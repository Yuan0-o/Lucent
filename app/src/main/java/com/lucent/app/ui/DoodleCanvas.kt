package com.lucent.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteOutline
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
 * The editable whiteboard.
 *
 * Drawing is a plain drag: touch down starts a stroke, movement extends it, lifting ends it. There
 * is no eraser tool — undo removes the last stroke and "clear" removes them all, which covers what
 * a note-sized sketch needs without a second mode the user has to remember they are in.
 */
@Composable
fun DoodleEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var strokes by remember(value) { mutableStateOf(Doodle.parse(value)) }
    var colorIndex by remember { mutableStateOf(0) }
    var widthIndex by remember { mutableStateOf(1) }
    // The stroke currently under the finger, kept separate so it can be drawn live without
    // re-serializing the whole drawing on every pointer sample.
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    fun commit(updated: List<Doodle.Stroke>) {
        strokes = updated
        onValueChange(Doodle.serialize(updated))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // ---- The board -----------------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(16.dp))
                // Opaque white, not glass: ink needs a surface with a known colour behind it, and
                // the animated gradient showing through would change what a drawing looks like
                // depending on where the background happened to be that second.
                .background(Color.White)
                .border(1.dp, onGradientMuted.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .pointerInput(colorIndex, widthIndex) {
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
            Canvas(modifier = Modifier.fillMaxWidth().height(340.dp)) {
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
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ---- Pen: five colours, three widths ---------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Doodle.COLORS.forEachIndexed { index, c ->
                Box(
                    modifier = Modifier
                        .size(if (index == colorIndex) 30.dp else 24.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            if (index == colorIndex) 2.dp else 1.dp,
                            onGradient.copy(alpha = if (index == colorIndex) 0.9f else 0.3f),
                            CircleShape
                        )
                        .clickableNoRipple { colorIndex = index }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Doodle.WIDTHS.forEachIndexed { index, w ->
                // The swatch is drawn at the width it selects, so the control shows the outcome
                // rather than naming it — "thin / medium / thick" would need translating and would
                // still be less informative than the line itself.
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(onGradient.copy(alpha = if (index == widthIndex) 0.20f else 0.06f))
                        .clickableNoRipple { widthIndex = index },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size((4 + index * 5).dp)
                            .clip(CircleShape)
                            .background(onGradient)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { if (strokes.isNotEmpty()) commit(strokes.dropLast(1)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = com.lucent.app.i18n.S.doodleUndoStroke,
                    tint = if (strokes.isEmpty()) onGradientMuted else onGradient
                )
            }
            IconButton(onClick = { commit(emptyList()) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = com.lucent.app.i18n.S.doodleClear,
                    tint = onGradientMuted
                )
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
        path.quadraticBezierTo(prev.x * w, prev.y * h, midX, midY)
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
