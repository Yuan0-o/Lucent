package com.lucent.app.harness

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object BuiltinShell {

    fun rootfsDir(context: Context): File = File(context.filesDir, "home/lucent/ubuntu/rootfs")

    fun builtinAvailable(context: Context): Boolean {
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        return File(libDir, "libproot.so").exists()
    }

    suspend fun run(
        context: Context,
        command: String,
        workspace: File,
        timeoutMs: Long,
        env: Map<String, String> = emptyMap(),
        onOutput: ((String) -> Unit)? = null
    ): ShellOutcome = withContext(Dispatchers.IO) {
        if (!builtinAvailable(context)) {
            return@withContext ShellOutcome(false, "", "built-in runtime is not available on this device", -1, false)
        }

        val rootfs = rootfsDir(context)
        if (!File(rootfs, "bin/bash").exists()) {
            return@withContext ShellOutcome(false, "", "built-in environment is not set up yet - open the setup guide to prepare it", -1, false)
        }

        ensureResolvConf(context, rootfs)

        val libDir = File(context.applicationInfo.nativeLibraryDir)
        val proot = File(libDir, "libproot.so")
        val tmpDir = File(context.filesDir, "proot-tmp").apply { mkdirs() }

        val cmdArgs = mutableListOf<String>()
        cmdArgs.add(proot.absolutePath)
        cmdArgs.add("--kill-on-exit")
        cmdArgs.add("-0")
        cmdArgs.add("--link2symlink")
        cmdArgs.add("-r")
        cmdArgs.add(rootfs.absolutePath)

        if (workspace.exists()) {
            cmdArgs.add("--cwd=/workspace")
            cmdArgs.add("-b")
            cmdArgs.add("${workspace.absolutePath}:/workspace")
        }

        for (bindPath in listOf("/dev", "/proc", "/sys")) {
            if (File(bindPath).exists()) {
                cmdArgs.add("-b")
                cmdArgs.add(bindPath)
            }
        }

        cmdArgs.add("/bin/bash")
        cmdArgs.add("-c")
        cmdArgs.add(command)

        val pb = ProcessBuilder(cmdArgs)
        pb.environment()["PROOT_LOADER"] = File(libDir, "libprootloader.so").absolutePath
        val nativeLibDir = File(context.filesDir, "native-lib")
        pb.environment()["LD_LIBRARY_PATH"] = "${nativeLibDir.absolutePath}:${libDir.absolutePath}"
        pb.environment()["PROOT_TMP_DIR"] = tmpDir.absolutePath
        pb.environment()["HOME"] = "/root"
        pb.environment()["USER"] = "root"
        pb.environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        pb.environment()["TERM"] = "xterm"
        pb.environment()["DEBIAN_FRONTEND"] = "noninteractive"
        pb.environment()["SSL_CERT_FILE"] = "/etc/ssl/certs/ca-certificates.crt"
        env.forEach { (k, v) -> pb.environment()[k] = v }

        pb.redirectErrorStream(true)
        val process = pb.start()
        val rawBytes = ByteArrayOutputStream()
        val tailer = StreamTailer()

        val result = withTimeoutOrNull(timeoutMs) {
            process.inputStream.use { stream ->
                val buffer = ByteArray(4096)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    rawBytes.write(buffer, 0, read)
                    if (onOutput != null) {
                        tailer.processNewBytes(buffer.copyOfRange(0, read), onOutput)
                    }
                }
            }
            if (onOutput != null) {
                tailer.flush(onOutput)
            }
            process.waitFor()
            Pair(process.exitValue(), rawBytes.toString(Charsets.UTF_8.name()))
        }
        if (result == null) {
            process.destroy()
            return@withContext ShellOutcome(false, "", "Command timed out after ${timeoutMs}ms", -1, true)
        }
        val (code, stdout) = result
        ShellOutcome(code == 0, stdout, if (code == 0) "" else stdout, code, false)
    }

    private fun deviceDnsServers(context: Context): List<String> = try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val network = manager?.activeNetwork
        val properties = if (network != null) manager.getLinkProperties(network) else null
        properties?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

    private fun ensureResolvConf(context: Context, rootfs: File) {
        runCatching {
            val resolv = File(rootfs, "etc/resolv.conf")
            if (java.nio.file.Files.isSymbolicLink(resolv.toPath())) {
                resolv.delete()
            }
            val current = if (resolv.isFile) resolv.readText() else ""
            if (!current.contains("nameserver")) {
                val servers = (deviceDnsServers(context) + listOf("223.5.5.5", "119.29.29.29", "8.8.8.8", "1.1.1.1"))
                    .distinct()
                    .take(4)
                resolv.parentFile?.mkdirs()
                resolv.writeText(servers.joinToString("\n") { "nameserver $it" } + "\n")
            }
        }
    }
}
