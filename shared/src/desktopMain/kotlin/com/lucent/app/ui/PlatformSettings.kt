package com.lucent.app.ui

import androidx.compose.runtime.Composable
import com.lucent.app.i18n.S
import com.lucent.app.platform.PlatformContext
import com.lucent.app.security.WindowsHello
import com.lucent.desktop.platform.DesktopFiles
import com.lucent.desktop.platform.StartupRegistration

actual val crashShieldLocksStartupLogging: Boolean = false
actual val dynamicColorSupported: Boolean = false
actual val dynamicColorRowVisible: Boolean = false
actual val desktopIntegrationVisible: Boolean = true

actual fun isBiometricUnlockAvailable(context: PlatformContext): Boolean = false

actual suspend fun isWindowsHelloAvailable(): Boolean =
    WindowsHello.availability() == WindowsHello.Availability.AVAILABLE

actual fun isStartupLoginEnabled(): Boolean = StartupRegistration.isEnabled()

actual fun setStartupLoginEnabled(enabled: Boolean) {
    StartupRegistration.setEnabled(enabled)
}

@Composable
actual fun rememberBackupFolderPicker(onFolderPicked: (String) -> Unit): () -> Unit {
    return {
        val dir = DesktopFiles.chooseFolder(S.autoBackupChooseFolder)
        if (dir != null) onFolderPicked(dir.absolutePath)
    }
}
