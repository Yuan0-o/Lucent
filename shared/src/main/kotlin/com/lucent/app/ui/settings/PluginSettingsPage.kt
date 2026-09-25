package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.harness.Workspace
import com.lucent.app.harness.plugins.PluginCatalog
import com.lucent.app.harness.plugins.PluginSource
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

@Composable
internal fun PluginSettingsPage(onRoute: (SettingsRoute) -> Unit) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(HarnessRuntime.config()) }
    var busy by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }

    val android = HarnessRuntime.android
    val plugins = remember(android) { PluginCatalog.forPlatform(android) }
    val canInstall = HarnessRuntime.pluginHost?.isReady() == true

    BackHeader(onBack = { onRoute(SettingsRoute.Agent) })
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(S.agentPluginsTitle, color = onGradient, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(S.agentPluginsSub, color = onGradientMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(HarnessRuntime.pluginHost?.describe() ?: "", color = onGradientMuted, fontSize = 11.sp)
            if (note.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(note, color = onGradient, fontSize = 12.sp)
            }
            if (busy.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        plugins.forEach { plugin ->
            val installed = config.pluginInstalled(plugin.id)
            Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(plugin.name, color = onGradient, fontSize = 14.sp)
                        Text(plugin.summary, color = onGradientMuted, fontSize = 11.sp)
                        Text(
                            (if (installed) S.agentPluginInstalled + " · " else "") +
                                if (plugin.bytes > 0) Workspace.humanSize(plugin.bytes) else plugin.licence,
                            color = onGradientMuted,
                            fontSize = 11.sp
                        )
                    }
                    TextButton(
                        enabled = busy.isBlank(),
                        onClick = {
                            val host = HarnessRuntime.pluginHost
                            if (host == null) {
                                note = "This build cannot install plugins."
                                return@TextButton
                            }
                            busy = plugin.id
                            progress = 0f
                            note = plugin.name
                            scope.launch {
                                if (installed) {
                                    val outcome = host.remove(plugin)
                                    note = outcome.message
                                } else {
                                    val source = PluginSource("", "", "")
                                    val outcome = host.install(plugin, source) { fraction, label ->
                                        progress = fraction
                                        note = "${plugin.name}: $label"
                                    }
                                    note = outcome.message
                                }
                                config = HarnessRuntime.config()
                                busy = ""
                            }
                        }
                    ) {
                        Text(
                            if (installed) S.agentPluginRemove else S.agentPluginInstall,
                            color = if (canInstall) onGradient else onGradientMuted,
                            fontSize = 13.sp
                        )
                    }
                }
                if (plugin.sources.isNotEmpty()) {
                    val chosen = config.mirrors[plugin.id]
                    plugin.sources.forEach { source ->
                        Text(
                            (if (source.id == chosen) "• " else "") + source.label +
                                if (source.official) " (official)" else "",
                            color = onGradientMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
