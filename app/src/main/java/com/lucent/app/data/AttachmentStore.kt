package com.lucent.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

object AttachmentStore {

    private const val DIR_NAME = "attachments"
    private const val COPY_BUFFER = 64 * 1024

    fun baseDir(context: Context): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        return File(root, DIR_NAME).apply { if (!exists()) mkdirs() }
    }

    fun fileFor(context: Context, id: String): File = File(baseDir(context), id)

    fun looksLikeId(value: String): Boolean {
        if (value.length != 36) return false
        return try {
            UUID.fromString(value); true
        } catch (e: Exception) {
            false
        }
    }

    fun importUri(context: Context, uri: Uri): String? {
        val id = UUID.randomUUID().toString()
        val dest = fileFor(context, id)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                openOutputStream(context, id)?.use { output -> copyStream(input, output) }
                    ?: run { dest.delete(); return null }
            } ?: run { dest.delete(); return null }
            id
        } catch (e: IOException) {
            dest.delete()
            null
        }
    }

    fun importBytes(context: Context, bytes: ByteArray): String? {
        val id = UUID.randomUUID().toString()
        return if (writeBytes(context, id, bytes)) id else null
    }

    fun writeBytes(context: Context, id: String, bytes: ByteArray): Boolean = try {
        openOutputStream(context, id)?.use { it.write(bytes) } != null
    } catch (e: IOException) {
        delete(context, id)
        false
    }

    fun openOutputStream(context: Context, id: String): OutputStream? = try {
        val key = DataKeys.attachmentKey(context)
        val dest = fileFor(context, id)
        FileCrypto.encryptingStream(dest.outputStream(), key)
    } catch (t: Throwable) {
        null
    }

    fun openInputStream(context: Context, id: String): InputStream? = try {
        val file = fileFor(context, id)
        if (!file.exists()) {
            null
        } else if (FileCrypto.isEncrypted(file)) {
            FileCrypto.decryptingStream(file.inputStream(), DataKeys.attachmentKey(context))
        } else {
            file.inputStream()
        }
    } catch (t: Throwable) {
        null
    }

    fun readBytes(context: Context, id: String, maxBytes: Long = 32L * 1024 * 1024): ByteArray? {
        if (sizeOf(context, id) > maxBytes) return null
        return try {
            openInputStream(context, id)?.use { it.readBytes() }
        } catch (t: Throwable) {
            null
        }
    }

    fun exists(context: Context, id: String): Boolean = fileFor(context, id).exists()

    fun sizeOf(context: Context, id: String): Long {
        val f = fileFor(context, id)
        if (!f.exists()) return 0L
        return if (FileCrypto.isEncrypted(f)) FileCrypto.plaintextSizeOf(f) else f.length()
    }

    fun totalBytes(context: Context): Long =
        baseDir(context).listFiles()?.sumOf { f ->
            if (FileCrypto.isEncrypted(f)) FileCrypto.plaintextSizeOf(f) else f.length()
        } ?: 0L

    fun encryptExistingFile(context: Context, id: String): Boolean {
        val file = fileFor(context, id)
        if (!file.exists() || FileCrypto.isEncrypted(file)) return false
        val temp = File(file.parentFile, "$id.enc-tmp")
        return try {
            val key = DataKeys.attachmentKey(context)
            file.inputStream().use { input ->
                FileCrypto.encryptingStream(temp.outputStream(), key).use { output ->
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

    fun delete(context: Context, id: String): Boolean {
        val f = fileFor(context, id)
        return if (f.exists()) f.delete() else true
    }

    fun pruneOrphans(context: Context, referencedIds: Set<String>) {
        baseDir(context).listFiles()?.forEach { f ->
            if (f.name !in referencedIds) f.delete()
        }
    }

    fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
    } catch (e: Exception) {
        null
    }

    fun sizeHint(context: Context, uri: Uri): Long = try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else -1L
            } ?: -1L
    } catch (e: Exception) {
        -1L
    }

    private fun copyStream(input: InputStream, output: java.io.OutputStream): Long {
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
