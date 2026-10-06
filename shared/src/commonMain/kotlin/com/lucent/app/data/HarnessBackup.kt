package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import com.lucent.app.harness.HarnessRuntime
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlin.concurrent.Volatile

internal object HarnessBackup {

    const val MAX_NAME_BYTES = BackupFrames.MAX_BLOB_NAME_BYTES

    @Volatile private var activeHome: Path? = null

    fun home(): Path = activeHome ?: HarnessRuntime.homePath().toPath()

    fun begin(): Path {
        val dir = HarnessRuntime.homePath().toPath()
        activeHome = dir
        return dir
    }

    fun end() {
        activeHome = null
    }

    fun <T> useHome(block: () -> T): T {
        begin()
        return try {
            block()
        } finally {
            end()
        }
    }

    fun listFiles(): List<Pair<String, Path>> {
        begin()
        try {
            val base = home()
            val files = mutableListOf<Pair<Path, String>>()
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
                (FileSystem.SYSTEM.metadata(file).size ?: -1)
            } catch (_: Throwable) {
                0L
            }
        }
        return files.size to total
    }

    private fun collect(root: Path, dir: Path, out: MutableList<Pair<Path, String>>) {
        val children = try {
            FileSystem.SYSTEM.list(dir)
        } catch (_: Throwable) {
            return
        }
        val sorted = children.sortedBy { it.name }
        for (child in sorted) {
            if (FileSystem.SYSTEM.metadata(child).isDirectory == true) {
                collect(root, child, out)
            } else if (FileSystem.SYSTEM.metadata(child).isRegularFile == true) {
                val rel = child.toString().removePrefix(root.toString()).replace('\\', '/')
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

    fun prepareRestoreTarget(name: String): Path {
        val rel = relativePath(name) ?: throw IllegalArgumentException("Unsafe harness entry: $name")
        val base = home()
        val target = base / rel
        val rootPath = base.toString().trimEnd('/', '\\') + okio.Path.DIRECTORY_SEPARATOR
        if (!target.toString().startsWith(rootPath)) {
            throw IllegalArgumentException("Unsafe harness entry: $name")
        }
        target.parent?.let { if (!FileSystem.SYSTEM.exists(it)) FileSystem.SYSTEM.createDirectories(it) }
        return target
    }

    fun writeEntry(
        context: PlatformContext,
        name: String,
        dataLen: Long,
        data: okio.Source,
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
        var tmp: Path? = null
        var out: okio.Sink? = null
        var written = 0L
        try {
            val tmpFile = (target.toString() + ".tmp").toPath()
            tmp = tmpFile
            out = FileSystem.SYSTEM.sink(tmpFile)
            
            while (written < dataLen) {
                BackupFrames.throwIfCancelled(cancelled)
                val n = data.read(scratch, 0, minOf(dataLen - written, scratch.size.toLong()).toInt())
                if (n < 0) throw okio.EOFException("Backup payload ended early")
                out.write(scratch, 0, n)
                written += n
            }
            out.close()
            out = null
            if (FileSystem.SYSTEM.exists(target)) FileSystem.SYSTEM.delete(target)
            try { FileSystem.SYSTEM.atomicMove(tmpFile, target); return true } catch (_: Throwable) {}
            FileSystem.SYSTEM.delete(tmpFile)
            record(context, name, "file could not be placed")
            return false
        } catch (t: Throwable) {
            try { out?.close() } catch (_: Throwable) {
            }
            tmp?.let { FileSystem.SYSTEM.delete(it) }
            if (t is kotlinx.coroutines.CancellationException) throw t
            if (t is okio.EOFException) throw t
            skipRemaining(data, dataLen - written, scratch, cancelled)
            record(context, name, t.message ?: t::class.simpleName ?: "error")
            return false
        }
    }

    fun record(context: PlatformContext, name: String, detail: String) {
        val rel = relativePath(name) ?: name
        try {
            StartupLog.event(context, "Harness restore skipped $rel — $detail")
        } catch (_: Throwable) {
        }
    }

    private fun skipRemaining(
        data: okio.Source,
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

private fun okio.Source.read(b: ByteArray, off: Int, len: Int): Int {
    val buf = okio.Buffer()
    val n = this.read(buf, len.toLong())
    if (n == -1L) return -1
    buf.read(b, off, n.toInt())
    return n.toInt()
}

private fun okio.Source.read(b: ByteArray): Int = read(b, 0, b.size)

private fun okio.Source.read(): Int {
    val buf = okio.Buffer()
    val n = this.read(buf, 1)
    if (n == -1L) return -1
    return buf.readByte().toInt() and 0xFF
}

private fun okio.Sink.write(b: Int) {
    val buf = okio.Buffer()
    buf.writeByte(b)
    this.write(buf, 1)
}

private fun okio.Sink.write(b: ByteArray, off: Int, len: Int) {
    val buf = okio.Buffer()
    buf.write(b, off, len)
    this.write(buf, len.toLong())
}

private fun okio.Sink.write(b: ByteArray) = write(b, 0, b.size)
