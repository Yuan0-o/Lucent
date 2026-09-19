package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.AutoBackup
import com.lucent.app.data.AutoBackupRunner
import com.lucent.app.data.DatabaseEncryption
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.ExportKind
import com.lucent.app.ui.GlassButton
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.StepperRow
import com.lucent.app.ui.formatTimestamp
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.rememberBackupFolderPicker
import kotlinx.coroutines.launch

/**
 * P1-3 — extracted from the `DataPage` local composable that used to live inside `SettingsScreen`,
 * the last and largest of the sixteen pages. Diffing the two originals found the same shape of
 * difference twice, both around picking a location on disk:
 * - The "choose backup folder" flow: Android needs a full `ActivityResultContracts` launcher
 *   registration (including persisting the URI grant, or the folder stops being writable the
 *   moment the process dies); desktop is a synchronous native dialog. Now
 *   [rememberBackupFolderPicker] — unlike font/model import, this seam also owns *what happens
 *   with the result*, not just the trigger, because both platforms need to feed the picked
 *   location into the same `AutoBackup.State` update and there was no reason to make each
 *   platform's `SettingsScreen.kt` repeat that.
 * - "Import backup" triggers through [onImportBackupClick], the same bare-trigger shape as every
 *   other file-picker seam in this batch.
 *
 * The auto-backup section's state (`loadedAuto`/`autoState`/`autoLoaded`) was already entirely
 * local to `DataPage`'s own body in both originals — nothing outside the page touched it — so it
 * moved here unchanged rather than needing to be threaded through as parameters.
 *
 * Everything else — the locked-database notice, the backup/restore card, the export-to-file
 * flow, the four destructive-clear buttons — reads or triggers outer-scope state the same way
 * the rest of this batch does: [lockedNotice]/[lockedNoticeFileName] are read-only,
 * [onLockedDismissedChange] and the four `onRequestClear*` calls are bare triggers for dialogs
 * that live in, and stay in, `SettingsScreen`, and [onRequestExportKind] sets the (now
 * `internal`, like `SettingsRoute`) [ExportKind] that drives the separate export-picker overlay
 * rendered outside this page entirely.
 */
@Composable
internal fun DataSettingsPage(
    repo: SettingsRepository,
    lockedNotice: String?,
    lockedNoticeFileName: String?,
    lockedDismissed: Boolean,
    onLockedDismissedChange: (Boolean) -> Unit,
    backupStatus: String,
    onImportBackupClick: () -> Unit,
    onRequestExportBackup: () -> Unit,
    onRequestExportKind: (ExportKind) -> Unit,
    onRequestClearNotes: () -> Unit,
    onRequestClearTasks: () -> Unit,
    onRequestClearChats: () -> Unit,
    onRequestClearData: () -> Unit,
    onRoute: (SettingsRoute) -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()

    BackHeader(S.settingsDataTitle) { onRoute(SettingsRoute.Root) }

    // Shown only in the (rare, alarming) case where the database couldn't be decrypted.
    // Nothing was deleted — the old file was set aside — but silence here would leave
    // someone staring at an empty app with no idea why, and no idea what to do.
    if (lockedNotice != null && !lockedDismissed) {
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(S.lockedNoticeTitle, color = Color(0xFFFF8A80))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (lockedNoticeFileName != null) S.lockedNoticeBody(lockedNoticeFileName)
                else lockedNotice,
                color = onGradientMuted,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                GlassButton(text = S.importBackup, onClick = onImportBackupClick)
                Spacer(modifier = Modifier.width(12.dp))
                GlassButton(text = S.actionDismiss, onClick = {
                    DatabaseEncryption.clearLockedNotice(context)
                    onLockedDismissedChange(true)
                })
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // ================================================================================
    //  Task 14 — automatic backup
    // ================================================================================
    //
    // [AutoBackup] has always held the policy — when a run is due, what to call the file,
    // which old ones may be deleted — and until now nothing called any of it. This card and
    // [AutoBackupRunner] are the two halves that were missing.
    //
    // A folder is not optional and the switch says so rather than failing quietly later:
    // AutoBackup.State.runnable is `enabled && folderUri.isNotBlank()`, and that is exactly
    // what starts the loop below.
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        // Task 7 — the retention and interval controls used to show the DEFAULTS for a
        // beat and then snap to the stored values.
        //
        // `collectAsState(initial = EMPTY)` has to render something before DataStore has
        // read anything back, and EMPTY is "12 hours, keep 5" — so every visit to this page
        // displayed a number the user had not chosen, then corrected itself. Collecting into
        // a nullable and drawing the numbers only once something real has arrived removes
        // the wrong value entirely; there is nothing to flash back from.
        var loadedAuto by remember { mutableStateOf<AutoBackup.State?>(null) }
        LaunchedEffect(Unit) { repo.autoBackup.collect { loadedAuto = it } }
        val autoState = loadedAuto ?: AutoBackup.State.EMPTY
        val autoLoaded = loadedAuto != null
        val pickBackupFolder = rememberBackupFolderPicker(onFolderPicked = { newUri ->
            scope.launch { repo.setAutoBackup(autoState.copy(folderUri = newUri)) }
        })
        // Starting the loop from here is safe to repeat: ensureStarted() is idempotent, so
        // every visit to this page simply confirms what is already running.
        LaunchedEffect(autoState.runnable) {
            if (autoState.runnable) AutoBackupRunner.ensureStarted(context)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.autoBackupTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = autoState.enabled,
                // Task 3.6 — turning this on without a folder used to be allowed, and it
                // produced a switch that read as ON while AutoBackup.State.runnable was
                // false, so nothing ever ran and nothing ever said why. The switch now
                // refuses and names the missing piece.
                onCheckedChange = { on ->
                    if (on && autoState.folderUri.isBlank()) {
                        LucentToast.show(context, S.autoBackupNeedsFolder)
                    } else {
                        scope.launch { repo.setAutoBackup(autoState.copy(enabled = on)) }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(S.autoBackupFolder, color = onGradient, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            autoState.folderUri.ifBlank { S.autoBackupNeedsFolder },
            color = onGradientMuted,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        GlassButton(text = S.autoBackupChooseFolder, compact = true, onClick = pickBackupFolder)

        Spacer(modifier = Modifier.height(12.dp))
        // Fixed choices, not free entry: AutoBackup.MIN_INTERVAL_HOURS is a floor rather than
        // a suggestion, and offering a number the feature would silently clamp is worse than
        // not offering it.
        Text(S.autoBackupInterval, color = onGradient, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        // ---- Task 3: four chips, all the same size, in every language ----
        //
        // One row cannot do it. The longest label ("Once a week" — 일주일에 한 번 in
        // Korean) needs roughly half a phone's width on its own, so four of them across can
        // only be achieved by squeezing, which is the defect this started as.
        //
        // Two rows of two, each cell weighted, gives the requirement exactly: every chip is
        // one half of the row, so all four are identical in size regardless of how many
        // characters their label happens to have — which is the point of a chip group. The
        // label is capped at one line (see GlassButton) and shortens if it ever has to,
        // rather than the chip changing shape around it.
        if (autoLoaded) {
            AutoBackup.INTERVAL_CHOICES.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { hours ->
                        GlassButton(
                            // The catalogue already names the two round numbers ("Once a
                            // day", "Once a week") and falls back to "Every N hours" for the
                            // rest, so the chips read as language rather than as arithmetic.
                            text = when (hours) {
                                24 -> S.autoBackupEveryDay
                                24 * 7 -> S.autoBackupEveryWeek
                                else -> S.autoBackupEvery(hours)
                            },
                            compact = true,
                            enabled = autoState.intervalHours != hours,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch { repo.setAutoBackup(autoState.copy(intervalHours = hours)) }
                            }
                        )
                    }
                    // An odd count would leave the last chip double width; balance it.
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (autoLoaded) {
            StepperRow(
                label = S.autoBackupKeep,
                value = autoState.keep,
                range = AutoBackup.KEEP_RANGE
            ) { v -> scope.launch { repo.setAutoBackup(autoState.copy(keep = v)) } }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (autoState.lastRunAt > 0L) S.autoBackupLastRun(formatTimestamp(autoState.lastRunAt))
            else S.autoBackupNever,
            color = onGradientMuted,
            fontSize = 12.sp
        )
        // A backup feature that has been failing in silence for a month is worse than none,
        // which at least nobody was relying on. So the last failure is shown, not swallowed.
        if (autoState.lastError.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(S.autoBackupFailed(autoState.lastError), color = Color(0xFFFF8A80), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))
        GlassButton(
            text = S.autoBackupRunNow,
            compact = true,
            enabled = autoState.folderUri.isNotBlank(),
            onClick = {
                scope.launch {
                    val err = AutoBackupRunner.runNow(context)
                    // Report the OUTCOME, not the button label: a toast that echoes the
                    // button ("Back up now") after the user pressed it says nothing about
                    // whether the backup actually happened. runNow returns null on success
                    // and the error text otherwise, so the message is chosen from that.
                    LucentToast.show(context, if (err == null) S.backupNowSucceeded else S.backupNowFailed)
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.backupRestoreTitle, color = onGradient)
        Spacer(modifier = Modifier.height(12.dp))
        Row {
            GlassButton(text = S.exportBackup, onClick = onRequestExportBackup)
            Spacer(modifier = Modifier.width(12.dp))
            // Allow any file so a beginner can always locate their .lcb even when the
            // device reports an unexpected MIME type for it. The import path validates the
            // content itself — it requires a Lucent .lcb envelope and rejects anything else
            // with a clear message (legacy ZIP/JSON support has been removed, task 5).
            GlassButton(text = S.importBackup, onClick = onImportBackupClick)
        }
        if (backupStatus.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(backupStatus, color = onGradientMuted)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(S.exportNotesTasksTitle, color = onGradient)
        Spacer(modifier = Modifier.height(12.dp))
        // Two full-width buttons, tasks first (task 9). They line up cleanly instead of the
        // old mismatched row, and each opens the pick-items-and-format screen.
        GlassButton(
            text = S.chooseTasksToExport,
            onClick = { onRequestExportKind(ExportKind.TASKS) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        GlassButton(
            text = S.chooseNotesToExport,
            onClick = { onRequestExportKind(ExportKind.NOTES) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text(S.dangerZone, color = onGradient)
        Spacer(modifier = Modifier.height(8.dp))
        // Targeted clears, then the full wipe. All four are identical full-width glass
        // pills in the danger tint; each asks for confirmation. Labels are kept short so
        // they fit on one line while still making each button's function obvious.
        //
        // These are the buttons task 11 was pointing at: they were solid Material red,
        // the only fully opaque objects on a page otherwise made of glass. They keep the
        // red — a destructive action should look destructive — but wear it as a tint on
        // the app's own material instead of arriving in someone else's.
        GlassButton(
            text = S.clearNotesBtn,
            onClick = onRequestClearNotes,
            danger = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        GlassButton(
            text = S.clearTasksBtn,
            onClick = onRequestClearTasks,
            danger = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        GlassButton(
            text = S.clearChatsBtn,
            onClick = onRequestClearChats,
            danger = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        GlassButton(
            text = S.clearAllDataBtn,
            onClick = onRequestClearData,
            danger = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
