package com.lucent.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.AutoUpdate
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import kotlinx.coroutines.launch

@Composable
fun AutoUpdateDialog(repo: SettingsRepository, onOpenUrl: ((String) -> Unit)? = null) {
    val info = AutoUpdate.offered ?: return
    if (AutoUpdate.hidden) return
    val phase = AutoUpdate.phase
    val ready = AutoUpdate.readyForInstall
    val downloading = phase == AutoUpdate.Phase.DOWNLOADING
    val installing = phase == AutoUpdate.Phase.INSTALLING
    val scope = rememberCoroutineScope()
    val busy = downloading || installing

    val pickFolder = rememberBackupFolderPicker { folder ->
        scope.launch {
            val current = repo.autoBackupOnce()
            repo.setAutoBackup(current.copy(folderUri = folder))
            AutoUpdate.downloadFolderChosen()
            AutoUpdate.downloadOffered()
        }
    }

    AlertDialog(
        onDismissRequest = { if (downloading) AutoUpdate.hide() else if (!busy) AutoUpdate.later() },
        title = { Text(S.updateAvailableTitle) },
        text = {
            Column {
                if (AutoUpdate.awaitingFolder) {
                    Text(S.updateNeedsFolder)
                } else {
                    Text(if (ready) S.updateReadyBody(info.version) else S.updateAvailableBody(info.version))
                }
                if (downloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val fraction = AutoUpdate.progress
                    if (fraction >= 0f) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("${(fraction * 100f).toInt()}%", fontSize = 12.sp)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(S.updateDownloading, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(S.updateBackgroundHint, fontSize = 12.sp)
                }
                if (installing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(S.updateInstalling, fontSize = 12.sp)
                }
                AutoUpdate.message?.let { line ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(line, fontSize = 12.sp)
                }
                if (!downloading && !installing && onOpenUrl != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ReleaseNotesLink(url = info.releaseUrl, onOpenUrl = onOpenUrl)
                }
            }
        },
        confirmButton = {
            if (AutoUpdate.awaitingFolder) {
                TextButton(onClick = { pickFolder() }) { Text(S.updateChooseFolder) }
            } else {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        AutoUpdate.report(null)
                        if (AutoUpdate.downloadOffered() && AutoUpdate.readyForInstall) {
                            scope.launch {
                                if (!AutoUpdate.installOffered()) AutoUpdate.report(S.updateInstallFailed)
                            }
                        }
                    }
                ) { Text(S.updateInstallNow) }
            }
        },
        dismissButton = {
            Row {
                if (downloading) {
                    TextButton(onClick = { AutoUpdate.hide() }) { Text(S.updateHide) }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = { AutoUpdate.cancelDownload() }) { Text(S.actionCancel) }
                } else if (!installing) {
                    TextButton(onClick = { AutoUpdate.later() }) { Text(S.updateLater) }
                }
            }
        }
    )
}

@Composable
fun ReleaseNotesLink(url: String, onOpenUrl: (String) -> Unit, fontSize: androidx.compose.ui.unit.TextUnit = 12.sp) {
    Text(
        text = S.updateNotesOnGitHub,
        color = LocalOnGradient.current,
        fontSize = fontSize,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable { onOpenUrl(url) }.padding(vertical = 2.dp)
    )
}
