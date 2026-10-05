package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.lucent.app.data.TokenEstimator
import com.lucent.app.harness.HarnessConfig
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute

@Composable
internal fun ExecutionSettingsPage(onRoute: (SettingsRoute) -> Unit) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var config by remember { mutableStateOf(HarnessRuntime.config()) }

    fun update(next: HarnessConfig) {
        config = next
        HarnessRuntime.update(next)
    }

    BackHeader(onBack = { onRoute(SettingsRoute.Agent) })
    Column(modifier = Modifier.fillMaxWidth()) {
        Section(onGradient, onGradientMuted, S.agentSandboxTitle, S.agentSandboxSub) {
            val modes = listOf("auto", "direct", "docker", "proot", "none")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                modes.forEach { mode ->
                    TextButton(onClick = { update(config.copy(sandboxMode = mode)) }) {
                        Text(
                            (if (config.sandboxMode == mode) "• " else "") + mode,
                            color = if (config.sandboxMode == mode) onGradient else onGradientMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.agentContextBudget, color = onGradient, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    update(config.copy(contextBudgetTokens = stepBudget(config.contextBudgetTokens, -1)))
                }) {
                    Text("-", color = onGradient)
                }
                Text(TokenEstimator.label(config.contextBudgetTokens), color = onGradient, fontSize = 13.sp)
                TextButton(onClick = {
                    update(config.copy(contextBudgetTokens = stepBudget(config.contextBudgetTokens, 1)))
                }) {
                    Text("+", color = onGradient)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

private const val CONTEXT_BUDGET_STEP = 4000

private fun stepBudget(current: Int, direction: Int): Int {
    val next = current + direction * CONTEXT_BUDGET_STEP
    return next.coerceIn(
        com.lucent.app.harness.ContextBudget.MIN_BUDGET_TOKENS,
        com.lucent.app.harness.ContextBudget.MAX_BUDGET_TOKENS
    )
}
