package com.lucent.app.harness
import com.lucent.app.platform.filesDir

import android.content.Context
import com.lucent.app.harness.terminal.PtyBackend
import com.lucent.app.harness.terminal.PtyProcess
import com.lucent.app.harness.terminal.PtyStartRequest
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class AndroidPtyBackend(private val context: Context) : PtyBackend {

    override fun isReady(): Boolean =
        BuiltinShell.builtinAvailable(context) && File(BuiltinShell.rootfsDir(context), "bin/bash").exists()

    override fun describe(): String = when {
        !BuiltinShell.builtinAvailable(context) -> "built-in runtime is not available on this device"
        !File(BuiltinShell.rootfsDir(context), "bin/bash").exists() ->
            "built-in environment is not set up yet - open the setup guide to prepare it"
        else -> "built-in proot terminal"
    }

    override fun start(request: PtyStartRequest): PtyProcess {
        check(isReady()) { describe() }

        val rootfs = BuiltinShell.rootfsDir(context)
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        val proot = File(libDir, "libproot.so")
        val tmpDir = File(context.filesDir, "proot-tmp").apply { mkdirs() }
        val workspace = request.workdir ?: File(HarnessRuntime.workspacePath())

        val args = mutableListOf<String>()
        args.add(proot.absolutePath)
        args.add("--kill-on-exit")
        args.add("-0")
        args.add("-r")
        args.add(rootfs.absolutePath)

        if (workspace.exists()) {
            args.add("--cwd=/workspace")
            args.add("-b")
            args.add("${workspace.absolutePath}:/workspace")
        }

        for (bindPath in listOf("/dev", "/proc", "/sys")) {
            if (File(bindPath).exists()) {
                args.add("-b")
                args.add(bindPath)
            }
        }

        if (File(rootfs, "usr/bin/script").exists()) {
            val cols = request.cols.coerceIn(20, 500)
            val rows = request.rows.coerceIn(5, 200)
            args.add("/usr/bin/script")
            args.add("-q")
            args.add("-f")
            args.add("-e")
            args.add("-c")
            args.add("stty cols $cols rows $rows 2>/dev/null; exec /bin/bash -l")
            args.add("/dev/null")
        } else {
            args.add("/bin/bash")
            args.add("-l")
        }

        val pb = ProcessBuilder(args)
        pb.environment()["PROOT_LOADER"] = File(libDir, "libprootloader.so").absolutePath
        val nativeLibDir = File(context.filesDir, "native-lib")
        pb.environment()["LD_LIBRARY_PATH"] = "${nativeLibDir.absolutePath}:${libDir.absolutePath}"
        pb.environment()["PROOT_TMP_DIR"] = tmpDir.absolutePath
        pb.environment()["HOME"] = "/root"
        pb.environment()["USER"] = "root"
        pb.environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        pb.environment()["TERM"] = "xterm-256color"
        pb.environment()["DEBIAN_FRONTEND"] = "noninteractive"
        pb.environment()["SSL_CERT_FILE"] = "/etc/ssl/certs/ca-certificates.crt"
        request.env.forEach { (key, value) -> pb.environment()[key] = value }
        pb.redirectErrorStream(true)

        return AndroidPtyProcess(pb.start())
    }
}

private class AndroidPtyProcess(private val process: Process) : PtyProcess {
    override val input: OutputStream get() = process.outputStream
    override val output: InputStream get() = process.inputStream
    override fun isAlive(): Boolean = process.isAlive
    override fun waitFor(): Int = process.waitFor()
    override fun destroy() = process.destroy()
    override fun destroyForcibly() {
        process.destroyForcibly()
    }
    override fun resize(cols: Int, rows: Int) = Unit
}
