package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.harness.GoalPhase
import com.lucent.app.harness.GoalState
import com.lucent.app.harness.GoalStore
import com.lucent.app.i18n.S
import kotlinx.coroutines.delay

@Composable
fun GoalChip(
    conversationId: Long?,
    tint: Color,
    mutedTint: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val revision by GoalStore.changes.collectAsState()
    var goal by remember { mutableStateOf<GoalState?>(GoalStore.current()) }

    LaunchedEffect(conversationId, revision) {
        conversationId?.let { GoalStore.load(context.applicationContext, it) }
        goal = GoalStore.current()
    }

    val shown = goal ?: return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.24f), RoundedCornerShape(percent = 50))
            .clickable { open = true }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Default.Flag, contentDescription = S.goalPanelTitle, tint = tint, modifier = Modifier.size(16.dp))
        Text("${shown.roundsStarted}/${shown.maxRounds}", color = tint, fontSize = 11.sp)
    }

    if (open) {
        GoalPanelDialog(
            conversationId = conversationId,
            tint = tint,
            mutedTint = mutedTint,
            onDismiss = { open = false }
        )
    }
}

@Composable
fun GoalPanelDialog(
    conversationId: Long?,
    tint: Color,
    mutedTint: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var goal by remember { mutableStateOf(GoalStore.current()) }

    LaunchedEffect(conversationId) {
        while (true) {
            conversationId?.let { GoalStore.load(context.applicationContext, it) }
            goal = GoalStore.current()
            delay(1000)
        }
    }

    val shown = goal

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.goalPanelTitle, color = tint, fontSize = 15.sp) },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                if (shown == null) {
                    Text(S.goalEmpty, color = mutedTint, fontSize = 12.sp)
                } else {
                    Text(goalPhaseLabel(shown.phase), color = tint, fontSize = 13.sp)
                    Text(S.goalRounds(shown.roundsStarted, shown.maxRounds), color = mutedTint, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(shown.objective, color = tint, fontSize = 12.sp)
                    if (shown.blockerReason.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(S.goalBlocker(shown.blockerReason), color = mutedTint, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (shown != null) {
                    when (shown.phase) {
                        GoalPhase.ACTIVE -> TextButton(
                            onClick = { GoalStore.pause(context.applicationContext, goalTarget(conversationId)) }
                        ) { Text(S.goalPause, color = tint) }
                        GoalPhase.COMPLETE -> Unit
                        else -> TextButton(
                            onClick = { GoalStore.resume(context.applicationContext, goalTarget(conversationId)) }
                        ) { Text(S.goalResume, color = tint) }
                    }
                    if (!shown.finished) {
                        TextButton(
                            onClick = { GoalStore.complete(context.applicationContext, goalTarget(conversationId)) }
                        ) { Text(S.goalComplete, color = tint) }
                    }
                }
                TextButton(onClick = onDismiss) { Text(S.actionClose, color = tint) }
            }
        }
    )
}

private fun goalTarget(conversationId: Long?): Long =
    conversationId ?: GoalStore.loadedFor()

private fun goalPhaseLabel(phase: GoalPhase): String = when (phase) {
    GoalPhase.ACTIVE -> S.goalPhaseActive
    GoalPhase.PAUSED -> S.goalPhasePaused
    GoalPhase.COMPLETE -> S.goalPhaseComplete
    GoalPhase.BLOCKED -> S.goalPhaseBlocked
}
