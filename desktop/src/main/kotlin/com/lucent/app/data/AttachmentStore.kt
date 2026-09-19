package com.lucent.app.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

object AttachmentStore {

    private const val DIR_NAME = "attachments"
    private const val COPY_BUFFER = 64 * 1024

    fun baseDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    fun fileFor(context: Context, id: String): File = File(baseDir(context), id)

    fun looksLikeId(value: String): Boolean {
        if (value.length != 36) return false
        return try {
            UUID.fromString(value); true
        } catch (e: Exception) {
            false
        }
    }

    fun importFile(context: Context, source: File): String? {
        val id = UUID.randomUUID().toString()
        val dest = fileFor(context, id)
        return try {
            source.inputStream().use { input ->
                openOutputStream(context, id)?.use { output -> copyStream(input, output) }
                    ?: run { dest.delete(); return null }
            }
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
        when {
            !file.exists() -> null
            FileCrypto.isEncrypted(file) ->
                FileCrypto.decryptingStream(file.inputStream(), DataKeys.attachmentKey(context))
            else -> file.inputStream()
        }
    } catch (t: Throwable) {
        null
    }

    fun readBytes(context: Context, id: String, maxBytes: Long = 32L * 1024 * 1024): ByteArray? {
        val input = openInputStream(context, id) ?: return null
        return try {
            input.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(COPY_BUFFER)
                var total = 0L
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) return null
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        } catch (t: Throwable) {
            null
        }
    }

    fun exists(context: Context, id: String): Boolean = fileFor(context, id).exists()

    fun sizeOf(context: Context, id: String): Long {
        val file = fileFor(context, id)
        if (!file.exists()) return 0L
        return if (FileCrypto.isEncrypted(file)) FileCrypto.plaintextSizeOf(file) else file.length()
    }

    fun totalBytes(context: Context): Long =
        baseDir(context).listFiles()?.sumOf { f ->
            if (FileCrypto.isEncrypted(f)) FileCrypto.plaintextSizeOf(f) else f.length()
        } ?: 0L

    fun encryptExistingFile(context: Context, id: String): Boolean {
        val file = fileFor(context, id)
        if (!file.exists()) return false
        if (FileCrypto.isEncrypted(file)) return true
        return try {
            val key = DataKeys.attachmentKey(context)
            val temp = File(file.parentFile, "${file.name}.enc.tmp")
            file.inputStream().use { input ->
                FileCrypto.encryptingStream(temp.outputStream(), key).use { output ->
                    copyStream(input, output)
                }
            }
            if (temp.renameTo(file)) true
            else {
                if (file.delete() && temp.renameTo(file)) true else { temp.delete(); false }
            }
        } catch (t: Throwable) {
            File(file.parentFile, "${file.name}.enc.tmp").delete()
            false
        }
    }

    fun delete(context: Context, id: String): Boolean {
        val file = fileFor(context, id)
        return !file.exists() || file.delete()
    }

    fun pruneOrphans(context: Context, referencedIds: Set<String>) {
        baseDir(context).listFiles()?.forEach { file ->
            if (file.name !in referencedIds && looksLikeId(file.name)) file.delete()
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(COPY_BUFFER)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
            total += n
        }
        return total
    }
}
