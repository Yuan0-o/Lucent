package com.lucent.app.data

import android.content.Context
import java.io.File
import java.io.OutputStream

object AttachmentAccess {

    private const val PREVIEW_DIR = "attachment-preview"
    private const val COPY_BUFFER = 64 * 1024

    private fun previewDir(context: Context): File =
        File(context.applicationContext.cacheDir, PREVIEW_DIR).apply { if (!exists()) mkdirs() }

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

    fun openExternally(context: Context, att: Attachment): Boolean {
        val file = materialize(context, att) ?: return false
        return try {
            if (!java.awt.Desktop.isDesktopSupported()) return false
            java.awt.Desktop.getDesktop().open(file)
            true
        } catch (t: Throwable) {
            false
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
            }
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun clearPreviewCache(context: Context) {
        previewDir(context).deleteRecursively()
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
