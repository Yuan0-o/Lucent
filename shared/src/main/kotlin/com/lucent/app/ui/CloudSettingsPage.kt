package com.lucent.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.BackupManager
import com.lucent.app.data.CloudSync
import com.lucent.app.data.CryptoUtil
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * v2.7.5 — the cloud storage settings page, shared by Android and Windows.
 *
 * WebDAV is the protocol; the page is the form. Everything here is deliberately plain: a provider
 * preset that fills in a well-known endpoint, the four fields WebDAV needs, a test button, an
 * automatic-backup switch, a "back up now" action, and a restore path that lists the cloud folder,
 * downloads the chosen backup and hands it to the same [BackupManager.inspect]/[BackupManager.commit]
 * pair the local restore uses. The password is sealed with the same [CryptoUtil] the API keys use.
 */
@Composable
fun CloudSettingsPage(
    repo: SettingsRepository,
    showToast: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    val enabled by repo.cloudEnabled.collectAsState(initial = false)
    val provider by repo.cloudProvider.collectAsState(initial = "Nutstore")
    val url by repo.cloudUrl.collectAsState(initial = "")
    val user by repo.cloudUser.collectAsState(initial = "")
    val folder by repo.cloudFolder.collectAsState(initial = "Lucent")
    val autoUpload by repo.cloudAutoBackup.collectAsState(initial = false)
    val storedPw by repo.cloudPasswordEnc.collectAsState(initial = "")

    var urlDraft by remember { mutableStateOf("") }
    var userDraft by remember { mutableStateOf("") }
    var pwDraft by remember { mutableStateOf("") }
    var folderDraft by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var uploadBusy by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var cloudList by remember { mutableStateOf<List<String>?>(null) }
    var pickedBackup by remember { mutableStateOf<String?>(null) }

    // Seed drafts once; changes are saved as user types (the fields are cheap strings).
    LaunchedEffect(url, user, folder, storedPw) {
        if (urlDraft.isEmpty() && url.isNotEmpty()) urlDraft = url
        if (userDraft.isEmpty() && user.isNotEmpty()) userDraft = user
        if (folderDraft.isEmpty() && folder.isNotEmpty()) folderDraft = folder
        if (pwDraft.isEmpty() && storedPw.isNotEmpty()) {
            pwDraft = runCatching { CryptoUtil.decrypt(storedPw) }.getOrDefault("")
        }
    }

    fun config(): CloudSync.Config? {
        val u = urlDraft.trim()
        if (u.isBlank() || userDraft.isBlank() || pwDraft.isBlank()) return null
        return CloudSync.Config(
            url = u,
            user = userDraft.trim(),
            password = pwDraft,
            folder = folderDraft.trim().ifBlank { "Lucent" }
        )
    }

    // The settings root already scrolls; adding verticalScroll here nested a scrollable inside a
    // scrollable and was measured with infinite constraints (crash on open). No scroll of our own.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onGradient)
            }
            Text(S.cloudTitle, color = onGradient, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ---- Enable ----
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = onGradientMuted)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(S.cloudEnableTitle, color = onGradient, fontSize = 16.sp)
                    Text(S.cloudEnableSub, color = onGradientMuted, fontSize = 13.sp)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { scope.launch { repo.setCloudEnabled(it) } }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ---- Provider presets ----
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(S.cloudProviderTitle, color = onGradient, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudSync.PRESETS.forEach { (name, presetUrl) ->
                    FilterChip(
                        selected = provider == name,
                        onClick = {
                            scope.launch {
                                repo.setCloudProvider(name)
                                if (presetUrl.isNotBlank()) {
                                    val current = urlDraft.trim()
                                    val isAnotherPreset = CloudSync.PRESETS.any {
                                        it.second.isNotBlank() && current == it.second
                                    }
                                    if (current.isBlank() || isAnotherPreset) {
                                        urlDraft = presetUrl
                                        repo.setCloudUrl(presetUrl)
                                    }
                                }
                            }
                        },
                        label = { Text(if (name == "Custom") S.cloudCustomProvider else name) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ---- Connection fields ----
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            OutlinedTextField(
                value = urlDraft,
                onValueChange = {
                    urlDraft = it
                    scope.launch { repo.setCloudUrl(it) }
                },
                label = { Text(S.cloudUrlLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = userDraft,
                onValueChange = {
                    userDraft = it
                    scope.launch { repo.setCloudUser(it) }
                },
                label = { Text(S.cloudUserLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = pwDraft,
                onValueChange = {
                    pwDraft = it
                    scope.launch { repo.setCloudPasswordEnc(CryptoUtil.encrypt(it)) }
                },
                label = { Text(S.cloudPasswordLabel) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = folderDraft,
                onValueChange = {
                    folderDraft = it
                    scope.launch { repo.setCloudFolder(it) }
                },
                label = { Text(S.cloudFolderLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                enabled = !testing && config() != null,
                onClick = {
                    val cfg = config() ?: return@TextButton
                    testing = true
                    scope.launch {
                        val r = CloudSync.test(cfg)
                        testing = false
                        showToast(r.fold(
                            onSuccess = { it },
                            onFailure = { S.cloudTestRunning.removeSuffix("…") + ": " + (it.message ?: "?") }
                        ))
                    }
                }
            ) {
                Text(if (testing) S.cloudTestRunning else S.cloudTestButton)
            }
            if (config() == null) {
                Text(S.cloudNeedsConfig, color = onGradientMuted, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ---- Automatic backup upload ----
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(S.cloudAutoBackupTitle, color = onGradient, fontSize = 15.sp)
                    Text(S.cloudAutoBackupSub, color = onGradientMuted, fontSize = 13.sp)
                }
                Switch(
                    checked = autoUpload,
                    onCheckedChange = { scope.launch { repo.setCloudAutoBackup(it) } }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ---- Actions ----
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            var busy by remember { mutableStateOf(false) }
            TextButton(
                enabled = enabled && config() != null && !busy,
                onClick = {
                    val cfg = config() ?: return@TextButton
                    busy = true
                    scope.launch {
                        val r = runCatching {
                            val bytes = ByteArrayOutputStream().use { out ->
                                BackupManager.exportEncrypted(
                                    context, AppDatabase.getInstance(context), repo, out, null
                                )
                                out.toByteArray()
                            }
                            CloudSync.upload(cfg, "lucent-backup-${System.currentTimeMillis()}.lcb", bytes)
                        }
                        busy = false
                        showToast(r.fold(
                            onSuccess = { if (it.isSuccess) S.cloudBackupNowDone else S.cloudBackupNowFailed + (it.exceptionOrNull()?.message ?: "?") },
                            onFailure = { S.cloudBackupNowFailed + (it.message ?: "?") }
                        ))
                    }
                }
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (busy) S.cloudTestRunning else S.cloudBackupNow)
            }
            TextButton(
                enabled = enabled && config() != null && !restoring,
                onClick = {
                    val cfg = config() ?: return@TextButton
                    restoring = true
                    scope.launch {
                        val r = CloudSync.list(cfg)
                        restoring = false
                        r.fold(
                            onSuccess = {
                                if (it.isEmpty()) showToast(S.cloudRestoreEmpty) else cloudList = it
                            },
                            onFailure = {
                                showToast(S.cloudBackupNowFailed + (it.message ?: "?"))
                            }
                        )
                    }
                }
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (restoring) S.cloudTestRunning else S.cloudRestore)
            }
        }
    }

    // ---- Pick a file from the cloud list ----
    cloudList?.let { files ->
        AlertDialog(
            onDismissRequest = { cloudList = null },
            title = { Text(S.cloudRestorePick) },
            text = {
                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    files.forEach { name ->
                        TextButton(onClick = {
                            cloudList = null
                            pickedBackup = name
                        }) {
                            Text(name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { cloudList = null }) { Text(S.actionCancel) } }
        )
    }

    // ---- Confirm the restore, then inspect + commit ----
    pickedBackup?.let { name ->
        AlertDialog(
            onDismissRequest = { pickedBackup = null },
            title = { Text(S.cloudRestore) },
            text = { Text(S.cloudRestoreConfirm(name)) },
            confirmButton = {
                TextButton(onClick = {
                    val cfg = config() ?: run {
                        pickedBackup = null
                        return@TextButton
                    }
                    pickedBackup = null
                    scope.launch {
                        val r = runCatching {
                            val bytes = CloudSync.download(cfg, name).getOrThrow()
                            val db = AppDatabase.getInstance(context)
                            val source = BackupManager.BackupSource { ByteArrayInputStream(bytes) }
                            val preview = BackupManager.inspect(context, source, null)
                            BackupManager.commit(context, db, repo, preview, source = source)
                        }
                        showToast(r.fold(
                            onSuccess = { S.cloudRestoreDone },
                            onFailure = { S.cloudBackupNowFailed + (it.message ?: "?") }
                        ))
                    }
                }) { Text(S.actionConfirm) }
            },
            dismissButton = { TextButton(onClick = { pickedBackup = null }) { Text(S.actionCancel) } }
        )
    }
}
