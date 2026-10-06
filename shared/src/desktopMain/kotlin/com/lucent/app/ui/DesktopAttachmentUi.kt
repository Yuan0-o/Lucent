package com.lucent.app.ui

import androidx.compose.runtime.LaunchedEffect
import kotlin.io.encoding.Base64
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import com.lucent.app.platform.PlatformContext
import com.lucent.app.platform.desktopPlatformContext
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

fun fileToChatImage(context: PlatformContext, file: File): Triple<String, String, String>? = try {
    if (!file.exists() || file.length() > AttachmentLimits.MAX_SINGLE_BYTES) {
        null
    } else {
        val mime = mimeForFileName(file.name)
        if (!mime.startsWith("image/")) {
            null
        } else {
            val (finalMime, bytes) = downscaleImageBytes(file.readBytes(), mime)
            val base64 = Base64.Default.encode(bytes)
            Triple(finalMime, base64, file.name.ifBlank { "image" })
        }
    }
} catch (t: Throwable) {
    null
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

fun iconForAttachment(att: Attachment) = when {
    att.isImage -> Icons.Default.Image
    att.isVideo -> Icons.Default.Videocam
    att.isAudio -> Icons.Default.Audiotrack
    att.isPdf -> Icons.Default.PictureAsPdf
    else -> Icons.Default.Description
}

@Composable
actual fun rememberSaveAttachmentLauncher(): (Attachment) -> Unit {
    val context = desktopPlatformContext
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
                        val ok = AttachmentAccess.writeTo(context, att, com.lucent.app.PlatformOutputStream(File(dir, name).outputStream()))
                        LucentToast.show(context, if (ok) com.lucent.app.i18n.S.savedToast else com.lucent.app.i18n.S.cantSaveFile)
                    }
                } catch (t: Throwable) {
                    LucentToast.show(context, com.lucent.app.i18n.S.cantSaveFile)
                }
            }.start()
        }
    }
}
