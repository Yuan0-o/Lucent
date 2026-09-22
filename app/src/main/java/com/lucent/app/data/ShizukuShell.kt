package com.lucent.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.api.Shizuku

object ShizukuShell : PrivilegedShell.PrivilegedShellProvider {

    override fun isReady(): Boolean = Shizuku.ping()

    override suspend fun runCommand(command: String): PrivilegedShell.ShellResult {
        return withContext(Dispatchers.IO) {
            try {
                if (!isReady()) {
                    return@withContext PrivilegedShell.ShellResult(false, "", "Shizuku not ready - grant permission first")
                }
                val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                val exitCode = process.waitFor()
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                PrivilegedShell.ShellResult(exitCode == 0, stdout, stderr)
            } catch (t: Throwable) {
                PrivilegedShell.ShellResult(false, "", "Shizuku exec failed: ${t.message}")
            }
        }
    }
}