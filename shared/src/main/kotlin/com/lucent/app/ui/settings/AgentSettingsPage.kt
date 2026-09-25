package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import com.lucent.app.harness.Approval
import com.lucent.app.harness.HarnessConfig
import com.lucent.app.harness.HarnessGroup
import com.lucent.app.harness.HarnessPermission
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass

@Composable
internal fun AgentSettingsPage(
    onRoute: (SettingsRoute) -> Unit,
    onOpenAccessibility: () -> Unit = {},
    onGrantStorage: () -> Unit = {},
    accessibilityRunning: Boolean = true
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var config by remember { mutableStateOf(HarnessRuntime.config()) }
    var workspace by remember { mutableStateOf(config.workspace) }
    var github by remember { mutableStateOf(config.githubToken) }

    fun update(next: HarnessConfig) {
        config = next
        HarnessRuntime.update(next)
    }

    BackHeader(onBack = { onRoute(SettingsRoute.Root) })
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Section(onGradient, onGradientMuted, S.agentToolkitTitle, S.agentToolkitSub) {
            ToggleRow(S.agentToolkitEnabled, S.agentToolkitEnabledSub, config.enabled, onGradient, onGradientMuted) {
                update(config.copy(enabled = it))
            }
            ToggleRow(S.agentSubAgents, S.agentSubAgentsSub, config.subAgents, onGradient, onGradientMuted) {
                update(config.copy(subAgents = it))
            }
            ToggleRow(S.agentSnapshots, S.agentSnapshotsSub, config.snapshots, onGradient, onGradientMuted) {
                update(config.copy(snapshots = it))
            }
            ToggleRow(S.agentDeviceControl, S.agentDeviceControlSub, config.deviceEnabled, onGradient, onGradientMuted) {
                update(config.copy(deviceEnabled = it))
            }
            if (!accessibilityRunning) {
                TextButton(onClick = onOpenAccessibility) {
                    Text(S.agentEnableAccessibility, color = onGradient, fontSize = 13.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Section(onGradient, onGradientMuted, S.agentWorkspaceTitle, S.agentWorkspaceSub) {
            OutlinedTextField(
                value = workspace,
                onValueChange = { workspace = it },
                label = { Text(S.agentWorkspaceLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(HarnessRuntime.workspace().path, color = onGradientMuted, fontSize = 11.sp)
            HarnessRuntime.host?.workspaceCandidates()?.take(4)?.forEach { candidate ->
                TextButton(onClick = {
                    workspace = candidate
                    update(config.copy(workspace = candidate))
                }) {
                    Text(candidate, color = onGradient, fontSize = 12.sp)
                }
            }
            Row {
                TextButton(onClick = { update(config.copy(workspace = workspace.trim())) }) {
                    Text(S.actionSave, color = onGradient, fontSize = 13.sp)
                }
                if (!accessibilityRunning) {
                    TextButton(onClick = onGrantStorage) {
                        Text(S.agentGrantStorage, color = onGradient, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Section(onGradient, onGradientMuted, S.agentPermissionsTitle, S.agentPermissionsSub) {
            HarnessPermission.entries.forEach { permission ->
                val current = config.approvalFor(permission)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(permission.title, color = onGradient, fontSize = 14.sp)
                        Text(permission.detail, color = onGradientMuted, fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        val next = when (current) {
                            Approval.ALLOW -> Approval.CONFIRM
                            Approval.CONFIRM -> Approval.DENY
                            Approval.DENY -> Approval.ALLOW
                        }
                        update(config.withApproval(permission, next))
                    }) {
                        Text(current.title, color = onGradient, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Section(onGradient, onGradientMuted, S.agentGroupsTitle, S.agentGroupsSub) {
            HarnessGroup.entries.forEach { group ->
                val on = config.groupEnabled(group)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text(group.title, color = onGradient, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(checked = on, onCheckedChange = { update(config.withGroup(group, it)) })
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Section(onGradient, onGradientMuted, S.agentSandboxTitle, S.agentSandboxSub) {
            listOf("auto", "direct", "docker", "proot", "none").forEach { mode ->
                TextButton(onClick = { update(config.copy(sandboxMode = mode)) }) {
                    Text(
                        (if (config.sandboxMode == mode) "• " else "") + mode,
                        color = if (config.sandboxMode == mode) onGradient else onGradientMuted,
                        fontSize = 13.sp
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.agentTimeout, color = onGradient, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { update(config.copy(timeoutSeconds = (config.timeoutSeconds - 60).coerceAtLeast(30))) }) {
                    Text("-", color = onGradient)
                }
                Text("${config.timeoutSeconds}s", color = onGradient, fontSize = 13.sp)
                TextButton(onClick = { update(config.copy(timeoutSeconds = (config.timeoutSeconds + 60).coerceAtMost(3600))) }) {
                    Text("+", color = onGradient)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.agentSubAgentLimit, color = onGradient, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { update(config.copy(maxSubAgents = (config.maxSubAgents - 1).coerceAtLeast(1))) }) {
                    Text("-", color = onGradient)
                }
                Text("${config.maxSubAgents}", color = onGradient, fontSize = 13.sp)
                TextButton(onClick = { update(config.copy(maxSubAgents = (config.maxSubAgents + 1).coerceAtMost(8))) }) {
                    Text("+", color = onGradient)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Section(onGradient, onGradientMuted, S.agentGithubTitle, S.agentGithubSub) {
            OutlinedTextField(
                value = github,
                onValueChange = { github = it },
                label = { Text(S.agentGithubToken) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { update(config.copy(githubToken = github.trim())) }) {
                Text(S.actionSave, color = onGradient, fontSize = 13.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            NavRow(S.agentPluginsTitle, onGradient, onGradientMuted) { onRoute(SettingsRoute.Plugins) }
            Spacer(modifier = Modifier.height(8.dp))
            NavRow(S.agentMcpTitle, onGradient, onGradientMuted) { onRoute(SettingsRoute.Mcp) }
            Spacer(modifier = Modifier.height(8.dp))
            NavRow(S.agentAuditTitle, onGradient, onGradientMuted) { onRoute(SettingsRoute.Audit) }
        }
    }
}

@Composable
internal fun Section(
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(title, color = onGradient, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(subtitle, color = onGradientMuted, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
internal fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = onGradient, fontSize = 14.sp)
            Text(subtitle, color = onGradientMuted, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NavRow(
    title: String,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = onGradient, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("›", color = onGradientMuted, fontSize = 16.sp)
    }
}
