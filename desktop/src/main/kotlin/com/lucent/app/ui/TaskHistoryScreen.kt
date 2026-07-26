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

/**
 * A task's local revision history (task A19) — the exact counterpart of [NoteHistoryScreen], down
 * to the layout, so the two features are learned once rather than twice.
 *
 * The only real difference is *what a version is made of*. A note version is title + body + tags;
 * a task version is title + details + subtasks + priority + due date. So the preview line reports
 * a subtask count where the note version reports body text, and the read-only preview renders the
 * subtask list rather than Markdown.
 *
 * Restoring is recorded as an edit of its own (see [TaskHistory.applyTo] and the restore path
 * below), which makes restoring undoable in turn.
 */
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
            // Re-read the live row rather than trusting the copy this screen was composed with: the
            // assistant could have edited the task while the history page sat open, and restoring
            // over a stale snapshot would silently throw that edit away without recording it.
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

    // Task A19 also asks for *manual* deletion. Automatic trimming answers "don't grow forever"; it
    // does not answer "I don't want that one kept", which is a different and entirely reasonable
    // request — a revision can contain a sentence someone would rather not keep a copy of.
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
        // ---- Read-only preview of one old version ----
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

                // Priority and due date belong in the preview because they are part of what this
                // version *was* — restoring puts them back, so seeing them first is the difference
                // between an informed restore and a surprise.
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
                    // Read-only: this is a photograph of the past, not a live list, and offering a
                    // checkbox that could not persist anywhere would be a lie.
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

    // ---- The list of versions ----
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

/**
 * One revision in the list: when it was current, and enough of its content to recognise it by.
 *
 * The preview line is what makes the list usable at all — a column of bare timestamps forces the
 * user to open every one to find what they are after, which is exactly the frustration the feature
 * exists to remove. For a task that means the details text when there is one, and the subtask
 * count when there isn't, because a task whose content lives entirely in its checklist is common
 * and would otherwise render as a blank row.
 */
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
            // Task A19 — muted, and second: deleting a revision is a rarer intent than restoring
            // one, and on a screen full of things you might want back, the destructive control
            // should not be the loudest thing on the row.
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
