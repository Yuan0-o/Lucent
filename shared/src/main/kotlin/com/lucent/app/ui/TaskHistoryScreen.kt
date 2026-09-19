package com.lucent.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Checklist
import com.lucent.app.data.Task
import com.lucent.app.data.TaskHistory
import com.lucent.app.data.TaskPriority
import com.lucent.app.data.TaskVersion
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@Composable
fun TaskHistoryScreen(
    task: Task,
    onBack: () -> Unit,
    onRestored: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val hazeState = LocalHazeState.current

    val versions by db.taskVersionDao().getForTask(task.id).collectAsState(initial = emptyList())

    var previewing by remember { mutableStateOf<TaskVersion?>(null) }
    var confirmRestore by remember { mutableStateOf<TaskVersion?>(null) }
    var confirmDelete by remember { mutableStateOf<TaskVersion?>(null) }

    BackHandler(enabled = previewing != null) { previewing = null }

    fun restore(version: TaskVersion) {
        AppScope.io.launch {
            val current = db.taskDao().getByIdOnce(task.id) ?: return@launch
            val restored = TaskHistory.applyTo(current, version)
            TaskHistory.recordIfChanged(
                db = db,
                existing = current,
                newTitle = restored.title,
                newNotes = restored.notes,
                newSubtasks = restored.subtasks,
                newPriority = restored.priority,
                newDueAt = restored.dueAt
            )
            db.taskDao().update(restored)
        }
        previewing = null
        confirmRestore = null
        onRestored()
    }

    confirmRestore?.let { version ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(com.lucent.app.i18n.S.restoreVersionTitle) },
            text = { Text(com.lucent.app.i18n.S.restoreVersionBody(formatTimestamp(version.savedAt))) },
            confirmButton = { TextButton(onClick = { restore(version) }) { Text(com.lucent.app.i18n.S.actionRestore) } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    confirmDelete?.let { version ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(com.lucent.app.i18n.S.deleteVersionTitle) },
            text = { Text(com.lucent.app.i18n.S.deleteVersionBody(formatTimestamp(version.savedAt))) },
            confirmButton = {
                TextButton(onClick = {
                    AppScope.io.launch { db.taskVersionDao().deleteById(version.id) }
                    if (previewing?.id == version.id) previewing = null
                    confirmDelete = null
                }) { Text(com.lucent.app.i18n.S.actionDelete) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    val preview = previewing
    if (preview != null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()).padding(bottom = LocalBottomBarInset.current)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { previewing = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.lucent.app.i18n.S.actionBack, tint = onGradient)
                }
                Text(com.lucent.app.i18n.S.screenVersion, color = onGradient, fontSize = 20.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { confirmDelete = preview }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = com.lucent.app.i18n.S.deleteThisVersion, tint = onGradientMuted)
                }
            }

            Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
                Text(preview.title.ifBlank { com.lucent.app.i18n.S.untitled }, color = onGradient, fontSize = 22.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(com.lucent.app.i18n.S.historyAsOf(formatTimestamp(preview.savedAt)), color = onGradientMuted, fontSize = 12.sp)

                val priority = remember(preview.priority) { TaskPriority.fromValue(preview.priority) }
                if (priority != TaskPriority.NONE) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(com.lucent.app.i18n.S.priorityBadge(priority.uiLabel), color = priority.color(), fontSize = 12.sp)
                }
                preview.dueAt?.let { due ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(friendlyDue(due), color = onGradientMuted, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                if (preview.notes.isNotBlank()) {
                    MarkdownText(text = preview.notes)
                } else if (preview.subtasks == "[]") {
                    Text(com.lucent.app.i18n.S.emptyParen, color = onGradientMuted)
                }

                val items = remember(preview.subtasks) { Checklist.parse(preview.subtasks) }
                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ChecklistView(
                        items = items,
                        onToggle = null,
                        header = com.lucent.app.i18n.S.historySubtasksHeader
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            GlassCapsuleButton(
                text = com.lucent.app.i18n.S.restoreThisVersion,
                icon = Icons.AutoMirrored.Filled.Undo,
                onClick = { confirmRestore = preview }
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.lucent.app.i18n.S.actionBack, tint = onGradient)
            }
            Text(com.lucent.app.i18n.S.screenVersionHistory, color = onGradient, fontSize = 20.sp, modifier = Modifier.weight(1f))
        }

        Text(com.lucent.app.i18n.S.historyIntro(task.title.ifBlank { com.lucent.app.i18n.S.untitled }))
        Spacer(modifier = Modifier.height(16.dp))

        if (versions.isEmpty()) {
            EmptyState(
                isFiltered = false,
                emptyMessage = com.lucent.app.i18n.S.historyEmpty,
                noMatchMessage = ""
            )
            return
        }

        LazyColumn(
            modifier = Modifier.hazeSource(state = hazeState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current)
        ) {
            items(versions, key = { it.id }) { version ->
                TaskVersionCard(
                    version = version,
                    onPreview = { previewing = version },
                    onRestore = { confirmRestore = version },
                    onDelete = { confirmDelete = version }
                )
            }
        }
    }
}

@Composable
private fun TaskVersionCard(
    version: TaskVersion,
    onPreview: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    val preview = remember(version) {
        if (version.notes.isNotBlank()) {
            version.notes
        } else {
            val items = Checklist.parse(version.subtasks)
            if (items.isEmpty()) com.lucent.app.i18n.S.emptyParen
            else com.lucent.app.i18n.S.checklistDoneCount(items.count { it.done }, items.size)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .clickable { onPreview() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    version.title.ifBlank { com.lucent.app.i18n.S.untitled },
                    color = onGradient,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(formatTimestamp(version.savedAt), color = onGradientMuted, fontSize = 12.sp)
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = com.lucent.app.i18n.S.restoreThisVersion, tint = onGradient)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = com.lucent.app.i18n.S.deleteThisVersion, tint = onGradientMuted)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            preview,
            color = onGradientMuted,
            fontSize = 13.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
