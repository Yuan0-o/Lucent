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
import com.lucent.app.ui.NavCard
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass

@Composable
internal fun AgentSettingsPage(onRoute: (SettingsRoute) -> Unit) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var config by remember { mutableStateOf(HarnessRuntime.config()) }

    fun update(next: HarnessConfig) {
        config = next
        HarnessRuntime.update(next)
    }

    BackHeader(onBack = { onRoute(SettingsRoute.Advanced) })
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            ToolkitToggleRow(S.agentToolkitEnabled, S.agentToolkitEnabledSub, config.enabled, onGradient, onGradientMuted) {
                update(config.copy(enabled = it))
            }
            if (!config.enabled) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(S.agentToolkitOffHint, color = onGradientMuted, fontSize = 11.sp)
            }
        }
        if (config.enabled) {
            Spacer(modifier = Modifier.height(12.dp))
            toolkitPages().forEach { page ->
                NavCard(page.title, page.subtitle) { onRoute(page.route) }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

internal data class ToolkitPage(val title: String, val subtitle: String, val route: SettingsRoute)

internal fun toolkitPages(): List<ToolkitPage> = listOf(
    ToolkitPage(S.agentWorkspaceTitle, S.agentWorkspaceSub, SettingsRoute.Workspace),
    ToolkitPage(S.agentCapabilitiesTitle, S.agentCapabilitiesSub, SettingsRoute.Capabilities),
    ToolkitPage(S.agentPermissionsTitle, S.agentPermissionsSub, SettingsRoute.Permissions),
    ToolkitPage(S.agentGroupsTitle, S.agentGroupsSub, SettingsRoute.Groups),
    ToolkitPage(S.agentSandboxTitle, S.agentSandboxSub, SettingsRoute.Execution),
    ToolkitPage(S.agentGithubTitle, S.agentGithubSub, SettingsRoute.Github),
    ToolkitPage(S.agentPluginsTitle, S.agentPluginsSub, SettingsRoute.Plugins),
    ToolkitPage(S.agentMcpTitle, S.agentMcpSub, SettingsRoute.Mcp),
    ToolkitPage(S.agentAuditTitle, S.agentAuditSub, SettingsRoute.Audit)
)

@Composable
internal fun Section(
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    notes: List<String> = emptyList(),
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(title, color = onGradient, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = onGradientMuted, fontSize = 11.sp)
            notes.forEach { note ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(note, color = onGradientMuted, fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            content()
        }
    }
}

@Composable
internal fun ToolkitToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (enabled) onGradient else onGradientMuted, fontSize = 14.sp)
            Text(subtitle, color = onGradientMuted, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}
