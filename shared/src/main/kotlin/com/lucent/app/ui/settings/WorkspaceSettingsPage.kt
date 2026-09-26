package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.harness.HarnessConfig
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.DirectoryPickerDialog
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.SettingsRoute

@Composable
internal fun WorkspaceSettingsPage(
    onRoute: (SettingsRoute) -> Unit,
    onGrantStorage: () -> Unit = {},
    storageGranted: Boolean = true
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var config by remember { mutableStateOf(HarnessRuntime.config()) }
    var draft by remember { mutableStateOf(config.workspace) }
    var picking by remember { mutableStateOf(false) }

    fun update(next: HarnessConfig) {
        config = next
        HarnessRuntime.update(next)
    }

    fun apply(path: String) {
        val chosen = path.trim()
        if (chosen.isEmpty()) return
        draft = chosen
        update(config.copy(workspace = chosen))
        LucentToast.show(context, S.settingsSaved)
    }

    BackHeader(onBack = { onRoute(SettingsRoute.Agent) })
    Column(modifier = Modifier.fillMaxWidth()) {
        Section(onGradient, onGradientMuted, S.agentWorkspaceTitle, S.agentWorkspaceSub) {
            Text(HarnessRuntime.workspace().path, color = onGradientMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(S.agentWorkspaceLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row {
                TextButton(onClick = { picking = true }) {
                    Text(S.agentWorkspacePick, color = onGradient, fontSize = 13.sp)
                }
                if (!storageGranted) {
                    TextButton(onClick = onGrantStorage) {
                        Text(S.agentGrantStorage, color = onGradient, fontSize = 13.sp)
                    }
                }
            }
            Row {
                TextButton(
                    enabled = draft.isNotBlank(),
                    onClick = { apply(draft) }
                ) {
                    Text(S.actionSave, color = onGradient, fontSize = 13.sp)
                }
            }
        }
    }

    if (picking) {
        DirectoryPickerDialog(
            initialPath = draft.trim().ifBlank { HarnessRuntime.workspace().path },
            onDismiss = { picking = false },
            onOpen = { picked ->
                apply(picked)
                picking = false
            }
        )
    }
}
