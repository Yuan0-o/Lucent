package com.lucent.app.ui.settings

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.CrashShield
import com.lucent.app.data.EncryptionStatus
import com.lucent.app.data.PasswordAttempts
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.OverdueColor
import com.lucent.app.ui.SecondaryUnlockRow
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.StepperRow
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

@Composable
internal fun SecuritySettingsPage(
    repo: SettingsRepository,
    onRequestEnableAppLock: () -> Unit,
    onRequestDisableAppLock: () -> Unit,
    onRequestEnableSelfDestruct: () -> Unit,
    onRequestCrashShieldInfo: () -> Unit,
    onRoute: (SettingsRoute) -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    var appLockOn by remember { mutableStateOf(SettingsCache.appLockEnabled) }
    LaunchedEffect(Unit) { repo.appLockEnabled.collect { appLockOn = it } }
    var selfDestructOn by remember { mutableStateOf(SettingsCache.pwSelfDestructEnabled) }
    LaunchedEffect(Unit) { repo.pwSelfDestructEnabled.collect { selfDestructOn = it } }
    val selfDestructThreshold by repo.pwSelfDestructThreshold.collectAsState(
        initial = SettingsCache.pwSelfDestructThreshold
    )
    var crashShieldOn by remember { mutableStateOf(SettingsCache.crashShieldEnabled) }
    LaunchedEffect(Unit) { repo.crashShieldEnabled.collect { crashShieldOn = it } }
    val pwFirstRound by repo.pwFirstRoundLimit.collectAsState(
        initial = SettingsCache.pwFirstRoundLimit
    )
    val pwLaterRound by repo.pwLaterRoundLimit.collectAsState(
        initial = SettingsCache.pwLaterRoundLimit
    )

    BackHeader(S.settingsSecurityTitle) { onRoute(SettingsRoute.Root) }

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.appLockTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = appLockOn,
                onCheckedChange = { turnOn ->
                    if (turnOn) {
                        onRequestEnableAppLock()
                    } else {
                        onRequestDisableAppLock()
                    }
                }
            )
        }

        SecondaryUnlockRow(repo, appLockOn)

        if (appLockOn) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(S.attemptLimitsTitle, color = onGradient, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

            Spacer(modifier = Modifier.height(12.dp))
            StepperRow(
                label = S.attemptFirstRound,
                value = pwFirstRound,
                range = PasswordAttempts.ROUND_LIMIT_RANGE,
                onChange = {
                    scope.launch { repo.setPwFirstRoundLimit(it) }
                    SettingsCache.pwFirstRoundLimit = it
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            StepperRow(
                label = S.attemptLaterRounds,
                value = pwLaterRound,
                range = PasswordAttempts.ROUND_LIMIT_RANGE,
                onChange = {
                    scope.launch { repo.setPwLaterRoundLimit(it) }
                    SettingsCache.pwLaterRoundLimit = it
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(S.selfDestructTitle, color = onGradient)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = selfDestructOn,
                    onCheckedChange = { turnOn ->
                        if (turnOn) {
                            onRequestEnableSelfDestruct()
                        } else {
                            scope.launch { repo.setPwSelfDestructEnabled(false) }
                            SettingsCache.pwSelfDestructEnabled = false
                        }
                    }
                )
            }
            if (selfDestructOn) {
                Spacer(modifier = Modifier.height(12.dp))
                StepperRow(
                    label = S.selfDestructThreshold,
                    value = selfDestructThreshold,
                    range = PasswordAttempts.SELF_DESTRUCT_RANGE,
                    step = 5,
                    onChange = {
                        scope.launch { repo.setPwSelfDestructThreshold(it) }
                        SettingsCache.pwSelfDestructThreshold = it
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.crashShieldTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = crashShieldOn,
                onCheckedChange = { turnOn ->
                    if (turnOn) onRequestCrashShieldInfo()
                    else {
                        scope.launch { repo.setCrashShieldEnabled(false) }
                        SettingsCache.crashShieldEnabled = false
                    }
                }
            )
        }
        if (crashShieldOn) {
            Spacer(modifier = Modifier.height(8.dp))
            if (!CrashShield.isInstalled()) {
                Text(S.crashShieldNextLaunch, color = onGradientMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(S.crashShieldLoggingLocked, color = onGradientMuted, fontSize = 12.sp)
            if (CrashShield.isInstalled() && CrashShield.caughtCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    S.crashShieldCaught(CrashShield.caughtCount),
                    color = onGradientMuted,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(S.encryptionStatusTitle, color = onGradient, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            when {
                EncryptionStatus.lockedOut -> S.encryptionStatusLockedOut
                EncryptionStatus.degraded -> S.encryptionStatusDegraded
                else -> S.encryptionStatusHealthy
            },
            color = if (EncryptionStatus.degraded || EncryptionStatus.lockedOut) OverdueColor else onGradientMuted,
            fontSize = 13.sp
        )
    }
}
