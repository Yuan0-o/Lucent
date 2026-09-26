package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.lucent.app.harness.PluginFailure
import com.lucent.app.harness.PluginOutcome
import com.lucent.app.harness.Workspace
import com.lucent.app.harness.plugins.PluginCatalog
import com.lucent.app.harness.plugins.PluginSource
import com.lucent.app.harness.plugins.PluginSpec
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class PluginFailureReport(val plugin: PluginSpec, val outcome: PluginOutcome)

@Composable
internal fun PluginSettingsPage(
    onRoute: (SettingsRoute) -> Unit,
    onOpenUrl: (String) -> Unit = {},
    termuxInstalled: Boolean = false
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(HarnessRuntime.config()) }
    var busy by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var failure by remember { mutableStateOf<PluginFailureReport?>(null) }
    var retry by remember { mutableStateOf<PluginSpec?>(null) }
    var running by remember { mutableStateOf<Job?>(null) }

    val android = HarnessRuntime.android
    val shellReady = HarnessRuntime.pluginHost?.isReady() == true
    val plugins = remember(android) { PluginCatalog.forPlatform(android).filterNot { it.id == "termux" } }
    val termux = remember { PluginCatalog.forPlatform(true).firstOrNull { it.id == "termux" } }

    fun runAction(plugin: PluginSpec, remove: Boolean) {
        val target = HarnessRuntime.pluginHost
        if (target == null) {
            note = S.agentPluginsUnavailable
            return
        }
        busy = plugin.id
        progress = 0f
        note = plugin.name
        running = scope.launch {
            val outcome = if (remove) {
                target.remove(plugin)
            } else {
                target.install(plugin, PluginSource("", "", "")) { fraction, label ->
                    progress = fraction
                    note = "${plugin.name}: $label"
                }
            }
            config = HarnessRuntime.config()
            busy = ""
            running = null
            note = if (outcome.ok) outcome.message else ""
            if (!outcome.ok) failure = PluginFailureReport(plugin, outcome)
        }
    }

    fun cancelRunning() {
        running?.cancel()
        running = null
        busy = ""
        note = ""
        progress = 0f
    }

    BackHeader(onBack = { onRoute(SettingsRoute.Agent) })
    Column(modifier = Modifier.fillMaxWidth()) {
        PluginIntroCard(shellReady = shellReady, onGradient = onGradient, onGradientMuted = onGradientMuted)
        if (android && termux != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TermuxCard(
                termux = termux,
                installed = termuxInstalled,
                onOpenUrl = onOpenUrl,
                onGradient = onGradient,
                onGradientMuted = onGradientMuted
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        plugins.forEach { plugin ->
            PluginRow(
                plugin = plugin,
                installed = config.pluginInstalled(plugin.id),
                mirror = config.mirrors[plugin.id].orEmpty(),
                shellReady = shellReady,
                busy = busy == plugin.id,
                progress = progress,
                note = if (busy == plugin.id) note else "",
                onGradient = onGradient,
                onGradientMuted = onGradientMuted,
                onAction = { runAction(plugin, it) },
                onCancel = { cancelRunning() }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    val report = failure
    if (report != null) {
        PluginFailureDialog(
            report = report,
            android = android,
            termuxInstalled = termuxInstalled,
            fdroidUrl = termux?.sources?.firstOrNull { it.official }?.url ?: termux?.homepage.orEmpty(),
            onOpenUrl = onOpenUrl,
            onRetry = {
                failure = null
                retry = report.plugin
            },
            onDismiss = { failure = null }
        )
    }

    val pending = retry
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        retry = null
        runAction(pending, false)
    }
}

@Composable
private fun PluginIntroCard(
    shellReady: Boolean,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color
) {
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.agentPluginsTitle, color = onGradient, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(S.agentPluginsSub, color = onGradientMuted, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            if (shellReady) S.pluginShellReady(HarnessRuntime.shell?.id ?: "shell") else S.pluginShellNone,
            color = onGradientMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun TermuxCard(
    termux: PluginSpec,
    installed: Boolean,
    onOpenUrl: (String) -> Unit,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color
) {
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.agentTermuxTitle, color = onGradient, fontSize = 14.sp)
                Text(S.agentTermuxSub, color = onGradientMuted, fontSize = 11.sp)
                if (installed) {
                    Text(S.agentTermuxInstalled, color = onGradientMuted, fontSize = 11.sp)
                }
            }
            if (!installed) {
                TextButton(onClick = {
                    val url = termux.sources.firstOrNull { it.official }?.url ?: termux.homepage
                    onOpenUrl(url)
                }) {
                    Text(S.agentTermuxAction, color = onGradient, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: PluginSpec,
    installed: Boolean,
    mirror: String,
    shellReady: Boolean,
    busy: Boolean,
    progress: Float,
    note: String,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color,
    onAction: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(plugin.name, color = onGradient, fontSize = 14.sp)
                Text(plugin.summary, color = onGradientMuted, fontSize = 11.sp)
                Text(stateLine(plugin, installed, shellReady), color = onGradientMuted, fontSize = 11.sp)
            }
            if (busy) {
                TextButton(onClick = onCancel) {
                    Text(S.actionStop, color = onGradient, fontSize = 13.sp)
                }
            } else {
                TextButton(onClick = { onAction(installed) }) {
                    Text(
                        if (installed) S.agentPluginRemove else S.agentPluginInstall,
                        color = onGradient,
                        fontSize = 13.sp
                    )
                }
            }
        }
        if (busy) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            if (note.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(note, color = onGradientMuted, fontSize = 11.sp)
            }
        }
        if (plugin.sources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            plugin.sources.forEach { source ->
                Text(
                    (if (source.id == mirror) "\u2022 " else "") + source.label +
                        if (source.official) " (official)" else "",
                    color = onGradientMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun stateLine(plugin: PluginSpec, installed: Boolean, shellReady: Boolean): String {
    if (installed) {
        return S.agentPluginInstalled + " \u00b7 " +
            if (plugin.bytes > 0) Workspace.humanSize(plugin.bytes) else plugin.licence
    }
    if (!shellReady && plugin.needsShell) return S.agentPluginNeedsShell
    return if (plugin.bytes > 0) Workspace.humanSize(plugin.bytes) else plugin.licence
}

@Composable
private fun PluginFailureDialog(
    report: PluginFailureReport,
    android: Boolean,
    termuxInstalled: Boolean,
    fdroidUrl: String,
    onOpenUrl: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val steps = repairSteps(report.outcome.failure, android)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.pluginInstallFailed) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(report.plugin.name, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(reasonText(report.outcome.failure), fontSize = 13.sp)
                if (report.outcome.message.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(report.outcome.message, fontSize = 11.sp)
                }
                if (steps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(S.pluginRepairTitle, fontSize = 13.sp)
                    steps.forEach { step ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("\u2022 $step", fontSize = 12.sp)
                    }
                }
                val detail = report.outcome.detail.ifBlank { report.outcome.installedPath }.take(1200)
                if (detail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(S.pluginDetailTitle, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(detail, fontSize = 10.sp)
                }
            }
        },
        confirmButton = {
            if (android && !termuxInstalled && report.outcome.failure == PluginFailure.NO_SHELL &&
                fdroidUrl.isNotBlank()
            ) {
                TextButton(onClick = { onOpenUrl(fdroidUrl) }) { Text(S.agentTermuxAction) }
            }
            TextButton(onClick = onRetry) { Text(S.actionRetry) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.actionClose) } }
    )
}

private fun reasonText(failure: PluginFailure): String = when (failure) {
    PluginFailure.CANCELLED -> S.pluginReasonCancelled
    PluginFailure.NO_SHELL -> S.pluginReasonNoShell
    PluginFailure.NO_PLATFORM -> S.pluginReasonUnsupported
    PluginFailure.INSTALL -> S.pluginReasonInstall
    PluginFailure.DETECT -> S.pluginReasonDetect
    else -> S.pluginReasonDownload
}

private fun repairSteps(failure: PluginFailure, android: Boolean): List<String> = buildList {
    when (failure) {
        PluginFailure.NO_SHELL -> {
            if (android) {
                add(S.pluginRepairTermux)
                add(S.pluginRepairStorage)
            } else {
                add(S.pluginRepairRetry)
            }
        }
        PluginFailure.DOWNLOAD -> {
            add(S.pluginRepairStorage)
            add(S.pluginRepairRetry)
        }
        PluginFailure.INSTALL -> {
            add(S.pluginRepairUserland)
            add(S.pluginRepairRetry)
        }
        else -> add(S.pluginRepairRetry)
    }
}
