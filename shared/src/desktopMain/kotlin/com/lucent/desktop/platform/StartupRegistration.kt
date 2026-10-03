package com.lucent.desktop.platform

object StartupRegistration {

    private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "Lucent"

    private fun launcherPath(): String? =
        ProcessHandle.current().info().command().orElse(null)

    private fun run(vararg args: String): Boolean = try {
        val p = ProcessBuilder(*args).redirectErrorStream(true).start()
        p.inputStream.readBytes()
        p.waitFor() == 0
    } catch (_: Throwable) {
        false
    }

    fun isEnabled(): Boolean = run("reg", "query", RUN_KEY, "/v", VALUE_NAME)

    fun setEnabled(enabled: Boolean): Boolean {
        return if (enabled) {
            val exe = launcherPath() ?: return false
            run("reg", "add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", "\"$exe\"", "/f")
        } else {
            if (!isEnabled()) true
            else run("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f")
        }
    }
}
