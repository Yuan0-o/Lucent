package com.lucent.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Task
import com.lucent.app.tools.TaskActions
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

private enum class CompletedDateField { COMPLETED, CREATED }

@Composable
fun CompletedTasksScreen(
    onBack: () -> Unit,
    onOpen: (Task) -> Unit,
    onDeleteRequest: (Task) -> Unit,
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val completed by db.taskDao().getCompleted().collectAsState(initial = com.lucent.app.data.DataCache.completedTasks)
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val hazeState = LocalHazeState.current

    var searchQuery by remember { mutableStateOf("") }
    var taskToRestore by remember { mutableStateOf<Task?>(null) }
    var dateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    taskToRestore?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToRestore = null },
            title = { Text(com.lucent.app.i18n.S.markNotDoneTitle) },
            text = {
                Text(
                    com.lucent.app.i18n.S.markNotDoneBody(task.title.ifBlank { com.lucent.app.i18n.S.untitledTask })
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = task
                    taskToRestore = null
                    AppScope.io.launch { TaskActions.restore(context, db, target) }
                }) { Text(com.lucent.app.i18n.S.markNotDone) }
            },
            dismissButton = { TextButton(onClick = { taskToRestore = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }
    var dateField by remember { mutableStateOf(CompletedDateField.COMPLETED) }

    val filtered = remember(completed, searchQuery, dateRange, dateField) {
        completed.filter { task ->
            val matchesText = searchQuery.isBlank() || task.title.contains(searchQuery, ignoreCase = true)
            val matchesDate = dateRange?.let { (start, end) ->
                when (dateField) {
                    CompletedDateField.CREATED -> withinLocalDayRange(task.createdAt, start, end)
                    CompletedDateField.COMPLETED -> task.completedAt?.let { withinLocalDayRange(it, start, end) } ?: false
                }
            } ?: true
            matchesText && matchesDate
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.lucent.app.i18n.S.actionBack, tint = onGradient)
            }
            Text(com.lucent.app.i18n.S.screenCompletedTasks, color = onGradient, fontSize = 20.sp, modifier = Modifier.weight(1f))
        }

        if (completed.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val dayMillis = 24L * 60 * 60 * 1000
            val past7 = completed.count { (it.completedAt ?: 0L) >= now - 7 * dayMillis }
            val past30 = completed.count { (it.completedAt ?: 0L) >= now - 30 * dayMillis }
            Row(
                modifier = Modifier.fillMaxWidth().frostedGlass().padding(vertical = 12.dp, horizontal = 8.dp)
            ) {
                StatTile(value = completed.size.toString(), label = com.lucent.app.i18n.S.statTotal, modifier = Modifier.weight(1f))
                StatTile(value = past7.toString(), label = com.lucent.app.i18n.S.statPast7, modifier = Modifier.weight(1f))
                StatTile(value = past30.toString(), label = com.lucent.app.i18n.S.statPast30, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(com.lucent.app.i18n.S.searchCompleted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            DateFilterIconButton(
                active = dateRange != null,
                onClick = {
                    showDateRangePicker(context, dateRange?.first, dateRange?.second) { start, end ->
                        dateRange = start to end
                    }
                }
            )
        }

        dateRange?.let { (start, end) ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = dateField == CompletedDateField.COMPLETED,
                    onClick = { dateField = CompletedDateField.COMPLETED },
                    label = { Text(com.lucent.app.i18n.S.filterCompletedOn) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = dateField == CompletedDateField.CREATED,
                    onClick = { dateField = CompletedDateField.CREATED },
                    label = { Text(com.lucent.app.i18n.S.filterCreatedOn) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            DateFilterChip(startMillis = start, endMillis = end, onClear = { dateRange = null })
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filtered.isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(24.dp)) {
                Text(
                    if (completed.isEmpty()) com.lucent.app.i18n.S.completedEmpty else com.lucent.app.i18n.S.completedNoMatch,
                    color = onGradientMuted
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.hazeSource(state = hazeState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current)
        ) {
            items(filtered, key = { it.id }) { task ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlass()
                        .clickable { onOpen(task) }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            CompletedCheckbox()
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    task.title.ifBlank { com.lucent.app.i18n.S.untitledTask },
                                    color = onGradient,
                                    textDecoration = TextDecoration.LineThrough
                                )
                                task.completedAt?.let { done ->
                                    Text(com.lucent.app.i18n.S.completedOn(formatTimestamp(done)), color = onGradientMuted, fontSize = 12.sp)
                                }
                                Text(com.lucent.app.i18n.S.createdOn(formatTimestamp(task.createdAt)), color = onGradientMuted, fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = { taskToRestore = task }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = com.lucent.app.i18n.S.a11yMarkActive, tint = onGradient)
                        }
                        IconButton(onClick = { onDeleteRequest(task) }) {
                            Icon(Icons.Default.Delete, contentDescription = com.lucent.app.i18n.S.actionDelete, tint = onGradient)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = onGradient, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = onGradientMuted, fontSize = 12.sp)
    }
}
