package com.lucent.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.ShizukuShell
import com.lucent.app.data.ShizukuState
import com.lucent.app.data.ShizukuWatcher
import com.lucent.app.i18n.S

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuNoticeDialog() {
    val state = ShizukuWatcher.notice ?: return
    val context = LocalContext.current

    when (state) {
        ShizukuState.NOT_INSTALLED -> AlertDialog(
            onDismissRequest = { ShizukuWatcher.acknowledge() },
            title = { Text(S.shizukuInstallTitle) },
            text = { Text(S.shizukuInstallBody) },
            confirmButton = {
                TextButton(onClick = {
                    ShizukuShell.openDownloadPage(context)
                    ShizukuWatcher.acknowledge()
                }) { Text(S.shizukuActionInstall) }
            },
            dismissButton = {
                TextButton(onClick = { ShizukuWatcher.acknowledge() }) { Text(S.actionDismiss) }
            }
        )

        ShizukuState.NOT_RUNNING -> ModalBottomSheet(
            onDismissRequest = { ShizukuWatcher.acknowledge() },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Text(S.shizukuStartTitle, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(S.shizukuStartBody, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    GlassButton(
                        text = S.shizukuActionOpen,
                        onClick = {
                            ShizukuShell.openManager(context)
                            ShizukuWatcher.acknowledge()
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    GlassButton(
                        text = S.actionDismiss,
                        onClick = { ShizukuWatcher.acknowledge() }
                    )
                }
            }
        }

        ShizukuState.NO_PERMISSION -> AlertDialog(
            onDismissRequest = { ShizukuWatcher.acknowledge() },
            title = { Text(S.shizukuGrantTitle) },
            text = { Text(S.shizukuGrantBody) },
            confirmButton = {
                TextButton(onClick = {
                    ShizukuShell.requestPermission(context)
                    ShizukuWatcher.acknowledge()
                }) { Text(S.shizukuActionGrant) }
            },
            dismissButton = {
                TextButton(onClick = { ShizukuWatcher.acknowledge() }) { Text(S.actionDismiss) }
            }
        )

        ShizukuState.READY -> Unit
    }
}
