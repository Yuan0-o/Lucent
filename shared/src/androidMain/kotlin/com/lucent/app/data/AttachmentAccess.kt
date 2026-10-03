package com.lucent.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.lucent.app.PlatformFile
import com.lucent.app.PlatformOutputStream
import com.lucent.app.platform.PlatformContext
import java.io.File

actual object AttachmentAccess {
    private const val PREVIEW_DIR = "attachment-preview"
    private const val COPY_BUFFER = 64 * 1024

    private fun previewDir(context: PlatformContext): PlatformFile {
        val ctx = context as Context
        return PlatformFile(File(ctx.applicationContext.cacheDir, PREVIEW_DIR).apply { if (!exists()) mkdirs() }.absolutePath)
    }

    private fun authority(context: PlatformContext): String {
        val ctx = context as Context
        return "${ctx.applicationContext.packageName}.fileprovider"
    }

    fun contentUri(context: PlatformContext, att: Attachment): Uri? {
        val ctx = context as Context
        val plaintext = materialize(context, att) ?: return null
        return try {
            FileProvider.getUriForFile(ctx.applicationContext, authority(context), File(plaintext.absolutePath))
        } catch (t: Throwable) {
            null
        }
    }

    actual fun materialize(context: PlatformContext, att: Attachment): PlatformFile? {
        val dir = PlatformFile(previewDir(context), safeFolder(att)).apply { if (!exists()) mkdirs() }
        val dest = PlatformFile(dir, safeName(att.name))
        val stream = Attachments.openStream(context as Context, att) as? java.io.InputStream
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
                val bytes = Attachments.readBytes(context as Context, att, maxBytes = Long.MAX_VALUE) ?: return null
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

    actual fun writeTo(context: PlatformContext, att: Attachment, out: PlatformOutputStream): Boolean {
        val stream = Attachments.openStream(context as Context, att) as? java.io.InputStream
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
                    val bytes = Attachments.readBytes(context as Context, att, maxBytes = Long.MAX_VALUE) ?: return false
                    out.write(bytes)
                }
                out.flush()
            } finally {
                out.close()
            }
            true
        } catch (t: Throwable) {
            false
        }
    }

    actual fun clearPreviewCache(context: PlatformContext) {
        try {
            previewDir(context).listFiles()?.forEach { it.deleteRecursively() }
        } catch (_: Throwable) {
        }
    }

    private fun safeFolder(att: Attachment): String {
        val basis = if (AttachmentStore.looksLikeId(att.data)) att.data else att.name
        return basis.filter { it.isLetterOrDigit() || it == '-' }.take(40).ifBlank { "att" }
    }

    private fun safeName(name: String): String {
        val cleaned = name.replace('\\', '_').replace('/', '_').trim()
        return cleaned.ifBlank { "file" }
    }
}
