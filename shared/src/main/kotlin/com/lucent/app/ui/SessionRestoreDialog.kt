package com.lucent.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lucent.app.AppScope
import com.lucent.app.Screen
import com.lucent.app.data.SessionRestore
import kotlinx.coroutines.launch

@Composable
fun SessionRestoreDialog() {
    val context = LocalContext.current
    val snapshot = SessionRestore.pending
    var visible by remember { mutableStateOf(!SessionRestore.asked && snapshot != null) }
    if (!visible || snapshot == null) return
    SessionRestore.asked = true

    fun decline() {
        visible = false
        SessionRestore.decline()
        AppScope.io.launch { SessionRestore.clear(context) }
    }

    val title = snapshot.title.ifBlank { com.lucent.app.i18n.S.untitled }
    AlertDialog(
        onDismissRequest = {
            decline()
        },
        title = { Text(com.lucent.app.i18n.S.sessionRestoreTitle) },
        text = {
            Text(
                if (snapshot.kind == SessionRestore.KIND_TASK) {
                    com.lucent.app.i18n.S.sessionRestoreTaskBody(title)
                } else {
                    com.lucent.app.i18n.S.sessionRestoreNoteBody(title)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                visible = false
                SessionRestore.beginRestore(snapshot)
                com.lucent.app.AppNavigation.requestScreen(
                    if (snapshot.kind == SessionRestore.KIND_TASK) Screen.Tasks else Screen.Notes
                )
            }) { Text(com.lucent.app.i18n.S.sessionRestoreConfirm) }
        },
        dismissButton = {
            TextButton(onClick = { decline() }) { Text(com.lucent.app.i18n.S.sessionRestoreDismiss) }
        }
    )
}
