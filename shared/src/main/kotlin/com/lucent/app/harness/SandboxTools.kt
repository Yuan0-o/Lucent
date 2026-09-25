package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import org.json.JSONObject
import java.io.File

object SandboxTools : HarnessGroupTools {

    override val group = HarnessGroup.SANDBOX

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "sandbox_status",
            group = group,
            permission = HarnessPermission.READ,
            description = "Report how commands can be run here: the privileged shell, the ordinary shell, Docker, a " +
                "Linux userland from the plugins, and which route the settings select."
        ),
        HarnessTool(
            name = "sandbox_run",
            group = group,
            permission = HarnessPermission.EXECUTE,
            description = "Run a command inside the safest available sandbox. With Docker it runs in a container with " +
                "the workspace mounted at /work; with a Linux userland plugin it runs inside that; otherwise it runs " +
                "directly with a timeout. Arguments: command, workdir, timeout, image, memory, cpus, network.",
            params = listOf(
                HarnessSchema.text("command", "Command to run"),
                HarnessSchema.text("workdir", "Directory to run in", false),
                HarnessSchema.number("timeout", "Seconds before giving up", false),
                HarnessSchema.text("image", "Docker image, default ubuntu:24.04", false),
                HarnessSchema.text("memory", "Memory limit such as 2g", false),
                HarnessSchema.text("cpus", "CPU limit such as 1.5", false),
                HarnessSchema.flag("network", "Allow network access in the sandbox", false)
            )
        ),
        HarnessTool(
            name = "sandbox_limits",
            group = group,
            permission = HarnessPermission.READ,
            description = "Show the limits that apply to commands right now: timeout, output cap, the chosen route and " +
                "which engines were found."
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = when (name) {
        "sandbox_status" -> ToolExecResult(status(ctx))
        "sandbox_limits" -> ToolExecResult(limits(ctx))
        "sandbox_run" -> run(ctx, args)
        else -> null
    }

    private fun dockerVersion(): String {
        if (!HarnessRuntime.shellReady()) return ""
        val outcome = HarnessRuntime.runShell("docker version --format '{{.Server.Version}}'", null, 25)
        return if (outcome.ok) outcome.text.trim() else ""
    }

    private fun prootVersion(): String {
        if (!HarnessRuntime.shellReady()) return ""
        val outcome = HarnessRuntime.runShell("command -v proot >/dev/null 2>&1 && proot --version 2>&1 | head -n 1", null, 20)
        return if (outcome.ok && outcome.text.isNotBlank() && !outcome.text.contains("not found")) outcome.text.trim() else ""
    }

    private fun route(ctx: HarnessCtx, docker: String, proot: String): String {
        val mode = ctx.config.sandboxMode.lowercase()
        return when {
            mode == "none" -> "none"
            mode == "docker" -> if (docker.isNotEmpty()) "docker" else "direct"
            mode == "proot" -> if (proot.isNotEmpty() || ctx.config.pluginInstalled("ubuntu")) "proot" else "direct"
            docker.isNotEmpty() -> "docker"
            proot.isNotEmpty() || ctx.config.pluginInstalled("ubuntu") -> "proot"
            else -> "direct"
        }
    }

    private fun status(ctx: HarnessCtx): String {
        val docker = dockerVersion()
        val proot = prootVersion()
        val capabilities = HarnessRuntime.capabilities()
        return buildString {
            append("Sandbox mode: ").append(ctx.config.sandboxMode).append('\n')
            append("Route chosen: ").append(route(ctx, docker, proot)).append('\n')
            append("Privileged shell: ").append(if (capabilities.contains(HarnessRuntime.CAP_PRIVILEGED)) "available" else "no").append('\n')
            append("Ordinary shell: ").append(if (capabilities.contains(HarnessRuntime.CAP_SHELL)) "available" else "no").append('\n')
            append("Docker: ").append(docker.ifEmpty { "not found" }).append('\n')
            append("proot: ").append(proot.ifEmpty { "not found" }).append('\n')
            append("Plugins installed: ")
            val plugins = ctx.config.installedPlugins()
            append(if (plugins.isEmpty()) "none" else plugins.joinToString(", ")).append('\n')
            append("Timeout: ").append(ctx.config.timeoutSeconds).append("s, output cap ")
            append(ctx.config.maxOutputChars).append(" characters")
        }
    }

    private fun limits(ctx: HarnessCtx): String {
        val docker = dockerVersion()
        val proot = prootVersion()
        return buildString {
            append("timeout=").append(ctx.config.timeoutSeconds).append("s\n")
            append("max output=").append(ctx.config.maxOutputChars).append(" chars\n")
            append("route=").append(route(ctx, docker, proot)).append('\n')
            append("docker=").append(docker.ifEmpty { "no" }).append('\n')
            append("proot=").append(proot.ifEmpty { "no" }).append('\n')
            append("workspace=").append(HarnessRuntime.workspace().path)
        }
    }

    private suspend fun run(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val command = args.optString("command", "")
        if (command.isBlank()) return ToolExecResult("There is no command to run.", success = false)
        if (!HarnessRuntime.shellReady()) {
            return ToolExecResult("No shell is available, so nothing can be sandboxed.", success = false)
        }
        val docker = dockerVersion()
        val proot = prootVersion()
        val route = route(ctx, docker, proot)
        val timeout = args.optInt("timeout", ctx.config.timeoutSeconds).coerceIn(5, 1800)
        val workdir = if (args.optString("workdir", "").isBlank()) HarnessRuntime.workspace() else try {
            Workspace.resolve(ctx, args.optString("workdir", ""))
        } catch (e: HarnessError) {
            return ToolExecResult(e.message ?: "That working directory cannot be used", success = false)
        }
        val quoted = command.replace("'", "'\\''")
        val started = System.currentTimeMillis()
        val outcome = when (route) {
            "docker" -> {
                val image = args.optString("image", "").ifBlank { "ubuntu:24.04" }
                val flags = StringBuilder()
                if (!args.optBoolean("network", false)) flags.append(" --network none")
                val memory = args.optString("memory", "")
                if (memory.isNotBlank()) flags.append(" --memory ").append(memory.filter { it.isLetterOrDigit() })
                val cpus = args.optString("cpus", "")
                if (cpus.isNotBlank()) flags.append(" --cpus ").append(cpus.filter { it.isDigit() || it == '.' })
                val line = "docker run --rm -i" + flags +
                    " -v '" + workdir.path.replace("'", "'\\''") + ":/work' -w /work " + image +
                    " sh -lc '" + quoted + "'"
                HarnessRuntime.runShell(line, workdir, timeout)
            }
            "proot" -> {
                val rootfs = File(HarnessRuntime.filesDir(), "plugins/ubuntu/rootfs")
                val line = "proot -0 -r '" + rootfs.path.replace("'", "'\\''") + "' -w /work -b '" +
                    workdir.path.replace("'", "'\\''") + ":/work' " +
                    "/usr/bin/env -i HOME=/root PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin " +
                    "/bin/sh -lc '" + quoted + "'"
                HarnessRuntime.runShell(line, workdir, timeout)
            }
            else -> HarnessRuntime.runShell(command, workdir, timeout)
        }
        val elapsed = System.currentTimeMillis() - started
        val body = buildString {
            append("route: ").append(route).append(" (").append(elapsed).append(" ms)\n")
            append("exit code ").append(outcome.exitCode)
            if (outcome.timedOut) append(" (timed out after ${timeout}s)")
            append('\n')
            append(outcome.text.ifBlank { "(no output)" })
        }
        return ToolExecResult(ctx.limit(body), success = outcome.ok)
    }
}
