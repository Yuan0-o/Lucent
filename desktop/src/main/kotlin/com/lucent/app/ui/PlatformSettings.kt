package com.lucent.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.security.WindowsHello
import kotlinx.coroutines.launch

/**
 * Whether Crash Shield being on freezes the startup-logging switch on the Privacy page. Always
 * false here: desktop's Privacy page never had this coupling in the UI (Android's value of this
 * same constant is true) — a genuine product difference between the two, not an oversight, so it
 * is preserved as-is rather than reconciled one way or the other.
 */
const val crashShieldLocksStartupLogging: Boolean = false

/**
 * Desktop seams for the shared Settings pages (P1-3). Material You dynamic colour — deriving a
 * palette from the wallpaper — is an Android 12+ concept; Windows has no wallpaper-palette API to
 * read, so both halves of the feature are no-ops here rather than a disabled control. See the
 * Android implementation of this same pair (same file name, `app/`'s own source set) for the
 * real behaviour and the reasoning behind each piece.
 */

/**
 * No-op on desktop: the stored `dynamicColorEnabled` flag still round-trips through backup/restore
 * (so a backup taken on Android and restored on Windows, or vice versa, doesn't lose the value),
 * but there is no Windows API to act on it and no Appearance-page row for it either — not even a
 * disabled one, since unlike an old Android version this isn't "unsupported for now", it is a
 * concept that does not apply to this platform at all.
 */
@Composable
fun DynamicColorRow(repo: SettingsRepository) {
}

/** Always false: without the feature, it can never be the thing currently overriding the theme. */
@Composable
fun rememberDynamicColorActive(repo: SettingsRepository): Boolean = false

/**
 * "Close to tray" (repo-backed, so it round-trips through backup/restore like any other setting)
 * and "start with Windows" (a direct Windows-registry read/write via `StartupRegistration`, which
 * is why it is plain `remember` state here rather than a repository flow — there is nothing in
 * DataStore to collect, the registry key IS the state, re-read after every write so the switch
 * always shows what the registry actually holds rather than what we hoped to write). PHASE 3
 * (C-3): desktop citizenship. Android has AlarmManager and needs neither, so this pair only
 * exists on this side of the seam — see the Android implementation of this same function.
 */
@Composable
fun DesktopIntegrationRows(repo: SettingsRepository) {
    val onGradient = LocalOnGradient.current
    val scope = rememberCoroutineScope()
    val closeToTray by repo.closeToTray.collectAsState(initial = true)
    var startWithWindows by remember {
        mutableStateOf(com.lucent.desktop.platform.StartupRegistration.isEnabled())
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(S.closeToTrayTitle, color = onGradient)
            Text(S.closeToTraySub, color = onGradient.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = closeToTray,
            onCheckedChange = { checked -> scope.launch { repo.setCloseToTray(checked) } }
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(S.startWithWindowsTitle, color = onGradient)
            Text(S.startWithWindowsSub, color = onGradient.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = startWithWindows,
            onCheckedChange = { checked ->
                // Written straight to the registry, then re-read: the toggle must show
                // what the registry actually holds, not what we hoped to write.
                com.lucent.desktop.platform.StartupRegistration.setEnabled(checked)
                startWithWindows = com.lucent.desktop.platform.StartupRegistration.isEnabled()
            }
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}

/**
 * The secondary-unlock row on the Security page: Windows Hello here, fingerprint/face on Android
 * — same idea, different platform API and different [SettingsRepository] field names
 * (`appLockHelloEnabled` vs `appLockBiometricEnabled`), hence a seam over the whole row. The
 * availability probe is comparatively slow (`WindowsHello.availability()` spins up PowerShell +
 * WinRT), so it runs once, off the composition, via `LaunchedEffect(Unit)` rather than blocking
 * first frame — the row simply doesn't appear until the probe resolves, same as it not appearing
 * at all when unavailable. See the Android implementation of this same function.
 */
@Composable
fun SecondaryUnlockRow(repo: SettingsRepository, appLockOn: Boolean) {
    var helloAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        helloAvailable = WindowsHello.availability() == WindowsHello.Availability.AVAILABLE
    }
    if (!appLockOn || !helloAvailable) return

    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    val helloEnabled by repo.appLockHelloEnabled.collectAsState(initial = false)

    Spacer(modifier = Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(S.helloTitle, color = onGradient)
            Spacer(modifier = Modifier.height(4.dp))
            Text(S.helloDesc, color = onGradientMuted, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = helloEnabled,
            onCheckedChange = { turnOn -> scope.launch { repo.setAppLockHelloEnabled(turnOn) } }
        )
    }
}

/**
 * The Data page's "choose auto-backup folder" trigger. Desktop's picker is a synchronous native
 * dialog — no persisted grant to manage, the chosen [java.io.File] is simply usable from then on —
 * so this is far simpler than the Android side of this same seam, which needs a full
 * `ActivityResultContracts` registration. [onFolderPicked] is called with the picked folder's
 * absolute path; the page decides what to do with it. See the Android implementation of this
 * same function.
 */
@Composable
fun rememberBackupFolderPicker(onFolderPicked: (String) -> Unit): () -> Unit {
    return {
        val dir = com.lucent.desktop.platform.DesktopFiles.chooseFolder(S.autoBackupChooseFolder)
        if (dir != null) onFolderPicked(dir.absolutePath)
    }
}
