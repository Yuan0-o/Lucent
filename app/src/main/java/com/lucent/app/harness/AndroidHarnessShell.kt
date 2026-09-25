package com.lucent.app.harness

import android.content.Context
import com.lucent.app.data.PrivilegedShell
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class AndroidHarnessShell(private val context: Context) : HarnessShell {

    override val id = "android"

    override fun isReady(): Boolean = privilegedReady() || TermuxBridge.installed(context)

    private fun privilegedReady(): Boolean = try {
        PrivilegedShell.isReady()
    } catch (t: Throwable) {
        false
    }

    override fun describe(): String = when {
        privilegedReady() -> "privileged shell (Shizuku or root)"
        TermuxBridge.installed(context) -> "Termux — ${TermuxBridge.describe(context)}"
        else -> "no shell: install Termux or enable the privileged shell"
    }

    override fun capabilityNames(): Set<String> {
        val out = mutableSetOf("shell")
        if (privilegedReady()) {
            out.add("privileged")
            out.add("root-files")
        }
        if (TermuxBridge.installed(context)) out.add("termux")
        return out
    }

    override suspend fun run(
        command: String,
        workdir: File?,
        timeoutSeconds: Int,
        env: Map<String, String>
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
        if (TermuxBridge.installed(context)) {
            return TermuxBridge.run(context, command, dir, timeoutSeconds, env)
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
