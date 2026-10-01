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
        !isReady() -> if (android) "the built-in environment is needed before anything can be installed" else "no shell available"
        else -> "plugins install through ${HarnessRuntime.shell?.id ?: "the shell"}"
    }

    override suspend fun available(): List<String> = HarnessRuntime.config().installedPlugins().toList()

    override suspend fun detect(plugin: PluginSpec): Boolean {
        if (plugin.id == "ubuntu" && android && HarnessRuntime.host?.builtinRuntimeState() == "ready") {
            return true
        }
        return probe(plugin).ok
    }

    suspend fun probe(plugin: PluginSpec): ShellOutcome {
        val stored = HarnessRuntime.config().pluginInstalled(plugin.id)
        val command = plugin.probeFor(android)
        if (command.isBlank()) {
            return ShellOutcome(stored, "", "", if (stored) 0 else 1)
        }
        if (!isReady()) return ShellOutcome(false, "", "No shell is available to check this plugin", -1)
        return runScript(plugin, command, 90)
    }

    override suspend fun install(
        plugin: PluginSpec,
        source: PluginSource,
        onProgress: (Float, String) -> Unit,
        onOutput: ((String) -> Unit)?
    ): PluginOutcome {
        if (plugin.id == "ubuntu" && android && HarnessRuntime.host?.builtinRuntimeState() == "ready") {
            val state = PluginState(
                id = plugin.id,
                installed = true,
                source = "built-in",
                version = HarnessRuntime.config().builtinRootfsVersion.toString(),
                sizeBytes = plugin.bytes,
                installedAt = System.currentTimeMillis()
            )
            HarnessRuntime.update(HarnessRuntime.config().withPlugin(state))
            PluginJournal.clear(plugin.id)
            onProgress(1f, "installed")
            record(plugin, "installed", "Ubuntu is provided by the built-in environment")
            return PluginOutcome(true, "Ubuntu is provided by the built-in environment")
        }

        if (probe(plugin).ok) {
            val state = PluginState(
                id = plugin.id,
                installed = true,
                source = "already present",
                version = System.currentTimeMillis().toString(),
                sizeBytes = plugin.bytes,
                installedAt = System.currentTimeMillis()
            )
            HarnessRuntime.update(HarnessRuntime.config().withPlugin(state))
            PluginJournal.clear(plugin.id)
            onProgress(1f, "installed")
            record(plugin, "installed", "${plugin.name} was already available on this device")
            return PluginOutcome(true, "${plugin.name} is already available on this device")
        }

        var script = plugin.installFor(android)
        if (script.isBlank()) {
            return PluginOutcome(
                false,
                "${plugin.name} has nothing to install on this platform",
                failure = PluginFailure.NO_PLATFORM
            )
        }
        if (script.contains("{aptMirror}")) {
            val region = HarnessRuntime.config().pluginMirrorRegion
            script = script.replace("{aptMirror}", PluginCatalog.aptMirror(region))
                .replace("{aptFallback}", PluginCatalog.aptFallback(region))
                .replace("{pipIndex}", PluginCatalog.pipIndex(region))
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
                chosen = PluginDownload.fastest(usable, HarnessRuntime.config().pluginMirrorRegion) ?: usable.first()
            }
            sourceId = chosen.id.ifBlank { "built-in" }
            val reused = PluginPreflight.reusableStaged(plugin, chosen)
            if (reused != null) {
                staged = reused
                script = script.replace("{file}", reused.path)
                onProgress(0.55f, "reusing the download from last time")
                PluginJournal.write(plugin.id, "download", "reused ${reused.name}", true)
            } else {
                onProgress(0.05f, "downloading from ${chosen.label}")
                val target = PluginPreflight.targetFile(plugin, chosen)
                PluginJournal.write(plugin.id, "download", chosen.label, false)
                val fetched = try {
                    PluginDownload.fetch(chosen, target) { fraction, note ->
                        onProgress(0.05f + fraction * 0.5f, "${chosen.label}: $note")
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    target.delete()
                    PluginJournal.write(plugin.id, "download", "cancelled", false)
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
                    val hint = if (fetched.message.contains("was expected")) {
                        " The mirror may have refreshed the file since the catalogue was written; " +
                            "installing again re-tests every source automatically."
                    } else {
                        ""
                    }
                    PluginJournal.write(plugin.id, "download", fetched.message, false)
                    record(plugin, "download failed", fetched.message)
                    return fetched.copy(failure = PluginFailure.DOWNLOAD, message = fetched.message + hint)
                }
                staged = target
                PluginJournal.write(plugin.id, "download", "ok", true)
                script = script.replace("{file}", target.path)
            }
        }
        if (!isReady()) {
            val kept = staged?.path.orEmpty()
            val action = if (staged != null) "downloaded only" else "no shell"
            record(plugin, action, kept.ifBlank { "nothing staged" })
            val message = if (staged != null) {
                "${plugin.name} was downloaded but cannot be installed without a shell"
            } else {
                "${plugin.name} cannot be installed: no shell is available on this device"
            }
            return PluginOutcome(
                false,
                message,
                kept,
                PluginFailure.NO_SHELL,
                kept
            )
        }
        onProgress(0.6f, "installing")
        PluginJournal.write(plugin.id, "install", sourceId, false)
        val outcome = try {
            run(plugin, script, 3600, onOutput)
        } catch (e: kotlinx.coroutines.CancellationException) {
            record(plugin, "install cancelled", staged?.path.orEmpty())
            return PluginOutcome(
                false,
                "${plugin.name}: the install was cancelled, but any downloaded files were kept",
                staged?.path.orEmpty(),
                PluginFailure.CANCELLED,
                ""
            )
        }
        if (!outcome.ok) {
            val detail = outcome.text.take(1200)
            PluginJournal.write(plugin.id, "install", detail.take(200), false)
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
            PluginJournal.write(plugin.id, "install", "check failed", false)
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
        HarnessRuntime.update(HarnessRuntime.config().withPlugin(state))
        kotlinx.coroutines.delay(1000)
        kept?.delete()
        PluginJournal.clear(plugin.id)
        onProgress(1f, "installed")
        record(plugin, "installed", outcome.text.take(600))
        return PluginOutcome(true, "${plugin.name} is installed")
    }

    override suspend fun remove(plugin: PluginSpec, onOutput: ((String) -> Unit)?): PluginOutcome {
        if (!isReady()) {
            return PluginOutcome(
                false,
                "No shell is available, so ${plugin.name} cannot be removed",
                failure = PluginFailure.NO_SHELL
            )
        }
        val script = plugin.removeFor(android)
        val outcome = if (script.isBlank()) ShellOutcome(true, "", "", 0) else run(plugin, script, 1800, onOutput)
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

    private suspend fun run(plugin: PluginSpec, script: String, timeoutSeconds: Int, onOutput: ((String) -> Unit)? = null): ShellOutcome {
        if (script.isBlank()) return ShellOutcome(false, "", "This plugin has nothing to run.", -1)
        return runScript(plugin, script, timeoutSeconds, onOutput)
    }

    private suspend fun runScript(plugin: PluginSpec, script: String, timeoutSeconds: Int, onOutput: ((String) -> Unit)? = null): ShellOutcome {
        return HarnessRuntime.runShellAsync(script, HarnessRuntime.workspace(), timeoutSeconds, onOutput = onOutput)
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

        fun desktop(context: Context? = null): PluginManager = PluginManager(context?.applicationContext, false)

        internal fun testInstance(android: Boolean): PluginManager = PluginManager(null, android)
    }
}
