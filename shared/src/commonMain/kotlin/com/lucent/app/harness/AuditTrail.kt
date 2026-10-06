package com.lucent.app.harness
import com.lucent.app.platform.filesDir

import com.lucent.app.platform.PlatformContext
import com.lucent.app.AppScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

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
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    fun file(context: PlatformContext): File {
        val dir = File(baseDir(context), "harness")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }

    private fun baseDir(context: PlatformContext): File =
        runCatching { File(HarnessRuntime.filesDirPath()) }.getOrNull()
            ?: File(System.getProperty("java.io.tmpdir"), "lucent-audit")

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
                    target.appendText(line + "\n")
                    if (target.length() > MAX_BYTES) {
                        val kept = target.readText().takeLast(KEEP_BYTES)
                        target.writeText(kept.substringAfter("\n", kept))
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun entries(context: PlatformContext, limit: Int = 200): List<AuditEntry> {
        val target = file(context)
        if (!target.exists()) return emptyList()
        val lines = synchronized(lock) {
            try { target.readLines() } catch (e: Exception) { emptyList() }
        }
        return lines.asReversed().take(limit).mapNotNull { parse(it) }
    }

    fun clear(context: PlatformContext) {
        synchronized(lock) {
            try { file(context).writeText("") } catch (_: Throwable) {
            }
        }
    }

    fun format(entry: AuditEntry): String {
        val time = synchronized(lock) { stamp.format(Date(entry.at)) }
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
