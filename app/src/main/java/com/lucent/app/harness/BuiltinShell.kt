package com.lucent.app.harness

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BuiltinShell {

    fun builtinAvailable(context: Context): Boolean {
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        return File(libDir, "libproot.so").exists() || File(libDir, "libproot-loader.so").exists()
    }

    private fun prootBinary(context: Context): File? {
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        val proot = File(libDir, "libproot.so")
        if (proot.exists()) return proot
        val loader = File(libDir, "libproot-loader.so")
        if (loader.exists()) return loader
        return null
    }

    suspend fun run(
        context: Context,
        command: String,
        workspace: File,
        timeoutMs: Long,
        env: Map<String, String> = emptyMap()
    ): ShellOutcome = withContext(Dispatchers.IO) {
        val binary = prootBinary(context)
            ?: return@withContext ShellOutcome(false, "", "built-in runtime is not bundled in this build", -1, false)

        val homeDir = File(context.filesDir, "home").apply { mkdirs() }
        val tmpDir = File(context.filesDir, "tmp").apply { mkdirs() }
        val rootfs = File(homeDir, "lucent/ubuntu/rootfs")
        val ubuntuInstalled = HarnessRuntime.config().pluginInstalled("ubuntu")

        val cmdArgs = mutableListOf<String>()
        cmdArgs.add(binary.absolutePath)

        if (ubuntuInstalled && rootfs.isDirectory) {
            cmdArgs.add("-0")
            cmdArgs.add("-r")
            cmdArgs.add(rootfs.absolutePath)
        } else {
            cmdArgs.add("-R")
            cmdArgs.add("/")
        }

        cmdArgs.add("-b")
        cmdArgs.add("/dev")
        cmdArgs.add("-b")
        cmdArgs.add("/proc")
        cmdArgs.add("-b")
        cmdArgs.add("/sys")
        cmdArgs.add("-w")
        cmdArgs.add(workspace.absolutePath)
        
        if (ubuntuInstalled && rootfs.isDirectory) {
            cmdArgs.add("/bin/sh")
        } else {
            cmdArgs.add("/system/bin/sh")
        }
        cmdArgs.add("-c")
        cmdArgs.add(command)

        val pb = ProcessBuilder(cmdArgs)
        pb.environment()["HOME"] = homeDir.absolutePath
        pb.environment()["PROOT_TMP_DIR"] = tmpDir.absolutePath
        pb.environment()["TMPDIR"] = tmpDir.absolutePath
        pb.environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin:/system/xbin"
        pb.environment()["TERM"] = "xterm"
        env.forEach { (k, v) -> pb.environment()[k] = v }

        pb.redirectErrorStream(true)
        val process = pb.start()
        val result = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            val out = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            Pair(process.exitValue(), out)
        }
        if (result == null) {
            process.destroy()
            return@withContext ShellOutcome(false, "", "Command timed out after ${timeoutMs}ms", -1, true)
        }
        val (code, stdout) = result
        ShellOutcome(code == 0, stdout, "", code, false)
    }
}
