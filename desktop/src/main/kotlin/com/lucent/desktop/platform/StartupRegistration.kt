package com.lucent.desktop.platform

/**
 * PHASE 3 (codebase review C-3) — the "start with Windows" toggle.
 *
 * ### Why the registry, and why `reg.exe`
 *
 * A per-user `HKCU\...\Run` value is the canonical no-admin way to start at sign-in: no installer
 * change, no scheduled task, removable by the user from Task Manager's Startup page like any other
 * app. It is driven through `reg.exe` rather than JNI or `java.util.prefs` because prefs cannot
 * reach this hive path, and a whole native binding for three one-line operations would be the
 * heaviest possible tool for the lightest possible job. `reg.exe` ships on every Windows install
 * this app can run on.
 *
 * ### The registered command
 *
 * The current process's own launcher path (`ProcessHandle.info().command()`), which for a jpackage
 * install is the `Lucent.exe` the user double-clicks. Quoted, because "Program Files" has a space
 * in it and an unquoted Run value silently truncates at that space — the classic way this feature
 * "works on the dev machine" and breaks everywhere else.
 *
 * ### The registry is the source of truth
 *
 * There is deliberately no settings-store mirror of this flag. Two records of one fact drift — the
 * user removes the entry from Task Manager, the app's toggle still says on — so the toggle reads
 * the registry when the settings page opens and writes it when flipped. Slower by one process spawn
 * per settings visit, and never wrong.
 */
object StartupRegistration {

    private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "Lucent"

    /** The exe to register: this process's own launcher; null when it can't be determined. */
    private fun launcherPath(): String? =
        ProcessHandle.current().info().command().orElse(null)

    private fun run(vararg args: String): Boolean = try {
        val p = ProcessBuilder(*args).redirectErrorStream(true).start()
        p.inputStream.readBytes() // drain, or reg.exe can block on a full pipe
        p.waitFor() == 0
    } catch (_: Throwable) {
        false
    }

    /** Whether the Run entry currently exists. Safe anywhere; false on any failure. */
    fun isEnabled(): Boolean = run("reg", "query", RUN_KEY, "/v", VALUE_NAME)

    /**
     * Register or unregister. Returns whether the operation succeeded, so the settings toggle can
     * refuse to lie: on failure it snaps back instead of showing a state the registry doesn't have.
     */
    fun setEnabled(enabled: Boolean): Boolean {
        return if (enabled) {
            val exe = launcherPath() ?: return false
            run("reg", "add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", "\"$exe\"", "/f")
        } else {
            // Deleting a value that isn't there exits non-zero; that outcome IS "disabled", so
            // check first and treat absence as success.
            if (!isEnabled()) true
            else run("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f")
        }
    }
}
