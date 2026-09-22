package com.lucent.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

object ShizukuShell : PrivilegedShell.PrivilegedShellProvider {

    const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"

    const val DOWNLOAD_PAGE = "https://shizuku.rikka.app/download/"

    const val PERMISSION_REQUEST_CODE = 6021

    private const val MAX_CAPTURE_BYTES = 256 * 1024

    private var permissionCallback: ((Boolean) -> Unit)? = null

    private var binderCallback: ((Boolean) -> Unit)? = null

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        permissionCallback?.invoke(grantResult == PackageManager.PERMISSION_GRANTED)
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        binderCallback?.invoke(true)
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        binderCallback?.invoke(false)
    }

    fun isInstalled(context: Context?): Boolean {
        if (context == null) return false
        return try {
            context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun isServiceRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        isServiceRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    override fun isReady(): Boolean = hasPermission()

    fun privilegeUid(): Int = try {
        if (isServiceRunning()) Shizuku.getUid() else -1
    } catch (t: Throwable) {
        -1
    }

    fun observePermission(callback: ((Boolean) -> Unit)?) {
        if (callback == null) {
            if (permissionCallback != null) {
                runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
            }
            permissionCallback = null
            return
        }
        if (permissionCallback == null) {
            runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
        }
        permissionCallback = callback
    }

    fun observeBinder(callback: ((Boolean) -> Unit)?) {
        if (callback == null) {
            if (binderCallback != null) {
                runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
                runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
            }
            binderCallback = null
            return
        }
        if (binderCallback == null) {
            runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceivedListener) }
            runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }
        }
        binderCallback = callback
    }

    fun requestPermission(context: Context?): Boolean {
        if (hasPermission()) return true
        if (!isServiceRunning()) {
            return openManager(context) || openDownloadPage(context)
        }
        return try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun openManager(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun openDownloadPage(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_PAGE))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }

    override suspend fun runCommand(command: String): PrivilegedShell.ShellResult =
        withContext(Dispatchers.IO) { runProcess(arrayOf("sh", "-c", command), null) }

    override suspend fun installPackage(packagePath: String, sizeBytes: Long): PrivilegedShell.ShellResult =
        withContext(Dispatchers.IO) {
            val file = File(packagePath)
            if (!file.isFile) {
                return@withContext PrivilegedShell.ShellResult(false, "", "APK not found: $packagePath")
            }
            val size = if (sizeBytes > 0) sizeBytes else file.length()
            val streamed = runProcess(
                arrayOf("pm", "install", "--user", "0", "-r", "-t", "-S", size.toString()),
                file
            )
            if (streamed.success) streamed else installThroughTempFile(file)
        }

    private fun installThroughTempFile(file: File): PrivilegedShell.ShellResult {
        val remote = "/data/local/tmp/lucent-update-${System.currentTimeMillis()}.apk"
        val pushed = runProcess(arrayOf("sh", "-c", "cat > $remote"), file)
        if (!pushed.success) return pushed
        val installed = runProcess(arrayOf("pm", "install", "--user", "0", "-r", "-t", remote), null)
        runProcess(arrayOf("rm", "-f", remote), null)
        return installed
    }

    private fun service(): IShizukuService? = try {
        if (!isServiceRunning()) null
        else Shizuku.getBinder()?.let { IShizukuService.Stub.asInterface(it) }
    } catch (t: Throwable) {
        null
    }

    private fun runProcess(command: Array<String>, stdin: File?): PrivilegedShell.ShellResult {
        val backend = service()
            ?: return PrivilegedShell.ShellResult(false, "", "Shizuku is not available - grant permission first")
        return try {
            val remote = backend.newProcess(command, null, null)
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val outThread = drain(remote, true, stdout)
            val errThread = drain(remote, false, stderr)
            val sink = ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream)
            if (stdin != null) {
                stdin.inputStream().use { input -> sink.use { output -> input.copyTo(output) } }
            } else {
                runCatching { sink.close() }
            }
            val exit = remote.waitFor()
            runCatching { outThread.join() }
            runCatching { errThread.join() }
            PrivilegedShell.ShellResult(exit == 0, stdout.toString("UTF-8"), stderr.toString("UTF-8"))
        } catch (t: Throwable) {
            PrivilegedShell.ShellResult(false, "", "Shizuku command failed: ${t.message}")
        }
    }

    private fun drain(remote: IRemoteProcess, stdout: Boolean, sink: ByteArrayOutputStream): Thread {
        val thread = Thread {
            var source: InputStream? = null
            try {
                val descriptor: ParcelFileDescriptor =
                    if (stdout) remote.inputStream else remote.errorStream
                source = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (total < MAX_CAPTURE_BYTES) {
                        sink.write(buffer, 0, minOf(read, MAX_CAPTURE_BYTES - total))
                        total += read
                    }
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { source?.close() }
            }
        }
        thread.isDaemon = true
        thread.start()
        return thread
    }
}
