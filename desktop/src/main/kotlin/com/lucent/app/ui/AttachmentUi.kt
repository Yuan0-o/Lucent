package com.lucent.app.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentAccess
import com.lucent.app.data.AttachmentLimits
import com.lucent.app.data.AttachmentStore
import com.lucent.app.data.Attachments
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File


private const val MAX_IMAGE_DIM = 1600

fun decodeSampledBitmap(bytes: ByteArray, maxDim: Int = MAX_IMAGE_DIM): ImageBitmap? = try {
    val image = org.jetbrains.skia.Image.makeFromEncoded(bytes)
    val w = image.width
    val h = image.height
    if (w <= 0 || h <= 0) {
        null
    } else if (w <= maxDim && h <= maxDim) {
        image.toComposeImageBitmap()
    } else {
        val scale = maxDim.toFloat() / maxOf(w, h)
        val outW = maxOf(1, (w * scale).toInt())
        val outH = maxOf(1, (h * scale).toInt())
        val surface = Surface.makeRasterN32Premul(outW, outH)
        surface.canvas.drawImageRect(
            image,
            Rect.makeWH(w.toFloat(), h.toFloat()),
            Rect.makeWH(outW.toFloat(), outH.toFloat()),
            null
        )
        surface.makeImageSnapshot().toComposeImageBitmap()
    }
} catch (t: Throwable) {
    null
}

fun decodeSampledBitmapFromFile(file: File, maxDim: Int = MAX_IMAGE_DIM): ImageBitmap? = try {
    decodeSampledBitmap(file.readBytes(), maxDim)
} catch (t: Throwable) {
    null
}

private fun downscaleImageBytes(bytes: ByteArray, mime: String, maxDim: Int = MAX_IMAGE_DIM): Pair<String, ByteArray> {
    return try {
        val image = org.jetbrains.skia.Image.makeFromEncoded(bytes)
        val w = image.width
        val h = image.height
        if (w <= maxDim && h <= maxDim) return mime to bytes
        val scale = maxDim.toFloat() / maxOf(w, h)
        val outW = maxOf(1, (w * scale).toInt())
        val outH = maxOf(1, (h * scale).toInt())
        val surface = Surface.makeRasterN32Premul(outW, outH)
        surface.canvas.drawImageRect(
            image,
            Rect.makeWH(w.toFloat(), h.toFloat()),
            Rect.makeWH(outW.toFloat(), outH.toFloat()),
            null
        )
        val snapshot = surface.makeImageSnapshot()
        val preferPng = mime == "image/png" || mime == "image/gif"
        val format = if (preferPng) EncodedImageFormat.PNG else EncodedImageFormat.JPEG
        val encoded = snapshot.encodeToData(format, 90)?.bytes
        if (encoded != null) {
            (if (preferPng) "image/png" else "image/jpeg") to encoded
        } else {
            mime to bytes
        }
    } catch (t: Throwable) {
        mime to bytes
    }
}

fun mimeForFileName(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "heic" -> "image/heic"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "md" -> "text/markdown"
        "json" -> "application/json"
        "csv" -> "text/csv"
        "zip" -> "application/zip"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        else -> "application/octet-stream"
    }
}

fun fileToAttachment(context: Context, file: File): Attachment? {
    if (!file.exists() || !file.isFile) return null
    if (file.length() > AttachmentLimits.MAX_SINGLE_BYTES) return null
    val id = AttachmentStore.importFile(context, file) ?: return null
    return Attachment(
        mime = mimeForFileName(file.name),
        data = id,
        name = file.name.ifBlank { "file" }
    )
}

fun fileToChatImage(context: Context, file: File): Triple<String, String, String>? = try {
    if (!file.exists() || file.length() > AttachmentLimits.MAX_SINGLE_BYTES) {
        null
    } else {
        val mime = mimeForFileName(file.name)
        if (!mime.startsWith("image/")) {
            null
        } else {
            val (finalMime, bytes) = downscaleImageBytes(file.readBytes(), mime)
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            Triple(finalMime, base64, file.name.ifBlank { "image" })
        }
    }
} catch (t: Throwable) {
    null
}


private val ATTACHMENT_ROW_HEIGHT = 44.dp

@Composable
fun CardAttachments(
    attachments: List<Attachment>,
    onGradient: Color,
    onGradientMuted: Color,
    onRename: ((Attachment, String) -> Unit)? = null
) {
    if (attachments.isEmpty()) return
    var renaming by remember { mutableStateOf<Attachment?>(null) }
    val context = android.content.DesktopContext
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    val save = rememberSaveAttachmentLauncher()

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        attachments.forEach { att ->
            AttachmentRow(
                att = att,
                tint = if (att.isImage) onGradientMuted else onGradient,
                onClick = { Haptics.tick(context); viewing = att },
                trailing = {
                    if (onRename != null) {
                        IconButton(onClick = { renaming = att }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.DriveFileRenameOutline,
                                contentDescription = com.lucent.app.i18n.S.a11yRenameNamed(att.name),
                                tint = onGradientMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(onClick = { Haptics.tick(context); save(att) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = com.lucent.app.i18n.S.a11yDownloadNamed(att.name),
                            tint = onGradientMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        }
    }

    viewing?.let { att ->
        val idx = attachments.indexOfFirst { it.data == att.data }
        if (idx >= 0) {
            AttachmentViewerDialog(
                attachments = attachments,
                initialIndex = idx,
                onDismiss = { viewing = null }
            )
        }
    }

    renaming?.let { att ->
        AttachmentRenameDialog(
            current = att,
            takenNames = attachments.filterNot { it.data == att.data }.map { it.name },
            onConfirm = { newName -> onRename?.invoke(att, newName); renaming = null },
            onDismiss = { renaming = null }
        )
    }
}

@Composable
fun PendingAttachmentChips(
    attachments: List<Attachment>,
    tint: Color,
    onRemove: (Attachment) -> Unit,
    onRename: ((Attachment, String) -> Unit)? = null,
    onReorder: ((Int, Int) -> Unit)? = null
) {
    if (attachments.isEmpty()) return
    val context = android.content.DesktopContext
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    var renaming by remember { mutableStateOf<Attachment?>(null) }
    val rowHeightPx = with(LocalDensity.current) { ATTACHMENT_ROW_HEIGHT.toPx() }
    val liveAttachments = androidx.compose.runtime.rememberUpdatedState(attachments)

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        attachments.forEach { att ->
            key(att.data) {
                val position = liveAttachments.value.indexOfFirst { it.data == att.data }
                var lastPosition by remember { mutableStateOf(position) }
                val slide = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(position) {
                    if (position >= 0 && position != lastPosition) {
                        slide.snapTo((lastPosition - position) * rowHeightPx)
                        lastPosition = position
                        slide.animateTo(
                            0f,
                            androidx.compose.animation.core.spring(
                                dampingRatio = 0.62f,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
                AttachmentRow(
                    modifier = Modifier.graphicsLayer { translationY = slide.value },
                    att = att,
                    tint = tint,
                    onClick = { Haptics.tick(context); viewing = att },
                    leading = if (onReorder == null) null else {
                        {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .pointerInput(att.data) {
                                        var travelled = 0f
                                        detectDragGestures(
                                            onDragEnd = { travelled = 0f },
                                            onDragCancel = { travelled = 0f },
                                            onDrag = { change, delta ->
                                                change.consume()
                                                travelled += delta.y
                                                while (travelled >= rowHeightPx) {
                                                    travelled -= rowHeightPx
                                                    moveAttachment(liveAttachments.value, att, +1, onReorder)
                                                }
                                                while (travelled <= -rowHeightPx) {
                                                    travelled += rowHeightPx
                                                    moveAttachment(liveAttachments.value, att, -1, onReorder)
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = com.lucent.app.i18n.S.a11yDragToReorder,
                                    tint = tint.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    trailing = {
                        if (onRename != null) {
                            IconButton(onClick = { renaming = att }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.DriveFileRenameOutline,
                                    contentDescription = com.lucent.app.i18n.S.a11yRenameNamed(att.name),
                                    tint = tint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(onClick = { onRemove(att) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = com.lucent.app.i18n.S.a11yRemoveNamed(att.name),
                                tint = tint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }
        }
    }

    viewing?.let { att ->
        val idx = attachments.indexOfFirst { it.data == att.data }
        if (idx >= 0) {
            AttachmentViewerDialog(
                attachments = attachments,
                initialIndex = idx,
                onDismiss = { viewing = null }
            )
        }
    }

    renaming?.let { att ->
        AttachmentRenameDialog(
            current = att,
            takenNames = attachments.filterNot { it.data == att.data }.map { it.name },
            onConfirm = { newName -> onRename?.invoke(att, newName); renaming = null },
            onDismiss = { renaming = null }
        )
    }
}

private fun moveAttachment(
    attachments: List<Attachment>,
    att: Attachment,
    step: Int,
    onReorder: ((Int, Int) -> Unit)?
) {
    val from = attachments.indexOfFirst { it.data == att.data }
    if (from < 0) return
    val to = from + step
    if (to !in attachments.indices) return
    onReorder?.invoke(from, to)
}

@Composable
private fun AttachmentRow(
    att: Attachment,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .pointerInput(att.data) { detectTapGestures(onTap = { onClick() }) }
            .heightIn(min = ATTACHMENT_ROW_HEIGHT)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.size(6.dp))
        }
        Icon(iconForAttachment(att), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            att.name,
            color = tint,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp)
        )
        trailing?.invoke(this)
    }
}

@Composable
private fun AttachmentRenameDialog(
    current: Attachment,
    takenNames: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(current.data) { mutableStateOf(current.name) }
    val proposed = withPreservedExtension(text.trim(), current.name)
    val clash = proposed.isNotEmpty() && takenNames.any { it.equals(proposed, ignoreCase = true) }
    val valid = proposed.isNotEmpty() && !clash

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(com.lucent.app.i18n.S.attachmentRenameTitle) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(com.lucent.app.i18n.S.attachmentNameLabel) },
                    singleLine = true,
                    isError = clash,
                    modifier = Modifier.fillMaxWidth()
                )
                if (clash) {
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(com.lucent.app.i18n.S.attachmentNameTaken, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(proposed) }) {
                Text(com.lucent.app.i18n.S.actionRename)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(com.lucent.app.i18n.S.actionCancel) }
        }
    )
}

private fun withPreservedExtension(typed: String, original: String): String {
    if (typed.isEmpty()) return ""
    if (typed.substringAfterLast('.', "").isNotEmpty()) return typed
    val ext = original.substringAfterLast('.', "")
    return if (ext.isEmpty()) typed else "$typed.$ext"
}

fun iconForAttachment(att: Attachment) = when {
    att.isImage -> Icons.Default.Image
    att.isVideo -> Icons.Default.Videocam
    att.isAudio -> Icons.Default.Audiotrack
    att.isPdf -> Icons.Default.PictureAsPdf
    else -> Icons.Default.Description
}

@Composable
fun rememberSaveAttachmentLauncher(): (Attachment) -> Unit {
    val context = android.content.DesktopContext
    return remember {
        { att ->
            Thread {
                try {
                    val dialog = java.awt.FileDialog(null as java.awt.Frame?, com.lucent.app.i18n.S.actionSave, java.awt.FileDialog.SAVE)
                    dialog.file = att.name.ifBlank { "attachment" }
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val name = dialog.file
                    if (dir != null && name != null) {
                        val ok = AttachmentAccess.writeTo(context, att, File(dir, name).outputStream())
                        LucentToast.show(context, if (ok) com.lucent.app.i18n.S.savedToast else com.lucent.app.i18n.S.cantSaveFile)
                    }
                } catch (t: Throwable) {
                    LucentToast.show(context, com.lucent.app.i18n.S.cantSaveFile)
                }
            }.start()
        }
    }
}
