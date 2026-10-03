package com.lucent.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream

object AttachmentAccess {

    private const val PREVIEW_DIR = "attachment-preview"
    private const val COPY_BUFFER = 64 * 1024

    private fun previewDir(context: Context): File =
        File(context.applicationContext.cacheDir, PREVIEW_DIR).apply { if (!exists()) mkdirs() }

    private fun authority(context: Context): String =
        "${context.applicationContext.packageName}.fileprovider"

    fun contentUri(context: Context, att: Attachment): Uri? {
        val plaintext = materialize(context, att) ?: return null
        return try {
            FileProvider.getUriForFile(context.applicationContext, authority(context), plaintext)
        } catch (t: Throwable) {
            null
        }
    }

    fun materialize(context: Context, att: Attachment): File? {
        val dir = File(previewDir(context), safeFolder(att)).apply { if (!exists()) mkdirs() }
        val dest = File(dir, safeName(att.name))
        val stream = Attachments.openStream(context, att)
        return try {
            if (stream != null) {
                stream.use { input ->
                    dest.outputStream().use { output ->
                        val buf = ByteArray(COPY_BUFFER)
                        while (true) {
                            val r = input.read(buf); if (r == -1) break; output.write(buf, 0, r)
                        }
                        output.flush()
                    }
                }
            } else {
                val bytes = Attachments.readBytes(context, att, maxBytes = Long.MAX_VALUE) ?: return null
                dest.outputStream().use { it.write(bytes); it.flush() }
            }
            dest
        } catch (t: Throwable) {
            dest.delete()
            null
        }
    }

    fun writeTo(context: Context, att: Attachment, out: OutputStream): Boolean {
        val stream = Attachments.openStream(context, att)
        return try {
            out.use { output ->
                if (stream != null) {
                    stream.use { input ->
                        val buf = ByteArray(COPY_BUFFER)
                        while (true) {
                            val r = input.read(buf); if (r == -1) break; output.write(buf, 0, r)
                        }
                    }
                } else {
                    val bytes = Attachments.readBytes(context, att, maxBytes = Long.MAX_VALUE) ?: return false
                    output.write(bytes)
                }
                output.flush()
            }
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun clearPreviewCache(context: Context) {
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
