package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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

    private val counter = AtomicInteger(1)
    private val jobs = ConcurrentHashMap<String, HarnessJobHandle>()

    fun start(command: String, workdir: File, timeoutSeconds: Int): HarnessJobHandle {
        val id = "job-${counter.getAndIncrement()}"
        val job = HarnessJob(id, command, workdir.path, System.currentTimeMillis(), timeoutSeconds)
        val handle = HarnessJobHandle(job)
        jobs[id] = handle
        handle.task = HarnessRuntime.background().launch {
            try {
                val shell = HarnessRuntime.shell
                if (shell == null || !shell.isReady()) {
                    handle.failure = "no shell backend is available"
                } else {
                    handle.outcome = shell.run(command, workdir, timeoutSeconds, emptyMap())
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

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = when (name) {
        "run_command" -> runCommand(ctx, args)
        "start_job" -> startJob(ctx, args)
        "job_output" -> jobOutput(ctx, args)
        "job_kill" -> jobKill(ctx, args)
        "jobs_list" -> jobsList()
        "which_tool" -> whichTool(ctx, args)
        "environment_info" -> environmentInfo(ctx)
        else -> null
    }

    private fun workdirOf(ctx: HarnessCtx, args: JSONObject): File {
        val raw = args.optString("workdir", "")
        if (raw.isBlank()) return HarnessRuntime.workspace()
        val dir = try {
            Workspace.resolve(ctx, raw)
        } catch (e: HarnessError) {
            HarnessRuntime.workspace()
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun timeoutOf(ctx: HarnessCtx, args: JSONObject): Int {
        val requested = args.optInt("timeout", ctx.config.timeoutSeconds)
        return requested.coerceIn(5, 3600)
    }

    private suspend fun runCommand(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val command = args.optString("command", "")
        if (command.isBlank()) return ToolExecResult("There is no command to run.", success = false)
        if (!HarnessRuntime.shellReady()) {
            return ToolExecResult(noShellMessage(ctx), success = false)
        }
        val dir = workdirOf(ctx, args)
        val timeout = timeoutOf(ctx, args)
        val env = mutableMapOf<String, String>()
        val envJson = args.optJSONObject("env")
        if (envJson != null) {
            val keys = envJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) env[key] = envJson.optString(key, "")
            }
        }
        val shell = HarnessRuntime.shell
        val outcome = shell?.run(command, dir, timeout, env)
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

    private fun startJob(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val command = args.optString("command", "")
        if (command.isBlank()) return ToolExecResult("There is no command to run.", success = false)
        if (!HarnessRuntime.shellReady()) return ToolExecResult(noShellMessage(ctx), success = false)
        val handle = HarnessJobs.start(command, workdirOf(ctx, args), timeoutOf(ctx, args))
        return ToolExecResult(
            "Started ${handle.job.id}. Poll it with job_output(job_id=\"${handle.job.id}\") or stop it with job_kill."
        )
    }

    private suspend fun jobOutput(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val id = args.optString("job_id", "")
        val handle = HarnessJobs.get(id) ?: return ToolExecResult("No job called $id.", success = false)
        val wait = args.optInt("wait_seconds", 0)
        if (wait > 0) HarnessJobs.waitFor(handle, wait)
        return ToolExecResult(ctx.limit(handle.render(withOutput = true)), success = handle.finished && handle.failure.isEmpty())
    }

    private fun jobKill(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val id = args.optString("job_id", "")
        if (id.isBlank()) return ToolExecResult("Which job?", success = false)
        val ok = HarnessJobs.kill(id)
        return if (ok) ToolExecResult("Asked ${id} to stop.") else ToolExecResult("No job called $id.", success = false)
    }

    private fun jobsList(): ToolExecResult {
        val all = HarnessJobs.list()
        if (all.isEmpty()) return ToolExecResult("No background jobs have been started.")
        return ToolExecResult(all.joinToString("\n") { it.render(withOutput = false) })
    }

    private suspend fun whichTool(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val names = args.optString("name", "").split(Regex("[,\\s]+")).filter { it.isNotBlank() }
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
                HarnessRuntime.workspace(),
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
        sb.append("Workspace: ${HarnessRuntime.workspace().path}\n")
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
        "No shell is available. Install Termux and switch on its external command permission, or enable the privileged " +
            "shell in Settings → Advanced, then try again."
    } else {
        "No shell backend is available on this machine."
    }
}
