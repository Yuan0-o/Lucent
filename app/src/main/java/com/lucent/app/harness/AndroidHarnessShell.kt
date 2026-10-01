package com.lucent.app.harness

import android.content.Context
import com.lucent.app.data.PrivilegedShell
import com.lucent.app.harness.plugins.RuntimeBackend
import com.lucent.app.harness.plugins.selectBackend
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class AndroidHarnessShell(private val context: Context) : HarnessShell {

    override val id = "android"

    private fun backend(): RuntimeBackend {
        return selectBackend(
            HarnessRuntime.config().runtimeMode,
            TermuxBridge.installed(context),
            BuiltinShell.builtinAvailable(context)
        )
    }

    override fun isReady(): Boolean = privilegedReady() || backend() != RuntimeBackend.NONE

    private fun privilegedReady(): Boolean = try {
        HarnessRuntime.config().shizukuForAssistant && PrivilegedShell.isReady()
    } catch (t: Throwable) {
        false
    }

    override fun describe(): String = when {
        privilegedReady() -> "privileged shell (Shizuku or root)"
        backend() == RuntimeBackend.BUILTIN -> "built-in proot runtime"
        backend() == RuntimeBackend.TERMUX -> "Termux — ${TermuxBridge.describe(context)}"
        else -> "no shell: install Termux, or grant the privileged shell in Settings"
    }

    override fun capabilityNames(): Set<String> {
        val out = mutableSetOf("shell")
        if (privilegedReady()) {
            out.add("privileged")
            out.add("shizuku")
            out.add("root-files")
        }
        val b = backend()
        if (b == RuntimeBackend.TERMUX) out.add(HarnessRuntime.CAP_TERMUX)
        if (BuiltinShell.builtinAvailable(context)) out.add(HarnessRuntime.CAP_BUILTIN_RUNTIME)
        return out
    }

    override suspend fun runInTermux(
        command: String,
        workdir: File?,
        timeoutSeconds: Int,
        env: Map<String, String>,
        onOutput: ((String) -> Unit)?
    ): ShellOutcome {
        val dir = workdir ?: HarnessRuntime.workspace()
        val b = backend()
        if (b == RuntimeBackend.BUILTIN) {
            return BuiltinShell.run(context, command, dir, timeoutSeconds.coerceIn(1, 7200) * 1000L, env, onOutput)
        }
        if (b == RuntimeBackend.TERMUX) {
            return TermuxBridge.run(context, command, dir, timeoutSeconds, env, onOutput)
        }
        return ShellOutcome(
            false,
            "",
            "This plugin requires a shell, but none is available. Install Termux " +
                "from F-Droid, open it once, and set allow-external-apps=true in " +
                "~/.termux/termux.properties, or wait for built-in runtime support.",
            -1
        )
    }

    override suspend fun run(
        command: String,
        workdir: File?,
        timeoutSeconds: Int,
        env: Map<String, String>,
        onOutput: ((String) -> Unit)?
    ): ShellOutcome {
        val dir = workdir ?: HarnessRuntime.workspace()
        if (privilegedReady()) {
            val prefix = buildString {
                append("cd '").append(dir.path.replace("'", "'\\''")).append("' 2>/dev/null; ")
                env.forEach { (key, value) ->
                    append("export ").append(key).append("='").append(value.replace("'", "'\\''")).append("'; ")
                }
            }
            val result = withTimeoutOrNull(timeoutSeconds.coerceIn(1, 7200) * 1000L) {
                try {
                    PrivilegedShell.runCommand(prefix + command)
                } catch (t: Throwable) {
                    null
                }
            }
            if (result == null) {
                return ShellOutcome(false, "", "The privileged shell did not answer within ${timeoutSeconds}s", -1, true)
            }
            return ShellOutcome(result.success, result.stdout, result.stderr, if (result.success) 0 else 1)
        }
        
        val b = backend()
        if (b == RuntimeBackend.BUILTIN) {
            return BuiltinShell.run(context, command, dir, timeoutSeconds.coerceIn(1, 7200) * 1000L, env, onOutput)
        }
        if (b == RuntimeBackend.TERMUX) {
            return TermuxBridge.run(context, command, dir, timeoutSeconds, env, onOutput)
        }
        return ShellOutcome(
            false,
            "",
            "No shell is available. Install Termux from F-Droid and switch on allow-external-apps, or turn on the " +
                "privileged shell in Settings.",
            -1
        )
    }
}
