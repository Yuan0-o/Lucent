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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.CrashShield
import com.lucent.app.data.EncryptionStatus
import com.lucent.app.data.PasswordAttempts
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.GlassButton
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.OverdueColor
import com.lucent.app.ui.SecondaryUnlockRow
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.StepperRow
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

/**
 * P1-3 — extracted from the `SecurityPage` local composable that used to live inside
 * `SettingsScreen`. Diffing the two originals found exactly one difference: the secondary-unlock
 * row (fingerprint/face on Android, Windows Hello on desktop), including a repository field that
 * is genuinely named differently per platform (`appLockBiometricEnabled` vs `appLockHelloEnabled`)
 * — now the [SecondaryUnlockRow] seam. Everything else, including the section header essay on why
 * Security and Privacy are split the way they are, was already byte-identical.
 *
 * The app-lock enable/disable flow and the self-destruct arm flow each reset several fields
 * (password, confirmation, security question, an error string) before opening their setup
 * dialog, and those dialogs — along with the fields they reset — live in, and stay in,
 * [SettingsScreen]. So this page never sees the individual fields, only one intention-named
 * callback per flow ([onRequestEnableAppLock], [onRequestDisableAppLock],
 * [onRequestEnableSelfDestruct], [onRequestCrashShieldInfo]) — the same "page doesn't need to
 * know what the trigger resets" shape as Memory/Editor's warning triggers, just for a flow with
 * more than one field behind it. [encryptionCheckResult] is the one piece of this page's own
 * state that stays outer-owned: a `LaunchedEffect` in `SettingsScreen` clears it back to null a
 * moment after a passing check, the same reveal-timer shape as the API key's `keyVisible`.
 */
@Composable
internal fun SecuritySettingsPage(
    repo: SettingsRepository,
    onRequestEnableAppLock: () -> Unit,
    onRequestDisableAppLock: () -> Unit,
    onRequestEnableSelfDestruct: () -> Unit,
    onRequestCrashShieldInfo: () -> Unit,
    encryptionCheckResult: String?,
    onEncryptionCheckResultChange: (String?) -> Unit,
    onRoute: (SettingsRoute) -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    val appLockOn by repo.appLockEnabled.collectAsState(initial = false)
    val selfDestructOn by repo.pwSelfDestructEnabled.collectAsState(initial = false)
    val selfDestructThreshold by repo.pwSelfDestructThreshold.collectAsState(
        initial = PasswordAttempts.DEFAULT_SELF_DESTRUCT_THRESHOLD
    )
    val crashShieldOn by repo.crashShieldEnabled.collectAsState(initial = false)
    val pwFirstRound by repo.pwFirstRoundLimit.collectAsState(
        initial = PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT
    )
    val pwLaterRound by repo.pwLaterRoundLimit.collectAsState(
        initial = PasswordAttempts.DEFAULT_LATER_ROUND_LIMIT
    )

    BackHeader(S.settingsSecurityTitle) { onRoute(SettingsRoute.Root) }

    // ================================================================================
    //  C-GROUP TASK 2 — the Security / Privacy split, restated
    // ================================================================================
    //
    // The two pages had drifted into overlap: "app lock" sat in Security while "startup
    // logging" sat in Privacy, and neither heading explained why. The line that actually
    // separates them, and that both pages are now organised around, is:
    //
    //   SECURITY  = keeping other people OUT of data that stays here.
    //               (app lock, biometrics, unlock attempt limits, self-destruct,
    //                crash shield, at-rest encryption status)
    //
    //   PRIVACY   = controlling what LEAVES this device, or gets written down about you.
    //               (Blackout Mode, system share integration, diagnostic logging,
    //                opening links in another app)
    //
    // Read that way every control has exactly one home, and the two questions a worried
    // user actually asks — "can someone else get in?" and "where does my stuff go?" — each
    // have a page that answers them completely.
    //
    // Blackout Mode is filed under Privacy despite forcing the app lock on, because the
    // lock is a MEANS for it, not its purpose: it exists to stop data leaving.
    //
    // Sections within the page are ordered by how much damage getting them wrong does.
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        // ---- App Lock ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.appLockTitle, color = onGradient)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    S.appLockDesc,
                    color = onGradientMuted,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = appLockOn,
                onCheckedChange = { turnOn ->
                    if (turnOn) {
                        // Capture credentials before enabling; the lock isn't turned on
                        // until the setup dialog is completed.
                        onRequestEnableAppLock()
                    } else {
                        // Don't disable straight away: a dialog confirms the password
                        // first and explains the risk of removing the lock (task).
                        onRequestDisableAppLock()
                    }
                }
            )
        }

        // ---- Secondary unlock (fingerprint/face on Android, Windows Hello on desktop) ----
        // Shown only once the lock is on AND the device actually has it available, so it
        // reads as a follow-on choice to "App lock" rather than a dead control on a device
        // that can't use it. Turning the lock off hides this again (and clears the opt-in
        // via setAppLock), so re-enabling the lock always starts from "off".
        SecondaryUnlockRow(repo, appLockOn)

        // ---- C-group task 18: unlock attempt limits ----
        //
        // Shown only while the lock is on. A throttle for a password that does not exist is
        // a setting with nothing to configure, and a page full of inert controls is how
        // people learn to skim past the ones that matter.
        if (appLockOn) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(S.attemptLimitsTitle, color = onGradient, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(S.attemptLimitsDesc, color = onGradientMuted, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(12.dp))
            StepperRow(
                label = S.attemptFirstRound,
                value = pwFirstRound,
                range = PasswordAttempts.ROUND_LIMIT_RANGE,
                onChange = { scope.launch { repo.setPwFirstRoundLimit(it) } }
            )
            Spacer(modifier = Modifier.height(8.dp))
            StepperRow(
                label = S.attemptLaterRounds,
                value = pwLaterRound,
                range = PasswordAttempts.ROUND_LIMIT_RANGE,
                onChange = { scope.launch { repo.setPwLaterRoundLimit(it) } }
            )
            Spacer(modifier = Modifier.height(8.dp))
            // The escalation ladder itself is fixed, not configurable. A user-editable
            // backoff curve is a backoff curve that whoever reaches this screen can flatten.
            Text(S.attemptLadderNote, color = onGradientMuted, fontSize = 12.sp)

            // ---- Self-destruct: OFF by default, typed confirmation to turn on ----
            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(S.selfDestructTitle, color = onGradient)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(S.selfDestructDesc, color = onGradientMuted, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = selfDestructOn,
                    onCheckedChange = { turnOn ->
                        if (turnOn) {
                            onRequestEnableSelfDestruct()
                        } else {
                            // Turning a destructive feature OFF needs no ceremony. Only
                            // arming it does.
                            scope.launch { repo.setPwSelfDestructEnabled(false) }
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
                    onChange = { scope.launch { repo.setPwSelfDestructThreshold(it) } }
                )
            }
        }

        // ---- C-group task 3: Crash Shield ----
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.crashShieldTitle, color = onGradient)
                Spacer(modifier = Modifier.height(4.dp))
                Text(S.crashShieldDesc, color = onGradientMuted, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = crashShieldOn,
                onCheckedChange = { turnOn ->
                    if (turnOn) onRequestCrashShieldInfo()
                    else scope.launch { repo.setCrashShieldEnabled(false) }
                }
            )
        }
        if (crashShieldOn) {
            Spacer(modifier = Modifier.height(8.dp))
            // Two honest notes rather than one reassuring one: the switch does not take
            // effect until relaunch, and logging is now held on and cannot be turned off
            // from the Privacy page while this is running.
            //
            // The "next launch" note is a PENDING notice, not a permanent caption: once a
            // later launch has actually installed the shield (CrashShield.isInstalled), the
            // promise in the note has been kept, so the note disappears instead of telling
            // the user forever that a change is still coming (R3 report).
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

        // ---- C-group task 17: at-rest encryption readout ----
        //
        // Placed last because it is a STATUS, not a switch — nothing here is configurable,
        // and mixing a readout in among controls invites people to look for the toggle that
        // isn't there. It is on this page rather than Privacy because encryption is about
        // keeping others out of data that stays here, which is exactly this page's job.
        Spacer(modifier = Modifier.height(24.dp))
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
        Spacer(modifier = Modifier.height(8.dp))
        // The machine-readable summary, verbatim. It is the same line written to the
        // startup log, so what a user reads here and what they send in a bug report cannot
        // disagree.
        Text(
            EncryptionStatus.summaryLine(),
            color = onGradientMuted,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassButton(text = S.encryptionRunCheck, onClick = {
                // A status flag records what a code path BELIEVED. This does the work.
                val failure = EncryptionStatus.probeSecrets()
                onEncryptionCheckResultChange(failure ?: "")
            })
        }
        encryptionCheckResult?.let { result ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (result.isEmpty()) S.encryptionCheckPassed else S.encryptionCheckFailed(result),
                color = if (result.isEmpty()) onGradientMuted else OverdueColor,
                fontSize = 12.sp
            )
        }
    }
}
