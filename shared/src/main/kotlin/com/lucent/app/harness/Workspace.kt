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
        if (target == base) return true
        val prefix = if (base.endsWith(File.separator)) base else base + File.separator
        target.startsWith(prefix)
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

    fun resolve(raw: String): File {
        val clean = raw.trim()
        if (clean.isEmpty()) throw HarnessError("No path was given")
        if (blocked(clean)) throw HarnessError("$clean is off limits", blocked = true)
        val file = if (clean.startsWith("~")) {
            File(HarnessRuntime.workspacePath(), clean.removePrefix("~").removePrefix("/"))
        } else {
            val candidate = File(clean)
            if (candidate.isAbsolute) candidate else File(HarnessRuntime.workspacePath(), clean)
        }
        return file.canonicalFile
    }

    fun resolve(ctx: HarnessCtx, raw: String): File = resolve(raw)

    fun writable(config: HarnessConfig, file: File): Boolean {
        val roots = mutableListOf(HarnessRuntime.workspace())
        config.writeRoots.forEach { roots.add(File(it)) }
        return roots.any { isInside(file, it) }
    }

    fun writable(ctx: HarnessCtx, file: File): Boolean = writable(ctx.config, file)

    fun readable(config: HarnessConfig, file: File): Boolean {
        val roots = mutableListOf(HarnessRuntime.workspace())
        config.writeRoots.forEach { roots.add(File(it)) }
        config.readOnlyRoots.forEach { roots.add(File(it)) }
        return roots.any { isInside(file, it) }
    }

    fun readable(ctx: HarnessCtx, file: File): Boolean = readable(ctx.config, file)

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

    fun display(file: File): String {
        val root = HarnessRuntime.workspace()
        return if (isInside(file, root)) {
            val rel = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
            if (rel.isEmpty()) "." else rel
        } else file.path
    }

    fun display(ctx: HarnessCtx, file: File): String = display(file)

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

    private val fs: HarnessFs
        get() = systemHarnessFs()

    fun isInside(path: String, root: String): Boolean = try {
        val target = fs.canonicalize(path)
        val base = fs.canonicalize(root)
        if (target == base) return true
        val prefix = if (base.endsWith(fs.separator)) base else base + fs.separator
        target.startsWith(prefix)
    } catch (e: Exception) {
        false
    }

    fun resolvePath(raw: String): String {
        val clean = raw.trim()
        if (clean.isEmpty()) throw HarnessError("No path was given")
        if (blocked(clean)) throw HarnessError("$clean is off limits", blocked = true)
        val path = if (clean.startsWith("~")) {
            fs.join(HarnessRuntime.workspacePath(), clean.removePrefix("~").removePrefix("/"))
        } else {
            if (fs.isAbsolute(clean)) clean else fs.join(HarnessRuntime.workspacePath(), clean)
        }
        return fs.canonicalize(path)
    }

    fun resolvePath(ctx: HarnessCtx, raw: String): String = resolvePath(raw)

    fun writable(config: HarnessConfig, path: String): Boolean {
        val roots = mutableListOf(HarnessRuntime.workspacePath())
        config.writeRoots.forEach { roots.add(it) }
        return roots.any { isInside(path, it) }
    }

    fun writable(ctx: HarnessCtx, path: String): Boolean = writable(ctx.config, path)

    fun readable(config: HarnessConfig, path: String): Boolean {
        val roots = mutableListOf(HarnessRuntime.workspacePath())
        config.writeRoots.forEach { roots.add(it) }
        config.readOnlyRoots.forEach { roots.add(it) }
        return roots.any { isInside(path, it) }
    }

    fun readable(ctx: HarnessCtx, path: String): Boolean = readable(ctx.config, path)

    fun forReadPath(ctx: HarnessCtx, raw: String): String {
        val path = resolvePath(ctx, raw)
        if (!fs.exists(path)) throw HarnessError("${displayPath(ctx, path)} does not exist")
        if (!readable(ctx, path) && ctx.config.approvalFor(HarnessPermission.SENSITIVE) == Approval.DENY) {
            throw HarnessError("${displayPath(ctx, path)} is outside the workspace and reading outside is blocked", blocked = true)
        }
        return path
    }

    fun forWritePath(ctx: HarnessCtx, raw: String): String {
        val path = resolvePath(ctx, raw)
        if (!writable(ctx, path)) {
            throw HarnessError("${displayPath(ctx, path)} is outside the workspace, so it cannot be written", blocked = true)
        }
        return path
    }

    fun displayPath(path: String): String {
        val root = HarnessRuntime.workspacePath()
        return if (isInside(path, root)) {
            val rel = fs.relativize(root, path)
            if (rel.isEmpty()) "." else rel
        } else path
    }

    fun displayPath(ctx: HarnessCtx, path: String): String = displayPath(path)

    fun readBytes(path: String, maxBytes: Long = 32L * 1024 * 1024): ByteArray {
        if (fs.length(path) > maxBytes) throw HarnessError("${fs.nameOf(path)} is larger than ${maxBytes / 1048576} MiB")
        return fs.readBytes(path)
    }

    fun readText(path: String, maxBytes: Int = 1024 * 1024): String {
        if (fs.isDirectory(path)) throw HarnessError("${fs.nameOf(path)} is a directory")
        if (fs.length(path) > maxBytes) {
            val head = fs.readHead(path, maxBytes)
            return head.decodeToString() + "\n… file truncated at ${maxBytes / 1024} KiB"
        }
        val bytes = fs.readBytes(path)
        return if (looksBinary(bytes)) throw HarnessError("${fs.nameOf(path)} looks like a binary file") else bytes.decodeToString()
    }

    fun writeText(ctx: HarnessCtx, path: String, text: String) {
        if (ctx.config.snapshots && fs.exists(path)) Snapshots.capture(ctx, File(path))
        fs.parentOf(path)?.let { fs.mkdirs(it) }
        fs.writeText(path, text)
    }

    fun countLines(path: String): Int = try {
        fs.readLines(path).size
    } catch (e: Exception) {
        0
    }
}
