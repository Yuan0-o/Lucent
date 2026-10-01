package com.lucent.app.harness

import android.content.Context
import com.lucent.app.data.PrivilegedShell
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class AndroidHarnessShell(private val context: Context) : HarnessShell {

    override val id = "android"

    override fun isReady(): Boolean = privilegedReady() || BuiltinShell.builtinAvailable(context)

    private fun privilegedReady(): Boolean = try {
        HarnessRuntime.config().shizukuForAssistant && PrivilegedShell.isReady()
    } catch (t: Throwable) {
        false
    }

    override fun describe(): String = when {
        privilegedReady() -> "privileged shell (Shizuku or root)"
        BuiltinShell.builtinAvailable(context) -> "built-in proot runtime"
        else -> "no shell: wait for built-in runtime setup, or grant the privileged shell in Settings"
    }

    override fun capabilityNames(): Set<String> {
        val out = mutableSetOf("shell")
        if (privilegedReady()) {
            out.add("privileged")
            out.add("shizuku")
            out.add("root-files")
        }
        if (BuiltinShell.builtinAvailable(context)) out.add(HarnessRuntime.CAP_BUILTIN_RUNTIME)
        return out
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
        
        if (BuiltinShell.builtinAvailable(context)) {
            return BuiltinShell.run(context, command, dir, timeoutSeconds.coerceIn(1, 7200) * 1000L, env, onOutput)
        }
        return ShellOutcome(
            false,
            "",
            "No shell is available. Built-in runtime is not available, and privileged shell is off.",
            -1
        )
    }
}
