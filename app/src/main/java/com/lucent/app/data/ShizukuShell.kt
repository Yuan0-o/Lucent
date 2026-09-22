package com.lucent.app.data

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shizuku-backed privileged shell.
 *
 * Uses reflection so the app builds without a Shizuku compile dependency. When the Shizuku
 * API is not on the classpath the in-app permission dialog cannot be shown, so pairing falls
 * back to opening the Shizuku manager, where the user authorises Lucent.
 */
object ShizukuShell : PrivilegedShell.PrivilegedShellProvider {

    private const val SHIZUKU_MANAGER_PACKAGE = "moe.shizuku.privileged.api"

    private var pingResult: Boolean? = null

    /** Clears the cached ping so the next isReady() reflects a freshly granted permission. */
    fun refresh() {
        pingResult = null
    }

    private fun shizukuClass(): Class<*>? = try {
        Class.forName("moe.shizuku.api.Shizuku")
    } catch (t: Throwable) {
        null
    }

    private fun shizukuReady(): Boolean {
        pingResult?.let { return it }
        val result = try {
            val cls = shizukuClass() ?: return false
            cls.getMethod("ping").invoke(null) as? Boolean ?: false
        } catch (t: Throwable) {
            false
        }
        pingResult = result
        return result
    }

    @Suppress("UNCHECKED_CAST")
    override fun isReady(): Boolean = shizukuReady()

    /**
     * Asks Shizuku for permission. Returns true when a permission flow was started.
     *
     * 1. Tries the in-app request dialog (needs the Shizuku API + provider on the classpath).
     * 2. Falls back to opening the Shizuku manager app so the user can authorise Lucent there.
     */
    fun requestPermission(context: Context?): Boolean {
        val inApp = try {
            val cls = shizukuClass() ?: return openShizukuManager(context)
            cls.getMethod("requestPermission", Int::class.java).invoke(null, 0)
            true
        } catch (t: Throwable) {
            false
        }
        return if (inApp) true else openShizukuManager(context)
    }

    /** True when the Shizuku manager app is installed on this device. */
    fun isShizukuInstalled(context: Context?): Boolean {
        if (context == null) return false
        return try {
            context.packageManager.getLaunchIntentForPackage(SHIZUKU_MANAGER_PACKAGE) != null
        } catch (t: Throwable) {
            false
        }
    }

    /** Opens the Shizuku manager so the user can grant Lucent access. */
    fun openShizukuManager(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_MANAGER_PACKAGE)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }

    override suspend fun runCommand(command: String): PrivilegedShell.ShellResult {
        return withContext(Dispatchers.IO) {
            try {
                if (!shizukuReady()) {
                    return@withContext PrivilegedShell.ShellResult(
                        false, "", "Shizuku not ready - grant permission first"
                    )
                }
                val cls = shizukuClass()
                    ?: return@withContext PrivilegedShell.ShellResult(false, "", "Shizuku missing")
                val newProcess = cls.getMethod(
                    "newProcess", Array<String>::class.java, String::class.java, String::class.java
                )
                val process = newProcess.invoke(null, arrayOf("sh", "-c", command), null, null)
                val waitFor = process.javaClass.getMethod("waitFor")
                val getInput = process.javaClass.getMethod("getInputStream")
                val getErr = process.javaClass.getMethod("getErrorStream")
                val exitCode = waitFor.invoke(process) as Int
                val stdout = (getInput.invoke(process) as java.io.InputStream).bufferedReader().readText()
                val stderr = (getErr.invoke(process) as java.io.InputStream).bufferedReader().readText()
                PrivilegedShell.ShellResult(exitCode == 0, stdout, stderr)
            } catch (t: Throwable) {
                PrivilegedShell.ShellResult(false, "", "Shizuku exec failed: ${t.message}")
            }
        }
    }
}
