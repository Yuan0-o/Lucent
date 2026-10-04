package com.lucent.app.data

import android.content.Context
import com.lucent.app.PlatformFile
import com.lucent.app.PlatformOutputStream
import com.lucent.app.platform.PlatformContext
import com.lucent.app.platform.PlatformInputStream
import kotlin.io.encoding.Base64
import java.io.File

actual object AttachmentAccess {
    private const val PREVIEW_DIR = "attachment-preview"
    private const val COPY_BUFFER = 64 * 1024

    private fun previewDir(context: PlatformContext): PlatformFile {
        val ctx = context as Context
        return PlatformFile(File(ctx.applicationContext.cacheDir, PREVIEW_DIR).apply { if (!exists()) mkdirs() }.absolutePath)
    }

    actual fun materialize(context: PlatformContext, att: Attachment): PlatformFile? {
        val dir = PlatformFile(previewDir(context), safeFolder(att)).apply { if (!exists()) mkdirs() }
        val dest = PlatformFile(dir, safeName(att.name))
        val stream = openAttachmentStream(context, att) as? java.io.InputStream
        return try {
            if (stream != null) {
                stream.use { input ->
                    val out = dest.outputStream()
                    try {
                        val buf = ByteArray(COPY_BUFFER)
                        while (true) {
                            val r = input.read(buf); if (r == -1) break; out.write(buf, 0, r)
                        }
                        out.flush()
                    } finally {
                        out.close()
                    }
                }
            } else {
                val bytes = readAttachmentBytes(context, att, maxBytes = Long.MAX_VALUE) ?: return null
                val out = dest.outputStream()
                try {
                    out.write(bytes); out.flush()
                } finally {
                    out.close()
                }
            }
            dest
        } catch (t: Throwable) {
            dest.delete()
            null
        }
    }

    fun openExternally(context: PlatformContext, att: Attachment): Boolean {
        val file = materialize(context, att) ?: return false
        return try {
            if (!java.awt.Desktop.isDesktopSupported()) return false
            java.awt.Desktop.getDesktop().open(File(file.absolutePath))
            true
        } catch (t: Throwable) {
            false
        }
    }

    actual fun writeTo(context: PlatformContext, att: Attachment, out: PlatformOutputStream): Boolean {
        val stream = openAttachmentStream(context, att) as? java.io.InputStream
        return try {
            try {
                if (stream != null) {
                    stream.use { input ->
                        val buf = ByteArray(COPY_BUFFER)
                        while (true) {
                            val r = input.read(buf); if (r == -1) break; out.write(buf, 0, r)
                        }
                    }
                } else {
                    val bytes = readAttachmentBytes(context, att, maxBytes = Long.MAX_VALUE) ?: return false
                    out.write(bytes)
                }
            } finally {
                out.close()
            }
            true
        } catch (t: Throwable) {
            false
        }
    }

    actual fun clearPreviewCache(context: PlatformContext) {
        previewDir(context).deleteRecursively()
    }

    private fun openAttachmentStream(context: PlatformContext, att: Attachment): PlatformInputStream? =
        if (AttachmentStore.looksLikeId(att.data)) AttachmentStore.openInputStream(context, att.data) else null

    private fun readAttachmentBytes(context: PlatformContext, att: Attachment, maxBytes: Long): ByteArray? {
        return if (AttachmentStore.looksLikeId(att.data)) {
            AttachmentStore.readBytes(context, att.data, maxBytes)
        } else {
            val approx = estimateDecodedBase64Size(att.data)
            if (approx > maxBytes) return null
            try {
                Base64.Mime.decode(att.data)
            } catch (t: Throwable) {
                null
            }
        }
    }

    private fun estimateDecodedBase64Size(base64: String): Long {
        if (base64.isEmpty()) return 0
        val padding = when {
            base64.endsWith("==") -> 2
            base64.endsWith("=") -> 1
            else -> 0
        }
        return (base64.length.toLong() * 3 / 4) - padding
    }

    private fun safeFolder(att: Attachment): String {
        val key = att.data.ifBlank { att.name }
        return key.filter { it.isLetterOrDigit() }.take(24).ifBlank { "att" }
    }

    private fun safeName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifBlank { "attachment" }.take(120)
    }
}
