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

/**
 * Longest-edge cap for stored images. Full-resolution phone photos (12MP+) inflate to tens of
 * MB in ARGB_8888 when decoded, which is what could exhaust memory when the app tries to
 * render several of them. We downscale on import so the stored copy is small and cheap to
 * decode; users who need pixel-perfect originals can attach to a note without downscaling by
 * picking through the file picker instead of a photo picker (irrelevant here — the picker
 * this code owns always downscales images).
 */
private const val MAX_IMAGE_DIM = 1600
// Below this size an image is small enough that re-encoding it would just cost quality.
private const val DOWNSCALE_BYTE_THRESHOLD = 400_000

/**
 * Two-pass decode of a byte array with subsampling so the resulting bitmap's longest edge is
 * at most ~[maxDim]. Catches `Throwable` because [OutOfMemoryError] is an `Error` (not an
 * `Exception`) and would otherwise slip past a plain `catch (e: Exception)` and crash.
 */
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

/**
 * Same two-pass subsampled decode as [decodeSampledBitmap], but reading directly from a file
 * on disk so the whole encoded image never has to sit in a Kotlin `ByteArray`. This is what
 * lets huge stored images render safely: we bounds-decode once (no pixel data), pick a sample
 * factor, then decode a downscaled bitmap — never touching the full resolution pixels.
 */
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

/**
 * Read any picked file into an [Attachment] stored on disk.
 *
 * The picked bytes stream through a 64KB buffer straight into a private (encrypted) file, so a
 * hundreds-of-MB pick never inflates into a `ByteArray` in memory.
 *
 * **Images are stored at their original quality and size** — the bytes are copied verbatim, never
 * re-encoded or downscaled. Keeping the file exactly as the user attached it is a deliberate choice:
 * a notes app that silently re-compresses your photos is quietly degrading your data, and the render
 * paths already subsample images from disk at display time (see [decodeSampledBitmap]), so a large
 * original costs nothing to *show* — it only costs the disk space the user chose to spend. The old
 * import-time downscale traded that away for a smaller file nobody had asked to shrink.
 *
 * Returns null if the stream can't be opened; on failure the partial file is cleaned up.
 * Must be called from an IO context (streams block).
 */
fun uriToAttachment(context: Context, uri: Uri): Attachment? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val name = AttachmentStore.queryDisplayName(context, uri) ?: "file"
    val id = AttachmentStore.importUri(context, uri) ?: return null
    // No image downscaling: the stored copy is byte-for-byte the source file, original quality kept.
    return Attachment(mime = mime, data = id, name = name)
}

/**
 * Prepare a chat-message image attachment: stream to disk, downscale, then read the (small)
 * downscaled bytes back so they can be Base64-encoded into the ChatMessage. Chat attachments
 * live inside the message row for portability with backups; images always end up small after
 * downscaling so this remains cheap even when the source was a 500MB photo. Non-image files
 * aren't handled here — the assistant screen inlines their text into the message body instead.
 *
 * Returns (mime, base64) on success, or null.
 */
fun uriToChatImage(context: Context, uri: Uri): Triple<String, String, String>? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: return null
    if (!mime.startsWith("image/")) return null
    val name = AttachmentStore.queryDisplayName(context, uri) ?: "image"
    // Stream the source to a scratch file, downscale, read the small result back, then
    // delete the scratch. We deliberately do NOT put it in the AttachmentStore because it
    // isn't a persistent note/task attachment; leaving it there would show up as an orphan
    // to the sweep and be silently deleted.
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
        // At this point `scratch` holds the downscaled image (typically well under 1 MB),
        // so reading it back into memory to Base64-encode it is fine and unavoidable — the
        // chat message row stores its attachment inline.
        val base64 = android.util.Base64.encodeToString(scratch.readBytes(), android.util.Base64.NO_WRAP)
        Triple(finalMime, base64, name)
    } catch (t: Throwable) {
        null
    } finally {
        scratch.delete()
    }
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
        // R3 report: resolve the viewer's start page against the CURRENT list by the file's
        // unique store id, at the moment the dialog is about to open. Resolving by data-class
        // equality on a list that may have been refreshed between the tap frame and this
        // composition could land on an older equal element — or on none at all (silently clamped
        // to 0) — which is exactly the "tapping a just-added attachment shows the old preview
        // instead" report.
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
    val context = LocalContext.current
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    var renaming by remember { mutableStateOf<Attachment?>(null) }
    val rowHeightPx = with(LocalDensity.current) { ATTACHMENT_ROW_HEIGHT.toPx() }
    // Task 6 — the gesture below runs in a coroutine keyed on the row's identity, so it outlives
    // every recomposition and kept whichever `attachments` list it was created with. After the first
    // swap that list was stale, `indexOfFirst` returned the row's OLD position, and every further
    // step was computed from the wrong base — which is why dragging one way appeared to work and the
    // other did not. Read through a state holder so it always sees the current order.
    val liveAttachments = androidx.compose.runtime.rememberUpdatedState(attachments)

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        attachments.forEach { att ->
            // Task A11: keyed so a row that is being dragged keeps its identity — and therefore its
            // in-flight gesture — while the list reorders underneath it. Without the key, Compose
            // reuses slots positionally, the row's data changes mid-drag, and the drag dies on the
            // first swap.
            key(att.data) {
                // Task 6 — motion. These rows live in a plain Column, so there is no animateItem to
                // lean on: when the order changes a row simply appears somewhere else. This is the
                // FLIP trick done by hand — the moment the index changes, jump back to where the row
                // used to be and spring to zero, so the eye sees it travel.
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
                            // Task 5 — the handle was a 20dp icon, and the drag had to START on it.
                            //
                            // 20dp is well under the ~48dp a fingertip actually covers, so most
                            // attempts landed on the row beside it, where the composer's own
                            // vertical scroll took the gesture instead — which is why reordering
                            // "was not implemented" from the outside. The touch target is a 44dp box
                            // now with the same 20dp glyph drawn inside it: identical to look at,
                            // more than twice as wide to hit.
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
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
        // R3 report: resolve the viewer's start page against the CURRENT list by the file's
        // unique store id, at the moment the dialog is about to open. Resolving by data-class
        // equality on a list that may have been refreshed between the tap frame and this
        // composition could land on an older equal element — or on none at all (silently clamped
        // to 0) — which is exactly the "tapping a just-added attachment shows the old preview
        // instead" report.
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

/** Pick a leading icon that tells the user what kind of file this is at a glance. */
private fun iconForAttachment(att: Attachment) = when {
    att.isVideo -> Icons.Default.Movie
    att.isAudio -> Icons.Default.MusicNote
    att.isPdf -> Icons.Default.PictureAsPdf
    att.isImage -> Icons.Default.Image
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}
