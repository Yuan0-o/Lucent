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
        val dest = fileFor(context, id).toString().toPath()
        PlatformOutputStream(FileCrypto.encryptingSink(okio.FileSystem.SYSTEM.sink(dest), DataKeys.attachmentKey(context)).buffer().outputStream())
    } catch (t: Throwable) {
        null
    }

    actual fun openInputStream(context: PlatformContext, id: String): PlatformInputStream? = try {
        val file = fileFor(context, id).toString().toPath()
        when {
            !okio.FileSystem.SYSTEM.exists(file) -> null
            FileCrypto.isEncrypted(okio.FileSystem.SYSTEM, file.toString().toPath()) ->
                FileCrypto.decryptingSource(okio.FileSystem.SYSTEM.source(file), DataKeys.attachmentKey(context)).buffer().inputStream()
            else -> okio.FileSystem.SYSTEM.source(file).buffer().inputStream()
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
        okio.FileSystem.SYSTEM.exists(fileFor(context, id).toString().toPath())

    actual fun sizeOf(context: PlatformContext, id: String): Long {
        val file = fileFor(context, id).toString().toPath()
        if (!okio.FileSystem.SYSTEM.exists(file)) return 0L
        return if (FileCrypto.isEncrypted(okio.FileSystem.SYSTEM, file.toString().toPath())) FileCrypto.plaintextSizeOf(okio.FileSystem.SYSTEM, file.toString().toPath()) else (okio.FileSystem.SYSTEM.metadataOrNull(file)?.size ?: 0L)
    }

    actual fun totalBytes(context: PlatformContext): Long =
        baseDir(context).listFiles()?.sumOf { f ->
            val jf = f.toString().toPath()
            if (FileCrypto.isEncrypted(okio.FileSystem.SYSTEM, jf.toString().toPath())) FileCrypto.plaintextSizeOf(okio.FileSystem.SYSTEM, jf.toString().toPath()) else (okio.FileSystem.SYSTEM.metadataOrNull(jf)?.size ?: 0L)
        } ?: 0L

    actual fun encryptExistingFile(context: PlatformContext, id: String): Boolean {
        val file = fileFor(context, id).toString().toPath()
        if (!okio.FileSystem.SYSTEM.exists(file)) return false
        if (FileCrypto.isEncrypted(okio.FileSystem.SYSTEM, file.toString().toPath())) return true
        return try {
            val key = DataKeys.attachmentKey(context)
            val temp = (file.parent!!.toString() + "/" + "${file.name}.enc.tmp").toPath()
            okio.FileSystem.SYSTEM.source(file).buffer().inputStream().use { input ->
                FileCrypto.encryptingSink(okio.FileSystem.SYSTEM.sink(temp), DataKeys.attachmentKey(context)).buffer().outputStream().use { output ->
                    copyStream(input, output)
                }
            }
            if (run { try { okio.FileSystem.SYSTEM.atomicMove(temp, file); true } catch (e: Exception) { false } }) {
                true
            } else {
                if (run { try { okio.FileSystem.SYSTEM.delete(file); true } catch (e: Exception) { false } } && run { try { okio.FileSystem.SYSTEM.atomicMove(temp, file); true } catch (e: Exception) { false } }) true else { run { try { okio.FileSystem.SYSTEM.delete(temp); true } catch (e: Exception) { false } }; false }
            }
        } catch (t: Throwable) {
            try { okio.FileSystem.SYSTEM.delete((file.parent!!.toString() + "/" + "${file.name}.enc.tmp").toPath()) } catch (e: Exception) {}
            false
        }
    }

    actual fun delete(context: PlatformContext, id: String): Boolean {
        val file = fileFor(context, id).toString().toPath()
        return !okio.FileSystem.SYSTEM.exists(file) || run { try { okio.FileSystem.SYSTEM.delete(file); true } catch (e: Exception) { false } }
    }

    actual fun pruneOrphans(context: PlatformContext, referencedIds: Set<String>) {
        baseDir(context).listFiles()?.forEach { file ->
            if (file.name !in referencedIds && looksLikeId(file.name)) run { try { okio.FileSystem.SYSTEM.delete(file.toString().toPath()); true } catch (e: Exception) { false } }
        }
    }

    fun importFile(context: PlatformContext, source: File): String? {
        val id = UUID.randomUUID().toString()
        val dest = fileFor(context, id).toString().toPath()
        return try {
            okio.FileSystem.SYSTEM.source(source.toString().toPath()).buffer().inputStream().use { input ->
                val out = openOutputStream(context, id) ?: run { try { okio.FileSystem.SYSTEM.delete(dest) } catch (e: Exception) {}; return null }
                try {
                    copyStream(input, out.stream)
                } finally {
                    out.close()
                }
            }
            id
        } catch (e: IOException) {
            try { okio.FileSystem.SYSTEM.delete(dest) } catch (e: Exception) {}
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
