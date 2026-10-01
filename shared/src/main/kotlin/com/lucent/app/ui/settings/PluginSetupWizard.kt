package com.lucent.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
internal fun PluginSetupWizard(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var steps by remember { mutableStateOf<List<SetupStep>>(emptyList()) }
    var busy by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var errorMsg by remember { mutableStateOf("") }
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
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(S.setupWizardDone, color = onGradient, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(S.setupWizardDoneBody, color = onGradientMuted, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) {
                    Text(S.setupActionDone)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(S.setupWizardTitle, color = onGradient, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                steps.forEach { step ->
                    SetupStepCard(
                        step = step,
                        busy = busy == step.id,
                        progress = progress,
                        errorMsg = if (errorStep == step.id) errorMsg else "",
                        statusText = if (busy == step.id) statusMsg else "",
                        onGradient = onGradient,
                        onGradientMuted = onGradientMuted,
                        onReload = { reload() },
                        onAction = {
                            errorMsg = ""
                            errorStep = ""
                            statusMsg = ""
                            when (step.id) {
                                "termux_installed" -> {
                                    val termuxSpec = PluginCatalog.forPlatform(true).firstOrNull { it.id == "termux" }
                                    val url = termuxSpec?.sources?.firstOrNull { it.official }?.url ?: termuxSpec?.homepage.orEmpty()
                                    if (url.isNotBlank()) HarnessRuntime.host?.openUrl(url)
                                    scope.launch { delay(1000); reload() }
                                }
                                "termux_configured" -> {
                                    scope.launch {
                                        HarnessRuntime.host?.launchApp("com.termux")
                                        delay(1000)
                                        reload()
                                    }
                                }
                                "workspace_shared" -> {
                                    pickingWorkspace = true
                                }
                                "disk_space" -> {
                                    reload()
                                }
                                "ubuntu_userland" -> {
                                    val spec = PluginCatalog.find("ubuntu")
                                    if (spec != null) {
                                        busy = step.id
                                        scope.launch {
                                            val outcome = HarnessRuntime.pluginHost?.install(spec, PluginSource("", "", "")) { p, t ->
                                                progress = p
                                                statusMsg = t
                                            }
                                            if (outcome?.ok != true) {
                                                errorMsg = outcome?.message.orEmpty().ifEmpty { S.setupFailed }
                                                errorStep = step.id
                                            } else {
                                                reload()
                                            }
                                            statusMsg = ""
                                            busy = ""
                                        }
                                    }
                                }
                                "base_tools" -> {
                                    busy = step.id
                                    scope.launch {
                                        var failureMessage = ""
                                        val tools = PluginCatalog.forPlatform(HarnessRuntime.android).filter { it.id != "ubuntu" && it.id != "playwright" }
                                        for (tool in tools) {
                                            if (!HarnessRuntime.config().pluginInstalled(tool.id) && HarnessRuntime.pluginHost?.detect(tool) != true) {
                                                val outcome = HarnessRuntime.pluginHost?.install(tool, PluginSource("", "", "")) { p, t ->
                                                    progress = p
                                                    statusMsg = "${tool.name}: $t"
                                                }
                                                if (outcome?.ok != true && failureMessage.isEmpty()) {
                                                    failureMessage = outcome?.message.orEmpty()
                                                }
                                            }
                                        }
                                        if (failureMessage.isNotEmpty()) {
                                            errorMsg = failureMessage
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
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (pickingWorkspace) {
        DirectoryPickerDialog(
            initialPath = HarnessRuntime.workspace().path,
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
    errorMsg: String,
    statusText: String,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color,
    onAction: () -> Unit,
    onReload: () -> Unit
) {
    val alpha = if (step.state == SetupStepState.WAITING) 0.5f else 1f
    Column(modifier = Modifier.fillMaxWidth().alpha(alpha).background(androidx.compose.ui.graphics.Color(0x1AFFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = when (step.state) {
                SetupStepState.DONE -> Icons.Default.CheckCircle
                SetupStepState.CURRENT -> Icons.Default.PlayArrow
                SetupStepState.WAITING -> Icons.Default.RadioButtonUnchecked
            }
            Icon(icon, contentDescription = null, tint = onGradient, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(step.title, color = onGradient, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(step.body, color = onGradientMuted, fontSize = 14.sp)
                
                if (step.id == "termux_configured" && step.state == SetupStepState.CURRENT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(S.setupTermuxConfigCommand, color = onGradient, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.background(androidx.compose.ui.graphics.Color(0x33000000)).padding(8.dp))
                    }
                }

                if (errorMsg.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(errorMsg, color = androidx.compose.ui.graphics.Color.Red, fontSize = 12.sp)
                }

                if (busy) {
                    if (statusText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(statusText, color = onGradientMuted, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else if (step.state == SetupStepState.CURRENT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onAction) {
                        Text(step.action)
                    }
                    if (step.id == "termux_configured" || step.id == "disk_space") {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = onReload) {
                            Text(S.setupActionCheck)
                        }
                    }
                }
            }
        }
    }
}
