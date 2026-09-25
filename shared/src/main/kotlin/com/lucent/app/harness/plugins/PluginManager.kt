package com.lucent.app.harness.plugins

import android.content.Context
import com.lucent.app.harness.AuditEntry
import com.lucent.app.harness.AuditTrail
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.harness.PluginHost
import com.lucent.app.harness.PluginOutcome
import com.lucent.app.harness.PluginState
import com.lucent.app.harness.ShellOutcome
import java.io.File

class PluginManager private constructor(private val context: Context?, private val android: Boolean) : PluginHost {

    override val id = "lucent-plugins"

    override fun isReady(): Boolean = HarnessRuntime.shell?.isReady() == true

    override fun describe(): String = when {
        !isReady() -> if (android) "Termux is needed before anything can be installed" else "no shell available"
        else -> "plugins install through ${HarnessRuntime.shell?.id ?: "the shell"}"
    }

    override suspend fun available(): List<String> = HarnessRuntime.config().installedPlugins().toList()

    override suspend fun detect(plugin: PluginSpec): Boolean {
        val stored = HarnessRuntime.config().pluginInstalled(plugin.id)
        if (plugin.detectCommand.isBlank()) {
            if (plugin.id == "termux") return HarnessRuntime.capabilities().contains(HarnessRuntime.CAP_TERMUX)
            return stored
        }
        if (!isReady()) return stored
        val outcome = HarnessRuntime.runShell(plugin.detectCommand, null, 60)
        return outcome.ok
    }

    override suspend fun install(
        plugin: PluginSpec,
        source: PluginSource,
        onProgress: (Float, String) -> Unit
    ): PluginOutcome {
        if (!isReady()) {
            return PluginOutcome(
                false,
                if (android) "Installing ${plugin.name} needs Termux. Install Termux from F-Droid, set " +
                    "allow-external-apps=true in ~/.termux/termux.properties, run termux-setup-storage once, then try again."
                else "No shell is available on this machine."
            )
        }
        var chosen = source
        if (plugin.sources.isNotEmpty() && plugin.sources.any { it.url.startsWith("http") }) {
            if (chosen.id.isBlank() || chosen.url.isBlank()) {
                onProgress(0.02f, "testing download sources")
                chosen = PluginDownload.fastest(plugin.sources) ?: plugin.sources.first()
            }
            onProgress(0.05f, "downloading from ${chosen.label}")
        }
        val target = File(HarnessRuntime.subDir("downloads"), fileNameOf(plugin, chosen))
        var script = plugin.installScript
        if (chosen.url.startsWith("http") && plugin.sources.isNotEmpty()) {
            val fetched = PluginDownload.fetch(chosen, target) { fraction, note ->
                onProgress(0.05f + fraction * 0.6f, "${chosen.label}: $note")
            }
            if (!fetched.ok) return fetched
            script = script.replace("{file}", target.path)
        }
        onProgress(0.7f, "installing")
        val outcome = run(plugin, script, 3600)
        val ok = outcome.ok && detect(plugin)
        if (ok) {
            val state = PluginState(
                id = plugin.id,
                installed = true,
                source = chosen.id.ifBlank { "built-in" },
                version = System.currentTimeMillis().toString(),
                sizeBytes = if (target.exists()) target.length() else plugin.bytes,
                installedAt = System.currentTimeMillis()
            )
            HarnessRuntime.update(HarnessRuntime.config().withPlugin(state).withMirror(plugin.id, state.source))
            if (target.exists() && plugin.sources.isNotEmpty()) target.delete()
        }
        onProgress(1f, if (ok) "installed" else "install failed")
        record(plugin, if (ok) "installed" else "install failed", outcome.text.take(600))
        return PluginOutcome(
            ok,
            if (ok) "${plugin.name} is installed" else "Installing ${plugin.name} failed:\n" + outcome.text.takeLast(1200),
            target.path
        )
    }

    override suspend fun remove(plugin: PluginSpec): PluginOutcome {
        if (!isReady()) return PluginOutcome(false, "No shell is available, so nothing can be removed.")
        val outcome = if (plugin.removeScript.isBlank()) ShellOutcome(true, "", "", 0)
        else run(plugin, plugin.removeScript, 1800)
        val state = PluginState(id = plugin.id, installed = false)
        HarnessRuntime.update(HarnessRuntime.config().withPlugin(state))
        record(plugin, "removed", outcome.text.take(400))
        return PluginOutcome(true, "${plugin.name} removed." + if (outcome.text.isBlank()) "" else "\n" + outcome.text.take(400))
    }

    override suspend fun runPluginCommand(
        plugin: PluginSpec,
        command: String,
        timeoutSeconds: Int
    ): ShellOutcome = run(plugin, command, timeoutSeconds)

    private suspend fun run(plugin: PluginSpec, script: String, timeoutSeconds: Int): ShellOutcome {
        if (script.isBlank()) return ShellOutcome(false, "", "This plugin has nothing to run.", -1)
        val wrapped = if (android && needsUserland(plugin)) insideUserland(script) else script
        return HarnessRuntime.runShell(wrapped, HarnessRuntime.workspace(), timeoutSeconds)
    }

    private fun needsUserland(plugin: PluginSpec): Boolean = when (plugin.id) {
        "ubuntu", "termux" -> false
        else -> HarnessRuntime.config().pluginInstalled("ubuntu")
    }

    private fun insideUserland(script: String): String {
        val rootfs = "~/lucent/ubuntu/rootfs"
        val escaped = script.replace("'", "'\\''")
        return "proot -0 -r $rootfs -w /root -b /dev -b /proc -b /sys " +
            "/usr/bin/env -i HOME=/root PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin " +
            "TERM=xterm LANG=C.UTF-8 /bin/bash -lc '$escaped'"
    }

    private fun fileNameOf(plugin: PluginSpec, source: PluginSource): String {
        val fromUrl = source.url.substringAfterLast('/').substringBefore('?')
        if (fromUrl.contains('.')) return fromUrl
        return "${plugin.id}.download"
    }

    private fun record(plugin: PluginSpec, action: String, detail: String) {
        val app = context ?: return
        AuditTrail.record(
            app,
            AuditEntry(
                at = System.currentTimeMillis(),
                tool = "plugin:${plugin.id}",
                group = "plugins",
                permission = "execute",
                approval = "allow",
                arguments = action,
                outcome = if (action.contains("failed")) "failed" else "ok",
                detail = detail,
                millis = 0L,
                files = emptyList()
            )
        )
    }

    companion object {
        fun android(context: Context): PluginManager = PluginManager(context.applicationContext, true)

        fun desktop(): PluginManager = PluginManager(null, false)
    }
}
