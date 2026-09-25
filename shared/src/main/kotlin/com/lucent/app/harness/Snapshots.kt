package com.lucent.app.harness

import org.json.JSONObject
import java.io.File

data class SnapshotEntry(
    val id: String,
    val at: Long,
    val path: String,
    val store: String,
    val bytes: Long
)

object Snapshots {

    private const val INDEX = "index.jsonl"

    private fun dir(): File = HarnessRuntime.subDir("snapshots")

    private fun index(): File = File(dir(), INDEX)

    fun capture(file: File) {
        if (!file.exists() || file.isDirectory) return
        try {
            val id = "${System.currentTimeMillis()}-${(1000..9999).random()}"
            val store = File(dir(), id)
            store.mkdirs()
            val copy = File(store, file.name)
            file.copyTo(copy, overwrite = true)
            val line = JSONObject().apply {
                put("id", id)
                put("at", System.currentTimeMillis())
                put("path", file.canonicalPath)
                put("store", copy.canonicalPath)
                put("bytes", copy.length())
            }.toString()
            index().appendText(line + "\n")
            prune()
        } catch (_: Throwable) {
        }
    }

    fun history(path: String, limit: Int = 20): List<SnapshotEntry> =
        all().filter { it.path == path }.takeLast(limit.coerceIn(1, 200)).reversed()

    fun all(): List<SnapshotEntry> {
        val target = index()
        if (!target.exists()) return emptyList()
        return try {
            target.readLines().mapNotNull { line ->
                if (line.isBlank()) null else {
                    val o = try { JSONObject(line) } catch (e: Exception) { return@mapNotNull null }
                    SnapshotEntry(
                        id = o.optString("id", ""),
                        at = o.optLong("at", 0L),
                        path = o.optString("path", ""),
                        store = o.optString("store", ""),
                        bytes = o.optLong("bytes", 0L)
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun restore(id: String): File {
        val entry = all().firstOrNull { it.id == id } ?: throw HarnessError("No snapshot with id $id")
        val copy = File(entry.store)
        if (!copy.exists()) throw HarnessError("That snapshot is no longer on disk")
        val target = File(entry.path)
        if (!Workspace.writable(HarnessRuntime.config(), target)) throw HarnessError("${entry.path} is outside the workspace", blocked = true)
        target.parentFile?.mkdirs()
        copy.copyTo(target, overwrite = true)
        return target
    }

    fun latest(path: String): SnapshotEntry? = history(path, 1).firstOrNull()

    fun totalBytes(): Long = all().sumOf { it.bytes }

    fun clear() {
        try {
            dir().deleteRecursively()
            dir().mkdirs()
        } catch (_: Throwable) {
        }
    }

    private fun prune() {
        val limit = HarnessRuntime.config().snapshotLimit.coerceIn(10, 1000)
        val entries = all()
        if (entries.size <= limit) return
        val doomed = entries.take(entries.size - limit)
        doomed.forEach { entry ->
            try { File(entry.store).delete() } catch (_: Throwable) {
            }
        }
        val keep = entries.drop(entries.size - limit).map { it.id }.toSet()
        val kept = try {
            index().readLines().filter { line ->
                val id = try { JSONObject(line).optString("id", "") } catch (e: Exception) { "" }
                keep.contains(id)
            }
        } catch (e: Exception) {
            emptyList()
        }
        try { index().writeText(kept.joinToString("\n") + "\n") } catch (_: Throwable) {
        }
    }

    fun capture(ctx: HarnessCtx, file: File) = capture(file)

    fun history(ctx: HarnessCtx, path: String, limit: Int = 20): List<SnapshotEntry> = history(path, limit)

    fun all(ctx: HarnessCtx): List<SnapshotEntry> = all()

    fun restore(ctx: HarnessCtx, id: String): File = restore(id)

    fun latest(ctx: HarnessCtx, path: String): SnapshotEntry? = latest(path)

    fun totalBytes(ctx: HarnessCtx): Long = totalBytes()

    fun clear(ctx: HarnessCtx) = clear()

}

data class DiffLine(val kind: Char, val text: String)

object Diffs {

    fun unified(old: String, new: String, context: Int = 3): String {
        val a = old.split("\n")
        val b = new.split("\n")
        val rows = lcs(a, b)
        val out = StringBuilder()
        var index = 0
        var lastShown = -2
        rows.forEachIndexed { position, row ->
            if (row.kind == ' ') return@forEachIndexed
            val start = (position - context).coerceAtLeast(0)
            val end = (position + context).coerceAtMost(rows.size - 1)
            if (start > lastShown + 1) out.append("@@ line ${start + 1} @@\n")
            for (i in start..end) {
                if (i <= lastShown) continue
                val line = rows[i]
                out.append(line.kind).append(line.text).append('\n')
                lastShown = i
            }
            index++
        }
        return if (index == 0) "" else out.toString().trimEnd()
    }

    fun lcs(a: List<String>, b: List<String>): List<DiffLine> {
        val n = a.size
        val m = b.size
        if (n * m > 4_000_000) return fallback(a, b)
        val table = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                table[i][j] = if (a[i] == b[j]) table[i + 1][j + 1] + 1 else maxOf(table[i + 1][j], table[i][j + 1])
            }
        }
        val out = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> {
                    out.add(DiffLine(' ', a[i]))
                    i++
                    j++
                }
                table[i + 1][j] >= table[i][j + 1] -> {
                    out.add(DiffLine('-', a[i]))
                    i++
                }
                else -> {
                    out.add(DiffLine('+', b[j]))
                    j++
                }
            }
        }
        while (i < n) {
            out.add(DiffLine('-', a[i]))
            i++
        }
        while (j < m) {
            out.add(DiffLine('+', b[j]))
            j++
        }
        return out
    }

    private fun fallback(a: List<String>, b: List<String>): List<DiffLine> =
        a.map { DiffLine('-', it) } + b.map { DiffLine('+', it) }
}
