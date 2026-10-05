package com.lucent.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.harness.HarnessJobHandle
import com.lucent.app.harness.HarnessJobs
import com.lucent.app.i18n.S
import kotlinx.coroutines.delay

@Composable
fun JobsChip(
    tint: Color,
    mutedTint: Color,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(HarnessJobs.runningCount()) }
    var total by remember { mutableStateOf(HarnessJobs.list().size) }

    LaunchedEffect(Unit) {
        while (true) {
            running = HarnessJobs.runningCount()
            total = HarnessJobs.list().size
            delay(1500)
        }
    }

    if (total == 0) return

    HeaderPanelChip(
        icon = Icons.Default.Build,
        contentDescription = S.jobsPanelTitle,
        onClick = { open = true },
        tint = tint,
        modifier = modifier,
        badge = if (running > 0) "$running" else null
    )

    if (open) {
        JobsPanelDialog(tint = tint, mutedTint = mutedTint, onDismiss = { open = false })
    }
}

@Composable
fun JobsPanelDialog(
    tint: Color,
    mutedTint: Color,
    onDismiss: () -> Unit
) {
    var jobs by remember { mutableStateOf(HarnessJobs.list()) }

    LaunchedEffect(Unit) {
        while (true) {
            jobs = HarnessJobs.list()
            delay(1200)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(S.jobsPanelTitle, color = tint, fontSize = 15.sp)
                Text(jobs.count { !it.finished }.toString() + " " + S.jobsRunning, color = mutedTint, fontSize = 11.sp)
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                if (jobs.isEmpty()) {
                    Text(S.jobsEmpty, color = mutedTint, fontSize = 12.sp)
                } else {
                    jobs.forEachIndexed { index, handle ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        JobRow(
                            handle = handle,
                            tint = tint,
                            mutedTint = mutedTint,
                            onStop = {
                                HarnessJobs.kill(handle.job.id)
                                jobs = HarnessJobs.list()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(S.actionClose, color = tint) } }
    )
}

@Composable
private fun JobRow(
    handle: HarnessJobHandle,
    tint: Color,
    mutedTint: Color,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(handle.job.id, color = tint, fontSize = 12.sp)
            Text(
                handle.job.command,
                color = mutedTint,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${if (handle.finished) S.jobsFinished else S.jobsRunningLabel} · ${formatTimestamp(handle.job.startedAt)}",
                color = mutedTint,
                fontSize = 10.sp
            )
        }
        if (!handle.finished) {
            IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Stop, contentDescription = S.actionStop, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
    }
}
