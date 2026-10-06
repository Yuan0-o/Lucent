package com.lucent.app.harness.plugins
import com.lucent.app.platform.filesDir

import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.harness.PluginFailure
import com.lucent.app.harness.Workspace
import com.lucent.app.i18n.S
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

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

    fun targetFile(plugin: PluginSpec, source: PluginSource): Path {
        val fromUrl = source.url.substringAfterLast('/').substringBefore('?')
        val name = if (fromUrl.contains('.')) fromUrl else "${plugin.id}.download"
        return (HarnessRuntime.downloadsDirPath().toPath() / name)
    }

    fun reusableStaged(plugin: PluginSpec, source: PluginSource): Path? {
        if (!plugin.installFor(true).contains("{file}") && !plugin.installFor(false).contains("{file}")) {
            return null
        }
        val target = targetFile(plugin, source)
        if (FileSystem.SYSTEM.metadataOrNull(target)?.isRegularFile != true) return null
        val size = (FileSystem.SYSTEM.metadata(target).size ?: 0L)
        if (source.bytes > 0 && size != source.bytes) return null
        if (source.bytes <= 0 && size < 1024) return null
        return target
    }

    suspend fun inspect(plugin: PluginSpec, source: PluginSource, android: Boolean): PreflightReport {
        val problems = mutableListOf<PreflightProblem>()
        val notes = mutableListOf<String>()
        val mirrorResults = mutableListOf<Triple<String, Long, Boolean>>()

        val capsBuiltin = HarnessRuntime.capabilities().contains(HarnessRuntime.CAP_BUILTIN_RUNTIME)
        val backend = selectBackend(capsBuiltin)
        val builtinActive = backend == RuntimeBackend.BUILTIN

        if (android && builtinActive && plugin.id == "ubuntu") {
            val state = HarnessRuntime.host?.builtinRuntimeState() ?: "unavailable"
            if (state != "ready") {
                problems.add(
                    PreflightProblem(
                        "builtin_not_setup",
                        "The built-in environment is not set up yet - open the toolbox setup guide and tap Set up environment.",
                        listOf("Open Settings -> Plugins and run the setup guide to prepare the built-in environment.")
                    )
                )
            }
            if (HarnessRuntime.config().pluginInstalled(plugin.id)) {
                notes.add(S.pluginAlreadyInstalled(plugin.name))
            }
            return PreflightReport(plugin, problems, notes, mirrorResults)
        }

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
        if (plugin.bytes > 0L) {
            val space = Long.MAX_VALUE
            val needed = plugin.bytes * 2L
            if (space in 1L..<needed) {
                problems.add(
                    PreflightProblem(
                        "disk_space",
                        S.pluginProblemDiskSpace(Workspace.humanSize(space), Workspace.humanSize(needed)),
                        listOf(S.pluginProblemDiskSpaceStep)
                    )
                )
            }
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
                if (!true) {
                    problems.add(
                        PreflightProblem(
                            "storage",
                            S.pluginProblemStorage,
                            listOf(S.pluginProblemStorageStep)
                        )
                    )
                }
            }
        }
        if (android && plugin.needsShell && backend != RuntimeBackend.BUILTIN && !sharedStorage(HarnessRuntime.workspacePath().toPath())) {
            problems.add(
                PreflightProblem(
                    "workspace_not_shared",
                    S.pluginProblemWorkspaceNotShared,
                    listOf(S.pluginProblemWorkspaceNotSharedStep)
                )
            )
        }
        val shellReady = if (android) {
            backend != RuntimeBackend.NONE || HarnessRuntime.pluginHost?.isReady() == true
        } else {
            HarnessRuntime.pluginHost?.isReady() == true
        }

        if (plugin.needsShell && !shellReady) {
            val steps = if (android) {
                listOf(
                    S.pluginRepairSetupGuide,
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

    fun sharedStorage(dir: Path): Boolean {
        val path = okio.FileSystem.SYSTEM.canonicalize(dir.toString().toPath()).toString()
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
            "storage" in codes || "disk_space" in codes -> PluginFailure.STORAGE
            "no_shell" in codes || "workspace_not_shared" in codes || "builtin_not_setup" in codes ->
                PluginFailure.NO_SHELL
            else -> PluginFailure.NONE
        }
    }
}

data class PluginJournalEntry(val stage: String, val message: String, val ok: Boolean)

object PluginJournal {

    private fun dir(): Path? {
        val files = HarnessRuntime.host?.filesDirPath() ?: return null
        return (files.toPath() / "harness/plugin-journal").apply { FileSystem.SYSTEM.createDirectories(this) }
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
            FileSystem.SYSTEM.write(dir / "$safe.json") { writeUtf8(line) }
        } catch (_: Throwable) {
        }
    }

    fun read(pluginId: String): PluginJournalEntry? {
        val dir = dir() ?: return null
        return try {
            val safe = pluginId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = dir / "$safe.json"
            if (FileSystem.SYSTEM.metadataOrNull(file)?.isRegularFile != true) return null
            val o = org.json.JSONObject(FileSystem.SYSTEM.read(file) { readUtf8() })
            PluginJournalEntry(o.optString("stage", ""), o.optString("message", ""), o.optBoolean("ok", false))
        } catch (_: Throwable) {
            null
        }
    }

    fun clear(pluginId: String) {
        val dir = dir() ?: return
        try {
            val safe = pluginId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            FileSystem.SYSTEM.delete(dir / "$safe.json")
        } catch (_: Throwable) {
        }
    }
}
