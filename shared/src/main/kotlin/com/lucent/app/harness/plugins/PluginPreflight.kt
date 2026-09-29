package com.lucent.app.harness.plugins

import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.harness.PluginFailure
import com.lucent.app.harness.Workspace
import com.lucent.app.i18n.S
import java.io.File

data class PreflightProblem(
    val code: String,
    val title: String,
    val steps: List<String>,
    val blocking: Boolean = true
)

data class PreflightReport(
    val plugin: PluginSpec,
    val problems: List<PreflightProblem>,
    val notes: List<String>,
    val mirrorResults: List<Triple<String, Long, Boolean>>
) {
    val blocked: Boolean get() = problems.any { it.blocking }
}

object PluginPreflight {

    fun targetFile(plugin: PluginSpec, source: PluginSource): File {
        val fromUrl = source.url.substringAfterLast('/').substringBefore('?')
        val name = if (fromUrl.contains('.')) fromUrl else "${plugin.id}.download"
        return File(HarnessRuntime.downloadsDir(), name)
    }

    fun reusableStaged(plugin: PluginSpec, source: PluginSource): File? {
        if (!plugin.installFor(true).contains("{file}") && !plugin.installFor(false).contains("{file}")) {
            return null
        }
        val target = targetFile(plugin, source)
        if (!target.isFile) return null
        val size = target.length()
        if (source.bytes > 0 && size != source.bytes) return null
        if (source.bytes <= 0 && size < 1024) return null
        return target
    }

    suspend fun inspect(plugin: PluginSpec, source: PluginSource, android: Boolean): PreflightReport {
        val problems = mutableListOf<PreflightProblem>()
        val notes = mutableListOf<String>()
        val mirrorResults = mutableListOf<Triple<String, Long, Boolean>>()
        val script = plugin.installFor(android)
        if (script.isBlank()) {
            problems.add(
                PreflightProblem(
                    "no_script",
                    S.pluginProblemNoScript,
                    listOf(S.pluginProblemNoScriptStep)
                )
            )
            return PreflightReport(plugin, problems, notes, mirrorResults)
        }
        val needsDownload = script.contains("{file}")
        if (needsDownload) {
            val usable = plugin.sources.filter { it.url.startsWith("http") }
            if (usable.isEmpty()) {
                problems.add(
                    PreflightProblem(
                        "no_source",
                        S.pluginProblemNoSource,
                        listOf(S.pluginProblemNoSourceStep)
                    )
                )
            } else {
                val results = PluginDownload.speedTable(usable)
                mirrorResults.addAll(results)
                if (results.none { it.second != 0L }) {
                    problems.add(
                        PreflightProblem(
                            "source_unreachable",
                            S.pluginProblemUnreachable,
                            listOf(S.pluginProblemUnreachableStep, S.pluginProblemUnreachableMirrors)
                        )
                    )
                } else {
                    val chosen = if (source.id.isNotBlank()) {
                        usable.firstOrNull { it.id == source.id }
                    } else {
                        null
                    }
                    val reused = chosen?.let { reusableStaged(plugin, it) }
                    if (reused != null) {
                        notes.add(S.pluginReuseDownload(reused.name))
                    }
                    val journal = PluginJournal.read(plugin.id)
                    if (journal != null && !journal.ok) {
                        notes.add(S.pluginJournalNote(journal.stage, journal.message))
                    }
                }
                val dir = HarnessRuntime.downloadsDir()
                if (!dir.canWrite()) {
                    problems.add(
                        PreflightProblem(
                            "storage",
                            S.pluginProblemStorage,
                            listOf(S.pluginProblemStorageStep)
                        )
                    )
                } else if (android && !sharedStorage(dir)) {
                    problems.add(
                        PreflightProblem(
                            "staged_unreadable",
                            S.pluginProblemUnreadable,
                            listOf(S.pluginProblemUnreadableStep)
                        )
                    )
                }
            }
        }
        if (plugin.needsShell && HarnessRuntime.pluginHost?.isReady() != true) {
            val steps = if (android) {
                listOf(
                    S.pluginRepairTermux,
                    S.pluginRepairPermission,
                    S.pluginRepairStorage
                )
            } else {
                listOf(S.pluginProblemShellDesktop)
            }
            problems.add(PreflightProblem("no_shell", S.pluginReasonNoShell, steps))
        }
        if (HarnessRuntime.config().pluginInstalled(plugin.id)) {
            notes.add(S.pluginAlreadyInstalled(plugin.name))
        }
        return PreflightReport(plugin, problems, notes, mirrorResults)
    }

    private fun sharedStorage(dir: File): Boolean {
        val path = dir.canonicalPath
        return path.startsWith("/storage/") || path.startsWith("/sdcard") ||
            path.contains("/storage/emulated/")
    }

    fun render(report: PreflightReport): String = buildString {
        append(report.plugin.name).append(": ")
        if (!report.blocked) {
            append(S.pluginPreflightOk)
        } else {
            append(S.pluginPreflightBlocked)
        }
        report.problems.forEach { problem ->
            append("\n- [").append(problem.code).append("] ").append(problem.title)
            problem.steps.forEach { step -> append("\n    ").append(step) }
        }
        report.notes.forEach { note -> append("\n").append(note) }
        if (report.mirrorResults.isNotEmpty()) {
            append("\n").append(S.pluginMirrorResults)
            report.mirrorResults.forEach { (label, rate, official) ->
                append("\n  ").append(if (official) S.pluginMirrorOfficial else S.pluginMirrorAlt)
                    .append(' ').append(label).append(": ")
                    .append(if (rate > 0) "${Workspace.humanSize(rate)}/s" else S.pluginMirrorSilent)
            }
        }
    }

    fun failureOf(report: PreflightReport): PluginFailure {
        val codes = report.problems.map { it.code }.toSet()
        return when {
            "no_script" in codes -> PluginFailure.NO_SCRIPT
            "no_source" in codes || "source_unreachable" in codes -> PluginFailure.DOWNLOAD
            "storage" in codes -> PluginFailure.STORAGE
            "no_shell" in codes || "staged_unreadable" in codes -> PluginFailure.NO_SHELL
            else -> PluginFailure.NONE
        }
    }
}

data class PluginJournalEntry(val stage: String, val message: String, val ok: Boolean)

object PluginJournal {

    private fun dir(): File? {
        val files = HarnessRuntime.host?.filesDir() ?: return null
        return File(files, "harness/plugin-journal").apply { mkdirs() }
    }

    fun write(pluginId: String, stage: String, message: String, ok: Boolean) {
        val dir = dir() ?: return
        try {
            val safe = pluginId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val line = org.json.JSONObject().apply {
                put("stage", stage)
                put("message", message.take(400))
                put("ok", ok)
                put("at", System.currentTimeMillis())
            }.toString()
            File(dir, "$safe.json").writeText(line)
        } catch (_: Throwable) {
        }
    }

    fun read(pluginId: String): PluginJournalEntry? {
        val dir = dir() ?: return null
        return try {
            val safe = pluginId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(dir, "$safe.json")
            if (!file.isFile) return null
            val o = org.json.JSONObject(file.readText())
            PluginJournalEntry(o.optString("stage", ""), o.optString("message", ""), o.optBoolean("ok", false))
        } catch (_: Throwable) {
            null
        }
    }

    fun clear(pluginId: String) {
        val dir = dir() ?: return
        try {
            val safe = pluginId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            File(dir, "$safe.json").delete()
        } catch (_: Throwable) {
        }
    }
}
