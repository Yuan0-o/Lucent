package com.lucent.app.harness

import android.content.Context
import com.lucent.app.AppScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun file(context: Context): File {
        val dir = File(baseDir(context), "harness")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }

    private fun baseDir(context: Context): File =
        context.applicationContext?.filesDir ?: File(System.getProperty("java.io.tmpdir"), "lucent-audit")

    fun record(context: Context?, entry: AuditEntry) {
        val app = context ?: return
        val line = JSONObject().apply {
            put("at", entry.at)
            put("tool", entry.tool)
            put("group", entry.group)
            put("permission", entry.permission)
            put("approval", entry.approval)
            put("arguments", entry.arguments)
            put("outcome", entry.outcome)
            put("detail", entry.detail)
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

    fun entries(context: Context, limit: Int = 200): List<AuditEntry> {
        val target = file(context.applicationContext)
        if (!target.exists()) return emptyList()
        val lines = synchronized(lock) {
            try { target.readLines() } catch (e: Exception) { emptyList() }
        }
        return lines.asReversed().take(limit).mapNotNull { parse(it) }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            try { file(context.applicationContext).writeText("") } catch (_: Throwable) {
            }
        }
    }

    fun format(entry: AuditEntry): String {
        val time = synchronized(lock) { stamp.format(Date(entry.at)) }
        return "$time  ${entry.tool}  ${entry.outcome}  ${entry.millis}ms"
    }

    private fun parse(line: String): AuditEntry? {
        if (line.isBlank()) return null
        val o = try { JSONObject(line) } catch (e: Exception) { return null }
        return AuditEntry(
            at = o.optLong("at", 0L),
            tool = o.optString("tool", ""),
            group = o.optString("group", ""),
            permission = o.optString("permission", ""),
            approval = o.optString("approval", ""),
            arguments = o.optString("arguments", ""),
            outcome = o.optString("outcome", ""),
            detail = o.optString("detail", ""),
            millis = o.optLong("millis", 0L),
            files = o.optString("files", "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        )
    }
}
