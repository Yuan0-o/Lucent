package com.lucent.app.harness

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

object TermuxBridge {

    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"

    @Volatile private var installedCache: Boolean? = null
    @Volatile private var lastError: String = ""

    fun installed(context: Context): Boolean {
        installedCache?.let { return it }
        val found = try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
        installedCache = found
        return found
    }

    fun error(): String = lastError

    fun refresh() {
        installedCache = null
    }

    fun permissionDeclared(context: Context): Boolean = try {
        context.packageManager.checkPermission(
            "com.termux.permission.RUN_COMMAND",
            context.packageName
        ) == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    fun describe(context: Context): String = when {
        !installed(context) -> "Termux is not installed"
        !permissionDeclared(context) -> "Termux is installed but has not granted this app the run-command permission"
        lastError.isNotEmpty() -> lastError
        else -> "Termux is available"
    }

    suspend fun run(
        context: Context,
        command: String,
        workdir: File,
        timeoutSeconds: Int,
        env: Map<String, String>
    ): ShellOutcome = withContext(Dispatchers.IO) {
        if (!installed(context)) {
            return@withContext ShellOutcome(false, "", "Termux is not installed", -1)
        }
        val jobDir = File(HarnessRuntime.workspace(), ".lucent/jobs").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val outFile = File(jobDir, "out-$stamp.txt")
        val codeFile = File(jobDir, "code-$stamp.txt")
        val script = buildString {
            append("cd '").append(workdir.path.replace("'", "'\\''")).append("' 2>/dev/null || cd \"$HOME\"; ")
            if (env.isNotEmpty()) {
                env.forEach { (key, value) ->
                    append("export ").append(key).append("='").append(value.replace("'", "'\\''")).append("'; ")
                }
            }
            append("{ ").append(command).append(" ; } > '").append(outFile.path).append("' 2>&1; ")
            append("echo $? > '").append(codeFile.path).append("'")
        }
        try {
            val intent = Intent()
            intent.component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            intent.action = ACTION_RUN_COMMAND
            intent.putExtra(EXTRA_PATH, BASH)
            intent.putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", script))
            intent.putExtra(EXTRA_WORKDIR, workdir.path)
            intent.putExtra(EXTRA_BACKGROUND, true)
            intent.putExtra(EXTRA_SESSION_ACTION, "1")
            context.startService(intent)
        } catch (t: Throwable) {
            lastError = "Termux refused the command: ${t.message ?: t::class.simpleName}. " +
                "Set allow-external-apps=true in Termux and grant the run-command permission."
            return@withContext ShellOutcome(false, "", lastError, -1)
        }
        val deadline = System.currentTimeMillis() + timeoutSeconds.coerceIn(1, 7200) * 1000L
        while (System.currentTimeMillis() < deadline) {
            if (codeFile.exists()) break
            delay(300)
        }
        if (!codeFile.exists()) {
            lastError = "Termux did not answer within ${timeoutSeconds}s. Check that Termux is installed, that " +
                "allow-external-apps=true is set in ~/.termux/termux.properties, and that it holds the run-command permission."
            return@withContext ShellOutcome(false, outFile.takeIf { it.exists() }?.readText().orEmpty(), lastError, -1, true)
        }
        val code = codeFile.readText().trim().toIntOrNull() ?: -1
        val out = outFile.takeIf { it.exists() }?.readText().orEmpty()
        outFile.delete()
        codeFile.delete()
        lastError = ""
        ShellOutcome(code == 0, out, "", code)
    }
}
