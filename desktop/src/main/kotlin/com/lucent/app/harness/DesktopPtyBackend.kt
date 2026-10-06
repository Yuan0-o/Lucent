package com.lucent.app.harness

import com.lucent.app.harness.terminal.PtyBackend
import com.lucent.app.harness.terminal.PtyProcess
import com.lucent.app.harness.terminal.PtyStartRequest
import java.io.File
import okio.Sink
import okio.Source
import okio.sink
import okio.source

object DesktopPtyBackend : PtyBackend {

    override fun isReady(): Boolean = true

    override fun describe(): String = "desktop shell terminal"

    override fun start(request: PtyStartRequest): PtyProcess {
        val windows = System.getProperty("os.name", "").lowercase().contains("win")
        val command = if (windows) {
            listOf("powershell.exe", "-NoLogo")
        } else {
            listOf("/bin/bash", "-li")
        }
        val pb = ProcessBuilder(command)
        val dir = request.workdir?.toFile() ?: File(HarnessRuntime.workspacePath())
        if (dir.exists()) pb.directory(dir)
        pb.environment()["TERM"] = "xterm-256color"
        request.env.forEach { (key, value) -> pb.environment()[key] = value }
        pb.redirectErrorStream(true)
        return DesktopPtyProcess(pb.start())
    }
}

private class DesktopPtyProcess(private val process: Process) : PtyProcess {
    override val input: Sink get() = process.outputStream.sink()
    override val output: Source get() = process.inputStream.source()
    override fun isAlive(): Boolean = process.isAlive
    override fun waitFor(): Int = process.waitFor()
    override fun destroy() = process.destroy()
    override fun destroyForcibly() {
        process.destroyForcibly()
    }
    override fun resize(cols: Int, rows: Int) = Unit
}
