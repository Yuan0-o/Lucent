package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.lucent.app.harness.plugins.PluginPreflight
import com.lucent.app.harness.plugins.PluginSetup
import com.lucent.app.harness.plugins.PluginSource
import com.lucent.app.harness.plugins.PluginSpec
import com.lucent.app.harness.plugins.PreflightReport
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
fun PluginSettingsPage(
    onRoute: (SettingsRoute) -> Unit,
    onOpenUrl: (String) -> Unit = {},
    storageGranted: Boolean = true,
    onGrantStorage: () -> Unit = {}
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(HarnessRuntime.config()) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeLogOwner by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var failure by remember { mutableStateOf<PluginFailureReport?>(null) }
    var retry by remember { mutableStateOf<Pair<PluginSpec, Boolean>?>(null) }
    var preflight by remember { mutableStateOf<PreflightReport?>(null) }
    var pendingInstall by remember { mutableStateOf<PluginSpec?>(null) }
    var running by remember { mutableStateOf<Job?>(null) }
    var bannerRunning by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var effectivePlugins by remember { mutableStateOf<List<PluginSpec>>(emptyList()) }
    var fetchingCatalog by remember { mutableStateOf(false) }

    LaunchedEffect(config.pluginCatalogUrl, config.pluginCatalogCacheEpoch) {
        kotlinx.coroutines.delay(600)
        fetchingCatalog = true
        effectivePlugins = PluginCatalog.effective()
        fetchingCatalog = false
    }

    val android = HarnessRuntime.android
    val shellReady = HarnessRuntime.pluginHost?.isReady() == true
    val setupComplete = config.setupComplete

    LaunchedEffect(Unit) {
        if (!config.setupComplete) {
            val steps = PluginSetup.computeSteps()
            if (PluginSetup.fullSetupPasses(steps)) {
                HarnessRuntime.update(config.copy(setupComplete = true, setupCompletedAt = System.currentTimeMillis()))
                config = HarnessRuntime.config()
            }
        }
    }
    
    val plugins = remember(android, effectivePlugins, searchQuery) {
        val base = if (effectivePlugins.isEmpty()) PluginCatalog.forPlatform(android) 
                   else effectivePlugins.filter { if (android) it.android else it.desktop }
        val filtered = base
        if (searchQuery.isBlank()) filtered
        else filtered.filter { 
            it.name.contains(searchQuery, true) || 
            it.summary.contains(searchQuery, true) || 
            it.id.contains(searchQuery, true) 
        }
    }
    
    fun startInstall(plugin: PluginSpec, remove: Boolean) {
        val target = HarnessRuntime.pluginHost
        if (target == null) {
            note = S.agentPluginsUnavailable
            return
        }
        busy = plugin.id
        progress = 0f
        note = plugin.name
        logLines = emptyList()
        activeLogOwner = plugin.id
        val onOutput: (String) -> Unit = { line ->
            logLines = (logLines + line).takeLast(300)
        }
        running = scope.launch {
            val outcome = if (remove) {
                target.remove(plugin, onOutput)
            } else {
                target.install(plugin, PluginSource("", "", ""), onProgress = { fraction, label ->
                    progress = fraction
                    note = "${plugin.name}: $label"
                }, onOutput = onOutput)
            }
            config = HarnessRuntime.config()
            busy = ""
            running = null
            note = if (outcome.ok) outcome.message else ""
            if (!outcome.ok) failure = PluginFailureReport(plugin, outcome)
        }
    }

    fun runAction(plugin: PluginSpec, remove: Boolean) {
        if (remove) {
            startInstall(plugin, true)
            return
        }
        busy = plugin.id
        progress = 0f
        note = S.pluginPreflightTitle
        running = scope.launch {
            val report = PluginPreflight.inspect(plugin, PluginSource("", "", ""), android)
            busy = ""
            running = null
            note = ""
            if (report.blocked) {
                preflight = report
            } else {
                startInstall(plugin, false)
            }
        }
    }

    fun cancelRunning() {
        running?.cancel()
        running = null
        busy = ""
        note = ""
        progress = 0f
    }

    fun startPendingReinstall() {
        val target = HarnessRuntime.pluginHost
        if (target == null) return
        bannerRunning = true
        logLines = emptyList()
        activeLogOwner = "banner"
        val onOutput: (String) -> Unit = { line ->
            logLines = (logLines + line).takeLast(300)
        }
        scope.launch {
            val pendingList = config.pluginPendingReinstall.toList()
            for (id in pendingList) {
                val plugin = effectivePlugins.firstOrNull { it.id == id } ?: PluginCatalog.find(id) ?: continue
                val outcome = target.install(plugin, PluginSource("", "", ""), onProgress = { fraction, label ->
                    progress = fraction
                    note = "${plugin.name}: $label"
                }, onOutput = onOutput)
                if (outcome.ok) {
                    val next = HarnessRuntime.config().copy(
                        pluginPendingReinstall = HarnessRuntime.config().pluginPendingReinstall - id
                    )
                    HarnessRuntime.update(next)
                    config = HarnessRuntime.config()
                } else {
                    failure = PluginFailureReport(plugin, outcome)
                    break
                }
            }
            bannerRunning = false
        }
    }

    BackHeader(onBack = { onRoute(SettingsRoute.Agent) })
    Column(modifier = Modifier.fillMaxWidth()) {
        if (config.pluginPendingReinstall.isNotEmpty()) {
            PendingReinstallCard(
                pendingCount = config.pluginPendingReinstall.size,
                running = bannerRunning,
                progress = progress,
                note = note,
                logLines = if (activeLogOwner == "banner") logLines else emptyList(),
                onReinstall = { startPendingReinstall() },
                onDismiss = {
                    val next = config.copy(pluginPendingReinstall = emptyList())
                    HarnessRuntime.update(next)
                    config = next
                },
                onClearLog = {
                    logLines = emptyList()
                    activeLogOwner = ""
                },
                onGradient = onGradient,
                onGradientMuted = onGradientMuted
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (!setupComplete) {
            SetupGateCard(onGradient = onGradient, onGradientMuted = onGradientMuted, onStart = { onRoute(SettingsRoute.PluginSetup) })
            Spacer(modifier = Modifier.height(12.dp))
        }
        PluginIntroCard(shellReady = shellReady, onGradient = onGradient, onGradientMuted = onGradientMuted)

        if (android && !storageGranted) {
            Spacer(modifier = Modifier.height(12.dp))
            StorageCard(
                onGrant = onGrantStorage,
                onGradient = onGradient,
                onGradientMuted = onGradientMuted
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        
        if (setupComplete) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(S.pluginInstalledTitle, color = onGradient, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = com.lucent.app.collapseExcessBlankLines(it) },
                placeholder = { Text(S.pluginSearchHint, fontSize = 13.sp) },
                supportingText = { Text(S.pluginSearchRemoteHint, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedTextColor = onGradient,
                    focusedTextColor = onGradient
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (setupComplete) plugins.forEach { plugin ->
            PluginRow(
                plugin = plugin,
                installed = config.pluginInstalled(plugin.id),
                shellReady = shellReady,
                busy = busy == plugin.id,
                progress = progress,
                note = if (busy == plugin.id) note else "",
                logLines = if (activeLogOwner == plugin.id) logLines else emptyList(),
                onGradient = onGradient,
                onGradientMuted = onGradientMuted,
                onAction = { runAction(plugin, it) },
                onCancel = { cancelRunning() },
                onClearLog = { logLines = emptyList() }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (setupComplete) {
            var advancedExpanded by remember { mutableStateOf(false) }
            Spacer(modifier = Modifier.height(16.dp))
            com.lucent.app.ui.MoreOptionsFold(
                expanded = advancedExpanded,
                onToggle = { advancedExpanded = !advancedExpanded },
                label = S.settingsAdvancedTitle
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = config.pluginCatalogUrl,
                    onValueChange = {
                        val next = config.copy(pluginCatalogUrl = com.lucent.app.collapseExcessBlankLines(it))
                        HarnessRuntime.update(next)
                        config = next
                    },
                    label = { Text(S.pluginCatalogUrlLabel, fontSize = 13.sp) },
                    placeholder = { Text(S.pluginCatalogUrlHint, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedTextColor = onGradient,
                        focusedTextColor = onGradient
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    val report = failure
    if (report != null) {
        PluginFailureDialog(
            report = report,
            android = android,
            onRetry = {
                failure = null
                retry = report.plugin to (report.outcome.failure == PluginFailure.REMOVE)
            },
            onDismiss = { failure = null }
        )
    }

    val pending = retry
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        retry = null
        startInstall(pending.first, pending.second)
    }

    val forced = pendingInstall
    LaunchedEffect(forced) {
        if (forced == null) return@LaunchedEffect
        pendingInstall = null
        startInstall(forced, false)
    }

    val blocked = preflight
    if (blocked != null) {
        PluginPreflightDialog(
            report = blocked,
            onInstallAnyway = {
                preflight = null
                pendingInstall = blocked.plugin
            },
            onDismiss = { preflight = null }
        )
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
private fun StorageCard(
    onGrant: () -> Unit,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color
) {
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.pluginStorageTitle, color = onGradient, fontSize = 14.sp)
                Text(S.pluginStorageSub, color = onGradientMuted, fontSize = 11.sp)
            }
            TextButton(onClick = onGrant) {
                Text(S.pluginStorageAction, color = onGradient, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: PluginSpec,
    installed: Boolean,
    shellReady: Boolean,
    busy: Boolean,
    progress: Float,
    note: String,
    logLines: List<String>,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color,
    onAction: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onClearLog: () -> Unit
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
        if (busy || logLines.isNotEmpty()) {
            PluginOutputLog(
                lines = logLines,
                running = busy,
                onGradient = onGradient,
                onGradientMuted = onGradientMuted,
                onClear = onClearLog
            )
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
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val steps = repairSteps(report.outcome.failure, android)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (report.outcome.failure == PluginFailure.REMOVE) S.pluginRemoveFailed else S.pluginInstallFailed) },
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
            TextButton(onClick = onRetry) { Text(S.actionRetry) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.actionClose) } }
    )
}

@Composable
private fun PluginPreflightDialog(
    report: PreflightReport,
    onInstallAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.pluginPreflightTitle) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(report.plugin.name, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(S.pluginPreflightBlocked, fontSize = 13.sp)
                report.problems.forEach { problem ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(problem.title, fontSize = 13.sp)
                    problem.steps.forEach { step ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("\u2022 $step", fontSize = 12.sp)
                    }
                }
                report.notes.forEach { note ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(note, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onInstallAnyway) { Text(S.pluginInstallAnyway) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.actionClose) } }
    )
}

private fun reasonText(failure: PluginFailure): String = when (failure) {
    PluginFailure.CANCELLED -> S.pluginReasonCancelled
    PluginFailure.NO_SHELL -> S.pluginReasonNoShell
    PluginFailure.NO_PLATFORM -> S.pluginReasonUnsupported
    PluginFailure.INSTALL -> S.pluginReasonInstall
    PluginFailure.REMOVE -> S.pluginReasonRemove
    PluginFailure.DETECT -> S.pluginReasonDetect
    else -> S.pluginReasonDownload
}

private fun repairSteps(failure: PluginFailure, android: Boolean): List<String> = buildList {
    when (failure) {
        PluginFailure.NO_SHELL -> {
            if (android) {
                add(S.pluginRepairSetupGuide)
                add(S.pluginRepairStorage)
            } else {
                add(S.pluginRepairRetry)
            }
        }
        PluginFailure.DOWNLOAD -> {
            add(S.pluginRepairMirrors)
            add(S.pluginRepairStorage)
            add(S.pluginRepairRetry)
        }
        PluginFailure.INSTALL -> {
            if (android) add(S.pluginRepairUserland)
            add(S.pluginRepairRetry)
        }
        else -> add(S.pluginRepairRetry)
    }
}

@Composable
private fun SetupGateCard(
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color,
    onStart: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.setupGateTitle, color = onGradient, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(S.setupGateBody, color = onGradientMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onStart) {
            Text(S.setupGateAction, color = onGradient, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PendingReinstallCard(
    pendingCount: Int,
    running: Boolean,
    progress: Float,
    note: String,
    logLines: List<String>,
    onReinstall: () -> Unit,
    onDismiss: () -> Unit,
    onClearLog: () -> Unit,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color
) {
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.pluginPendingReinstallTitle, color = onGradient, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(S.pluginPendingReinstallBody(pendingCount), color = onGradientMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        if (running) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            if (note.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(note, color = onGradientMuted, fontSize = 11.sp)
            }
        } else {
            Row {
                TextButton(onClick = onReinstall) {
                    Text(S.actionReinstall, color = onGradient, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(S.actionDismiss)
                }
            }
        }
        if (running || logLines.isNotEmpty()) {
            PluginOutputLog(
                lines = logLines,
                running = running,
                onGradient = onGradient,
                onGradientMuted = onGradientMuted,
                onClear = onClearLog
            )
        }
    }
}

@Composable
internal fun PluginOutputLog(
    lines: List<String>,
    running: Boolean,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color,
    onClear: () -> Unit
) {
    if (lines.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(S.pluginOutputTitle, color = onGradient, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, modifier = Modifier.weight(1f))
            if (!running) {
                TextButton(onClick = onClear) {
                    Text(S.actionClear, color = onGradientMuted, fontSize = 11.sp)
                }
            }
        }
        val scroll = rememberScrollState()
        LaunchedEffect(lines.size) {
            scroll.animateScrollTo(scroll.maxValue)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .verticalScroll(scroll)
        ) {
            val text = lines.joinToString("\n")
            Text(
                text = text,
                color = onGradientMuted,
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                lineHeight = 16.sp
            )
        }
    }
}
