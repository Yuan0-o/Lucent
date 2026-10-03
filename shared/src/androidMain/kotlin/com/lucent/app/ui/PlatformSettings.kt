package com.lucent.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.lucent.app.platform.PlatformContext

actual val crashShieldLocksStartupLogging: Boolean = true
actual val dynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
actual val dynamicColorRowVisible: Boolean = true
actual val desktopIntegrationVisible: Boolean = false

actual fun isBiometricUnlockAvailable(context: PlatformContext): Boolean =
    BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS

actual suspend fun isWindowsHelloAvailable(): Boolean = false

actual fun isStartupLoginEnabled(): Boolean = false

actual fun setStartupLoginEnabled(enabled: Boolean) = Unit

@Composable
actual fun rememberBackupFolderPicker(onFolderPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            onFolderPicked(uri.toString())
        }
    }
    return { launcher.launch(null) }
}
