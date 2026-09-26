package com.lucent.app.harness

import android.content.Context
import com.lucent.app.AppScope
import org.json.JSONObject
import java.io.File

object HarnessPrompt {

    private const val MEMORY_KEYS = 8
    private const val MEMORY_VALUE = 60
    private const val MEMORY_CHARS = 520
    private const val SKILLS_SHOWN = 10
    private const val SKILL_DESCRIPTION = 70
    private const val SKILLS_CHARS = 460

    fun block(): String {
        val config = HarnessRuntime.config()
        if (!config.enabled) return ""
        val workspace = HarnessRuntime.workspace()
        val capabilities = HarnessRuntime.capabilities()
        val tools = HarnessGate.enabledTools(HarnessRuntime.android, capabilities)
        if (tools.isEmpty()) return ""
        return buildString {
            append("\n\nYou also have a working toolkit on this device, not just the notes and tasks tools. ")
            append("Your workspace is ").append(workspace.path).append(". ")
            append("Everything you make — documents, spreadsheets, slides, code, reports, converted files — belongs ")
            append("inside that workspace; the person can open it from there, and you can attach a finished file to a ")
            append("note or a task with the note and task tools. ")

            val groups = HarnessGroup.entries
                .filter { config.groupEnabled(it) && tools.any { tool -> tool.group == it } }
                .joinToString(", ") { it.key }
            append("Tool groups switched on right now: ").append(groups).append(". ")

            append(shellLine(config, capabilities))

            if (HarnessRuntime.android && capabilities.contains(HarnessRuntime.CAP_PRIVILEGED)) {
                append("Shizuku is granted to you, so shell commands can run with its privileges on this device; ")
                append("use that reach only for the job in front of you. ")
            }

            val plugins = config.installedPlugins()
            if (plugins.isEmpty()) {
                append("No optional plugins are installed yet. When a job genuinely needs one — a Linux userland, ")
                append("Python with its document libraries, LibreOffice for conversions and page rendering, Node.js, ")
                append("a browser engine, OCR, media tools — call plugin_status, tell the person what it would ")
                append("download and how big it is, and only install it once they agree. ")
            } else {
                append("Installed plugins: ").append(plugins.joinToString(", ")).append(". ")
                append("Use plugin_run to work inside them, and call plugin_status when you need the exact names. ")
            }

            val mcp = config.mcpServers.count { it.enabled }
            if (mcp > 0) {
                append("There ").append(if (mcp == 1) "is 1 external MCP server" else "are $mcp external MCP servers")
                append(" connected; call mcp_tools to see what they offer before saying you cannot do something. ")
            }

            if (config.deviceEnabled && HarnessRuntime.android) {
                append("Device control is on: you can read the screen, tap, type, swipe, take screenshots, list and ")
                append("open apps, read notifications and use the clipboard. Read the screen before tapping, and read ")
                append("it again after every tap rather than tapping from memory. ")
            }

            append("HOW TO WORK. Prefer doing over describing: if a tool can do it, do it and report what happened. ")
            append("For anything with more than a couple of steps, publish the plan with update_plan and keep it ")
            append("updated as you go. Read a file before editing it, and use edit_file for surgical changes rather ")
            append("than rewriting a whole file. Run things you create: a script, a test, a conversion, a render. ")
            append("If you make a document or a deck, render it and look at the picture before calling it finished. ")
            append("When a command fails, read the error and fix it rather than repeating the same call. ")
            append("Long jobs belong in start_job so you can carry on while they run. Deleting, installing and ")
            append("anything that reaches outside the workspace asks the person first, so explain briefly what you ")
            append("are about to do. Never claim a file exists, a command ran, or a change was made unless a tool ")
            append("told you so. ")

            append(memoryAndSkills())
            append(goalLine().orEmpty())
        }
    }

    fun goalLine(): String? {
        val text = GoalStore.summary()
        if (text.isBlank()) return null
        return "Working towards: $text. "
    }

    private fun memoryAndSkills(): String {
        val parts = mutableListOf<String>()
        memoryLine()?.let { parts.add(it) }
        skillsLine()?.let { parts.add(it) }
        if (parts.isEmpty()) return ""
        return "ALREADY KNOWN. " + parts.joinToString(" ") + " "
    }

    fun memoryLine(): String? {
        val context = AppScope.appContext ?: return null
        val dir = File(File(HarnessRuntime.filesDir(), "harness"), "memory")
        val scopes = listOf(
            "user" to File(dir, "user.json"),
            "project" to File(dir, "project-${workspaceSlug()}.json")
        )
        val lines = mutableListOf<String>()
        scopes.forEach { (scope, file) ->
            if (!file.isFile) return@forEach
            val facts = factsIn(context, file)
            lines.add(if (facts.isEmpty()) "$scope: kept, nothing readable" else "$scope: " + facts.joinToString(", "))
        }
        if (lines.isEmpty()) return null
        val text = "Remembered (${lines.joinToString("; ")}). "
        return if (text.length <= MEMORY_CHARS) text else text.take(MEMORY_CHARS) + "… "
    }

    private fun factsIn(context: Context, file: File): List<String> {
        val text = HarnessVault.read(context, file)
        if (text.isBlank()) return emptyList()
        val json = try { JSONObject(text) } catch (e: Exception) { return emptyList() }
        val keys = json.keys()
        val out = mutableListOf<String>()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.startsWith("__")) continue
            val value = json.optString(key, "").replace(Regex("\\s+"), " ").trim()
            out.add("$key=" + if (value.length > MEMORY_VALUE) value.take(MEMORY_VALUE) + "…" else value)
            if (out.size >= MEMORY_KEYS) break
        }
        return out.sorted()
    }

    private fun workspaceSlug(): String =
        HarnessRuntime.workspace().name.lowercase().replace(Regex("[^a-z0-9]+"), "-")

    fun skillsLine(): String? {
        val dirs = mutableListOf(File(HarnessRuntime.workspace(), ".lucent/skills"))
        HarnessRuntime.config().skillDirs.forEach { if (it.isNotBlank()) dirs.add(File(it)) }
        val files = dirs.filter { it.isDirectory }
            .flatMap { dir -> (dir.listFiles() ?: emptyArray()).filter { it.isFile && it.name.endsWith(".md") } }
            .distinctBy { it.name.lowercase() }
            .sortedBy { it.name.lowercase() }
        if (files.isEmpty()) return null
        val shown = files.take(SKILLS_SHOWN).map { file ->
            val label = file.nameWithoutExtension
            val description = try {
                file.useLines { lines -> lines.firstOrNull { it.startsWith("description:") } }
                    ?.removePrefix("description:")?.trim()?.take(SKILL_DESCRIPTION) ?: ""
            } catch (e: Exception) {
                ""
            }
            if (description.isEmpty()) label else "$label ($description)"
        }
        val rest = if (files.size > SKILLS_SHOWN) " and ${files.size - SKILLS_SHOWN} more" else ""
        val text = "Skills on hand, read one with read_skill before a specialised job: " +
            shown.joinToString("; ") + rest + ". "
        return if (text.length <= SKILLS_CHARS) text else text.take(SKILLS_CHARS) + "… "
    }

    private fun shellLine(config: HarnessConfig, capabilities: Set<String>): String {
        val shell = HarnessRuntime.shell
        return when {
            capabilities.contains(HarnessRuntime.CAP_SHELL) ->
                "You can run shell commands (run_command, start_job) through ${shell?.id ?: "the shell"}. "
            HarnessRuntime.android ->
                "You cannot run shell commands yet: no Termux bridge and no privileged shell are available. "
            else ->
                "You cannot run shell commands yet. "
        }
    }

    fun compactBlock(): String {
        val config = HarnessRuntime.config()
        if (!config.enabled) return ""
        val tools = HarnessGate.enabledTools(HarnessRuntime.android)
        if (tools.isEmpty()) return ""
        return "You can also work with real files in " + HarnessRuntime.workspace().path +
            ": read, write and edit them, create Word, Excel and PowerPoint documents, look things up on the web " +
            "when that is switched on, and run commands when a shell is available. Do the work with the tools " +
            "before you answer, keep a short plan with update_plan for anything long, and never claim something " +
            "happened unless a tool said so.\n" + goalLine().orEmpty()
    }

    fun capabilitySummary(): String {        val config = HarnessRuntime.config()
        val capabilities = HarnessRuntime.capabilities()
        val tools = HarnessGate.enabledTools(HarnessRuntime.android, capabilities)
        return buildString {
            append("tools=").append(tools.size)
            append(" groups=").append(HarnessGroup.entries.count { config.groupEnabled(it) && tools.any { tool -> tool.group == it } })
            append(" shell=").append(capabilities.contains(HarnessRuntime.CAP_SHELL))
            append(" plugins=").append(config.installedPlugins().size)
            append(" mcp=").append(config.mcpServers.count { it.enabled })
            append(" device=").append(config.deviceEnabled && HarnessRuntime.android)
        }
    }
}
