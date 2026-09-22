package com.lucent.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.AutoUpdate
import com.lucent.app.i18n.S
import kotlinx.coroutines.launch

@Composable
fun AutoUpdateDialog() {
    val info = AutoUpdate.offered ?: return
    val phase = AutoUpdate.phase
    val scope = rememberCoroutineScope()
    val busy = phase != AutoUpdate.Phase.IDLE

    AlertDialog(
        onDismissRequest = { if (!busy) AutoUpdate.dismiss() },
        title = { Text(S.updateAvailableTitle) },
        text = {
            Column {
                Text(S.updateAvailableBody(info.version))
                val notes = info.notes.trim()
                if (notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(notes, fontSize = 12.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                AutoUpdate.message?.let { line ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(line, fontSize = 12.sp)
                }
                if (busy) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        when (phase) {
                            AutoUpdate.Phase.DOWNLOADING -> S.updateDownloading
                            AutoUpdate.Phase.INSTALLING -> S.updateInstalling
                            AutoUpdate.Phase.CHECKING -> S.updateChecking
                            AutoUpdate.Phase.IDLE -> ""
                        },
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    AutoUpdate.report(null)
                    scope.launch {
                        if (!AutoUpdate.installOffered()) AutoUpdate.report(S.updateInstallFailed)
                    }
                }
            ) { Text(S.updateInstallNow) }
        },
        dismissButton = {
            TextButton(onClick = { AutoUpdate.dismiss() }) { Text(S.updateLater) }
        }
    )
}
