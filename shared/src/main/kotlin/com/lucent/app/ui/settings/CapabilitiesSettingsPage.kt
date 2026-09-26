package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucent.app.harness.HarnessConfig
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute

@Composable
internal fun CapabilitiesSettingsPage(
    onRoute: (SettingsRoute) -> Unit,
    onOpenAccessibility: () -> Unit = {},
    accessibilityRunning: Boolean = true
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var config by remember { mutableStateOf(HarnessRuntime.config()) }

    fun update(next: HarnessConfig) {
        config = next
        HarnessRuntime.update(next)
    }

    BackHeader(onBack = { onRoute(SettingsRoute.Agent) })
    Column(modifier = Modifier.fillMaxWidth()) {
        Section(onGradient, onGradientMuted, S.agentCapabilitiesTitle, S.agentCapabilitiesSub) {
            ToolkitToggleRow(S.agentSubAgents, S.agentSubAgentsSub, config.subAgents, onGradient, onGradientMuted) {
                update(config.copy(subAgents = it))
            }
            ToolkitToggleRow(S.agentSnapshots, S.agentSnapshotsSub, config.snapshots, onGradient, onGradientMuted) {
                update(config.copy(snapshots = it))
            }
            ToolkitToggleRow(S.agentDeviceControl, S.agentDeviceControlSub, config.deviceEnabled, onGradient, onGradientMuted) {
                update(config.copy(deviceEnabled = it))
            }
            ToolkitToggleRow(S.agentScreenAccess, S.agentScreenAccessSub, accessibilityRunning, onGradient, onGradientMuted) { wanted ->
                if (wanted && !accessibilityRunning) onOpenAccessibility()
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
