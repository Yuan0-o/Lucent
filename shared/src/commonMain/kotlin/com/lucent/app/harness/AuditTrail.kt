package com.lucent.app.harness
import com.lucent.app.platform.filesDir

import com.lucent.app.platform.PlatformContext
import com.lucent.app.AppScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class AuditEntry(
    val at: Long,
    val tool: String,
    val group: String,
    val permission: String,
    val approval: String,
    val arguments: String,
    val outcome: String,
    val detail: String,
    val millis: Long,
    val files: List<String>
)

object AuditTrail {

    private const val FILE_NAME = "harness_audit.jsonl"
    private const val MAX_BYTES = 512 * 1024
    private const val KEEP_BYTES = 256 * 1024

    private val lock = Any()

    fun file(context: PlatformContext): Path {
        val dir = baseDir(context) / "harness"
        if (!FileSystem.SYSTEM.exists(dir)) FileSystem.SYSTEM.createDirectories(dir)
        return dir / FILE_NAME
    }

    private fun baseDir(context: PlatformContext): Path =
        runCatching { HarnessRuntime.filesDirPath().toPath() }.getOrNull()
            ?: System.getProperty("java.io.tmpdir").toPath() / "lucent-audit"

    fun record(context: PlatformContext?, entry: AuditEntry) {
        val app = context ?: return
        val line = buildJsonObject {
            put("at", entry.at)
            put("tool", entry.tool)
            put("group", entry.group)
            put("permission", entry.permission)
            put("approval", entry.approval)
            put("arguments", redact(entry.arguments))
            put("outcome", entry.outcome)
            put("detail", redact(entry.detail))
            put("millis", entry.millis)
            put("files", entry.files.joinToString(", "))
        }.toString()
        AppScope.io.launch {
            synchronized(lock) {
                try {
                    val target = file(app)
                    FileSystem.SYSTEM.write(target, mustExist = false) { }; FileSystem.SYSTEM.appendingSink(target).buffer().use { it.writeUtf8(line + "\n") }
                    if ((FileSystem.SYSTEM.metadata(target).size ?: -1) > MAX_BYTES) {
                        val kept = FileSystem.SYSTEM.read(target) { readUtf8() }.takeLast(KEEP_BYTES)
                        FileSystem.SYSTEM.write(target) { writeUtf8(kept.substringAfter("\n", kept)) }
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun entries(context: PlatformContext, limit: Int = 200): List<AuditEntry> {
        val target = file(context)
        if (!FileSystem.SYSTEM.exists(target)) return emptyList()
        val lines = synchronized(lock) {
            try { FileSystem.SYSTEM.read(target) { readUtf8() }.lines() } catch (e: Exception) { emptyList() }
        }
        return lines.asReversed().take(limit).mapNotNull { parse(it) }
    }

    fun clear(context: PlatformContext) {
        synchronized(lock) {
            try { FileSystem.SYSTEM.write(file(context)) { writeUtf8("") } } catch (_: Throwable) {
            }
        }
    }

    fun format(entry: AuditEntry): String {
        val dt = Instant.fromEpochMilliseconds(entry.at).toLocalDateTime(TimeZone.UTC)
        val time = "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}:${dt.second.toString().padStart(2, '0')}"
        return "$time  ${entry.tool}  ${entry.outcome}  ${entry.millis}ms"
    }

    private val secretValue = Regex("(?i)(token|secret|password|passwd|api[_-]?key|auth)\\s*[:=]\\s*[^\\s,}]+")
    private val secretUrl = Regex("://[^\\s/:@]+:[^\\s/@]+@")

    fun redact(text: String): String {
        var out = secretValue.replace(text, "$1=***")
        out = secretUrl.replace(out, "://***@")
        return out
    }

    private fun parse(line: String): AuditEntry? {
        if (line.isBlank()) return null
        val o = try { Json.parseToJsonElement(line).jsonObject } catch (e: Exception) { return null }
        return AuditEntry(
            at = o["at"]?.jsonPrimitive?.longOrNull ?: 0L,
            tool = o["tool"]?.jsonPrimitive?.content ?: "",
            group = o["group"]?.jsonPrimitive?.content ?: "",
            permission = o["permission"]?.jsonPrimitive?.content ?: "",
            approval = o["approval"]?.jsonPrimitive?.content ?: "",
            arguments = o["arguments"]?.jsonPrimitive?.content ?: "",
            outcome = o["outcome"]?.jsonPrimitive?.content ?: "",
            detail = o["detail"]?.jsonPrimitive?.content ?: "",
            millis = o["millis"]?.jsonPrimitive?.longOrNull ?: 0L,
            files = (o["files"]?.jsonPrimitive?.content ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        )
    }
}
