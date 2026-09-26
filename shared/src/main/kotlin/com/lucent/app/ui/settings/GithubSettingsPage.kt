package com.lucent.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.harness.GithubToken
import com.lucent.app.harness.HarnessConfig
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.SettingsRoute

@Composable
internal fun GithubSettingsPage(onRoute: (SettingsRoute) -> Unit) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var config by remember { mutableStateOf(HarnessRuntime.config()) }
    var typed by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<GithubToken?>(null) }
    var renameText by remember { mutableStateOf("") }

    fun update(next: HarnessConfig) {
        config = next
        HarnessRuntime.update(next)
    }

    BackHeader(onBack = { onRoute(SettingsRoute.Agent) })
    Column(modifier = Modifier.fillMaxWidth()) {
        Section(
            onGradient,
            onGradientMuted,
            S.agentGithubTitle,
            S.agentGithubSub,
            notes = listOf(S.agentGithubTokensHint)
        ) {
            config.githubTokens.forEachIndexed { index, entry ->
                val active = entry.id == config.githubActiveId
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { update(config.withGithubTokens(config.githubTokens, entry.id)) }
                    ) {
                        Text(
                            (if (active) "• " else "") + entry.name.ifBlank { S.agentGithubTokenName(index + 1) },
                            color = onGradient,
                            fontSize = 14.sp
                        )
                        Text(dots(entry.token), color = onGradientMuted, fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        renaming = entry
                        renameText = entry.name.ifBlank { S.agentGithubTokenName(index + 1) }
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = S.agentGithubRename, tint = onGradient, modifier = Modifier.size(18.dp))
                    }
                    TextButton(onClick = {
                        update(
                            config.withGithubTokens(
                                config.githubTokens.filterNot { it.id == entry.id },
                                if (entry.id == config.githubActiveId) "" else config.githubActiveId
                            )
                        )
                    }) {
                        Text("✕", color = onGradientMuted, fontSize = 14.sp)
                    }
                }
            }
            if (config.githubTokens.isEmpty()) {
                Text(S.agentConnectorEmpty, color = onGradientMuted, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (config.githubTokens.size < HarnessConfig.MAX_GITHUB_TOKENS) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(S.agentGithubToken) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    TextButton(
                        enabled = typed.isNotBlank(),
                        onClick = {
                            val added = GithubToken(
                                id = "gh-${System.currentTimeMillis()}",
                                name = S.agentGithubTokenName(config.githubTokens.size + 1),
                                token = typed.trim()
                            )
                            update(config.withGithubTokens(config.githubTokens + added, config.githubActiveId))
                            typed = ""
                            LucentToast.show(context, S.settingsSaved)
                        }
                    ) {
                        Text(S.actionSave, color = onGradient, fontSize = 13.sp)
                    }
                }
            }
            if (config.githubTokens.isNotEmpty()) {
                Text(S.agentGithubStored, color = onGradientMuted, fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    val target = renaming
    if (target != null) {
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(S.agentGithubRenameTitle) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val others = config.githubTokens.map {
                        if (it.id == target.id) it.copy(name = renameText.trim()) else it
                    }
                    update(config.withGithubTokens(others))
                    renaming = null
                    LucentToast.show(context, S.settingsSaved)
                }) { Text(S.actionSave) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text(S.actionCancel) }
            }
        )
    }
}

private fun dots(token: String): String = "•".repeat(token.length.coerceIn(6, 24))
