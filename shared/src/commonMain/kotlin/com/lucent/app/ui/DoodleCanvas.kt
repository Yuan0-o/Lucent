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

object Doodle {

    val COLORS: List<Color> = listOf(
        Color(0xFF1B1B1F),
        Color(0xFFE53935),
        Color(0xFF1E88E5),
        Color(0xFF43A047),
        Color(0xFFFFB300)
    )

    val WIDTHS: List<Float> = listOf(0.004f, 0.010f, 0.022f)

    data class Stroke(
        val color: Int,
        val width: Float,
        val points: List<Offset>
    )

    fun parse(json: String?): List<Stroke> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val pts = o.optJSONArray("p") ?: return@mapNotNull null
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

object DoodlePages {

    fun parse(json: String?): List<String> {
        val raw = json?.trim().orEmpty()
        if (raw.isEmpty()) return listOf("")
        if (raw.startsWith("[")) return listOf(raw)
        return try {
            val arr = JSONObject(raw).optJSONArray("pages") ?: return listOf("")
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) out.add(arr.optString(i, ""))
            if (out.isEmpty()) listOf("") else out
        } catch (_: Throwable) {
            listOf(raw)
        }
    }

    fun serialize(pages: List<String>): String {
        val kept = if (pages.isEmpty()) listOf("") else pages
        if (kept.size == 1 && Doodle.isEmpty(kept.first())) return ""
        if (kept.size == 1) return kept.first()
        val arr = JSONArray()
        kept.forEach { arr.put(it) }
        return JSONObject().put("pages", arr).toString()
    }

    fun drawnCount(pages: List<String>): Int = pages.count { !Doodle.isEmpty(it) }

    fun isEmpty(json: String?): Boolean = drawnCount(parse(json)) == 0

    fun pruneEmpty(json: String?): String = serialize(parse(json).filter { !Doodle.isEmpty(it) })
}

@Composable
fun DoodleEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false,
    onToggleFullScreen: (() -> Unit)? = null,
    onAddPage: (() -> Unit)? = null,
    editable: Boolean = true
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val toolTint = if (fullScreen) Color(0xFFEDEDF2) else onGradient
    val toolMuted = if (fullScreen) Color(0xFFEDEDF2).copy(alpha = 0.60f) else onGradientMuted

    val strokesState = remember { mutableStateOf(Doodle.parse(value)) }
    var strokes by strokesState
    var lastKnownValue by remember { mutableStateOf(value) }
    if (value != lastKnownValue) {
        lastKnownValue = value
        strokes = Doodle.parse(value)
    }
    val past = remember { mutableStateOf(listOf<List<Doodle.Stroke>>()) }
    val future = remember { mutableStateOf(listOf<List<Doodle.Stroke>>()) }
    var colorIndex by remember { mutableStateOf(0) }
    var widthIndex by remember { mutableStateOf(1) }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    val emitValue = androidx.compose.runtime.rememberUpdatedState(onValueChange)
    fun write(updated: List<Doodle.Stroke>) {
        strokes = updated
        val json = Doodle.serialize(updated)
        lastKnownValue = json
        emitValue.value(json)
    }
    fun commit(updated: List<Doodle.Stroke>) {
        past.value = (past.value + listOf(strokes)).takeLast(UNDO_DEPTH)
        future.value = emptyList()
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
        Box(
            modifier = (if (fullScreen) Modifier.fillMaxWidth().weight(1f)
                        else Modifier.fillMaxWidth().height(340.dp))
                .clip(RoundedCornerShape(16.dp))
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
    var editingOpen by remember { mutableStateOf(true) }

    val pages = remember(value) { DoodlePages.parse(value) }
    val index = activeIndex.coerceIn(0, pages.lastIndex)

    fun writePages(updated: List<String>) = onValueChange(DoodlePages.serialize(updated))
    fun updateActive(pageJson: String) {
        writePages(pages.toMutableList().also { it[index] = pageJson })
    }
    fun addPage() {
        writePages(pages + "")
        activeIndex = pages.size
        editingOpen = true
    }
    fun deletePage(at: Int) {
        if (at !in pages.indices) return
        val left = pages.toMutableList().also { it.removeAt(at) }
        writePages(if (left.isEmpty()) listOf("") else left)
        activeIndex = activeIndex.coerceAtMost((if (left.isEmpty()) 1 else left.size) - 1)
        previewIndex = null
    }

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
        if (editingOpen) {
            board(false, modifier)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassButton(
                    text = com.lucent.app.i18n.S.doodleSaveCanvas,
                    compact = true,
                    onClick = { editingOpen = false }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        pages.forEachIndexed { i, page ->
            val current = i == index && editingOpen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(onGradient.copy(alpha = if (current) 0.16f else 0.07f))
                    .clickable { previewIndex = i }
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    com.lucent.app.i18n.S.doodlePageName(i + 1),
                    color = onGradient,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                if (Doodle.isEmpty(page)) {
                    Text(com.lucent.app.i18n.S.doodleEmpty, color = onGradientMuted, fontSize = 11.sp)
                }
                IconButton(onClick = { deletePage(i) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = com.lucent.app.i18n.S.doodleDeletePage,
                        tint = onGradientMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

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
                        IconButton(onClick = { previewIndex = null; activeIndex = i; editingOpen = true }) {
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
                    DoodleView(value = pages[i], height = 460.dp)
                }
            }
        }
    }
}

@Composable
fun DoodleView(value: String, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 260.dp) {
    val canvases = remember(value) {
        DoodlePages.parse(value).filter { !Doodle.isEmpty(it) }.map { Doodle.parse(it) }
    }
    if (canvases.isEmpty()) {
        DoodleCanvasSurface(strokes = emptyList(), modifier = modifier, height = height)
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        canvases.forEachIndexed { index, strokes ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            DoodleCanvasSurface(strokes = strokes, modifier = Modifier, height = height)
        }
    }
}

@Composable
private fun DoodleCanvasSurface(
    strokes: List<Doodle.Stroke>,
    modifier: Modifier,
    height: androidx.compose.ui.unit.Dp
) {
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

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        detectTapGestures(onTap = { onClick() })
    }
