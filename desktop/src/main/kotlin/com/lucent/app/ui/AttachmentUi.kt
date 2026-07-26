package com.lucent.app.ui

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

/**
 * Desktop twin of the Android AttachmentUi: decode-and-thumbnail helpers plus the chip/card
 * composables shared by the notes, tasks, and assistant editors.
 *
 * Adaptations, all platform-shaped:
 *  - Decoding runs on Skia ([org.jetbrains.skia.Image]) instead of BitmapFactory. Skia decodes the
 *    same formats the Android path did (including WebP) and the subsampled-decode contract is
 *    preserved: a huge stored image renders as a bounded-size bitmap, never at full resolution.
 *  - `uriToAttachment`/`uriToChatImage` become [fileToAttachment]/[fileToChatImage], taking the
 *    [java.io.File] a desktop file dialog produces.
 *  - The save launcher wraps the AWT save dialog instead of the Storage Access Framework.
 * The composables themselves (chips, inline image cards, chip rows) mirror the Android layouts.
 */

private const val MAX_IMAGE_DIM = 1600

/**
 * Decode [bytes] into an [ImageBitmap] no larger than [maxDim] on its longest side, or null when
 * the bytes are not a decodable image. Downscaling happens on a Skia surface so the full-size
 * pixels live only for the duration of the draw.
 */
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

/** [decodeSampledBitmap] for a plaintext file on disk (used by the preview cache readers). */
fun decodeSampledBitmapFromFile(file: File, maxDim: Int = MAX_IMAGE_DIM): ImageBitmap? = try {
    decodeSampledBitmap(file.readBytes(), maxDim)
} catch (t: Throwable) {
    null
}

/** Re-encode [bytes] downscaled to [maxDim], preferring JPEG for photos and PNG otherwise. */
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

/** A rough mime guess from a file extension — the desktop stand-in for the resolver's type. */
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

/**
 * Import a picked [file] into the attachment store and return the row-ready [Attachment], or null
 * when the copy fails or the file exceeds [AttachmentLimits]. Desktop counterpart of Android's
 * `uriToAttachment`; the size gate and disk-backed shape are identical. Call from an IO context.
 */
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

/**
 * Import a picked image [file] for a CHAT message: downscaled, re-encoded, Base64'd, because chat
 * rows store their images inline. Returns (mime, base64, name) — same Triple as Android's
 * `uriToChatImage`. Call from an IO context.
 */
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

// =============================================================================================
//  Attachment rows (tasks A2, A4, A11)
// =============================================================================================
//
// ### A2 — one row shape for every attachment
//
// Images used to render as a full-width inline thumbnail while everything else got a one-line
// chip, and the two disagreed about almost everything: how much of the card they consumed, where
// the actions lived, and what tapping them did. Three photos on a note pushed its text off the
// screen entirely, which is the wrong trade — an attachment list should say *what is attached*,
// and the picture itself is one tap away in a viewer that can actually show it properly (zoom,
// swipe, edit, share).
//
// So every attachment — image, video, PDF, document, anything — is now the same row: type icon,
// file name, one trailing action. Tapping *anywhere* on the row opens the full-screen viewer,
// which is what the eye button used to do; a second control that duplicates the row's own tap
// target is just a smaller way to do the same thing, so it is gone. The trailing slot keeps the
// one action the row can't express — download — so it stays reachable without opening the file.
//
// ### A4/A11 — rename and reorder belong to the composer
//
// Renaming a file and rearranging the list are *edits to the item*, so they live where the item's
// other edits live and are committed by the same Save. That keeps one write path (the composer's
// attachment state, serialized on save) instead of a second one that mutates the row behind the
// editor's back — which is exactly how a half-finished edit and a renamed attachment end up
// disagreeing about what the note contains.

/** Row height used by the reorder maths below; also what keeps the list visually even. */
private val ATTACHMENT_ROW_HEIGHT = 44.dp

/**
 * The attachment rows on a **saved** note or task (task A2): name, type icon, and a download
 * button. The whole row opens the viewer, positioned on the tapped file so the rest of the item's
 * attachments can be swiped through from there.
 */
@Composable
fun CardAttachments(
    attachments: List<Attachment>,
    onGradient: Color,
    onGradientMuted: Color,
    // Task A4, second half. The detail page has no composer state to fold a rename into, so the
    // caller commits it straight to its own row — which is why this is a callback and not a local
    // edit: only the screen that owns the note/task knows how to persist one.
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
        AttachmentViewerDialog(
            attachments = attachments,
            initialIndex = attachments.indexOf(att).coerceAtLeast(0),
            onDismiss = { viewing = null }
        )
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

/**
 * The attachment rows inside a **composer** — the same row shape as [CardAttachments], plus the
 * three things that only make sense while editing: a drag handle (A11), rename (A4), and remove.
 *
 * [onReorder] receives (from, to) as indices into the list it was handed. It is optional so the
 * chips still work anywhere reordering has no meaning.
 */
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

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        attachments.forEach { att ->
            // Task A11: keyed so a row that is being dragged keeps its identity — and therefore its
            // in-flight gesture — while the list reorders underneath it. Without the key, Compose
            // reuses slots positionally, the row's data changes mid-drag, and the drag dies on the
            // first swap.
            key(att.data) {
                AttachmentRow(
                    att = att,
                    tint = tint,
                    onClick = { Haptics.tick(context); viewing = att },
                    leading = if (onReorder == null) null else {
                        {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = com.lucent.app.i18n.S.a11yDragToReorder,
                                tint = tint.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .pointerInput(att.data) {
                                        // Accumulate raw drag distance and commit one swap per row
                                        // crossed. `travelled` is a plain local, not Compose state:
                                        // the gesture must survive the recomposition each swap
                                        // causes, and state written here would restart the effect.
                                        var travelled = 0f
                                        detectDragGestures(
                                            onDragEnd = { travelled = 0f },
                                            onDragCancel = { travelled = 0f },
                                            onDrag = { change, delta ->
                                                change.consume()
                                                travelled += delta.y
                                                while (travelled >= rowHeightPx) {
                                                    travelled -= rowHeightPx
                                                    moveAttachment(attachments, att, +1, onReorder)
                                                }
                                                while (travelled <= -rowHeightPx) {
                                                    travelled += rowHeightPx
                                                    moveAttachment(attachments, att, -1, onReorder)
                                                }
                                            }
                                        )
                                    }
                            )
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
        AttachmentViewerDialog(
            attachments = attachments,
            initialIndex = attachments.indexOf(att).coerceAtLeast(0),
            onDismiss = { viewing = null }
        )
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

/**
 * Move [att] by [step] places, resolving its position against the list as it stands *now* rather
 * than against an index captured when the drag began — the drag reorders the list under itself, so
 * a captured index is stale by the second swap. A move that would fall off either end is dropped.
 */
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

/** One attachment row: optional leading control, type icon, name, optional trailing controls. */
@Composable
private fun AttachmentRow(
    att: Attachment,
    tint: Color,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.10f))
            // A2: the row itself is the "open it" affordance. Placed before the padding so the
            // whole strip is the tap target, not just the text inside it.
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

/**
 * Rename one attachment (task A4).
 *
 * Two rules the field enforces, because both failures are silent otherwise:
 *
 *  - **The extension is kept.** [Attachment.isPdf] and every "can this be previewed" decision reads
 *    the name as well as the MIME type, so a file renamed from `report.pdf` to `report` would stop
 *    being recognised as a PDF. Typing a new extension deliberately still works; only *dropping*
 *    it restores the old one.
 *  - **Names stay unique.** [Attachments.upsert] and [Attachments.removeByName] match on the name,
 *    so two attachments sharing one would make removing either ambiguous.
 */
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

/** `report` + `report.pdf` -> `report.pdf`; `report.txt` + `report.pdf` -> `report.txt`. */
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

/**
 * A "save this attachment to disk" action: opens the system save dialog pre-filled with the
 * attachment's name and streams the decrypted bytes to the chosen location. Desktop counterpart of
 * the SAF CreateDocument launcher; like it, nothing is written when the user cancels.
 */
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
