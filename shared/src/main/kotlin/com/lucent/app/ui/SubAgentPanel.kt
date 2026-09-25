package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var open by remember { mutableStateOf<SubAgent?>(null) }

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
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(tint.copy(alpha = 0.12f))
                    .border(1.dp, tint.copy(alpha = 0.24f), RoundedCornerShape(percent = 50))
                    .clickable { open = agent }
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
        }
    }

    val current = open
    if (current != null) {
        SubAgentDialog(agent = SubAgents.get(current.id) ?: current, tint = tint, mutedTint = mutedTint, onDismiss = { open = null })
    }
}

private fun statusLabel(status: String): String = when (status) {
    "done" -> S.subAgentStatusDone
    "failed" -> S.subAgentStatusFailed
    else -> S.subAgentStatusRunning
}

@Composable
private fun SubAgentDialog(
    agent: SubAgent,
    tint: Color,
    mutedTint: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(agent.id + " · " + statusLabel(agent.status), color = tint, fontSize = 15.sp)
                Text(S.subAgentRounds(agent.rounds), color = mutedTint, fontSize = 11.sp)
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(agent.task, color = tint, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(S.subAgentWork, color = mutedTint, fontSize = 11.sp)
                agent.transcriptLines().forEach { line ->
                    Text(line, color = mutedTint, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
                if (agent.result.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(S.subAgentResult, color = mutedTint, fontSize = 11.sp)
                    Text(agent.result, color = tint, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(S.subAgentReportFolder(SubAgentReports.FOLDER), color = mutedTint, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val file = SubAgentReports.write(context, agent)
                LucentToast.show(context, S.subAgentReportSaved(file.name))
            }) {
                Text(S.subAgentSaveReport, color = tint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S.actionClose, color = mutedTint) }
        }
    )
}
