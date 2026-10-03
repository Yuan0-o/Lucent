package com.lucent.app.data

object PrivilegedShell {

    private var impl: PrivilegedShellProvider? = null

    fun install(provider: PrivilegedShellProvider) {
        impl = provider
    }

    fun isReady(): Boolean = impl?.isReady() == true

    suspend fun runCommand(command: String): ShellResult {
        val p = impl ?: return ShellResult(false, "", "No privilege backend installed")
        return p.runCommand(command)
    }

    suspend fun installPackage(packagePath: String, sizeBytes: Long): ShellResult {
        val p = impl ?: return ShellResult(false, "", "No privilege backend installed")
        return p.installPackage(packagePath, sizeBytes)
    }

    interface PrivilegedShellProvider {
        fun isReady(): Boolean
        suspend fun runCommand(command: String): ShellResult

        suspend fun installPackage(packagePath: String, sizeBytes: Long): ShellResult =
            runCommand("pm install -r \"$packagePath\"")
    }

    data class ShellResult(val success: Boolean, val stdout: String, val stderr: String)
}
