package com.lucent.app.data
import com.lucent.app.platform.applicationContext
import com.lucent.app.platform.filesDir

import com.lucent.app.platform.PlatformContext
import com.lucent.app.PlatformFile
import com.lucent.app.PlatformOutputStream
import com.lucent.app.platform.PlatformInputStream
import java.io.File
import java.io.IOException
import okio.buffer
import okio.sink
import okio.source
import okio.Path.Companion.toPath
import okio.FileSystem
import java.util.UUID

actual object AttachmentStore {
    private const val DIR_NAME = "attachments"
    private const val COPY_BUFFER = 64 * 1024

    actual fun baseDir(context: PlatformContext): PlatformFile {
        val ctx = context as PlatformContext
        return PlatformFile((ctx.applicationContext.filesDir / DIR_NAME).also { okio.FileSystem.SYSTEM.createDirectories(it) }.toString())
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
        val dest = File(fileFor(context, id).toString())
        PlatformOutputStream(FileCrypto.encryptingSink(dest.sink(), DataKeys.attachmentKey(context)).buffer().outputStream())
    } catch (t: Throwable) {
        null
    }

    actual fun openInputStream(context: PlatformContext, id: String): PlatformInputStream? = try {
        val file = File(fileFor(context, id).toString())
        when {
            !okio.FileSystem.SYSTEM.exists(file) -> null
            FileCrypto.isEncrypted(okio.FileSystem.SYSTEM, file.toString().toPath()) ->
                FileCrypto.decryptingSource(file.source(), DataKeys.attachmentKey(context)).buffer().inputStream()
            else -> file.inputStream()
        }
    } catch (t: Throwable) {
        null
    }

    actual fun readBytes(context: PlatformContext, id: String, maxBytes: Long): ByteArray? {
        val input = openInputStream(context, id) as? java.io.InputStream ?: return null
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

    actual fun exists(context: PlatformContext, id: String): Boolean =
        fileFor(context, id).exists()

    actual fun sizeOf(context: PlatformContext, id: String): Long {
        val file = File(fileFor(context, id).toString())
        if (!okio.FileSystem.SYSTEM.exists(file)) return 0L
        return if (FileCrypto.isEncrypted(okio.FileSystem.SYSTEM, file.toString().toPath())) FileCrypto.plaintextSizeOf(okio.FileSystem.SYSTEM, file.toString().toPath()) else (okio.FileSystem.SYSTEM.metadataOrNull(file)?.size ?: 0L)
    }

    actual fun totalBytes(context: PlatformContext): Long =
        baseDir(context).listFiles()?.sumOf { f ->
            val jf = File(f.toString())
            if (FileCrypto.isEncrypted(okio.FileSystem.SYSTEM, jf.toString().toPath())) FileCrypto.plaintextSizeOf(okio.FileSystem.SYSTEM, jf.toString().toPath()) else (okio.FileSystem.SYSTEM.metadataOrNull(jf)?.size ?: 0L)
        } ?: 0L

    actual fun encryptExistingFile(context: PlatformContext, id: String): Boolean {
        val file = File(fileFor(context, id).toString())
        if (!okio.FileSystem.SYSTEM.exists(file)) return false
        if (FileCrypto.isEncrypted(okio.FileSystem.SYSTEM, file.toString().toPath())) return true
        return try {
            val key = DataKeys.attachmentKey(context)
            val temp = File(file.parent, "${file.name}.enc.tmp")
            file.inputStream().use { input ->
                FileCrypto.encryptingSink(temp.sink(), DataKeys.attachmentKey(context)).buffer().outputStream().use { output ->
                    copyStream(input, output)
                }
            }
            if (temp.renameTo(file)) true
            else {
                if (okio.FileSystem.SYSTEM.delete(file) && temp.renameTo(file)) true else { okio.FileSystem.SYSTEM.delete(temp); false }
            }
        } catch (t: Throwable) {
            File(file.parent, "${file.name}.enc.tmp").delete()
            false
        }
    }

    actual fun delete(context: PlatformContext, id: String): Boolean {
        val file = File(fileFor(context, id).toString())
        return !okio.FileSystem.SYSTEM.exists(file) || okio.FileSystem.SYSTEM.delete(file)
    }

    actual fun pruneOrphans(context: PlatformContext, referencedIds: Set<String>) {
        baseDir(context).listFiles()?.forEach { file ->
            if (file.name !in referencedIds && looksLikeId(file.name)) okio.FileSystem.SYSTEM.delete(file)
        }
    }

    fun importFile(context: PlatformContext, source: File): String? {
        val id = UUID.randomUUID().toString()
        val dest = File(fileFor(context, id).toString())
        return try {
            source.inputStream().use { input ->
                val out = openOutputStream(context, id) ?: run { okio.FileSystem.SYSTEM.delete(dest); return null }
                try {
                    copyStream(input, out.stream)
                } finally {
                    out.close()
                }
            }
            id
        } catch (e: IOException) {
            okio.FileSystem.SYSTEM.delete(dest)
            null
        }
    }

    private fun copyStream(input: java.io.InputStream, output: java.io.OutputStream): Long {
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
