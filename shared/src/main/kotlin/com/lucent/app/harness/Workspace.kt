package com.lucent.app.harness

import java.io.File
import java.nio.charset.StandardCharsets

class HarnessError(message: String, val blocked: Boolean = false) : Exception(message)

object Workspace {

    private const val TEXT_PROBE_BYTES = 4096

    private val BLOCKED_PREFIXES = listOf(
        "/proc", "/sys", "/dev", "/system", "/vendor", "/product", "/apex", "/data/data", "/data/system"
    )

    fun normalize(path: String): String = path.trim().replace('\\', '/').removeSuffix("/")

    fun isInside(file: File, root: File): Boolean = try {
        val target = file.canonicalPath
        val base = root.canonicalPath
        target == base || target.startsWith(base + File.separator)
    } catch (e: Exception) {
        false
    }

    fun blocked(path: String): Boolean {
        val clean = normalize(path)
        if (clean.isEmpty()) return false
        if (HarnessRuntime.android) {
            if (BLOCKED_PREFIXES.any { clean == it || clean.startsWith("$it/") }) return true
        } else {
            if (clean.startsWith("/proc") || clean.startsWith("/sys") || clean.startsWith("/dev/")) return true
        }
        return false
    }

    fun resolve(ctx: HarnessCtx, raw: String): File {
        val clean = raw.trim()
        if (clean.isEmpty()) throw HarnessError("No path was given")
        if (blocked(clean)) throw HarnessError("$clean is off limits", blocked = true)
        val file = if (clean.startsWith("~")) {
            File(HarnessRuntime.workspace(), clean.removePrefix("~").removePrefix("/"))
        } else {
            val candidate = File(clean)
            if (candidate.isAbsolute) candidate else File(HarnessRuntime.workspace(), clean)
        }
        return file.canonicalFile
    }

    fun writable(ctx: HarnessCtx, file: File): Boolean {
        val roots = mutableListOf(HarnessRuntime.workspace())
        ctx.config.writeRoots.forEach { roots.add(File(it)) }
        return roots.any { isInside(file, it) }
    }

    fun readable(ctx: HarnessCtx, file: File): Boolean {
        val roots = mutableListOf(HarnessRuntime.workspace())
        ctx.config.writeRoots.forEach { roots.add(File(it)) }
        ctx.config.readOnlyRoots.forEach { roots.add(File(it)) }
        return roots.any { isInside(file, it) }
    }

    fun forRead(ctx: HarnessCtx, raw: String): File {
        val file = resolve(ctx, raw)
        if (!file.exists()) throw HarnessError("${display(ctx, file)} does not exist")
        if (!readable(ctx, file) && ctx.config.approvalFor(HarnessPermission.SENSITIVE) == Approval.DENY) {
            throw HarnessError("${display(ctx, file)} is outside the workspace and reading outside is blocked", blocked = true)
        }
        return file
    }

    fun forWrite(ctx: HarnessCtx, raw: String): File {
        val file = resolve(ctx, raw)
        if (!writable(ctx, file)) {
            throw HarnessError("${display(ctx, file)} is outside the workspace, so it cannot be written", blocked = true)
        }
        return file
    }

    fun display(ctx: HarnessCtx, file: File): String {
        val root = HarnessRuntime.workspace()
        return if (isInside(file, root)) {
            val rel = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
            if (rel.isEmpty()) "." else rel
        } else file.path
    }

    fun readBytes(file: File, maxBytes: Long = 32L * 1024 * 1024): ByteArray {
        if (file.length() > maxBytes) throw HarnessError("${file.name} is larger than ${maxBytes / 1048576} MiB")
        return file.readBytes()
    }

    fun readText(file: File, maxBytes: Int = 1024 * 1024): String {
        if (file.isDirectory) throw HarnessError("${file.name} is a directory")
        if (file.length() > maxBytes) {
            val head = ByteArray(maxBytes)
            file.inputStream().use { it.read(head) }
            return String(head, StandardCharsets.UTF_8) + "\n… file truncated at ${maxBytes / 1024} KiB"
        }
        val bytes = file.readBytes()
        return if (looksBinary(bytes)) throw HarnessError("${file.name} looks like a binary file") else String(bytes, StandardCharsets.UTF_8)
    }

    fun looksBinary(bytes: ByteArray): Boolean {
        val probe = bytes.take(TEXT_PROBE_BYTES)
        if (probe.isEmpty()) return false
        var weird = 0
        probe.forEach { b ->
            val value = b.toInt() and 0xFF
            if (value == 0) return true
            if (value < 9 || (value in 14..31)) weird++
        }
        return weird > probe.size / 20
    }

    fun writeText(ctx: HarnessCtx, file: File, text: String) {
        if (ctx.config.snapshots && file.exists()) Snapshots.capture(ctx, file)
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    fun countLines(file: File): Int = try {
        file.readLines().size
    } catch (e: Exception) {
        0
    }

    fun humanSize(bytes: Long): String = when {
        bytes >= 1073741824 -> String.format("%.2f GiB", bytes / 1073741824.0)
        bytes >= 1048576 -> String.format("%.2f MiB", bytes / 1048576.0)
        bytes >= 1024 -> String.format("%.1f KiB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
