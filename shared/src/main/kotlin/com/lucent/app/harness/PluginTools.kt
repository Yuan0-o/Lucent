package com.lucent.app.harness

import com.lucent.app.harness.plugins.PluginCatalog
import com.lucent.app.harness.plugins.PluginDownload
import com.lucent.app.harness.plugins.PluginSource
import com.lucent.app.harness.plugins.PluginSpec
import com.lucent.app.network.ToolExecResult
import kotlinx.coroutines.delay
import org.json.JSONObject

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
            name = "plugin_mirror_test",
            group = group,
            permission = HarnessPermission.NETWORK,
            description = "Measure every download source for a plugin and remember the fastest one.",
            params = listOf(HarnessSchema.text("id", "Plugin id"))
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = when (name) {
        "plugin_status" -> status(ctx)
        "plugin_list_available" -> list(ctx, args)
        "install_plugin" -> install(ctx, args)
        "remove_plugin" -> remove(ctx, args)
        "plugin_run" -> run(ctx, args)
        "plugin_mirror_test" -> mirrorTest(ctx, args)
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
        val mirror = ctx.config.mirrors[plugin.id]?.let { " via $it" } ?: ""
        return "$mark ${plugin.id} — ${plugin.name} ($size$mirror): ${plugin.summary}"
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

    private fun list(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val query = args.optString("query", "").lowercase()
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

    private suspend fun install(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val id = args.optString("id", "").trim()
        val plugin = PluginCatalog.find(id)
            ?: return ToolExecResult("No plugin called \"$id\". Use plugin_status for the list.", success = false)
        if (ctx.android && !plugin.android) return ToolExecResult("${plugin.name} is not for Android.", success = false)
        if (!ctx.android && !plugin.desktop) return ToolExecResult("${plugin.name} is not for this machine.", success = false)
        val host = HarnessRuntime.pluginHost ?: return ToolExecResult("This build cannot install plugins.", success = false)
        if (plugin.bytes > BIG_DOWNLOAD && args.optLong("confirm_bytes", 0L) != plugin.bytes) {
            return ToolExecResult(
                "${plugin.name} is ${Workspace.humanSize(plugin.bytes)}. Ask the user whether to download it, then " +
                    "call install_plugin again with confirm_bytes=${plugin.bytes}.",
                success = false
            )
        }
        val preferred = args.optString("source", "")
        val source = plugin.sources.firstOrNull { it.id == preferred } ?: PluginSource("", "", "")
        HarnessRuntime.note("installing ${plugin.name}")
        var lastNote = ""
        val outcome = host.install(plugin, source) { fraction, note ->
            if (note != lastNote && (fraction > 0.2f || note.contains("failed"))) {
                lastNote = note
                HarnessRuntime.note("${plugin.name}: $note")
            }
        }
        if (!outcome.ok) return ToolExecResult(outcome.message, success = false)
        val refreshed = PluginCatalog.find(id)?.let { detectAndRecord(ctx, it) }
        return ToolExecResult(
            "${plugin.name} is ready. ${outcome.message}" + if (refreshed == null) "" else ""
        )
    }

    private suspend fun detectAndRecord(ctx: HarnessCtx, plugin: PluginSpec): Boolean {
        val host = HarnessRuntime.pluginHost ?: return false
        return host.detect(plugin)
    }

    private suspend fun remove(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val plugin = PluginCatalog.find(args.optString("id", "").trim())
            ?: return ToolExecResult("No plugin with that id.", success = false)
        val host = HarnessRuntime.pluginHost ?: return ToolExecResult("This build cannot remove plugins.", success = false)
        val outcome = host.remove(plugin)
        return ToolExecResult(outcome.message, success = outcome.ok)
    }

    private suspend fun run(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val plugin = PluginCatalog.find(args.optString("id", "").trim())
            ?: return ToolExecResult("No plugin with that id.", success = false)
        val command = args.optString("command", "")
        if (command.isBlank()) return ToolExecResult("There is no command to run.", success = false)
        val host = HarnessRuntime.pluginHost ?: return ToolExecResult("This build cannot run plugins.", success = false)
        if (!ctx.config.pluginInstalled(plugin.id)) {
            return ToolExecResult("${plugin.name} is not installed yet. Use install_plugin first.", success = false)
        }
        val timeout = args.optInt("timeout", 600).coerceIn(5, 3600)
        val outcome = host.runPluginCommand(plugin, command, timeout)
        return ToolExecResult(
            "exit code ${outcome.exitCode}${if (outcome.timedOut) " (timed out)" else ""}\n" +
                ctx.limit(outcome.text.ifBlank { "(no output)" }),
            success = outcome.ok
        )
    }

    private suspend fun mirrorTest(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val plugin = PluginCatalog.find(args.optString("id", "").trim())
            ?: return ToolExecResult("No plugin with that id.", success = false)
        val sources = plugin.sources.filter { it.url.startsWith("http") }
        if (sources.isEmpty()) return ToolExecResult("${plugin.name} has no download sources to test.", success = false)
        val results = PluginDownload.speedTable(sources)
        val sb = StringBuilder("${plugin.name}:\n")
        results.forEach { (label, rate, official) ->
            sb.append("  ").append(if (official) "official " else "mirror   ").append(label).append(": ")
            if (rate > 0) sb.append(Workspace.humanSize(rate)).append("/s") else sb.append("no answer")
            sb.append('\n')
        }
        val best = results.filter { it.second > 0 }.maxByOrNull { it.second }
        if (best != null) {
            val chosen = sources.firstOrNull { it.label == best.first }
            if (chosen != null) {
                HarnessRuntime.update(HarnessRuntime.config().withMirror(plugin.id, chosen.id))
                sb.append("Fastest: ${chosen.label}, remembered for next time.")
            }
        } else {
            sb.append("Nothing answered; the official source will be tried first.")
        }
        delay(1)
        return ToolExecResult(sb.toString())
    }
}
