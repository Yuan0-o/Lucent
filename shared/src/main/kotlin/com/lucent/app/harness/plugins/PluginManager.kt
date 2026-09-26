package com.lucent.app.harness.plugins

import android.content.Context
import com.lucent.app.harness.AuditEntry
import com.lucent.app.harness.AuditTrail
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.harness.PluginFailure
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

    override suspend fun detect(plugin: PluginSpec): Boolean = probe(plugin).ok

    suspend fun probe(plugin: PluginSpec): ShellOutcome {
        val stored = HarnessRuntime.config().pluginInstalled(plugin.id)
        val command = plugin.probeFor(android)
        if (command.isBlank()) {
            val present = if (plugin.id == "termux") {
                HarnessRuntime.capabilities().contains(HarnessRuntime.CAP_TERMUX)
            } else {
                stored
            }
            return ShellOutcome(present, "", "", if (present) 0 else 1)
        }
        if (!isReady()) return ShellOutcome(false, "", "No shell is available to check this plugin", -1)
        return HarnessRuntime.runShellAsync(wrap(plugin, command), HarnessRuntime.workspace(), 90)
    }

    override suspend fun install(
        plugin: PluginSpec,
        source: PluginSource,
        onProgress: (Float, String) -> Unit
    ): PluginOutcome {
        var script = plugin.installFor(android)
        if (script.isBlank()) {
            return PluginOutcome(
                false,
                "${plugin.name} has nothing to install on this platform",
                failure = PluginFailure.NO_PLATFORM
            )
        }
        var sourceId = source.id.ifBlank { "built-in" }
        var staged: File? = null
        if (script.contains("{file}")) {
            val usable = plugin.sources.filter { it.url.startsWith("http") }
            if (usable.isEmpty()) {
                return PluginOutcome(
                    false,
                    "${plugin.name} has no downloadable source",
                    failure = PluginFailure.DOWNLOAD
                )
            }
            var chosen = source
            if (chosen.id.isBlank() || chosen.url.isBlank() || usable.none { it.id == chosen.id }) {
                onProgress(0.02f, "testing download sources")
                chosen = PluginDownload.fastest(usable) ?: usable.first()
            }
            sourceId = chosen.id.ifBlank { "built-in" }
            onProgress(0.05f, "downloading from ${chosen.label}")
            val target = File(HarnessRuntime.downloadsDir(), fileNameOf(plugin, chosen))
            val fetched = try {
                PluginDownload.fetch(chosen, target) { fraction, note ->
                    onProgress(0.05f + fraction * 0.5f, "${chosen.label}: $note")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                target.delete()
                record(plugin, "download cancelled", target.path)
                return PluginOutcome(
                    false,
                    "${plugin.name}: the download was cancelled and the partial file was removed",
                    "",
                    PluginFailure.CANCELLED,
                    ""
                )
            }
            if (!fetched.ok) {
                record(plugin, "download failed", fetched.message)
                return fetched.copy(failure = PluginFailure.DOWNLOAD)
            }
            staged = target
            script = script.replace("{file}", target.path)
        }
        if (!isReady()) {
            val kept = staged?.path.orEmpty()
            record(plugin, "downloaded only", kept)
            return PluginOutcome(
                false,
                "${plugin.name} was downloaded but cannot be installed without a shell",
                kept,
                PluginFailure.NO_SHELL,
                kept
            )
        }
        onProgress(0.6f, "installing")
        val outcome = try {
            run(plugin, script, 3600)
        } catch (e: kotlinx.coroutines.CancellationException) {
            staged?.delete()
            record(plugin, "install cancelled", staged?.path.orEmpty())
            return PluginOutcome(
                false,
                "${plugin.name}: the install was cancelled and the downloaded file was removed",
                "",
                PluginFailure.CANCELLED,
                ""
            )
        }
        if (!outcome.ok) {
            val detail = outcome.text.take(1200)
            record(plugin, "install failed", detail)
            return PluginOutcome(
                false,
                "${plugin.name}: the install command exited ${outcome.exitCode}" +
                    if (outcome.timedOut) " after the time limit" else "",
                staged?.path.orEmpty(),
                PluginFailure.INSTALL,
                detail
            )
        }
        val checked = probe(plugin)
        if (!checked.ok) {
            val detail = (outcome.text.take(600) + "\n" + checked.text.take(400)).trim()
            record(plugin, "install failed", detail)
            return PluginOutcome(
                false,
                "${plugin.name} was installed but the check still fails: ${plugin.probeFor(android)}",
                staged?.path.orEmpty(),
                PluginFailure.DETECT,
                detail
            )
        }
        val kept = staged
        val state = PluginState(
            id = plugin.id,
            installed = true,
            source = sourceId,
            version = System.currentTimeMillis().toString(),
            sizeBytes = if (kept != null && kept.exists()) kept.length() else plugin.bytes,
            installedAt = System.currentTimeMillis()
        )
        HarnessRuntime.update(HarnessRuntime.config().withPlugin(state).withMirror(plugin.id, state.source))
        kept?.delete()
        onProgress(1f, "installed")
        record(plugin, "installed", outcome.text.take(600))
        return PluginOutcome(true, "${plugin.name} is installed")
    }

    override suspend fun remove(plugin: PluginSpec): PluginOutcome {
        if (!isReady()) {
            return PluginOutcome(
                false,
                "No shell is available, so ${plugin.name} cannot be removed",
                failure = PluginFailure.NO_SHELL
            )
        }
        val script = plugin.removeFor(android)
        val outcome = if (script.isBlank()) ShellOutcome(true, "", "", 0) else run(plugin, script, 1800)
        if (!outcome.ok) {
            record(plugin, "remove failed", outcome.text.take(400))
            return PluginOutcome(
                false,
                "${plugin.name}: the remove command exited ${outcome.exitCode}",
                failure = PluginFailure.INSTALL,
                detail = outcome.text.take(400)
            )
        }
        HarnessRuntime.update(HarnessRuntime.config().withPlugin(PluginState(id = plugin.id, installed = false)))
        record(plugin, "removed", outcome.text.take(400))
        return PluginOutcome(
            true,
            "${plugin.name} removed." + if (outcome.text.isBlank()) "" else "\n" + outcome.text.take(400)
        )
    }

    override suspend fun runPluginCommand(
        plugin: PluginSpec,
        command: String,
        timeoutSeconds: Int
    ): ShellOutcome = run(plugin, command, timeoutSeconds)

    private suspend fun run(plugin: PluginSpec, script: String, timeoutSeconds: Int): ShellOutcome {
        if (script.isBlank()) return ShellOutcome(false, "", "This plugin has nothing to run.", -1)
        return HarnessRuntime.runShellAsync(wrap(plugin, script), HarnessRuntime.workspace(), timeoutSeconds)
    }

    private fun needsUserland(plugin: PluginSpec): Boolean =
        plugin.id in USERLAND_PLUGINS && HarnessRuntime.config().pluginInstalled("ubuntu")

    private fun wrap(plugin: PluginSpec, script: String): String {
        if (!android || !needsUserland(plugin)) return script
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

        private val USERLAND_PLUGINS = setOf("python-office", "libreoffice")

        fun android(context: Context): PluginManager = PluginManager(context.applicationContext, true)

        fun desktop(context: Context? = null): PluginManager = PluginManager(context?.applicationContext, false)
    }
}
