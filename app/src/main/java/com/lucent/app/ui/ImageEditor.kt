package com.lucent.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lucent.app.AppScope
import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentStore
import com.lucent.app.data.Attachments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Canvas as ComposeCanvas
import android.graphics.Color as AndroidColor

private enum class EditTool { DOODLE, MOSAIC, CROP }

@Composable
fun ImageEditorDialog(att: Attachment, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var working by remember(att.data) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(att.data) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf(EditTool.DOODLE) }

    val undoStack = remember(att.data) { mutableStateListOf<Bitmap>() }
    val canUndo = undoStack.isNotEmpty()

    LaunchedEffect(att.data) {
        val bmp = withContext(Dispatchers.IO) {
            val bytes = Attachments.readBytes(context, att, maxBytes = 96L * 1024 * 1024) ?: return@withContext null
            val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            decoded.copy(Bitmap.Config.ARGB_8888, true)
        }
        if (bmp != null) working = bmp else failed = true
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f))) {
            val bmp = working
            when {
                bmp != null -> {
                    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
                    var livePath by remember { mutableStateOf<List<Offset>>(emptyList()) }
                    var cropRect by remember(version) { mutableStateOf<Rect?>(null) }
                    val image = remember(version) { bmp.asImageBitmap() }

                    fun pushUndo(source: Bitmap) {
                        val copy = try {
                            source.copy(Bitmap.Config.ARGB_8888, true)
                        } catch (t: Throwable) {
                            null
                        }
                        if (copy != null) {
                            undoStack.add(copy)
                            trimUndoStack(undoStack)
                        }
                    }

                    fun undo() {
                        if (undoStack.isEmpty()) return
                        val previous = undoStack.removeAt(undoStack.size - 1)
                        working = previous
                        livePath = emptyList()
                        cropRect = null
                        version++
                    }

                    fun toBitmap(p: Offset): Offset {
                        if (canvasSize.width == 0 || canvasSize.height == 0) return p
                        val sx = bmp.width.toFloat() / canvasSize.width
                        val sy = bmp.height.toFloat() / canvasSize.height
                        return Offset(p.x * sx, p.y * sy)
                    }

                    Column(modifier = Modifier.fillMaxSize().padding(top = 48.dp, bottom = 96.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .wrapContentHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                            ) {
                                Image(
                                    bitmap = image,
                                    contentDescription = att.name,
                                    modifier = Modifier.fillMaxSize()
                                )
                                ComposeCanvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(tool, bmp) {
                                            detectDragGestures(
                                                onDragStart = { pos ->
                                                    livePath = listOf(pos)
                                                    if (tool == EditTool.CROP) cropRect = Rect(pos, pos)
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    livePath = livePath + change.position
                                                    if (tool == EditTool.CROP && livePath.isNotEmpty()) {
                                                        cropRect = Rect(livePath.first(), change.position)
                                                    }
                                                },
                                                onDragEnd = {
                                                    when (tool) {
                                                        EditTool.DOODLE -> {
                                                            if (livePath.size > 1) pushUndo(bmp)
                                                            drawDoodle(bmp, livePath.map { toBitmap(it) })
                                                            version++
                                                        }
                                                        EditTool.MOSAIC -> {
                                                            if (livePath.isNotEmpty()) pushUndo(bmp)
                                                            drawMosaic(bmp, livePath.map { toBitmap(it) })
                                                            version++
                                                        }
                                                        EditTool.CROP -> {  }
                                                    }
                                                    livePath = emptyList()
                                                }
                                            )
                                        }
                                        .onSizeChangedCompat { canvasSize = it }
                                ) {
                                    if (livePath.size > 1 && tool != EditTool.CROP) {
                                        val color = if (tool == EditTool.DOODLE) Color(0xFFFF3B30) else Color.White.copy(alpha = 0.5f)
                                        val width = if (tool == EditTool.DOODLE) DOODLE_STROKE else MOSAIC_BLOCK.toFloat()
                                        val sx = if (canvasSize.width == 0) 1f else canvasSize.width.toFloat() / bmp.width
                                        for (i in 1 until livePath.size) {
                                            drawLine(
                                                color = color,
                                                start = livePath[i - 1],
                                                end = livePath[i],
                                                strokeWidth = width * sx
                                            )
                                        }
                                    }
                                    cropRect?.let { r ->
                                        drawRect(
                                            color = Color.White,
                                            topLeft = Offset(minOf(r.left, r.right), minOf(r.top, r.bottom)),
                                            size = androidx.compose.ui.geometry.Size(kotlin.math.abs(r.width), kotlin.math.abs(r.height)),
                                            style = Stroke(width = 3f)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolChip(com.lucent.app.i18n.S.toolDraw, Icons.Default.Brush, tool == EditTool.DOODLE) { tool = EditTool.DOODLE }
                            ToolChip(com.lucent.app.i18n.S.toolMosaic, Icons.Default.GridOn, tool == EditTool.MOSAIC) { tool = EditTool.MOSAIC }
                            ToolChip(com.lucent.app.i18n.S.toolCrop, Icons.Default.Crop, tool == EditTool.CROP) { tool = EditTool.CROP }
                            if (tool == EditTool.CROP) {
                                Text(
                                    com.lucent.app.i18n.S.applyCrop,
                                    color = Color.Black,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(Color.White)
                                        .clickable {
                                            val r = cropRect
                                            if (r != null) {
                                                val cropped = applyCrop(bmp, toBitmap(Offset(minOf(r.left, r.right), minOf(r.top, r.bottom))), toBitmap(Offset(maxOf(r.left, r.right), maxOf(r.top, r.bottom))))
                                                if (cropped != null) {
                                                    undoStack.add(bmp)
                                                    trimUndoStack(undoStack)
                                                    working = cropped
                                                    cropRect = null
                                                    version++
                                                }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = com.lucent.app.i18n.S.actionCancel,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp).clickable { onDismiss() }
                        )
                        Text(com.lucent.app.i18n.S.editImageTitle, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = if (canUndo) com.lucent.app.i18n.S.a11yUndoLastEdit else com.lucent.app.i18n.S.a11yNothingToUndo,
                            tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.30f),
                            modifier = Modifier
                                .size(26.dp)
                                .clickable(enabled = canUndo) { undo() }
                        )
                        Spacer(Modifier.size(14.dp))
                        Text(
                            if (saving) com.lucent.app.i18n.S.savingEllipsis else com.lucent.app.i18n.S.actionSave,
                            color = Color.Black,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(Color.White)
                                .clickable(enabled = !saving) {
                                    saving = true
                                    val toSave = working
                                    AppScope.io.launch {
                                        val ok = toSave != null && saveEdited(context, att, toSave)
                                        withContext(Dispatchers.Main) {
                                            saving = false
                                            if (ok) onSaved() else onDismiss()
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
                failed -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(com.lucent.app.i18n.S.imageOpenFailed, color = Color.White)
                }
                else -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ToolChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Color.Black else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, color = if (selected) Color.Black else Color.White, fontSize = 13.sp)
    }
}

private fun Modifier.onSizeChangedCompat(block: (IntSize) -> Unit): Modifier =
    this.then(Modifier.onSizeChanged(block))

private fun drawDoodle(bmp: Bitmap, points: List<Offset>) {
    if (points.size < 2) return
    val canvas = Canvas(bmp)
    val paint = Paint().apply {
        color = AndroidColor.rgb(255, 59, 48)
        strokeWidth = DOODLE_STROKE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    for (i in 1 until points.size) {
        canvas.drawLine(points[i - 1].x, points[i - 1].y, points[i].x, points[i].y, paint)
    }
}

private fun drawMosaic(bmp: Bitmap, points: List<Offset>) {
    if (points.isEmpty()) return
    val block = MOSAIC_BLOCK
    val done = HashSet<Long>()
    val paint = Paint()
    val canvas = Canvas(bmp)
    for (p in points) {
        val cellX = (p.x.toInt() / block) * block
        val cellY = (p.y.toInt() / block) * block
        for (dx in -1..1) for (dy in -1..1) {
            val bx = cellX + dx * block
            val by = cellY + dy * block
            if (bx < 0 || by < 0 || bx >= bmp.width || by >= bmp.height) continue
            val key = bx.toLong() * 100000L + by
            if (!done.add(key)) continue
            val w = minOf(block, bmp.width - bx)
            val h = minOf(block, bmp.height - by)
            if (w <= 0 || h <= 0) continue
            val avg = averageColor(bmp, bx, by, w, h)
            paint.color = avg
            canvas.drawRect(bx.toFloat(), by.toFloat(), (bx + w).toFloat(), (by + h).toFloat(), paint)
        }
    }
}

private fun averageColor(bmp: Bitmap, x: Int, y: Int, w: Int, h: Int): Int {
    var r = 0L; var g = 0L; var b = 0L; var count = 0L
    val stepX = maxOf(1, w / 6)
    val stepY = maxOf(1, h / 6)
    var yy = y
    while (yy < y + h) {
        var xx = x
        while (xx < x + w) {
            val c = bmp.getPixel(xx, yy)
            r += AndroidColor.red(c); g += AndroidColor.green(c); b += AndroidColor.blue(c); count++
            xx += stepX
        }
        yy += stepY
    }
    if (count == 0L) return AndroidColor.BLACK
    return AndroidColor.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
}

private fun applyCrop(bmp: Bitmap, topLeft: Offset, bottomRight: Offset): Bitmap? {
    val left = topLeft.x.toInt().coerceIn(0, bmp.width - 1)
    val top = topLeft.y.toInt().coerceIn(0, bmp.height - 1)
    val right = bottomRight.x.toInt().coerceIn(left + 1, bmp.width)
    val bottom = bottomRight.y.toInt().coerceIn(top + 1, bmp.height)
    val w = right - left
    val h = bottom - top
    if (w < 8 || h < 8) return null
    return try {
        Bitmap.createBitmap(bmp, left, top, w, h)
    } catch (t: Throwable) {
        null
    }
}

private fun saveEdited(context: android.content.Context, att: Attachment, bmp: Bitmap): Boolean {
    return try {
        val useJpeg = att.mime.equals("image/jpeg", true) || att.mime.equals("image/jpg", true)
        val out = java.io.ByteArrayOutputStream()
        if (useJpeg) bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        else bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        val bytes = out.toByteArray()
        if (AttachmentStore.looksLikeId(att.data)) {
            AttachmentStore.writeBytes(context, att.data, bytes)
        } else {
            false
        }
    } catch (t: Throwable) {
        false
    }
}

private fun trimUndoStack(stack: MutableList<Bitmap>) {
    fun bytesOf(b: Bitmap): Long = b.width.toLong() * b.height.toLong() * 4L
    while (stack.size > UNDO_MAX_STEPS) {
        val evicted = stack.removeAt(0)
        if (!evicted.isRecycled) evicted.recycle()
    }
    var total = stack.sumOf { bytesOf(it) }
    while (stack.size > 1 && total > UNDO_MAX_BYTES) {
        val evicted = stack.removeAt(0)
        total -= bytesOf(evicted)
        if (!evicted.isRecycled) evicted.recycle()
    }
}

private const val UNDO_MAX_STEPS = 12
private const val UNDO_MAX_BYTES = 64L * 1024 * 1024

private const val DOODLE_STROKE = 12f
private const val MOSAIC_BLOCK = 28
