package com.lucent.app.harness

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

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

    private val lastErrorRef = AtomicReference("")

    private var lastError: String
        get() = lastErrorRef.get()
        set(value) {
            lastErrorRef.set(value)
        }

    fun installed(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun error(): String = lastError

    fun refresh() {
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
        env: Map<String, String>,
        onOutput: ((String) -> Unit)? = null
    ): ShellOutcome = withContext(Dispatchers.IO) {
        if (!installed(context)) {
            return@withContext ShellOutcome(false, "", "Termux is not installed", -1)
        }
        val jobDir = File(HarnessRuntime.workspace(), ".lucent/jobs").apply { mkdirs() }
        val probe = File(jobDir, "probe-${System.currentTimeMillis()}.txt")
        val writable = runCatching {
            probe.writeText("ok")
            probe.delete()
            true
        }.getOrDefault(false)
        if (!writable) {
            lastError = "The workspace folder ${HarnessRuntime.workspace().path} cannot be written, so Termux has " +
                "nowhere to hand back its output. Choose a workspace on shared storage and grant all-files access."
            return@withContext ShellOutcome(false, "", lastError, -1)
        }
        val stamp = System.currentTimeMillis()
        val outFile = File(jobDir, "out-$stamp.txt")
        val codeFile = File(jobDir, "code-$stamp.txt")
        val script = buildString {
            append("if [ -z \"${'$'}HOME\" ] || [ \"${'$'}HOME\" = \"/\" ] || [ ! -d \"${'$'}HOME\" ]; ")
            append("then export HOME=/data/data/com.termux/files/home; fi; ")
            append("cd '").append(workdir.path.replace("'", "'\\''")).append("' 2>/dev/null || cd \"\$HOME\"; ")
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
        val tailer = StreamTailer()
        while (System.currentTimeMillis() < deadline) {
            if (onOutput != null && outFile.exists()) {
                val len = outFile.length()
                if (len > tailer.offset) {
                    java.io.RandomAccessFile(outFile, "r").use { raf ->
                        raf.seek(tailer.offset)
                        val chunk = ByteArray((len - tailer.offset).toInt())
                        raf.readFully(chunk)
                        tailer.processNewBytes(chunk, onOutput)
                    }
                }
            }
            if (codeFile.exists()) break
            delay(300)
        }
        if (onOutput != null && outFile.exists()) {
            val len = outFile.length()
            if (len > tailer.offset) {
                java.io.RandomAccessFile(outFile, "r").use { raf ->
                    raf.seek(tailer.offset)
                    val chunk = ByteArray((len - tailer.offset).toInt())
                    raf.readFully(chunk)
                    tailer.processNewBytes(chunk, onOutput)
                }
            }
            tailer.flush(onOutput)
        }
        if (!codeFile.exists()) {
            if (!permissionDeclared(context)) {
                lastError = "Termux did not answer within ${timeoutSeconds}s because the run-command permission is missing."
            } else {
                lastError = "Termux did not answer within ${timeoutSeconds}s. Check that Termux is installed, that " +
                    "allow-external-apps=true is set in ~/.termux/termux.properties, and that it holds the run-command " +
                    "permission. If you use a Termux fork with a different package name, install the official " +
                    "com.termux build instead, since only it can receive Lucent's commands."
            }
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
