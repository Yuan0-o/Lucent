package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.harness.HarnessConfig
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass

internal data class AdvancedPrivilegeUi(
    val title: String,
    val description: String,
    val status: String,
    val enabled: Boolean,
    val ready: Boolean,
    val busy: Boolean,
    val actionLabel: String?
)

@Composable
internal fun ShizukuSettingsPage(
    ui: AdvancedPrivilegeUi,
    onToggle: (Boolean) -> Unit,
    onAction: () -> Unit,
    onRoute: (SettingsRoute) -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var config by remember { mutableStateOf(HarnessRuntime.config()) }

    BackHeader(onBack = { onRoute(SettingsRoute.Advanced) })
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(ui.title, color = onGradient, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(ui.description, color = onGradientMuted, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(checked = ui.enabled, enabled = !ui.busy, onCheckedChange = onToggle)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(ui.status, color = if (ui.ready) onGradient else onGradientMuted, fontSize = 12.sp)
            ui.actionLabel?.let { label ->
                TextButton(onClick = onAction) {
                    Text(label, color = onGradient, fontSize = 13.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            ToolkitToggleRow(
                title = S.shizukuForAssistant,
                subtitle = S.shizukuForAssistantSub,
                checked = config.shizukuForAssistant,
                onGradient = onGradient,
                onGradientMuted = onGradientMuted,
                enabled = ui.enabled
            ) { wanted ->
                config = config.copy(shizukuForAssistant = wanted)
                HarnessRuntime.update(config)
            }
        }
    }
}
