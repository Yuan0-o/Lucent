package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import com.lucent.app.harness.terminal.TerminalSessions
import com.lucent.app.harness.terminal.PtyStartRequest
import com.lucent.app.harness.terminal.PtySessionState
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

data class HarnessJob(
    val id: String,
    val command: String,
    val workdir: String,
    val startedAt: Long,
    val timeoutSeconds: Int
)

class HarnessJobHandle internal constructor(val job: HarnessJob) {
    @Volatile internal var outcome: ShellOutcome? = null
    @Volatile internal var failure: String = ""
    internal var task: Job? = null

    val finished: Boolean get() = outcome != null || failure.isNotEmpty()

    fun render(withOutput: Boolean): String {
        val head = "job ${job.id} — ${if (finished) "finished" else "running"} — ${job.command.take(200)}\n" +
            "started ${java.util.Date(job.startedAt)}, workdir ${job.workdir}, timeout ${job.timeoutSeconds}s"
        if (!withOutput) return head
        val out = outcome
        return when {
            out == null && failure.isEmpty() -> "$head\n(no output yet)"
            failure.isNotEmpty() -> "$head\nfailed: $failure"
            else -> {
                val text = out?.text.orEmpty()
                val code = out?.exitCode ?: 0
                val trimmed = if (text.length > 8000) text.takeLast(8000) else text
                "$head\nexit code $code${if (out?.timedOut == true) " (timed out)" else ""}\n$trimmed"
            }
        }
    }
}

object HarnessJobs {

    @OptIn(ExperimentalAtomicApi::class)
    private val counter = AtomicInt(1)
    private val jobs = mutableMapOf<String, HarnessJobHandle>()

    fun start(command: String, workdir: Path, timeoutSeconds: Int): HarnessJobHandle {
        val id = "job-${counter.fetchAndIncrement()}"
        val job = HarnessJob(id, command, workdir.toString(), System.currentTimeMillis(), timeoutSeconds)
        val handle = HarnessJobHandle(job)
        jobs[id] = handle
        handle.task = HarnessRuntime.background().launch {
            try {
                val shell = HarnessRuntime.shell
                if (shell == null || !shell.isReady()) {
                    handle.failure = "no shell backend is available"
                } else {
                    handle.outcome = shell.run(command, workdir.path, timeoutSeconds, emptyMap())
                }
            } catch (e: Exception) {
                handle.failure = e.message ?: e::class.simpleName ?: "failed"
            }
        }
        return handle
    }

    fun get(id: String): HarnessJobHandle? = jobs[id]

    fun list(): List<HarnessJobHandle> = jobs.values.sortedByDescending { it.job.startedAt }

    fun running(): List<HarnessJobHandle> = jobs.values.filter { !it.finished }

    fun runningCount(): Int = jobs.values.count { !it.finished }

    fun kill(id: String): Boolean {
        val handle = jobs[id] ?: return false
        handle.task?.cancel()
        handle.failure = if (handle.failure.isEmpty()) "cancelled" else handle.failure
        return true
    }

    fun clearFinished() {
        jobs.entries.removeIf { it.value.finished }
    }

    suspend fun waitFor(handle: HarnessJobHandle, seconds: Int) {
        val deadline = System.currentTimeMillis() + seconds.coerceIn(0, 600) * 1000L
        while (!handle.finished && System.currentTimeMillis() < deadline) delay(250)
    }
}

object TerminalTools : HarnessGroupTools {

    override val group = HarnessGroup.TERMINAL

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "terminal_open_session",
            group = group,
            permission = HarnessPermission.EXECUTE,
            requires = HarnessRuntime.CAP_SHELL,
            description = "Open a new interactive PTY terminal session (a tab). Returns the session id, state, and terminal backend description. Fails gracefully if the terminal backend is not available.",
            params = listOf(HarnessSchema.text("workdir", "Optional starting directory", false))
        ),
        HarnessTool(
            name = "terminal_write",
            group = group,
            permission = HarnessPermission.EXECUTE,
            requires = HarnessRuntime.CAP_SHELL,
            description = "Write input to an interactive terminal session's stdin. Optionally wait to capture output.",
            params = listOf(
                HarnessSchema.number("session_id", "The session id (tab id)"),
                HarnessSchema.text("input", "Text to append to stdin"),
                HarnessSchema.number("wait_ms", "How many milliseconds to wait for output before replying (default 0, max 120000)", false)
            )
        ),
        HarnessTool(
            name = "terminal_read",
            group = group,
            permission = HarnessPermission.READ,
            requires = HarnessRuntime.CAP_SHELL,
            description = "Read the transcript of an interactive terminal session. Optionally block until it exits or time expires.",
            params = listOf(
                HarnessSchema.number("session_id", "The session id (tab id)"),
                HarnessSchema.number("wait_ms", "How many milliseconds to block (max 120000)", false),
                HarnessSchema.number("tail_chars", "How many characters of the transcript tail to return (default 8000)", false)
            )
        ),
        HarnessTool(
            name = "terminal_close_session",
            group = group,
            permission = HarnessPermission.EXECUTE,
            requires = HarnessRuntime.CAP_SHELL,
            description = "Close an interactive terminal session.",
            params = listOf(HarnessSchema.number("session_id", "The session id (tab id)"))
        ),
        HarnessTool(
            name = "terminal_list_sessions",
            group = group,
            permission = HarnessPermission.READ,
            requires = HarnessRuntime.CAP_SHELL,
            description = "List all active interactive terminal sessions."
        ),
        HarnessTool(
            name = "run_command",
            group = group,
            permission = HarnessPermission.EXECUTE,
            description = "Run a shell command and wait for it. Arguments: command, workdir (default the workspace), " +
                "timeout (seconds, default 300), env (optional object of extra environment variables). Returns stdout, " +
                "stderr and the exit code. Use start_job instead for anything that runs longer than a few minutes.",
            params = listOf(
                HarnessSchema.text("command", "Shell command to run"),
                HarnessSchema.text("workdir", "Directory to run in", false),
                HarnessSchema.number("timeout", "Seconds before giving up", false),
                HarnessSchema.json("env", "Extra environment variables", false)
            )
        ),
        HarnessTool(
            name = "start_job",
            group = group,
            permission = HarnessPermission.EXECUTE,
            description = "Start a long-running command in the background and return a job id immediately. Poll it with " +
                "job_output and stop it with job_kill. Use it for builds, test suites, servers and big downloads.",
            params = listOf(
                HarnessSchema.text("command", "Shell command to run"),
                HarnessSchema.text("workdir", "Directory to run in", false),
                HarnessSchema.number("timeout", "Seconds before giving up", false)
            )
        ),
        HarnessTool(
            name = "job_output",
            group = group,
            permission = HarnessPermission.READ,
            description = "Read a background job. Pass wait_seconds to block until it finishes or the wait expires. " +
                "Returns the tail of stdout and stderr and the exit code.",
            params = listOf(
                HarnessSchema.text("job_id", "Job id from start_job"),
                HarnessSchema.number("wait_seconds", "How long to wait before answering", false)
            )
        ),
        HarnessTool(
            name = "job_kill",
            group = group,
            permission = HarnessPermission.EXECUTE,
            description = "Stop a background job. Arguments: job_id.",
            params = listOf(HarnessSchema.text("job_id", "Job id from start_job"))
        ),
        HarnessTool(
            name = "jobs_list",
            group = group,
            permission = HarnessPermission.READ,
            description = "List the background jobs started in this session with their state and command."
        ),
        HarnessTool(
            name = "which_tool",
            group = group,
            permission = HarnessPermission.READ,
            description = "Check whether a command exists on this machine and print its version, for example git, " +
                "python3, node, soffice, pandoc, ffmpeg, tesseract, rg, sqlite3. Arguments: name (one or more, " +
                "space separated).",
            params = listOf(HarnessSchema.text("name", "Command name to look for"))
        ),
        HarnessTool(
            name = "environment_info",
            group = group,
            permission = HarnessPermission.READ,
            description = "Describe the execution environment: operating system, working directory, shell backend, " +
                "whether plugins provide extra tools, and the PATH entries that matter."
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JsonObject): ToolExecResult? = when (name) {
        "terminal_open_session" -> terminalOpenSession(ctx, args)
        "terminal_write" -> terminalWrite(ctx, args)
        "terminal_read" -> terminalRead(ctx, args)
        "terminal_close_session" -> terminalCloseSession(ctx, args)
        "terminal_list_sessions" -> terminalListSessions(ctx)
        "run_command" -> runCommand(ctx, args)
        "start_job" -> startJob(ctx, args)
        "job_output" -> jobOutput(ctx, args)
        "job_kill" -> jobKill(ctx, args)
        "jobs_list" -> jobsList()
        "which_tool" -> whichTool(ctx, args)
        "environment_info" -> environmentInfo(ctx)
        else -> null
    }


    private fun terminalOpenSession(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        if (!TerminalSessions.manager.isAvailable()) {
            return ToolExecResult("The terminal backend is not available on this platform yet.", success = false)
        }
        val workdir = workdirOf(ctx, args)
        return try {
            val request = PtyStartRequest(workdir = workdir)
            val tab = TerminalSessions.manager.createSession(request, "助手")
            com.lucent.app.TerminalState.terminalOpen = true
            ToolExecResult("Opened session ${tab.id}. State: ${tab.value.state}. Backend: ${TerminalSessions.manager.describe()}")
        } catch (e: Exception) {
            ToolExecResult("Failed to open session: ${e.message}", success = false)
        }
    }

    private suspend fun terminalWrite(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        if (!TerminalSessions.manager.isAvailable()) {
            return ToolExecResult("The terminal backend is not available on this platform yet.", success = false)
        }
        val id = args["session_id"]?.jsonPrimitive?.longOrNull ?: -1L
        val input = args["input"]?.jsonPrimitive?.content ?: ""
        val waitMs = (args["wait_ms"]?.jsonPrimitive?.intOrNull ?: 0).coerceIn(0, 120000).toLong()

        val tab = TerminalSessions.manager.find(id) ?: return ToolExecResult("No terminal session found with id $id", success = false)
        if (tab.value.state != PtySessionState.RUNNING) {
            return ToolExecResult("Session $id is not running (state: ${tab.value.state}).", success = false)
        }

        val ok = tab.value.write(input)
        if (!ok) return ToolExecResult("Failed to write to session $id.", success = false)

        if (waitMs > 0) {
            val deadline = System.currentTimeMillis() + waitMs
            while (System.currentTimeMillis() < deadline && tab.value.state == PtySessionState.RUNNING) {
                kotlinx.coroutines.delay(100)
            }
        }

        val state = tab.value.state
        val text = tab.value.transcript()
        val tail = if (text.length > 8000) text.takeLast(8000) else text
        return ToolExecResult("Wrote to session $id. Current state: $state.\nTranscript tail:\n${ctx.limit(tail)}")
    }

    private suspend fun terminalRead(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        if (!TerminalSessions.manager.isAvailable()) {
            return ToolExecResult("The terminal backend is not available on this platform yet.", success = false)
        }
        val id = args["session_id"]?.jsonPrimitive?.longOrNull ?: -1L
        val waitMs = (args["wait_ms"]?.jsonPrimitive?.intOrNull ?: 0).coerceIn(0, 120000).toLong()
        val tailChars = (args["tail_chars"]?.jsonPrimitive?.intOrNull ?: 8000).coerceAtLeast(1)

        val tab = TerminalSessions.manager.find(id) ?: return ToolExecResult("No terminal session found with id $id", success = false)

        if (waitMs > 0) {
            val deadline = System.currentTimeMillis() + waitMs
            while (System.currentTimeMillis() < deadline && tab.value.state == PtySessionState.RUNNING) {
                kotlinx.coroutines.delay(100)
            }
        }

        val state = tab.value.state
        val exitCode = tab.value.exitCode
        val text = tab.value.transcript()
        val tail = if (text.length > tailChars) text.takeLast(tailChars) else text

        return ToolExecResult("Session $id state: $state (exit code: $exitCode)\nTranscript tail:\n${ctx.limit(tail)}")
    }

    private fun terminalCloseSession(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        if (!TerminalSessions.manager.isAvailable()) {
            return ToolExecResult("The terminal backend is not available on this platform yet.", success = false)
        }
        val id = args["session_id"]?.jsonPrimitive?.longOrNull ?: -1L
        val ok = TerminalSessions.manager.closeSession(id)
        return if (ok) ToolExecResult("Closed session $id.") else ToolExecResult("No terminal session found with id $id", success = false)
    }

    private fun terminalListSessions(ctx: HarnessCtx): ToolExecResult {
        if (!TerminalSessions.manager.isAvailable()) {
            return ToolExecResult("The terminal backend is not available on this platform yet.", success = false)
        }
        val sessions = TerminalSessions.manager.snapshot()
        if (sessions.isEmpty()) return ToolExecResult("No active terminal sessions.")

        val sb = java.lang.StringBuilder()
        for (tab in sessions) {
            sb.append("Session ${tab.id} (tab ${tab.number}): state=${tab.value.state}, exitCode=${tab.value.exitCode}\n")
        }
        return ToolExecResult(sb.toString().trimEnd())
    }

    private fun workdirOf(ctx: HarnessCtx, args: JsonObject): Path {
        val raw = args["workdir"]?.jsonPrimitive?.content ?: ""
        if (raw.isBlank()) return HarnessRuntime.workspacePath().toPath()
        val dir = try {
            Workspace.resolveFile(raw).absolutePath.toPath()
        } catch (e: HarnessError) {
            HarnessRuntime.workspacePath().toPath()
        }
        if (!FileSystem.SYSTEM.exists(dir)) FileSystem.SYSTEM.createDirectories(dir)
        return dir
    }

    private fun timeoutOf(ctx: HarnessCtx, args: JsonObject): Int {
        val requested = args["timeout"]?.jsonPrimitive?.intOrNull ?: ctx.config.timeoutSeconds
        return requested.coerceIn(5, 3600)
    }

    private suspend fun runCommand(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val command = args["command"]?.jsonPrimitive?.content ?: ""
        if (command.isBlank()) return ToolExecResult("There is no command to run.", success = false)
        if (!HarnessRuntime.shellReady()) {
            return ToolExecResult(noShellMessage(ctx), success = false)
        }
        val dir = workdirOf(ctx, args)
        val timeout = timeoutOf(ctx, args)
        val env = mutableMapOf<String, String>()
        val envJson = args["env"]?.jsonObject
        if (envJson != null) {
            val keys = envJson.keys.iterator()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) env[key] = envJson[key]?.jsonPrimitive?.content ?: ""
            }
        }
        val shell = HarnessRuntime.shell
        val outcome = shell?.run(command, dir.toString(), timeout, env)
            ?: ShellOutcome(false, "", "no shell backend is available", -1)
        val text = ctx.limit(outcome.text)
        val body = buildString {
            append("$ ")
            append(command.take(400))
            append('\n')
            append(text.ifBlank { "(no output)" })
            append("\n[exit code: ").append(outcome.exitCode).append(']')
            if (outcome.timedOut) append(" [timed out after ${timeout}s]")
            append(" [").append(shell?.id ?: "none").append(']')
        }
        return ToolExecResult(body, success = shell != null)
    }

    private fun startJob(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val command = args["command"]?.jsonPrimitive?.content ?: ""
        if (command.isBlank()) return ToolExecResult("There is no command to run.", success = false)
        if (!HarnessRuntime.shellReady()) return ToolExecResult(noShellMessage(ctx), success = false)
        val handle = HarnessJobs.start(command, workdirOf(ctx, args), timeoutOf(ctx, args))
        return ToolExecResult(
            "Started ${handle.job.id}. Poll it with job_output(job_id=\"${handle.job.id}\") or stop it with job_kill."
        )
    }

    private suspend fun jobOutput(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val id = args["job_id"]?.jsonPrimitive?.content ?: ""
        val handle = HarnessJobs.get(id) ?: return ToolExecResult("No job called $id.", success = false)
        val wait = args["wait_seconds"]?.jsonPrimitive?.intOrNull ?: 0
        if (wait > 0) HarnessJobs.waitFor(handle, wait)
        return ToolExecResult(ctx.limit(handle.render(withOutput = true)), success = handle.finished && handle.failure.isEmpty())
    }

    private fun jobKill(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val id = args["job_id"]?.jsonPrimitive?.content ?: ""
        if (id.isBlank()) return ToolExecResult("Which job?", success = false)
        val ok = HarnessJobs.kill(id)
        return if (ok) ToolExecResult("Asked ${id} to stop.") else ToolExecResult("No job called $id.", success = false)
    }

    private fun jobsList(): ToolExecResult {
        val all = HarnessJobs.list()
        if (all.isEmpty()) return ToolExecResult("No background jobs have been started.")
        return ToolExecResult(all.joinToString("\n") { it.render(withOutput = false) })
    }

    private suspend fun whichTool(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val names = (args["name"]?.jsonPrimitive?.content ?: "").split(Regex("[,\\s]+")).filter { it.isNotBlank() }
        if (names.isEmpty()) return ToolExecResult("Name a command to look for.", success = false)
        if (!HarnessRuntime.shellReady()) {
            val installed = HarnessRuntime.config().installedPlugins()
            return ToolExecResult(
                "No shell backend, so commands cannot be probed. Installed plugins: " +
                    (if (installed.isEmpty()) "none" else installed.joinToString(", ")),
                success = false
            )
        }
        val sb = StringBuilder()
        names.forEach { name ->
            val safe = name.filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
            if (safe.isBlank()) return@forEach
            val outcome = HarnessRuntime.runShell(
                "command -v $safe >/dev/null 2>&1 && { echo \"$safe: $(command -v $safe)\"; $safe --version 2>&1 | head -n 2; } || echo \"$safe: not found\"",
                HarnessRuntime.workspacePath(),
                30
            )
            sb.append(outcome.text.ifBlank { "$safe: no answer" }).append('\n')
        }
        return ToolExecResult(sb.toString().trimEnd())
    }

    private fun environmentInfo(ctx: HarnessCtx): ToolExecResult {
        val shell = HarnessRuntime.shell
        val sb = StringBuilder()
        sb.append("Platform: ${if (ctx.android) "Android" else System.getProperty("os.name") + " " + System.getProperty("os.version")}\n")
        sb.append("Workspace: ${HarnessRuntime.workspacePath()}\n")
        sb.append("Shell backend: ${shell?.id ?: "none"} — ${shell?.describe() ?: "not installed"}\n")
        sb.append("Plugin host: ${HarnessRuntime.pluginHost?.id ?: "none"} — ${HarnessRuntime.pluginHost?.describe() ?: "not installed"}\n")
        val plugins = ctx.config.installedPlugins()
        sb.append("Plugins installed: ${if (plugins.isEmpty()) "none" else plugins.joinToString(", ")}\n")
        val host = HarnessRuntime.host
        sb.append("Device capabilities: ${host?.capabilities()?.joinToString(", ") ?: "none"}\n")
        if (!ctx.android) {
            sb.append("User home: ${System.getProperty("user.home")}\n")
            sb.append("Java: ${System.getProperty("java.version")}\n")
        }
        return ToolExecResult(sb.toString())
    }

    private fun noShellMessage(ctx: HarnessCtx): String = if (ctx.android) {
        "No shell is available. Set up the built-in environment from the toolbox setup guide, or enable the " +
            "privileged shell in Settings → Advanced, then try again."
    } else {
        "No shell backend is available on this machine."
    }
}
