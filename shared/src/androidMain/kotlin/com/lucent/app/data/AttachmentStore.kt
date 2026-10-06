package com.lucent.app.data
import com.lucent.app.platform.filesDir

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lucent.app.PlatformFile
import com.lucent.app.PlatformOutputStream
import com.lucent.app.platform.PlatformContext
import com.lucent.app.platform.PlatformInputStream
import java.io.File
import java.io.IOException
import okio.buffer
import okio.Path.Companion.toPath
import okio.FileSystem
import java.util.UUID

actual object AttachmentStore {
    private const val DIR_NAME = "attachments"
    private const val COPY_BUFFER = 64 * 1024

    actual fun baseDir(context: PlatformContext): PlatformFile {
        val ctx = context as Context
        val root = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        return PlatformFile(File(root, DIR_NAME).apply { if (!exists()) mkdirs() }.absolutePath)
    }

    actual fun fileFor(context: PlatformContext, id: String): PlatformFile =
        PlatformFile(baseDir(context), id)

    actual fun looksLikeId(value: String): Boolean {
        if (value.length != 36) return false
        return try {
            UUID.fromString(value); true
        } catch (e: Exception) {
            false
        }
    }

    actual fun importBytes(context: PlatformContext, bytes: ByteArray): String? {
        val id = UUID.randomUUID().toString()
        return if (writeBytes(context, id, bytes)) id else null
    }

    actual fun writeBytes(context: PlatformContext, id: String, bytes: ByteArray): Boolean = try {
        val out = openOutputStream(context, id) ?: return false
        try {
            out.write(bytes)
            out.flush()
        } finally {
            out.close()
        }
        true
    } catch (e: IOException) {
        delete(context, id)
        false
    }

    actual fun openOutputStream(context: PlatformContext, id: String): PlatformOutputStream? = try {
        val key = DataKeys.attachmentKey(context)
        val dest = File(fileFor(context, id).absolutePath)
        PlatformOutputStream(FileCrypto.encryptingSink(FileSystem.SYSTEM.sink(dest.absolutePath.toPath()), DataKeys.attachmentKey(context)).buffer().outputStream())
    } catch (t: Throwable) {
        null
    }

    actual fun openInputStream(context: PlatformContext, id: String): PlatformInputStream? = try {
        val file = File(fileFor(context, id).absolutePath)
        if (!file.exists()) {
            null
        } else if (FileCrypto.isEncrypted(okio.FileSystem.SYSTEM, okio.Path.Companion.toPath(file.absolutePath))) {
            FileCrypto.decryptingSource(FileSystem.SYSTEM.source(file.absolutePath.toPath()), DataKeys.attachmentKey(context)).buffer().inputStream()
        } else {
            file.inputStream()
        }
    } catch (t: Throwable) {
        null
    }

    actual fun readBytes(context: PlatformContext, id: String, maxBytes: Long): ByteArray? {
        if (sizeOf(context, id) > maxBytes) return null
        return try {
            (openInputStream(context, id) as? java.io.InputStream)?.use { it.readBytes() }
        } catch (t: Throwable) {
            null
        }
    }

    actual fun exists(context: PlatformContext, id: String): Boolean =
        fileFor(context, id).exists()

    actual fun sizeOf(context: PlatformContext, id: String): Long {
        val f = File(fileFor(context, id).absolutePath)
        if (!f.exists()) return 0L
        return if (FileCrypto.isEncrypted(okio.FileSystem.SYSTEM, okio.Path.Companion.toPath(f.absolutePath))) FileCrypto.plaintextSizeOf(okio.FileSystem.SYSTEM, okio.Path.Companion.toPath(f.absolutePath)) else f.length()
    }

    actual fun totalBytes(context: PlatformContext): Long =
        baseDir(context).listFiles()?.sumOf { f ->
            val jf = File(f.absolutePath)
            if (FileCrypto.isEncrypted(okio.FileSystem.SYSTEM, okio.Path.Companion.toPath(jf.absolutePath))) FileCrypto.plaintextSizeOf(okio.FileSystem.SYSTEM, okio.Path.Companion.toPath(jf.absolutePath)) else jf.length()
        } ?: 0L

    actual fun encryptExistingFile(context: PlatformContext, id: String): Boolean {
        val file = File(fileFor(context, id).absolutePath)
        if (!file.exists() || FileCrypto.isEncrypted(okio.FileSystem.SYSTEM, okio.Path.Companion.toPath(file.absolutePath))) return false
        val temp = File(file.parentFile, "$id.enc-tmp")
        return try {
            val key = DataKeys.attachmentKey(context)
            file.inputStream().use { input ->
                FileCrypto.encryptingSink(FileSystem.SYSTEM.sink(temp.absolutePath.toPath()), DataKeys.attachmentKey(context)).buffer().outputStream().use { output ->
                    copyStream(input, output)
                }
            }
            if (temp.renameTo(file)) {
                true
            } else {
                temp.delete()
                false
            }
        } catch (t: Throwable) {
            temp.delete()
            false
        }
    }

    actual fun delete(context: PlatformContext, id: String): Boolean {
        val f = fileFor(context, id)
        return if (f.exists()) f.delete() else true
    }

    actual fun pruneOrphans(context: PlatformContext, referencedIds: Set<String>) {
        baseDir(context).listFiles()?.forEach { f ->
            if (f.name !in referencedIds) f.delete()
        }
    }

    fun importUri(context: PlatformContext, uri: Uri): String? {
        val ctx = context as Context
        val id = UUID.randomUUID().toString()
        val dest = File(fileFor(context, id).absolutePath)
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                openOutputStream(context, id)?.let { output ->
                    try {
                        copyStream(input, output.stream)
                    } finally {
                        output.close()
                    }
                } ?: run { dest.delete(); return null }
            } ?: run { dest.delete(); return null }
            id
        } catch (e: IOException) {
            dest.delete()
            null
        }
    }

    fun queryDisplayName(context: PlatformContext, uri: Uri): String? = try {
        val ctx = context as Context
        ctx.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
    } catch (e: Exception) {
        null
    }

    fun sizeHint(context: PlatformContext, uri: Uri): Long = try {
        val ctx = context as Context
        ctx.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else -1L
            } ?: -1L
    } catch (e: Exception) {
        -1L
    }

    private fun copyStream(input: java.io.InputStream, output: java.io.OutputStream): Long {
        val buffer = ByteArray(COPY_BUFFER)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            total += read
        }
        output.flush()
        return total
    }
}
