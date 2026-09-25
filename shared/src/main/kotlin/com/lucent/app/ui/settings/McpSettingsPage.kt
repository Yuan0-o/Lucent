package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.harness.HarnessConfig
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.harness.ConnectorConfig
import com.lucent.app.harness.McpServer
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass

@Composable
internal fun McpSettingsPage(onRoute: (SettingsRoute) -> Unit) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var config by remember { mutableStateOf(HarnessRuntime.config()) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    fun update(next: HarnessConfig) {
        config = next
        HarnessRuntime.update(next)
    }

    BackHeader(onBack = { onRoute(SettingsRoute.Agent) })
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(S.agentMcpTitle, color = onGradient, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(S.agentMcpSub, color = onGradientMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(10.dp))
            if (config.mcpServers.isEmpty()) {
                Text(S.agentMcpEmpty, color = onGradientMuted, fontSize = 12.sp)
            } else {
                config.mcpServers.forEach { server ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(server.name, color = onGradient, fontSize = 14.sp)
                            Text(
                                server.url.ifBlank { server.command },
                                color = onGradientMuted,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                        Switch(
                            checked = server.enabled,
                            onCheckedChange = { update(config.withMcp(server.copy(enabled = it))) }
                        )
                        TextButton(onClick = { update(config.withoutMcp(server.id)) }) {
                            Text("✕", color = onGradientMuted, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(S.agentMcpAdd, color = onGradient, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(S.agentMcpName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(S.agentMcpUrl) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text(S.agentMcpCommand) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(S.agentMcpToken) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                enabled = name.isNotBlank() && (url.isNotBlank() || command.isNotBlank()),
                onClick = {
                    val id = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    if (id.isNotBlank()) {
                        update(
                            config.withMcp(
                                McpServer(
                                    id = id,
                                    name = name.trim(),
                                    url = url.trim(),
                                    command = command.trim(),
                                    token = token.trim()
                                )
                            )
                        )
                        name = ""
                        url = ""
                        command = ""
                        token = ""
                    }
                }
            ) {
                Text(S.agentMcpAddAction, color = onGradient, fontSize = 13.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        ConnectorEditor(config = config, onUpdate = { update(it) }, onGradient = onGradient, onGradientMuted = onGradientMuted)
    }
}

private val CONNECTOR_IDS = listOf("notion", "slack", "gdrive", "onedrive", "gitlab", "jira", "linear", "webdav")

@Composable
private fun ConnectorEditor(
    config: HarnessConfig,
    onUpdate: (HarnessConfig) -> Unit,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color
) {
    var open by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var base by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.agentConnectorsTitle, color = onGradient, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(S.agentConnectorsSub, color = onGradientMuted, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(8.dp))
        CONNECTOR_IDS.forEach { id ->
            val existing = config.connector(id)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(id, color = onGradient, fontSize = 14.sp)
                    Text(
                        if (existing == null || existing.token.isBlank()) S.agentConnectorEmpty else S.agentConnectorSet,
                        color = onGradientMuted,
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = {
                    open = if (open == id) "" else id
                    token = existing?.token.orEmpty()
                    account = existing?.account.orEmpty()
                    base = existing?.baseUrl.orEmpty()
                }) {
                    Text(if (open == id) S.actionCancel else S.actionSave, color = onGradient, fontSize = 13.sp)
                }
                if (existing != null) {
                    TextButton(onClick = { onUpdate(config.withConnector(ConnectorConfig(id = id))) }) {
                        Text("✕", color = onGradientMuted, fontSize = 14.sp)
                    }
                }
            }
            if (open == id) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(S.agentConnectorToken) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text(S.agentConnectorAccount) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it },
                    label = { Text(S.agentConnectorBase) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = {
                    onUpdate(
                        config.withConnector(
                            ConnectorConfig(id = id, token = token.trim(), account = account.trim(), baseUrl = base.trim())
                        )
                    )
                    open = ""
                }) {
                    Text(S.actionSave, color = onGradient, fontSize = 13.sp)
                }
            }
        }
    }
}


@Composable
internal fun AuditSettingsPage(onRoute: (SettingsRoute) -> Unit) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val entries = remember(refresh) { com.lucent.app.harness.AuditTrail.entries(context, 150) }

    BackHeader(onBack = { onRoute(SettingsRoute.Agent) })
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(S.agentAuditTitle, color = onGradient, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(S.agentAuditSub, color = onGradientMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text(S.agentAuditEmpty, color = onGradientMuted, fontSize = 12.sp)
            } else {
                entries.forEach { entry ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            com.lucent.app.harness.AuditTrail.format(entry),
                            color = onGradient,
                            fontSize = 12.sp
                        )
                        if (entry.arguments.isNotBlank()) {
                            Text(entry.arguments, color = onGradientMuted, fontSize = 10.sp, maxLines = 2)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                TextButton(onClick = { refresh++ }) {
                    Text(S.actionRefresh, color = onGradient, fontSize = 13.sp)
                }
                TextButton(onClick = {
                    com.lucent.app.harness.AuditTrail.clear(context)
                    refresh++
                }) {
                    Text(S.actionClear, color = onGradient, fontSize = 13.sp)
                }
            }
        }
    }
}
