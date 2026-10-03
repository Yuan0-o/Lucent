package com.lucent.app.ui

import androidx.compose.runtime.Composable
import com.lucent.app.platform.PlatformContext

expect val crashShieldLocksStartupLogging: Boolean
expect val dynamicColorSupported: Boolean
expect val dynamicColorRowVisible: Boolean
expect val desktopIntegrationVisible: Boolean
expect fun isBiometricUnlockAvailable(context: PlatformContext): Boolean
expect suspend fun isWindowsHelloAvailable(): Boolean
expect fun isStartupLoginEnabled(): Boolean
expect fun setStartupLoginEnabled(enabled: Boolean)

@Composable
expect fun rememberBackupFolderPicker(onFolderPicked: (String) -> Unit): () -> Unit
