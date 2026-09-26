package com.lucent.app.data

import com.lucent.app.harness.HarnessRuntime
import java.io.File

internal object HarnessBackup {

    const val MAX_NAME_BYTES = BackupFrames.MAX_BLOB_NAME_BYTES

    @Volatile private var activeHome: File? = null

    fun home(): File = activeHome ?: HarnessRuntime.home()

    fun begin(): File {
        val dir = HarnessRuntime.home()
        activeHome = dir
        return dir
    }

    fun end() {
        activeHome = null
    }

    fun useHome(block: () -> Unit) {
        begin()
        try {
            block()
        } finally {
            end()
        }
    }

    fun listFiles(): List<Pair<String, File>> {
        begin()
        try {
            val base = home()
            val files = mutableListOf<Pair<File, String>>()
            collect(base, base, files)
            files.sortBy { it.second }
            return files.map { (file, rel) -> blobName(rel) to file }
        } finally {
            end()
        }
    }

    fun totalBytes(): Long = summary().second

    fun summary(): Pair<Int, Long> {
        val files = listFiles()
        var total = 0L
        for ((_, file) in files) {
            total += try {
                file.length()
            } catch (_: Throwable) {
                0L
            }
        }
        return files.size to total
    }

    private fun collect(root: File, dir: File, out: MutableList<Pair<File, String>>) {
        val children = try {
            dir.listFiles() ?: return
        } catch (_: Throwable) {
            return
        }
        children.sortBy { it.name }
        for (child in children) {
            if (child.isDirectory) {
                collect(root, child, out)
            } else if (child.isFile) {
                val rel = child.absolutePath.removePrefix(root.absolutePath).replace(File.separatorChar, '/')
                    .trimStart('/')
                if (rel.isNotEmpty()) out.add(child to rel)
            }
        }
    }

    fun blobName(relativePath: String): String = BackupFrames.HARNESS_BLOB_PREFIX + relativePath

    fun relativePath(name: String): String? {
        val rel = name.removePrefix(BackupFrames.HARNESS_BLOB_PREFIX).replace('\\', '/')
        val trimmed = rel.trimStart('/')
        if (trimmed.isEmpty()) return null
        if (trimmed.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES) return null
        val parts = trimmed.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        return trimmed
    }

    fun prepareRestoreTarget(name: String): File {
        val rel = relativePath(name) ?: throw IllegalArgumentException("Unsafe harness entry: $name")
        val base = home()
        val target = File(base, rel)
        val rootPath = base.absolutePath.trimEnd(File.separatorChar) + File.separator
        if (!target.absolutePath.startsWith(rootPath)) {
            throw IllegalArgumentException("Unsafe harness entry: $name")
        }
        target.parentFile?.let { if (!it.exists()) it.mkdirs() }
        return target
    }

    fun writeEntry(
        context: android.content.Context,
        name: String,
        dataLen: Long,
        data: java.io.InputStream,
        scratch: ByteArray,
        cancelled: (() -> Boolean)?
    ): Boolean {
        val target = try {
            prepareRestoreTarget(name)
        } catch (_: Throwable) {
            skipRemaining(data, dataLen, scratch, cancelled)
            record(context, name, "unsafe entry name")
            return false
        }
        var tmp: File? = null
        var out: java.io.OutputStream? = null
        var written = 0L
        try {
            val tmpFile = File(target.absolutePath + ".tmp")
            tmp = tmpFile
            val os = tmpFile.outputStream()
            out = os
            while (written < dataLen) {
                BackupFrames.throwIfCancelled(cancelled)
                val n = data.read(scratch, 0, minOf(dataLen - written, scratch.size.toLong()).toInt())
                if (n < 0) throw java.io.EOFException("Backup payload ended early")
                os.write(scratch, 0, n)
                written += n
            }
            os.close()
            out = null
            if (target.exists()) target.delete()
            if (tmpFile.renameTo(target)) return true
            tmpFile.delete()
            record(context, name, "file could not be placed")
            return false
        } catch (t: Throwable) {
            try { out?.close() } catch (_: Throwable) {
            }
            tmp?.delete()
            if (t is kotlinx.coroutines.CancellationException) throw t
            if (t is java.io.EOFException) throw t
            skipRemaining(data, dataLen - written, scratch, cancelled)
            record(context, name, t.message ?: t::class.simpleName ?: "error")
            return false
        }
    }

    fun record(context: android.content.Context, name: String, detail: String) {
        val rel = relativePath(name) ?: name
        try {
            StartupLog.event(context, "Harness restore skipped $rel — $detail")
        } catch (_: Throwable) {
        }
    }

    private fun skipRemaining(
        data: java.io.InputStream,
        count: Long,
        scratch: ByteArray,
        cancelled: (() -> Boolean)?
    ) {
        try {
            BackupFrames.skipFully(data, count, scratch, cancelled)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
        }
    }
}

fun harnessBackupSummary(): Pair<Int, Long> = HarnessBackup.summary()
