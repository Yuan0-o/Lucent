package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import com.lucent.app.platform.LocalPlatformContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.AutoBackup
import com.lucent.app.data.AutoBackupRunner
import com.lucent.app.data.DatabaseEncryption
import com.lucent.app.data.SettingsCache
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

@Composable
fun DataSettingsPage(
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
    val context = LocalPlatformContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()

    BackHeader(onBack = { onRoute(SettingsRoute.Root) })

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

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        var loadedAuto by remember { mutableStateOf<AutoBackup.State?>(SettingsCache.autoBackup) }
        LaunchedEffect(Unit) { repo.autoBackup.collect { loadedAuto = it } }
        val autoState = loadedAuto ?: AutoBackup.State.EMPTY
        val autoLoaded = loadedAuto != null
        val pickBackupFolder = rememberBackupFolderPicker(onFolderPicked = { newUri ->
            scope.launch { repo.setAutoBackup(autoState.copy(folderUri = newUri)) }
        })
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
        Text(S.autoBackupInterval, color = onGradient, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        if (autoLoaded) {
            AutoBackup.INTERVAL_CHOICES.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { hours ->
                        GlassButton(
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
                    LucentToast.show(context, if (err == null) S.backupNowSucceeded else S.backupNowFailed)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(S.autoBackupPasswordsTitle, color = onGradient)
        Spacer(modifier = Modifier.height(4.dp))
        Text(S.autoBackupPasswordsDesc, color = onGradientMuted, fontSize = 12.sp)

        var autoPasswords by remember { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(Unit) { repo.autoBackupPasswords.collect { autoPasswords = it } }

        var newPasswordDraft by remember { mutableStateOf("") }
        var newPasswordVisible by remember { mutableStateOf(false) }

        Spacer(modifier = Modifier.height(8.dp))
        autoPasswords.forEachIndexed { index, currentPw ->
            var slotDraft by remember(currentPw) { mutableStateOf(currentPw) }
            var slotVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = slotDraft,
                onValueChange = { slotDraft = com.lucent.app.collapseExcessBlankLines(it) },
                label = { Text(S.autoBackupPasswordSlot(index + 1)) },
                singleLine = true,
                visualTransformation = if (slotVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { slotVisible = !slotVisible }) {
                        Icon(
                            if (slotVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (slotVisible) S.hidePassword else S.showPassword
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButton(
                    text = S.autoBackupChangePassword,
                    compact = true,
                    enabled = slotDraft.isNotBlank() && slotDraft != currentPw,
                    onClick = {
                        scope.launch { repo.setAutoBackupPassword(index, slotDraft) }
                    }
                )
                GlassButton(
                    text = S.autoBackupRemovePassword,
                    compact = true,
                    danger = true,
                    onClick = {
                        scope.launch { repo.setAutoBackupPassword(index, "") }
                    }
                )
            }
        }

        if (autoPasswords.size < 3) {
            OutlinedTextField(
                value = newPasswordDraft,
                onValueChange = { newPasswordDraft = com.lucent.app.collapseExcessBlankLines(it) },
                label = { Text(S.autoBackupAddPassword) },
                singleLine = true,
                visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                        Icon(
                            if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (newPasswordVisible) S.hidePassword else S.showPassword
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            GlassButton(
                text = S.autoBackupAddPassword,
                compact = true,
                enabled = newPasswordDraft.isNotBlank(),
                onClick = {
                    val pw = newPasswordDraft
                    newPasswordDraft = ""
                    scope.launch { repo.setAutoBackupPassword(autoPasswords.size, pw) }
                }
            )
        } else {
            Text(S.autoBackupMaxPasswords, color = onGradientMuted, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        var recoveryQuestion by remember { mutableStateOf("") }
        var recoveryAnswer by remember { mutableStateOf("") }
        var recoveryLoaded by remember { mutableStateOf(false) }
        var recoveryEnabled by remember { mutableStateOf(false) }
        var questionDraft by remember { mutableStateOf("") }
        var answerDraft by remember { mutableStateOf("") }
        var answerVisible by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            repo.autoBackupRecoveryQuestion.collect { q ->
                recoveryQuestion = q
                if (!recoveryLoaded) {
                    questionDraft = q
                    if (q.isNotBlank()) recoveryEnabled = true
                }
            }
        }
        LaunchedEffect(Unit) {
            repo.autoBackupRecoveryAnswer.collect { a ->
                recoveryAnswer = a
                if (!recoveryLoaded) {
                    answerDraft = a
                    if (a.isNotBlank()) recoveryEnabled = true
                    recoveryLoaded = true
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.backupRecoveryTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = recoveryEnabled,
                onCheckedChange = { on ->
                    recoveryEnabled = on
                    if (!on) {
                        questionDraft = ""
                        answerDraft = ""
                        scope.launch { repo.setAutoBackupRecovery("", "") }
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(S.backupRecoveryDesc, color = onGradientMuted, fontSize = 12.sp)

        if (recoveryEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(S.backupRecoveryWarnBody, color = onGradientMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = questionDraft,
                onValueChange = { questionDraft = com.lucent.app.collapseExcessBlankLines(it) },
                label = { Text(S.lockSecurityQuestionFallback) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = answerDraft,
                onValueChange = { answerDraft = com.lucent.app.collapseExcessBlankLines(it) },
                label = { Text(S.lockAnswer) },
                singleLine = true,
                visualTransformation = if (answerVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { answerVisible = !answerVisible }) {
                        Icon(
                            if (answerVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (answerVisible) S.hidePassword else S.showPassword
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            GlassButton(
                text = S.actionSave,
                compact = true,
                enabled = questionDraft.isNotBlank() && answerDraft.isNotBlank() && (questionDraft != recoveryQuestion || answerDraft != recoveryAnswer),
                onClick = {
                    recoveryQuestion = questionDraft
                    recoveryAnswer = answerDraft
                    scope.launch { repo.setAutoBackupRecovery(questionDraft, answerDraft) }
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.backupRestoreTitle, color = onGradient)
        Spacer(modifier = Modifier.height(12.dp))
        Row {
            GlassButton(text = S.exportBackup, onClick = onRequestExportBackup)
            Spacer(modifier = Modifier.width(12.dp))
            GlassButton(text = S.importBackup, onClick = onImportBackupClick)
        }
        if (backupStatus.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(backupStatus, color = onGradientMuted)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(S.exportNotesTasksTitle, color = onGradient)
        Spacer(modifier = Modifier.height(12.dp))
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

        Spacer(modifier = Modifier.height(12.dp))
        Text(S.dangerZone, color = onGradient)
        Spacer(modifier = Modifier.height(8.dp))
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
