package com.lucent.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.lucent.app.data.ShizukuShell
import com.lucent.app.data.ShizukuWatcher
import com.lucent.app.i18n.S

@Composable
fun ShizukuLostDialog() {
    if (!ShizukuWatcher.lost) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { ShizukuWatcher.acknowledge() },
        title = { Text(S.shizukuLostTitle) },
        text = { Text(S.shizukuLostBody) },
        confirmButton = {
            TextButton(onClick = {
                ShizukuWatcher.acknowledge()
                ShizukuShell.openManager(context)
            }) { Text(S.shizukuActionOpen) }
        },
        dismissButton = {
            TextButton(onClick = { ShizukuWatcher.acknowledge() }) { Text(S.actionDismiss) }
        }
    )
}
