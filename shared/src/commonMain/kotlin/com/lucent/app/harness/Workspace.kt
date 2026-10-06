package com.lucent.app.harness

class HarnessError(message: String, val blocked: Boolean = false) : Exception(message)

object Workspace {

    private val fs: HarnessFs = systemHarnessFs()

    private const val TEXT_PROBE_BYTES = 4096

    private val BLOCKED_PREFIXES = listOf(
        "/proc", "/sys", "/dev", "/system", "/vendor", "/product", "/apex", "/data/data", "/data/system"
    )

    fun normalize(path: String): String = path.trim().replace('\\', '/').removeSuffix("/")

    fun isInside(path: String, root: String): Boolean = try {
        val target = fs.canonicalize(path)
        val base = fs.canonicalize(root)
        if (target == base) return true
        val prefix = if (base.endsWith(fs.separator)) base else base + fs.separator
        target.startsWith(prefix)
    } catch (e: Exception) {
        false
    }

    fun blocked(path: String): Boolean {
        val clean = normalize(path)
        if (clean.isEmpty()) return false
        if (harnessIsAndroid) {
            if (BLOCKED_PREFIXES.any { clean == it || clean.startsWith("$it/") }) return true
        } else {
            if (clean.startsWith("/proc") || clean.startsWith("/sys") || clean.startsWith("/dev/")) return true
        }
        return false
    }

    fun resolve(raw: String): String {
        val clean = raw.trim()
        if (clean.isEmpty()) throw HarnessError("No path was given")
        if (blocked(clean)) throw HarnessError("$clean is off limits", blocked = true)
        val resolved = if (clean.startsWith("~")) {
            fs.join(HarnessRuntime.workspacePath(), clean.removePrefix("~").removePrefix("/"))
        } else {
            if (fs.isAbsolute(clean)) clean else fs.join(HarnessRuntime.workspacePath(), clean)
        }
        return fs.canonicalize(resolved)
    }

    fun resolve(ctx: HarnessCtx, raw: String): String = resolve(raw)

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

    fun forRead(ctx: HarnessCtx, raw: String): String {
        val path = resolve(ctx, raw)
        if (!fs.exists(path)) throw HarnessError("${display(path)} does not exist")
        if (!readable(ctx, path) && ctx.config.approvalFor(HarnessPermission.SENSITIVE) == Approval.DENY) {
            throw HarnessError("${display(path)} is outside the workspace and reading outside is blocked", blocked = true)
        }
        return path
    }

    fun forWrite(ctx: HarnessCtx, raw: String): String {
        val path = resolve(ctx, raw)
        if (!writable(ctx, path)) {
            throw HarnessError("${display(path)} is outside the workspace, so it cannot be written", blocked = true)
        }
        return path
    }

    fun display(path: String): String {
        val root = HarnessRuntime.workspacePath()
        return if (isInside(path, root)) {
            val rel = fs.relativize(root, path).replace('\\', '/')
            if (rel.isEmpty()) "." else rel
        } else path
    }

    fun display(ctx: HarnessCtx, path: String): String = display(path)

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

    fun writeText(ctx: HarnessCtx, path: String, text: String) {
        if (ctx.config.snapshots && fs.exists(path)) Snapshots.capture(ctx, path)
        val parent = fs.parentOf(path)
        if (parent != null) fs.mkdirs(parent)
        fs.writeText(path, text)
    }

    fun countLines(path: String): Int = try {
        fs.readLines(path).size
    } catch (e: Exception) {
        0
    }

    fun humanSize(bytes: Long): String = when {
        bytes >= 1073741824 -> formatDecimal(bytes / 1073741824.0, 2) + " GiB"
        bytes >= 1048576 -> formatDecimal(bytes / 1048576.0, 2) + " MiB"
        bytes >= 1024 -> formatDecimal(bytes / 1024.0, 1) + " KiB"
        else -> "$bytes B"
    }

    private fun formatDecimal(value: Double, decimals: Int): String {
        val factor = when (decimals) { 1 -> 10.0; else -> 100.0 }
        val rounded = kotlin.math.round(value * factor).toLong()
        val intPart = rounded / factor.toLong()
        val fracPart = kotlin.math.abs(rounded % factor.toLong()).toString().padStart(decimals, '0')
        return "$intPart.$fracPart"
    }

    fun resolveFile(raw: String): okio.Path = resolve(raw).toPath()
    fun forReadFile(ctx: HarnessCtx, raw: String): okio.Path = forRead(ctx, raw).toPath()
    fun forWriteFile(ctx: HarnessCtx, raw: String): okio.Path = forWrite(ctx, raw).toPath()
    fun isInside(file: okio.Path, root: okio.Path): Boolean = isInside(file.toString(), root.toString())
    fun writable(config: HarnessConfig, file: okio.Path): Boolean = writable(config, file.toString())
    fun writable(ctx: HarnessCtx, file: okio.Path): Boolean = writable(ctx.config, file.toString())
    fun readable(config: HarnessConfig, file: okio.Path): Boolean = readable(config, file.toString())
    fun readable(ctx: HarnessCtx, file: okio.Path): Boolean = readable(ctx.config, file.toString())
    fun display(file: okio.Path): String = display(file.toString())
    fun display(ctx: HarnessCtx, file: okio.Path): String = display(file.toString())
    fun readBytes(file: okio.Path, maxBytes: Long = 32L * 1024 * 1024): ByteArray = readBytes(file.toString(), maxBytes)
    fun readText(file: okio.Path, maxBytes: Int = 1024 * 1024): String = readText(file.toString(), maxBytes)
    fun writeText(ctx: HarnessCtx, file: okio.Path, text: String) = writeText(ctx, file.toString(), text)
    fun countLines(file: okio.Path): Int = countLines(file.toString())
}