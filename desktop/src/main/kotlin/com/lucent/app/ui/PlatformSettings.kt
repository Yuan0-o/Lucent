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
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.security.WindowsHello
import kotlinx.coroutines.launch

const val crashShieldLocksStartupLogging: Boolean = false


@Composable
fun DynamicColorRow(repo: SettingsRepository) {
}

@Composable
fun rememberDynamicColorActive(repo: SettingsRepository): Boolean = false

@Composable
fun DesktopIntegrationRows(repo: SettingsRepository) {
    val onGradient = LocalOnGradient.current
    val scope = rememberCoroutineScope()
    val closeToTray by repo.closeToTray.collectAsState(initial = SettingsCache.closeToTray)
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
            onCheckedChange = { checked ->
                scope.launch { repo.setCloseToTray(checked) }
                SettingsCache.closeToTray = checked
            }
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(S.startWithWindowsTitle, color = onGradient)
            Text(S.startWithWindowsSub, color = onGradient.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = startWithWindows,
            onCheckedChange = { checked ->
                com.lucent.desktop.platform.StartupRegistration.setEnabled(checked)
                startWithWindows = com.lucent.desktop.platform.StartupRegistration.isEnabled()
            }
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
}

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
    val helloEnabled by repo.appLockHelloEnabled.collectAsState(initial = SettingsCache.appLockHelloEnabled)

    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(S.helloTitle, color = onGradient)
            Spacer(modifier = Modifier.height(4.dp))
            Text(S.helloDesc, color = onGradientMuted, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = helloEnabled,
            onCheckedChange = { turnOn ->
                SettingsCache.appLockHelloEnabled = turnOn
                scope.launch { repo.setAppLockHelloEnabled(turnOn) }
            }
        )
    }
}

@Composable
fun rememberBackupFolderPicker(onFolderPicked: (String) -> Unit): () -> Unit {
    return {
        val dir = com.lucent.desktop.platform.DesktopFiles.chooseFolder(S.autoBackupChooseFolder)
        if (dir != null) onFolderPicked(dir.absolutePath)
    }
}
