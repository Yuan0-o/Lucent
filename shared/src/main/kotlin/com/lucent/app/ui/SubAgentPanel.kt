package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.lucent.app.harness.SubAgent
import com.lucent.app.harness.SubAgentReports
import com.lucent.app.harness.SubAgents
import com.lucent.app.i18n.S
import kotlinx.coroutines.delay

@Composable
fun SubAgentStrip(
    visible: Boolean,
    tint: Color,
    mutedTint: Color,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    var tick by remember { mutableStateOf(0) }
    val agents = remember(tick) { SubAgents.list() }
    var expandedTreeId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            if (SubAgents.running() > 0) tick++
        }
    }

    if (agents.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(S.agentSubAgents, color = mutedTint, fontSize = 11.sp)
        agents.forEach { agent ->
            Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(tint.copy(alpha = 0.12f))
                    .border(1.dp, tint.copy(alpha = 0.24f), RoundedCornerShape(percent = 50))
                    .clickable { com.lucent.app.AppNavigation.openSubAgent(agent.id) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(agent.id, color = tint, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(5.dp))
                Text(statusLabel(agent.status), color = mutedTint, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    agent.task.replace('\n', ' '),
                    color = mutedTint,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(120.dp)
                )
            }
            val children = agents.filter { it.parentId == agent.id }
            if (children.isNotEmpty()) {
                Box {
                    Text("⌄", color = tint, modifier = Modifier.clickable { expandedTreeId = agent.id }.padding(horizontal = 5.dp, vertical = 4.dp))
                    DropdownMenu(expanded = expandedTreeId == agent.id, onDismissRequest = { expandedTreeId = null }) {
                        children.forEach { child ->
                            DropdownMenuItem(
                                text = { Text(child.id + " · " + child.task, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { expandedTreeId = null; com.lucent.app.AppNavigation.openSubAgent(child.id) }
                            )
                        }
                    }
                }
            }
            }
        }
    }

}

private fun statusLabel(status: String): String = when (status) {
    "done" -> S.subAgentStatusDone
    "failed" -> S.subAgentStatusFailed
    else -> S.subAgentStatusRunning
}

private const val SUB_AGENT_HEADER_CHIPS = 2

@Composable
fun SubAgentHeaderChips(
    tint: Color,
    mutedTint: Color,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            tick++
        }
    }

    val agents = remember(tick) { SubAgents.list() }
    headerAgents(agents).forEach { agent ->
        SubAgentNameChip(
            agent = agent,
            tint = tint,
            mutedTint = mutedTint,
            onClick = { com.lucent.app.AppNavigation.openSubAgent(agent.id) },
            modifier = modifier
        )
    }

}

private fun headerAgents(agents: List<SubAgent>): List<SubAgent> {
    val running = agents.filter { it.status == "running" }
    val pool = if (running.isEmpty()) agents.take(1) else running
    return pool.take(SUB_AGENT_HEADER_CHIPS)
}

@Composable
fun SubAgentNameChip(
    agent: SubAgent,
    tint: Color,
    mutedTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val working = agent.status == "running"
    Row(
        modifier = modifier
            .widthIn(max = 92.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(tint.copy(alpha = if (working) 0.18f else 0.10f))
            .border(
                1.dp,
                tint.copy(alpha = if (working) 0.38f else 0.20f),
                RoundedCornerShape(percent = 50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            agent.id,
            color = if (working) tint else mutedTint,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SubAgentChip(
    tint: Color,
    mutedTint: Color,
    modifier: Modifier = Modifier
) {
    var running by remember { mutableStateOf(SubAgents.running()) }
    var total by remember { mutableStateOf(SubAgents.list().size) }
    var first by remember { mutableStateOf<SubAgent?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            running = SubAgents.running()
            total = SubAgents.list().size
            first = SubAgents.list().firstOrNull()
            delay(1200)
        }
    }

    if (total == 0) return

    HeaderPanelChip(
        icon = Icons.Default.Groups,
        contentDescription = S.agentSubAgents,
        onClick = { first?.let { com.lucent.app.AppNavigation.openSubAgent(it.id) } },
        tint = tint,
        modifier = modifier,
        badge = if (running > 0) "$running" else "$total"
    )

}

@Composable
private fun EmptyAgentDialog(
    tint: Color,
    mutedTint: Color,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.agentSubAgents, color = tint, fontSize = 15.sp) },
        text = { Text(S.subAgentEmpty, color = mutedTint, fontSize = 12.sp) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(S.actionClose, color = tint) } }
    )
}

@Composable
fun SubAgentDialog(
    agent: SubAgent,
    tint: Color,
    mutedTint: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var instruction by remember { mutableStateOf("") }
    val current = remember(tick) { SubAgents.get(agent.id) ?: agent }

    LaunchedEffect(agent.id) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(current.id + " · " + statusLabel(current.status), color = tint, fontSize = 15.sp)
                Text(S.subAgentRounds(current.rounds), color = mutedTint, fontSize = 11.sp)
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(current.task, color = tint, fontSize = 13.sp)
                if (current.inbox.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(S.subAgentInbox, color = mutedTint, fontSize = 11.sp)
                    current.inbox.forEach { line ->
                        Text(line, color = mutedTint, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(S.subAgentWork, color = mutedTint, fontSize = 11.sp)
                current.transcriptLines().forEach { line ->
                    Text(line, color = mutedTint, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
                if (current.result.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(S.subAgentResult, color = mutedTint, fontSize = 11.sp)
                    Text(current.result, color = tint, fontSize = 12.sp)
                }
                if (current.status == "running") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = instruction,
                            onValueChange = { instruction  = com.lucent.app.collapseExcessBlankLines(it) },
                            placeholder = { Text(S.subAgentInstructHint, color = mutedTint, fontSize = 11.sp) },
                            maxLines = 2,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            enabled = instruction.isNotBlank(),
                            onClick = {
                                SubAgents.instruct(current.id, instruction)
                                instruction = ""
                                tick++
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = S.subAgentSend,
                                tint = tint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(S.subAgentReportFolder(SubAgentReports.FOLDER), color = mutedTint, fontSize = 10.sp)
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current.status == "running") {
                    TextButton(onClick = {
                        SubAgents.stop(current.id)
                        tick++
                    }) {
                        Icon(Icons.Default.Stop, contentDescription = null, tint = OverdueColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(S.subAgentStop, color = OverdueColor)
                    }
                }
                TextButton(onClick = {
                    val file = SubAgentReports.write(context, current)
                    LucentToast.show(context, S.subAgentReportSaved(file.name))
                }) {
                    Text(S.subAgentSaveReport, color = tint)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S.actionClose, color = mutedTint) }
        }
    )
}

@Composable
fun SubAgentChatPage(agentId: String, tint: Color, mutedTint: Color, onBack: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    var instruction by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val current = remember(agentId, tick) { SubAgents.get(agentId) }
    BackHandler(onBack = onBack)
    LaunchedEffect(agentId) {
        while (true) {
            delay(800)
            tick++
        }
    }
    if (current == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = S.actionBack, tint = tint) }
            Text(S.subAgentEmpty, color = mutedTint)
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = S.actionBack, tint = tint) }
            Column {
                Text(current.id + " · " + statusLabel(current.status), color = tint, fontSize = 16.sp)
                Text(current.task, color = mutedTint, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        val children = SubAgents.list().filter { it.parentId == current.id }
        if (children.isNotEmpty()) {
            Text(S.agentSubAgents, color = tint, fontSize = 13.sp, modifier = Modifier.clickable { expanded = !expanded }.padding(8.dp))
            if (expanded) children.forEach { child ->
                Text(child.id + " · " + child.task, color = mutedTint, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clickable {
                    com.lucent.app.AppNavigation.openSubAgent(child.id)
                }.padding(start = 18.dp, top = 5.dp, bottom = 5.dp))
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)) {
            Text(current.task, color = tint, fontSize = 14.sp)
            current.transcriptLines().forEach { Text(it, color = mutedTint, fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            if (current.result.isNotBlank()) Text(current.result, color = tint, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        if (current.status == "running") Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = instruction, onValueChange = { instruction = it }, placeholder = { Text(S.subAgentInstructHint) }, modifier = Modifier.weight(1f))
            IconButton(enabled = instruction.isNotBlank(), onClick = { SubAgents.instruct(current.id, instruction); instruction = "" }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = S.subAgentSend, tint = tint)
            }
        }
    }
}
