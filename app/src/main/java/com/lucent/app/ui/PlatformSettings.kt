package com.lucent.app.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.BiometricAuth
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import kotlinx.coroutines.launch

/**
 * Whether Crash Shield being on freezes the startup-logging switch on the Privacy page. True on
 * Android; desktop's Privacy page never had this coupling in the UI (see the desktop value of
 * this same constant) — a genuine product difference between the two, not an oversight, so it is
 * preserved as-is rather than reconciled one way or the other.
 */
const val crashShieldLocksStartupLogging: Boolean = true

/**
 * Android seams for the shared Settings pages (P1-3). Material You dynamic colour — deriving a
 * palette from the wallpaper — is an Android 12+ concept with no Windows equivalent, so it is the
 * whole [DynamicColorRow]/[rememberDynamicColorActive] pair that differs by platform, not just one
 * value inside a shared row. See the desktop implementation of this same pair for the other side.
 */

/** Android 12 (API 31) is when the wallpaper-palette API this feature depends on shipped. */
private val dynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The Material You toggle row shown at the top of the Appearance page, plus its trailing 12dp
 * gap before the next card — callers just place this once and get either the whole block or
 * nothing (see the desktop implementation), never a half-spaced layout.
 *
 * Shown unconditionally on every Android version rather than only 12+: hiding the row entirely on
 * an unsupported OS would leave someone hunting for a setting that used to be here, or reading
 * about it online, with no explanation of where it went. Instead the row stays, disabled, with
 * [S.dynamicColorUnsupported] saying why — a dead control that explains itself beats a missing one.
 */
@Composable
fun DynamicColorRow(repo: SettingsRepository) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val dynamicColorOn by repo.dynamicColorEnabled.collectAsState(initial = false)

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.dynamicColorTitle, color = onGradient, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    when {
                        !dynamicColorSupported -> S.dynamicColorUnsupported
                        dynamicColorOn -> S.dynamicColorOnSub
                        else -> S.dynamicColorSub
                    },
                    color = onGradientMuted,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = dynamicColorOn,
                enabled = dynamicColorSupported,
                onCheckedChange = { on ->
                    AppScope.io.launch { repo.setDynamicColorEnabled(on) }
                }
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

/**
 * Whether dynamic colour is, right now, overriding the stored theme/palette choice — the signal
 * Theme and Background use to show a "paused, here's why" notice instead of their normal list.
 * True only once the device supports the feature AND the user has switched it on.
 */
@Composable
fun rememberDynamicColorActive(repo: SettingsRepository): Boolean {
    val dynamicColorOn by repo.dynamicColorEnabled.collectAsState(initial = false)
    return dynamicColorOn && dynamicColorSupported
}

/**
 * No-op on Android: "close to tray" and "start with Windows" are Windows-shell concepts (a system
 * tray to minimise into, a registry key that launches the app at login). Android has its own
 * process-lifecycle and notification model — AlarmManager, the notification shade — and needs
 * neither, so the Editor page shows nothing here. See the desktop implementation of this same
 * function for the real rows.
 */
@Composable
fun DesktopIntegrationRows(repo: SettingsRepository) {
}

/**
 * The secondary-unlock row on the Security page: fingerprint/face on Android, Windows Hello on
 * desktop. Same idea, different platform API and — notably — different [SettingsRepository]
 * field names (`appLockBiometricEnabled` vs `appLockHelloEnabled`), so this needed a seam over
 * the whole row rather than just a boolean: shown only once the app lock is on AND the device
 * actually has the capability enrolled, so it reads as a follow-on choice rather than a dead
 * control on a device that can't use it. See the desktop implementation of this same function.
 */
@Composable
fun SecondaryUnlockRow(repo: SettingsRepository, appLockOn: Boolean) {
    val context = LocalContext.current
    val biometricAvailable = remember { BiometricAuth.isAvailable(context) }
    if (!appLockOn || !biometricAvailable) return

    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    val biometricOn by repo.appLockBiometricEnabled.collectAsState(initial = false)

    Spacer(modifier = Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(S.biometricUnlockTitle, color = onGradient)
            Spacer(modifier = Modifier.height(4.dp))
            Text(S.biometricUnlockDesc, color = onGradientMuted, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = biometricOn,
            onCheckedChange = { turnOn -> scope.launch { repo.setAppLockBiometricEnabled(turnOn) } }
        )
    }
}

/**
 * The Data page's "choose auto-backup folder" trigger. Android needs a full
 * `rememberLauncherForActivityResult(OpenDocumentTree)` registration — including persisting the
 * grant, or the folder stops being writable the moment the process dies, which for a feature that
 * runs on a schedule is every time it matters — so the whole thing, not just the launch call, is
 * the seam. [onFolderPicked] is called with the picked folder once picking succeeds; the page
 * decides what to do with it (write it into the stored `AutoBackup.State`), so this function never
 * needs to know about [SettingsRepository] at all. See the desktop implementation of this same
 * function.
 */
@Composable
fun rememberBackupFolderPicker(onFolderPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            onFolderPicked(uri.toString())
        }
    }
    return { launcher.launch(null) }
}
