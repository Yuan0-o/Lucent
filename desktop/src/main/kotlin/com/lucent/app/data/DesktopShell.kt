package com.lucent.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DesktopShell : PrivilegedShell.PrivilegedShellProvider {

    override fun isReady(): Boolean = true

    override suspend fun runCommand(command: String): PrivilegedShell.ShellResult {
        return withContext(Dispatchers.IO) {
            try {
                val osName = System.getProperty("os.name", "").lowercase()
                val cmd = if (osName.contains("win")) {
                    ProcessBuilder("cmd", "/c", command)
                } else {
                    ProcessBuilder("sh", "-c", command)
                }
                val process = cmd.start()
                val exitCode = process.waitFor()
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                PrivilegedShell.ShellResult(exitCode == 0, stdout, stderr)
            } catch (t: Throwable) {
                PrivilegedShell.ShellResult(false, "", "Shell exec failed: ${t.message}")
            }
        }
    }

    fun requestElevation(executablePath: String): Boolean {
        return try {
            val osName = System.getProperty("os.name", "").lowercase()
            if (osName.contains("win")) {
                val process = ProcessBuilder(
                    "powershell", "-Command",
                    "Start-Process '$executablePath' -Verb RunAs"
                ).start()
                process.waitFor() == 0
            } else {
                val process = ProcessBuilder("pkexec", executablePath).start()
                process.waitFor() == 0
            }
        } catch (t: Throwable) {
            false
        }
    }
}