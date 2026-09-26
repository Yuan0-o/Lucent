package com.lucent.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.i18n.S

@Composable
fun AgentTracePanel(
    trace: AgentTrace,
    tint: Color,
    mutedTint: Color,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!trace.hasContent) return
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    var thinkingOpen by remember(initiallyExpanded) { mutableStateOf(false) }

    val pulse = if (trace.status == AgentStepStatus.RUNNING) {
        val transition = rememberInfiniteTransition(label = "agentTrace")
        val value by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 700), repeatMode = RepeatMode.Reverse),
            label = "agentTracePulse"
        )
        value
    } else 1f
    val errorTint = if (tint == Color.White) Color(0xFFFFC1C1) else Color(0xFFC62828)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Build, contentDescription = null, tint = mutedTint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(S.agentTraceTitle, color = tint, fontSize = 12.sp)
            if (trace.steps.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(S.agentTraceStepCount(trace.steps.size), color = mutedTint, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                statusLabel(trace.status),
                color = if (trace.status == AgentStepStatus.FAILED) errorTint else mutedTint,
                fontSize = 11.sp
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = mutedTint,
                modifier = Modifier.size(16.dp)
            )
        }

        if (expanded) {
            if (trace.reasoning.isNotBlank()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { thinkingOpen = !thinkingOpen }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(S.agentTraceThinking, color = mutedTint, fontSize = 12.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            if (thinkingOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = mutedTint,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (thinkingOpen) {
                        Text(
                            trace.reasoning,
                            color = mutedTint,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(tint.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .border(1.dp, mutedTint.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }
                }
                if (trace.steps.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 6.dp),
                        color = mutedTint.copy(alpha = 0.20f)
                    )
                }
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                trace.steps.forEach { step ->
                    AgentStepRow(
                        step = step,
                        tint = tint,
                        mutedTint = mutedTint,
                        errorTint = errorTint,
                        pulse = pulse
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentStepRow(
    step: AgentStep,
    tint: Color,
    mutedTint: Color,
    errorTint: Color,
    pulse: Float
) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Icon(
            statusIcon(step.status),
            contentDescription = null,
            tint = if (step.status == AgentStepStatus.FAILED) errorTint else mutedTint,
            modifier = Modifier
                .size(13.dp)
                .alpha(if (step.status == AgentStepStatus.RUNNING) pulse else 1f)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (step.status == AgentStepStatus.CANCELLED) mutedTint else tint)) {
                        append(step.label())
                    }
                    if (step.millis > 0L) {
                        withStyle(SpanStyle(color = mutedTint)) {
                            append(" ")
                            append(durationLabel(step.millis))
                        }
                    }
                    if (step.detail.isNotBlank()) {
                        withStyle(SpanStyle(color = mutedTint)) {
                            append(" \u00b7 ")
                            append(step.detail)
                        }
                    }
                },
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (step.errorText.isNotBlank()) {
                Text(
                    step.errorText,
                    color = errorTint,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun statusLabel(status: AgentStepStatus): String = when (status) {
    AgentStepStatus.RUNNING -> S.agentTraceRunning
    AgentStepStatus.DONE -> S.actionDone
    AgentStepStatus.FAILED -> S.agentTraceFailed
    AgentStepStatus.CANCELLED -> S.agentTraceCancelled
}

private fun durationLabel(millis: Long): String =
    if (millis < 1000L) "$millis ms" else "${millis / 1000}.${(millis % 1000) / 100} s"

private fun statusIcon(status: AgentStepStatus): ImageVector = when (status) {
    AgentStepStatus.RUNNING -> Icons.Default.Refresh
    AgentStepStatus.DONE -> Icons.Default.CheckCircle
    AgentStepStatus.FAILED -> Icons.Default.Warning
    AgentStepStatus.CANCELLED -> Icons.Default.Close
}
