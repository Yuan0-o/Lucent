package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.lucent.app.harness.PlanBoard
import com.lucent.app.harness.PlanStep
import com.lucent.app.harness.TodoBoard
import com.lucent.app.harness.TodoItem
import com.lucent.app.i18n.S
import kotlinx.coroutines.delay

@Composable
fun TodoChip(
    conversationId: Long?,
    tint: Color,
    mutedTint: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val revision by TodoBoard.changes.collectAsState()
    var items by remember { mutableStateOf<List<TodoItem>>(emptyList()) }

    LaunchedEffect(conversationId, revision) {
        conversationId?.let { TodoBoard.load(context.applicationContext, it) }
        items = TodoBoard.todos()
    }

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
        Icon(Icons.Default.CheckCircle, contentDescription = S.todoPanelTitle, tint = tint, modifier = Modifier.size(16.dp))
        if (items.isNotEmpty()) {
            Text("${items.count { it.status == "done" }}/${items.size}", color = tint, fontSize = 11.sp)
        }
    }

    if (open) {
        TodoPanelDialog(
            items = items,
            tint = tint,
            mutedTint = mutedTint,
            onDismiss = { open = false }
        )
    }
}

@Composable
fun TodoPanelDialog(
    items: List<TodoItem>,
    tint: Color,
    mutedTint: Color,
    onDismiss: () -> Unit
) {
    var plan by remember { mutableStateOf(PlanBoard.current()) }
    var todos by remember { mutableStateOf(items) }
    var todosExpanded by remember { mutableStateOf(true) }
    var planExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            plan = PlanBoard.current()
            todos = TodoBoard.todos()
            delay(1000)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.todoPanelTitle, color = tint, fontSize = 15.sp) },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                PanelSection(
                    title = todoSectionTitle(todos),
                    expanded = todosExpanded,
                    tint = tint,
                    mutedTint = mutedTint,
                    onToggle = { todosExpanded = !todosExpanded }
                )
                if (todosExpanded) {
                    if (todos.isEmpty()) {
                        Text(S.todoEmpty, color = mutedTint, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        todos.forEach { item -> TodoRow(item = item, tint = tint, mutedTint = mutedTint) }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                PanelSection(
                    title = planSectionTitle(plan),
                    expanded = planExpanded,
                    tint = tint,
                    mutedTint = mutedTint,
                    onToggle = { planExpanded = !planExpanded }
                )
                if (planExpanded) {
                    if (plan.isEmpty()) {
                        Text(S.todoPlanEmpty, color = mutedTint, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        plan.forEach { step -> PlanRow(step = step, tint = tint, mutedTint = mutedTint) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(S.actionClose, color = tint) } }
    )
}

@Composable
private fun PanelSection(
    title: String,
    expanded: Boolean,
    tint: Color,
    mutedTint: Color,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = tint, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = mutedTint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TodoRow(item: TodoItem, tint: Color, mutedTint: Color) {
    val done = item.status == "done"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TodoMark(status = item.status, tint = tint)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, color = if (done) mutedTint else tint, fontSize = 12.sp)
            if (item.status == "active") {
                Text(S.todoStatusActive, color = mutedTint, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun TodoMark(status: String, tint: Color) {
    val markSize = 14.dp
    val shape = RoundedCornerShape(percent = 50)
    when (status) {
        "done" -> Box(modifier = Modifier.size(markSize).clip(shape).background(tint))
        "active" -> Box(modifier = Modifier.size(markSize).clip(shape).background(tint.copy(alpha = 0.55f)))
        else -> Box(modifier = Modifier.size(markSize).clip(shape).border(1.dp, tint.copy(alpha = 0.45f), shape))
    }
}

@Composable
private fun PlanRow(step: PlanStep, tint: Color, mutedTint: Color) {
    val done = step.status.startsWith("done") || step.status.startsWith("complete")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (done) "[x]" else if (step.status.startsWith("active")) "[~]" else "[ ]", color = mutedTint, fontSize = 11.sp)
        Text(step.title, color = if (done) mutedTint else tint, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

private fun todoSectionTitle(items: List<TodoItem>): String =
    if (items.isEmpty()) S.todoSectionTodo else "${S.todoSectionTodo} · ${items.count { it.status == "done" }}/${items.size}"

private fun planSectionTitle(plan: List<PlanStep>): String =
    if (plan.isEmpty()) S.todoSectionPlan else "${S.todoSectionPlan} · ${plan.size}"
