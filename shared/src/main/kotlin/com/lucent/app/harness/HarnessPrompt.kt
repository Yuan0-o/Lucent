package com.lucent.app.harness

object HarnessPrompt {

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
        }
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
            "happened unless a tool said so.\n"
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
