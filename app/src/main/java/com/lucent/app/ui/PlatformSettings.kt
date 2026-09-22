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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.BiometricAuth
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import kotlinx.coroutines.launch

const val crashShieldLocksStartupLogging: Boolean = true


private val dynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private object DynamicColorState {
    @Volatile
    var active: Boolean = SettingsCache.dynamicColor && dynamicColorSupported
}

@Composable
fun DynamicColorRow(repo: SettingsRepository) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val dynamicColorOn by repo.dynamicColorEnabled.collectAsState(initial = DynamicColorState.active)

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.dynamicColorTitle, color = onGradient, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
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
                    SettingsCache.dynamicColor = on
                    AppScope.io.launch { repo.setDynamicColorEnabled(on) }
                }
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
fun rememberDynamicColorActive(repo: SettingsRepository): Boolean {
    val dynamicColorOn by repo.dynamicColorEnabled.collectAsState(initial = DynamicColorState.active)
    val active = dynamicColorOn && dynamicColorSupported
    SideEffect { DynamicColorState.active = active }
    return active
}

@Composable
fun DesktopIntegrationRows(repo: SettingsRepository) {
}

@Composable
fun SecondaryUnlockRow(repo: SettingsRepository, appLockOn: Boolean) {
    val context = LocalContext.current
    val biometricAvailable = remember { BiometricAuth.isAvailable(context) }
    if (!appLockOn || !biometricAvailable) return

    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    var biometricOn by remember { mutableStateOf(SettingsCache.appLockBiometricEnabled) }
    LaunchedEffect(Unit) { repo.appLockBiometricEnabled.collect { biometricOn = it } }

    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(S.biometricUnlockTitle, color = onGradient)
            Spacer(modifier = Modifier.height(4.dp))
            Text(S.biometricUnlockDesc, color = onGradientMuted, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = biometricOn,
            onCheckedChange = { turnOn ->
                scope.launch { repo.setAppLockBiometricEnabled(turnOn) }
                SettingsCache.appLockBiometricEnabled = turnOn
            }
        )
    }
}

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
