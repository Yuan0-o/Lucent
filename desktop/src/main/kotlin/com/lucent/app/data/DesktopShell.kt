package com.lucent.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DesktopShell : PrivilegedShell.PrivilegedShellProvider {

    var exitRequested by mutableStateOf(false)
        private set

    override fun isReady(): Boolean = true

    fun isWindows(): Boolean = System.getProperty("os.name", "").lowercase().contains("win")

    fun isElevated(): Boolean = try {
        if (isWindows()) {
            val process = ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent())." +
                    "IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)"
            ).start()
            val text = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            text.equals("true", ignoreCase = true)
        } else {
            val process = ProcessBuilder("id", "-u").start()
            val text = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            text == "0"
        }
    } catch (t: Throwable) {
        false
    }

    fun openUrl(url: String): Boolean = try {
        if (java.awt.Desktop.isDesktopSupported()) {
            val desktop = java.awt.Desktop.getDesktop()
            if (url.startsWith("mailto:")) {
                desktop.mail(java.net.URI(url))
            } else {
                desktop.browse(java.net.URI(url))
            }
            true
        } else {
            val command = if (isWindows()) {
                arrayOf("rundll32", "url.dll,FileProtocolHandler", url)
            } else {
                arrayOf("xdg-open", url)
            }
            ProcessBuilder(*command).start()
            true
        }
    } catch (t: Throwable) {
        false
    }

    fun requestElevation(): Boolean {
        if (isElevated()) return true
        val executable = System.getProperty("jpackage.app-path")
            ?: ProcessHandle.current().info().command().orElse(null)
            ?: return false
        return try {
            val started = if (isWindows()) {
                ProcessBuilder(
                    "powershell", "-NoProfile", "-Command",
                    "Start-Process -FilePath '$executable' -ArgumentList '--elevated' -Verb RunAs"
                ).start().waitFor() == 0
            } else {
                ProcessBuilder("pkexec", executable).start().waitFor() == 0
            }
            if (started) {
                exitRequested = true
            }
            started
        } catch (t: Throwable) {
            false
        }
    }

    override suspend fun runCommand(command: String): PrivilegedShell.ShellResult {
        return withContext(Dispatchers.IO) {
            try {
                val cmd = if (isWindows()) {
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

    override suspend fun installPackage(packagePath: String, sizeBytes: Long): PrivilegedShell.ShellResult =
        withContext(Dispatchers.IO) {
            val installer = File(packagePath)
            if (!installer.isFile) {
                return@withContext PrivilegedShell.ShellResult(
                    false, "", "The downloaded installer is missing: $packagePath"
                )
            }
            try {
                val started = if (isWindows()) {
                    val quote = "'" + installer.absolutePath.replace("'", "''") + "'"
                    val elevate = if (isElevated()) "" else " -Verb RunAs"
                    ProcessBuilder(
                        "powershell", "-NoProfile", "-Command",
                        "Start-Process -FilePath $quote$elevate"
                    ).start().waitFor() == 0
                } else {
                    installer.setExecutable(true)
                    val process = ProcessBuilder("pkexec", installer.absolutePath).start()
                    process.waitFor() == 0
                }
                PrivilegedShell.ShellResult(started, "", if (started) "" else "The installer was not started")
            } catch (t: Throwable) {
                PrivilegedShell.ShellResult(false, "", "The installer was not started: ${t.message}")
            }
        }
}
