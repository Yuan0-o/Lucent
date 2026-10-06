package com.lucent.app.harness

import com.lucent.app.harness.plugins.PluginCatalog
import com.lucent.app.harness.plugins.PluginPreflight
import com.lucent.app.harness.plugins.PluginSource
import com.lucent.app.harness.plugins.PluginSpec
import com.lucent.app.network.ToolExecResult
import kotlinx.serialization.json.*

object PluginTools : HarnessGroupTools {

    override val group = HarnessGroup.PLUGINS

    private const val BIG_DOWNLOAD = 200L * 1024 * 1024

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "plugin_status",
            group = group,
            permission = HarnessPermission.READ,
            description = "List the optional tools available on this device, whether each one is installed, how big it " +
                "is and what it adds. Call this before saying you cannot do something."
        ),
        HarnessTool(
            name = "plugin_list_available",
            group = group,
            permission = HarnessPermission.READ,
            description = "Search the plugin catalogue and show the download sources for a match, official first.",
            params = listOf(HarnessSchema.text("query", "Part of a name or purpose", false))
        ),
        HarnessTool(
            name = "install_plugin",
            group = group,
            permission = HarnessPermission.EXECUTE,
            description = "Download and install a plugin. Speed-tests the mirrors and picks the fastest. For anything " +
                "over 200 MiB, ask the user first and then pass confirm_bytes with the size they agreed to.",
            params = listOf(
                HarnessSchema.text("id", "Plugin id from plugin_status"),
                HarnessSchema.text("source", "Preferred source id", false),
                HarnessSchema.number("confirm_bytes", "The size the user agreed to", false)
            )
        ),
        HarnessTool(
            name = "remove_plugin",
            group = group,
            permission = HarnessPermission.DELETE,
            description = "Remove an installed plugin and free the space it used.",
            params = listOf(HarnessSchema.text("id", "Plugin id"))
        ),
        HarnessTool(
            name = "plugin_run",
            group = group,
            permission = HarnessPermission.EXECUTE,
            description = "Run a command inside an installed plugin's environment, for example inside the Linux " +
                "userland. Arguments: id, command, timeout.",
            params = listOf(
                HarnessSchema.text("id", "Plugin id"),
                HarnessSchema.text("command", "Command to run inside it"),
                HarnessSchema.number("timeout", "Seconds before giving up", false)
            )
        ),
        HarnessTool(
            name = "plugin_inspect",
            group = group,
            permission = HarnessPermission.READ,
            description = "Check a plugin before installing it: reports every blocking problem (missing shell, " +
                "unreachable sources, unwritable folders) with exact repair steps, plus mirror speeds. " +
                "Call this when an install fails or before downloading anything large.",
            params = listOf(
                HarnessSchema.text("id", "Plugin id from plugin_status"),
                HarnessSchema.text("source", "Preferred source id", false)
            )
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JsonObject): ToolExecResult? = when (name) {
        "plugin_status" -> status(ctx)
        "plugin_list_available" -> list(ctx, args)
        "install_plugin" -> install(ctx, args)
        "remove_plugin" -> remove(ctx, args)
        "plugin_run" -> run(ctx, args)
        "plugin_inspect" -> inspect(ctx, args)
        else -> null
    }

    private fun describe(plugin: PluginSpec, ctx: HarnessCtx): String {
        val state = ctx.config.plugin(plugin.id)
        val installed = state?.installed == true
        val size = when {
            installed && (state?.sizeBytes ?: 0L) > 0 -> Workspace.humanSize(state!!.sizeBytes)
            plugin.bytes > 0 -> Workspace.humanSize(plugin.bytes)
            else -> "small"
        }
        val mark = if (installed) "[installed]" else "[not installed]"
        return "$mark ${plugin.id} — ${plugin.name} ($size): ${plugin.summary}"
    }

    private fun status(ctx: HarnessCtx): ToolExecResult {
        val catalogue = PluginCatalog.forPlatform(ctx.android)
        val shell = HarnessRuntime.shell
        val sb = StringBuilder()
        sb.append("Shell: ").append(shell?.id ?: "none").append(" — ").append(shell?.describe() ?: "not available").append('\n')
        sb.append("Plugins for this platform (").append(catalogue.size).append("):\n")
        catalogue.forEach { sb.append("  ").append(describe(it, ctx)).append('\n') }
        val canInstall = HarnessRuntime.pluginHost?.isReady() == true
        sb.append(if (canInstall) "Installing is possible here." else "Installing needs a shell first.")
        return ToolExecResult(sb.toString().trimEnd())
    }

    private fun list(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val query = (args["query"]?.jsonPrimitive?.content ?: "").lowercase()
        val matches = PluginCatalog.forPlatform(ctx.android).filter {
            query.isBlank() || it.id.contains(query) || it.name.lowercase().contains(query) ||
                it.summary.lowercase().contains(query)
        }
        if (matches.isEmpty()) return ToolExecResult("Nothing in the catalogue matches \"$query\".")
        val sb = StringBuilder()
        matches.forEach { plugin ->
            sb.append(describe(plugin, ctx)).append('\n')
            if (plugin.sources.isEmpty()) {
                sb.append("    source: built in")
                val probe = plugin.probeFor(ctx.android)
                if (probe.isNotBlank()) sb.append(" (detected with: $probe)")
                sb.append('\n')
            } else {
                plugin.sources.forEach { source ->
                    sb.append("    ").append(if (source.official) "official" else "mirror").append(": ")
                        .append(source.label).append(" — ").append(source.url).append('\n')
                }
            }
            sb.append("    licence: ").append(plugin.licence).append('\n')
        }
        return ToolExecResult(sb.toString().trimEnd())
    }

    private suspend fun install(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val id = (args["id"]?.jsonPrimitive?.content ?: "").trim()
        val plugin = PluginCatalog.find(id)
            ?: return ToolExecResult("No plugin called \"$id\". Use plugin_status for the list.", success = false)
        if (ctx.android && !plugin.android) return ToolExecResult("${plugin.name} is not for Android.", success = false)
        if (!ctx.android && !plugin.desktop) return ToolExecResult("${plugin.name} is not for this machine.", success = false)
        val host = HarnessRuntime.pluginHost ?: return ToolExecResult("This build cannot install plugins.", success = false)
        if (plugin.bytes > BIG_DOWNLOAD && (args["confirm_bytes"]?.jsonPrimitive?.longOrNull ?: 0L) != plugin.bytes) {
            return ToolExecResult(
                "${plugin.name} is ${Workspace.humanSize(plugin.bytes)}. Ask the user whether to download it, then " +
                    "call install_plugin again with confirm_bytes=${plugin.bytes}.",
                success = false
            )
        }
        val preferred = args["source"]?.jsonPrimitive?.content ?: ""
        val source = plugin.sources.firstOrNull { it.id == preferred } ?: PluginSource("", "", "")
        val preflight = PluginPreflight.inspect(plugin, source, ctx.android)
        if (preflight.blocked) {
            val failure = PluginOutcome(false, "", failure = PluginPreflight.failureOf(preflight))
            return ToolExecResult(PluginPreflight.render(preflight) + repairHint(ctx, failure), success = false)
        }
        HarnessRuntime.note("installing ${plugin.name}")
        var lastNote = ""
        val outcome = host.install(plugin, source, onProgress = { _, note ->
            if (note != lastNote) {
                lastNote = note
                HarnessRuntime.note("${plugin.name}: $note")
            }
        })
        if (!outcome.ok) return ToolExecResult(outcome.message + repairHint(ctx, outcome), success = false)
        val refreshed = PluginCatalog.find(id)?.let { detectAndRecord(ctx, it) }
        return ToolExecResult(
            "${plugin.name} is ready. ${outcome.message}" + if (refreshed == null) "" else ""
        )
    }

    private suspend fun inspect(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val plugin = PluginCatalog.find((args["id"]?.jsonPrimitive?.content ?: "").trim())
            ?: return ToolExecResult("No plugin with that id. Use plugin_status for the list.", success = false)
        val preferred = args["source"]?.jsonPrimitive?.content ?: ""
        val source = plugin.sources.firstOrNull { it.id == preferred } ?: PluginSource("", "", "")
        val report = PluginPreflight.inspect(plugin, source, ctx.android)
        return ToolExecResult(PluginPreflight.render(report), success = !report.blocked)
    }

    private fun repairHint(ctx: HarnessCtx, outcome: PluginOutcome): String = when (outcome.failure) {
        PluginFailure.NO_SHELL -> if (ctx.android) {
            if (outcome.message.contains("built-in environment is not set up") ||
                outcome.detail.contains("built-in environment is not set up") ||
                outcome.message.contains("setup guide") ||
                outcome.detail.contains("setup guide")
            ) {
                " The built-in environment is not set up yet - open the toolbox setup guide and tap Set up environment."
            } else {
                " Nothing can be installed without a working shell: open the toolbox setup guide and tap " +
                    "Set up environment to prepare the built-in runtime, or enable the privileged shell " +
                    "in Settings."
            }
        } else {
            " A shell is needed before this can be installed on this machine."
        }
        PluginFailure.DETECT -> " Everything installed, but the check still fails: read the output for the first error."
        PluginFailure.DOWNLOAD -> {
            val detail = outcome.detail
            when {
                detail.contains("was expected") ->
                    " The mirror sent a different-sized file, so it may have refreshed the image. " +
                        "Installing again re-tests every source and takes the fastest."
                else -> " The download failed: installing again re-tests every source and takes the fastest."
            }
        }
        PluginFailure.INSTALL -> {
            val detail = outcome.detail
            if (detail.contains("Unable to locate package", ignoreCase = true) ||
                detail.contains("E: Unable", ignoreCase = true)
            ) {
                " The environment could not find a package, which usually means its package list is stale. " +
                    "Install again: the install refreshes the list first."
            } else {
                ""
            }
        }
        else -> ""
    }

    private suspend fun detectAndRecord(ctx: HarnessCtx, plugin: PluginSpec): Boolean {
        val host = HarnessRuntime.pluginHost ?: return false
        return host.detect(plugin)
    }

    private suspend fun remove(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val plugin = PluginCatalog.find((args["id"]?.jsonPrimitive?.content ?: "").trim())
            ?: return ToolExecResult("No plugin with that id.", success = false)
        val host = HarnessRuntime.pluginHost ?: return ToolExecResult("This build cannot remove plugins.", success = false)
        val outcome = host.remove(plugin)
        return ToolExecResult(outcome.message, success = outcome.ok)
    }

    private suspend fun run(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val plugin = PluginCatalog.find((args["id"]?.jsonPrimitive?.content ?: "").trim())
            ?: return ToolExecResult("No plugin with that id.", success = false)
        val command = args["command"]?.jsonPrimitive?.content ?: ""
        if (command.isBlank()) return ToolExecResult("There is no command to run.", success = false)
        val host = HarnessRuntime.pluginHost ?: return ToolExecResult("This build cannot run plugins.", success = false)
        if (!ctx.config.pluginInstalled(plugin.id)) {
            return ToolExecResult("${plugin.name} is not installed yet. Use install_plugin first.", success = false)
        }
        val timeout = (args["timeout"]?.jsonPrimitive?.intOrNull ?: 600).coerceIn(5, 3600)
        val outcome = host.runPluginCommand(plugin, command, timeout)
        return ToolExecResult(
            "exit code ${outcome.exitCode}${if (outcome.timedOut) " (timed out)" else ""}\n" +
                ctx.limit(outcome.text.ifBlank { "(no output)" }),
            success = outcome.ok
        )
    }

}
