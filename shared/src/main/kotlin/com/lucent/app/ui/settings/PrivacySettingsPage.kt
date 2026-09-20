package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.AppLock
import com.lucent.app.data.BlackoutMode
import com.lucent.app.data.NoteHistory
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.ShareIntegration
import com.lucent.app.data.StartupLog
import com.lucent.app.data.TaskHistory
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.GlassButton
import com.lucent.app.ui.HiddenArea
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.crashShieldLocksStartupLogging
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

@Composable
internal fun PrivacySettingsPage(
    repo: SettingsRepository,
    gateLockedOut: Boolean,
    gateWiping: Boolean,
    onSettingsGateSuccess: () -> Unit,
    onChargeSettingsGate: () -> Unit,
    settingsGateFeedback: @Composable () -> Unit,
    onRequestBlackoutWarning: () -> Unit,
    onRequestShareWarning: () -> Unit,
    onExportLogsClick: () -> Unit,
    onRoute: (SettingsRoute) -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    val blackoutOn by repo.blackoutEnabled.collectAsState(initial = SettingsCache.blackoutEnabled)
    val systemIntegrationOn by repo.systemIntegrationEnabled.collectAsState(
        initial = SettingsCache.systemIntegrationEnabled
    )
    val startupLoggingOn by repo.startupLoggingEnabled.collectAsState(initial = SettingsCache.startupLoggingEnabled)
    val crashShieldOn by repo.crashShieldEnabled.collectAsState(initial = SettingsCache.crashShieldEnabled)
    val noteHistoryOn by repo.noteHistoryEnabled.collectAsState(initial = SettingsCache.noteHistoryEnabled)
    val taskHistoryOn by repo.taskHistoryEnabled.collectAsState(initial = SettingsCache.taskHistoryEnabled)
    val appLockCredsOrNull by repo.appLockCredentials.collectAsState(initial = null)

    BackHeader(S.settingsPrivacyTitle) { onRoute(SettingsRoute.Root) }

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.blackoutTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = blackoutOn,
                onCheckedChange = { turnOn ->
                    if (turnOn) {
                        onRequestBlackoutWarning()
                    } else {
                        scope.launch {
                            repo.setBlackoutEnabled(false)
                            BlackoutMode.hydrate(false)
                            LucentToast.show(context, S.blackoutOffToast)
                        }
                    }
                }
            )
        }
        if (blackoutOn) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                S.blackoutOverridesTitle,
                color = onGradient,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.systemIntegrationTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = systemIntegrationOn,
                onCheckedChange = { turnOn ->
                    if (turnOn) {
                        onRequestShareWarning()
                    } else {
                        scope.launch { repo.setSystemIntegrationEnabled(false) }
                        ShareIntegration.setEnabled(context, false)
                        LucentToast.show(context, S.systemIntegrationOffToast)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        var showLoggingConsent by remember { mutableStateOf(false) }
        if (showLoggingConsent) {
            AlertDialog(
                onDismissRequest = { showLoggingConsent = false },
                title = { Text(S.loggingConsentTitle) },
                text = { Text(S.loggingConsentBody) },
                confirmButton = {
                    TextButton(onClick = {
                        showLoggingConsent = false
                        scope.launch { repo.setStartupLoggingEnabled(true) }
                        StartupLog.setEnabled(true)
                        StartupLog.event(context, "Logging enabled from Settings")
                    }) { Text(S.loggingConsentConfirm) }
                },
                dismissButton = {
                    TextButton(onClick = { showLoggingConsent = false }) { Text(S.actionCancel) }
                }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.startupLoggingTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = startupLoggingOn,
                enabled = !(crashShieldLocksStartupLogging && crashShieldOn),
                onCheckedChange = { turnOn ->
                    if (turnOn) {
                        showLoggingConsent = true
                    } else {
                        scope.launch { repo.setStartupLoggingEnabled(false) }
                        StartupLog.setEnabled(false)
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (crashShieldLocksStartupLogging && crashShieldOn) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(S.crashShieldLoggingLocked, color = onGradientMuted, fontSize = 12.sp)
        }
        if (startupLoggingOn) {
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                GlassButton(text = S.exportLogs, onClick = onExportLogsClick)
                Spacer(modifier = Modifier.width(12.dp))
                GlassButton(text = S.clearLogs, onClick = {
                    StartupLog.clear(context)
                    LucentToast.show(context, S.logsClearedToast)
                })
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.historyTitle, color = onGradient)

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.historyNotes, color = onGradient, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = noteHistoryOn,
                onCheckedChange = { on ->
                    NoteHistory.enabled = on
                    scope.launch { repo.setNoteHistoryEnabled(on) }
                }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.historyTasks, color = onGradient, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = taskHistoryOn,
                onCheckedChange = { on ->
                    TaskHistory.enabled = on
                    scope.launch { repo.setTaskHistoryEnabled(on) }
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        var hiddenPw by remember { mutableStateOf("") }
        var hiddenPwError by remember { mutableStateOf(false) }
        var askingHiddenPw by remember { mutableStateOf(false) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.hiddenSettingTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = HiddenArea.visible,
                enabled = appLockCredsOrNull != null,
                onCheckedChange = { turnOn ->
                    val appLockCreds = appLockCredsOrNull ?: return@Switch
                    when {
                        !turnOn -> HiddenArea.close()
                        appLockCreds.isBlank() -> HiddenArea.open()
                        else -> { hiddenPw = ""; hiddenPwError = false; askingHiddenPw = true }
                    }
                }
            )
        }

        if (askingHiddenPw) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(S.hiddenUnlockPrompt, color = onGradientMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = hiddenPw,
                onValueChange = { hiddenPw = it; hiddenPwError = false },
                singleLine = true,
                isError = hiddenPwError,
                enabled = !gateLockedOut && !gateWiping,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            if (hiddenPwError) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(S.hiddenWrongPassword, color = onGradientMuted, fontSize = 12.sp)
            }
            settingsGateFeedback()
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                GlassButton(
                    text = S.actionConfirm,
                    compact = true,
                    enabled = !gateLockedOut && !gateWiping,
                    onClick = {
                        val stored = appLockCredsOrNull
                        if (stored != null && AppLock.verifyPassword(stored, hiddenPw)) {
                            onSettingsGateSuccess()
                            HiddenArea.open()
                            askingHiddenPw = false
                            hiddenPw = ""
                        } else {
                            hiddenPwError = false
                            onChargeSettingsGate()
                        }
                    })
                Spacer(modifier = Modifier.width(8.dp))
                GlassButton(text = S.actionCancel, compact = true, onClick = {
                    askingHiddenPw = false; hiddenPw = ""
                })
            }
        }
    }
}
