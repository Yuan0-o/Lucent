package com.lucent.app.ui
import com.lucent.app.platform.cacheDir

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
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
            decoded.scale(
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1)
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
