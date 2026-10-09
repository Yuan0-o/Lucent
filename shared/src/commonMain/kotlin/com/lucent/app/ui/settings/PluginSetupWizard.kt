package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.harness.plugins.PluginCatalog
import com.lucent.app.harness.plugins.PluginSource
import com.lucent.app.harness.plugins.PluginSetup
import com.lucent.app.harness.plugins.SetupStep
import com.lucent.app.harness.plugins.SetupStepState
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.DirectoryPickerDialog
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun PluginSetupWizard(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var steps by remember { mutableStateOf<List<SetupStep>>(emptyList()) }
    var busy by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var failKind by remember { mutableStateOf<SetupFailKind?>(null) }
    var logLines by remember { mutableStateOf(listOf<String>()) }
    var statusMsg by remember { mutableStateOf("") }
    var errorStep by remember { mutableStateOf("") }
    var setupComplete by remember { mutableStateOf(HarnessRuntime.config().setupComplete) }
    var pickingWorkspace by remember { mutableStateOf(false) }

    val reload = {
        scope.launch {
            steps = PluginSetup.computeSteps()
            val passes = PluginSetup.fullSetupPasses(steps)
            setupComplete = passes
            if (passes) {
                val cfg = HarnessRuntime.config()
                if (!cfg.setupComplete) {
                    HarnessRuntime.update(
                        cfg.copy(
                            setupComplete = true,
                            setupCompletedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    DisposableEffect(Unit) {
        val listener: (com.lucent.app.harness.HarnessConfig) -> Unit = { reload() }
        HarnessRuntime.observe(listener)
        onDispose { HarnessRuntime.unobserve(listener) }
    }

    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    Column(modifier = Modifier.fillMaxWidth()) {
        BackHeader(onBack = onBack)

        if (setupComplete) {
            Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
                Text(S.setupWizardDone, color = onGradient, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(S.setupWizardDoneBody, color = onGradientMuted, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onBack) {
                    Text(S.setupActionDone, color = onGradient, fontSize = 13.sp)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
                Text(S.setupWizardTitle, color = onGradient, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(12.dp))

            steps.forEach { step ->
                    SetupStepCard(
                        step = step,
                        busy = busy == step.id,
                        progress = progress,
                        failKind = if (errorStep == step.id) failKind else null,
                        statusText = if (busy == step.id) statusMsg else "",
                        logLines = if (busy == step.id || errorStep == step.id) logLines else emptyList(),
                        onClearLog = { logLines = emptyList() },
                        onGradient = onGradient,
                        onGradientMuted = onGradientMuted,
                        onReload = { reload() },
                        onAction = {
                            failKind = null
                            errorStep = ""
                            statusMsg = ""
                            logLines = emptyList()
                            when (step.id) {

                                "workspace_shared" -> {
                                    pickingWorkspace = true
                                }
                                "disk_space" -> {
                                    reload()
                                }
                                "bundled_env" -> {
                                    busy = step.id
                                    scope.launch {
                                        val outcome = HarnessRuntime.host?.extractBuiltinRuntime { line ->
                                            statusMsg = line
                                            logLines = (logLines + line).takeLast(300)
                                        }
                                        if (outcome?.ok == true) {
                                            reload()
                                        } else {
                                            failKind = classifyInstallFailure(null, "", outcome?.stderr.orEmpty() + "\n" + outcome?.text.orEmpty())
                                            errorStep = step.id
                                        }
                                        statusMsg = ""
                                        busy = ""
                                    }
                                }
                                "base_tools" -> {
                                    busy = step.id
                                    scope.launch {
                                        var failure: com.lucent.app.harness.PluginOutcome? = null
                                        val tools = PluginCatalog.forPlatform(HarnessRuntime.android).filter { it.id != "ubuntu" && it.id != "playwright" }
                                        for (tool in tools) {
                                            if (!HarnessRuntime.config().pluginInstalled(tool.id) && HarnessRuntime.pluginHost?.detect(tool) != true) {
                                                val outcome = HarnessRuntime.pluginHost?.install(
                                                    tool,
                                                    PluginSource("", "", ""),
                                                    onProgress = { p, t ->
                                                        progress = p
                                                        statusMsg = "${tool.name}: $t"
                                                    },
                                                    onOutput = { line ->
                                                        logLines = (logLines + line).takeLast(300)
                                                    }
                                                )
                                                if (outcome?.ok != true) {
                                                    failure = outcome
                                                    break
                                                }
                                            }
                                        }
                                        if (failure != null) {
                                            failKind = classifyInstallFailure(failure.failure, failure.message, failure.detail)
                                            errorStep = step.id
                                        }
                                        statusMsg = ""
                                        reload()
                                        busy = ""
                                    }
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
        }
    }

    if (pickingWorkspace) {
        DirectoryPickerDialog(
            initialPath = HarnessRuntime.workspacePath(),
            onDismiss = { pickingWorkspace = false },
            onOpen = { picked ->
                pickingWorkspace = false
                HarnessRuntime.update(HarnessRuntime.config().copy(workspace = picked))
                scope.launch { reload() }
            }
        )
    }
}

@Composable
private fun SetupStepCard(
    step: SetupStep,
    busy: Boolean,
    progress: Float,
    failKind: SetupFailKind?,
    statusText: String,
    logLines: List<String>,
    onClearLog: () -> Unit,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color,
    onAction: () -> Unit,
    onReload: () -> Unit
) {
    val alpha = if (step.state == SetupStepState.WAITING) 0.5f else 1f
    Column(modifier = Modifier.fillMaxWidth().alpha(alpha).frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = when (step.state) {
                SetupStepState.DONE -> Icons.Default.CheckCircle
                SetupStepState.CURRENT -> Icons.Default.PlayArrow
                SetupStepState.WAITING -> Icons.Default.RadioButtonUnchecked
            }
            Icon(icon, contentDescription = null, tint = onGradient, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(step.title, color = onGradient, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(step.body, color = onGradientMuted, fontSize = 13.sp)
                
                if (failKind != null) {
                    val reason = setupFailReason(failKind)
                    val steps = setupFailSteps(failKind)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(S.setupInstallFailedTitle, color = androidx.compose.ui.graphics.Color(0xFFFF8A80), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(reason, color = onGradientMuted, fontSize = 12.sp)
                    steps.forEach { s -> Spacer(modifier = Modifier.height(4.dp)); Text("\u2022 $s", color = onGradientMuted, fontSize = 12.sp) }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onAction) { Text(S.actionRetry, color = onGradient, fontSize = 13.sp) }
                }

                if (busy) {
                    if (statusText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(statusText, color = onGradientMuted, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else if (step.state == SetupStepState.CURRENT) {
                    if (step.action.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onAction) {
                            Text(step.action, color = onGradient, fontSize = 13.sp)
                        }
                    }
                    if (step.id == "disk_space") {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = onReload) {
                            Text(S.setupActionCheck, color = onGradientMuted, fontSize = 13.sp)
                        }
                    }
                }

                PluginOutputLog(lines = logLines, running = busy, onGradient = onGradient, onGradientMuted = onGradientMuted, onClear = onClearLog)
            }
        }
    }
}

private enum class SetupFailKind { NETWORK, DPKG, GENERIC }

private fun classifyInstallFailure(failure: com.lucent.app.harness.PluginFailure?, message: String, detail: String): SetupFailKind {
    val text = (message + "\n" + detail).lowercase()
    if (failure == com.lucent.app.harness.PluginFailure.DOWNLOAD) return SetupFailKind.NETWORK
    if (text.contains("dpkg returned an error") || text.contains("sub-process /usr/bin/dpkg")) return SetupFailKind.DPKG
    if (Regex("exited 100\\b").containsMatchIn(text) || text.contains("failed to fetch") || text.contains("unable to fetch") || text.contains("could not resolve") || text.contains("network is unreachable") || text.contains("connection timed out") || text.contains("connection failed")) return SetupFailKind.NETWORK
    return SetupFailKind.GENERIC
}

private fun setupFailReason(kind: SetupFailKind): String = when (kind) {
    SetupFailKind.NETWORK -> S.setupInstallReasonNetwork
    SetupFailKind.DPKG -> S.setupInstallReasonDpkg
    SetupFailKind.GENERIC -> S.setupInstallReasonGeneric
}

private fun setupFailSteps(kind: SetupFailKind): List<String> = when (kind) {
    SetupFailKind.NETWORK -> listOf(S.setupInstallStepCheckNetwork, S.setupInstallStepRetry)
    SetupFailKind.DPKG -> listOf(S.setupInstallStepFreeDisk, S.setupInstallStepRetry)
    SetupFailKind.GENERIC -> listOf(S.setupInstallStepRetry)
}
