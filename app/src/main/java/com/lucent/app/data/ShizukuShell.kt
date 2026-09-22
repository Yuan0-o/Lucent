package com.lucent.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShizukuShell : PrivilegedShell.PrivilegedShellProvider {

    private var pingResult: Boolean? = null

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

    private fun bindingPermission(): String? = try {
        val cls = Class.forName("moe.shizuku.api.Shizuku")
        cls.getField("PERMISSION").get(null) as? String
    } catch (t: Throwable) {
        null
    }

    fun permissionIntent(): String? = try {
        val cls = Class.forName("moe.shizuku.api.Shizuku")
        cls.getMethod("grantPermissionRequest").invoke(null) as? String
    } catch (t: Throwable) {
        null
    }

    @Suppress("UNCHECKED_CAST")
    override fun isReady(): Boolean = shizukuReady()

    fun requestPermission(context: Any? = null): Boolean = try {
        val cls = shizukuClass() ?: return false
        val method = cls.getMethod("requestPermission", Int::class.java)
        method.invoke(null, 0)
        true
    } catch (t: Throwable) {
        false
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun runCommand(command: String): PrivilegedShell.ShellResult {
        return withContext(Dispatchers.IO) {
            try {
                if (!shizukuReady()) {
                    return@withContext PrivilegedShell.ShellResult(
                        false, "", "Shizuku not ready - grant permission first"
                    )
                }
                val cls = shizukuClass() ?: return@withContext PrivilegedShell.ShellResult(false, "", "Shizuku missing")
                val newProcess = cls.getMethod("newProcess", Array<String>::class.java, String::class.java, String::class.java)
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