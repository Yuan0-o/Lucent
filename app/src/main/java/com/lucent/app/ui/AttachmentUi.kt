package com.lucent.app.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentStore
import com.lucent.app.data.Attachments
import java.io.File

private const val MAX_IMAGE_DIM = 1600
private const val DOWNSCALE_BYTE_THRESHOLD = 400_000

fun decodeSampledBitmap(bytes: ByteArray, maxDim: Int = MAX_IMAGE_DIM): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (t: Throwable) {
        null
    }
}

fun decodeSampledBitmapFromFile(file: File, maxDim: Int = MAX_IMAGE_DIM): Bitmap? {
    if (!file.exists()) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(file.absolutePath, opts)
    } catch (t: Throwable) {
        null
    }
}

private fun downscaleImageFileInPlace(file: File, mime: String, maxDim: Int = MAX_IMAGE_DIM): String {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return mime
        if (longest <= maxDim && file.length() <= DOWNSCALE_BYTE_THRESHOLD) return mime

        val decoded = decodeSampledBitmapFromFile(file, maxDim) ?: return mime
        val scale = maxDim.toFloat() / maxOf(decoded.width, decoded.height).toFloat()
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else decoded

        val useJpeg = mime.equals("image/jpeg", ignoreCase = true) || mime.equals("image/jpg", ignoreCase = true)
        val outMime = if (useJpeg) "image/jpeg" else "image/png"
        file.outputStream().use { out ->
            if (useJpeg) scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            else scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        outMime
    } catch (t: Throwable) {
        mime
    }
}

fun uriToAttachment(context: Context, uri: Uri): Attachment? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val name = AttachmentStore.queryDisplayName(context, uri) ?: "file"
    val id = AttachmentStore.importUri(context, uri) ?: return null
    return Attachment(mime = mime, data = id, name = name)
}

fun uriToChatImage(context: Context, uri: Uri): Triple<String, String, String>? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: return null
    if (!mime.startsWith("image/")) return null
    val name = AttachmentStore.queryDisplayName(context, uri) ?: "image"
    val scratch = File.createTempFile("chatimg_", null, context.cacheDir)
    return try {
        resolver.openInputStream(uri)?.use { input ->
            scratch.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val r = input.read(buf); if (r == -1) break; out.write(buf, 0, r)
                }
            }
        } ?: return null
        val finalMime = downscaleImageFileInPlace(scratch, mime)
        val base64 = android.util.Base64.encodeToString(scratch.readBytes(), android.util.Base64.NO_WRAP)
        Triple(finalMime, base64, name)
    } catch (t: Throwable) {
        null
    } finally {
        scratch.delete()
    }
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
    val context = LocalContext.current
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
    val context = LocalContext.current
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
            lineHeight = 20.sp,
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

private fun iconForAttachment(att: Attachment) = when {
    att.isVideo -> Icons.Default.Movie
    att.isAudio -> Icons.Default.MusicNote
    att.isPdf -> Icons.Default.PictureAsPdf
    att.isImage -> Icons.Default.Image
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}
