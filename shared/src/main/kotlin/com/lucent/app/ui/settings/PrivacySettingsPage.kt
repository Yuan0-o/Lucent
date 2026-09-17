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

/**
 * P1-3 — extracted from the `PrivacyPage` local composable that used to live inside
 * `SettingsScreen`. Diffing the two originals found two differences:
 * - Crash Shield freezing the startup-logging switch (with an explanatory line) only happens on
 *   Android; desktop's switch is never frozen by it. Genuinely different product behaviour, not
 *   an oversight repeated twice in the same diff, so it became one small seam value —
 *   [crashShieldLocksStartupLogging] — rather than a full row like Appearance's dynamic-colour
 *   card, since there was no block of UI to move, just a boolean two call sites needed.
 * - Exporting the log triggers through [onExportLogsClick], same reasoning as every other
 *   file-picker seam in this batch (font/model import): the picker itself stays in each
 *   platform's own `SettingsScreen.kt`.
 *
 * The local-diagnostic-logging consent dialog and the hidden-area unlock prompt were already
 * self-contained within `PrivacyPage`'s own body (their state was never read by anything outside
 * it), so both moved here whole rather than needing the value+setter treatment.
 * [gateLockedOut]/[gateWiping]/[onChargeSettingsGate]/[onSettingsGateSuccess]/
 * [settingsGateFeedback] are the shared password-attempt throttle — also used by the self-destruct
 * flow on the Security page — so they stay owned by `SettingsScreen`, threaded through the same
 * way the app-lock/self-destruct reset flows are on that page.
 */
@Composable
fun PrivacySettingsPage(
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
    val blackoutOn by repo.blackoutEnabled.collectAsState(initial = false)
    val systemIntegrationOn by repo.systemIntegrationEnabled.collectAsState(initial = false)
    val startupLoggingOn by repo.startupLoggingEnabled.collectAsState(initial = false)
    val crashShieldOn by repo.crashShieldEnabled.collectAsState(initial = false)
    val noteHistoryOn by repo.noteHistoryEnabled.collectAsState(initial = true)
    val taskHistoryOn by repo.taskHistoryEnabled.collectAsState(initial = true)
    val appLockCreds by repo.appLockCredentials.collectAsState(initial = "")

    BackHeader(S.settingsPrivacyTitle) { onRoute(SettingsRoute.Root) }

    // Privacy is the other half of the old combined page (task 10): not "who can get in"
    // but "what gets out, or written down". Both switches here are off by default and
    // both are about visibility beyond this screen — one makes Lucent visible to other
    // apps, the other records a local file about what the app did.
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        // ---- C-group task 1: Blackout Mode ----
        //
        // First on the page, and deliberately so: it is the only control here that
        // OUTRANKS the ones below it. Someone who turns it on has settled every question
        // the rest of the page asks, and burying it under three lesser switches would mean
        // the user configures each of them and only then discovers one switch did it all.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.blackoutTitle, color = onGradient)
                Spacer(modifier = Modifier.height(4.dp))
                Text(S.blackoutSub, color = onGradientMuted, fontSize = 13.sp)
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
            Spacer(modifier = Modifier.height(2.dp))
            Text(S.blackoutDesc, color = onGradientMuted, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
        // ---- System share / intent integration ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.systemIntegrationTitle, color = onGradient)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    S.systemIntegrationDesc,
                    color = onGradientMuted,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = systemIntegrationOn,
                onCheckedChange = { turnOn ->
                    if (turnOn) {
                        // Show the privacy warning first; only enable on explicit confirm.
                        onRequestShareWarning()
                    } else {
                        scope.launch { repo.setSystemIntegrationEnabled(false) }
                        ShareIntegration.setEnabled(context, false)
                        // Disabling now gets the same bottom notification enabling does
                        // (task): an acknowledgement that the change took effect. No dialog
                        // — turning a feature *off* needs confirming, not warning about.
                        LucentToast.show(context, S.systemIntegrationOffToast)
                    }
                }
            )
        }

        // ---- Local diagnostic logging ----
        Spacer(modifier = Modifier.height(20.dp))
        // Turning logging ON asks for consent first — it can capture technical detail and
        // the text you type to the assistant. Turning it OFF is immediate (no dialog).
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
                        // Log lines stay English on purpose so a bug report reads the same
                        // regardless of the UI language at the time.
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    S.startupLoggingDesc,
                    color = onGradientMuted,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = startupLoggingOn,
                // C-group task 3: Crash Shield holds logging ON, on Android only — see
                // [crashShieldLocksStartupLogging]. Frozen, not hidden — a control that
                // vanishes leaves the user wondering whether they imagined it, while a
                // visibly disabled one with a reason beside it teaches them which other
                // switch is responsible.
                enabled = !(crashShieldLocksStartupLogging && crashShieldOn),
                onCheckedChange = { turnOn ->
                    if (turnOn) {
                        showLoggingConsent = true          // enable only after consent
                    } else {
                        scope.launch { repo.setStartupLoggingEnabled(false) }
                        StartupLog.setEnabled(false)
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
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
                    // Toast rather than the Data page's backupStatus line, which isn't
                    // shown on this page (task 5 moved these controls here).
                    LucentToast.show(context, S.logsClearedToast)
                })
            }
        }
    }

    // Round R2, task 1: one gap, stated once. This was two consecutive 12dp spacers with
    // nothing between them — a leftover of something removed — which is why the gap ABOVE
    // this card was 24dp while the gap below it was zero. (24dp is the same CARD_GAP every
    // other pair of cards on this page uses.)
    Spacer(modifier = Modifier.height(24.dp))

    // ================================================================================
    //  Task 4 — version history ("flash records")
    // ================================================================================
    //
    // On by default, and switched off without a confirmation dialog: turning it off costs
    // nothing that exists yet — it only stops FUTURE snapshots — so a prompt would be
    // ceremony. What the page does owe the user is an explanation of what the feature is and
    // what its limit does, because "flash record" means nothing on its own and silent
    // deletion of an old version would otherwise look like data loss.
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.historyTitle, color = onGradient)
        Spacer(modifier = Modifier.height(4.dp))
        Text(S.historyDesc, color = onGradientMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            S.historyCapNote(NoteHistory.MAX_VERSIONS_PER_NOTE),
            color = onGradientMuted,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(14.dp))
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

    // Round R2, task 1: the history card and the hidden-area card below it had NO spacer
    // between them at all, so the two frosted panels met edge to edge and read as one
    // card with a rule through it. Same gap as every other pair on this page.
    Spacer(modifier = Modifier.height(24.dp))

    // ================================================================================
    //  Task 10 — "Show hidden area" is its own module, and it is the last thing on
    //  this page
    // ================================================================================
    //
    // It used to be the FIRST card here, which pushed Blackout Mode — the one control that
    // outranks everything else on this page — below the fold. Two things were wrong with
    // that. It read as the headline privacy setting when it is in fact a temporary,
    // session-scoped reveal; and a switch that exposes deliberately concealed content was
    // the first thing a thumb met on the way into the page.
    //
    // Bottom of the page, in a card of its own, fixes both. The controls that decide what
    // leaves this device come first, in rank order, and the reveal is where a deliberate
    // scroll takes you rather than where an idle one lands. It is still gated by the app
    // lock when one is set, and it still closes itself on the next launch.
    // ---- Task A21: the hidden area switch ----
    //
    // It lives here, off by default, and closes itself on the next launch (see
    // [HiddenArea]). When an app lock is set, turning it ON asks for that password first:
    // the lock is the user's statement that reaching this data requires proof, and a switch
    // that reveals a deliberately hidden area is exactly where that statement applies.
    // Turning it OFF is never gated — closing something is not a privileged act.
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        var hiddenPw by remember { mutableStateOf("") }
        var hiddenPwError by remember { mutableStateOf(false) }
        var askingHiddenPw by remember { mutableStateOf(false) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.hiddenSettingTitle, color = onGradient)
                Spacer(modifier = Modifier.height(4.dp))
                Text(S.hiddenSettingDesc, color = onGradientMuted, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = HiddenArea.visible,
                onCheckedChange = { turnOn ->
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
                        if (AppLock.verifyPassword(appLockCreds, hiddenPw)) {
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
