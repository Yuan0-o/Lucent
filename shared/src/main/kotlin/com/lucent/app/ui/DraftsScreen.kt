package com.lucent.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Unarchive
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
import com.lucent.app.data.Note
import com.lucent.app.data.Task
import com.lucent.app.data.TrashCleanup
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@Composable
fun DraftNotesScreen(onBack: () -> Unit, onOpen: (Note) -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val drafts by db.noteDao().getDrafts().collectAsState(initial = emptyList())
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val hazeState = LocalHazeState.current

    var toPurge by remember { mutableStateOf<Note?>(null) }

    toPurge?.let { note ->
        AlertDialog(
            onDismissRequest = { toPurge = null },
            title = { Text(com.lucent.app.i18n.S.actionDelete) },
            text = { Text(note.title.ifBlank { com.lucent.app.i18n.S.untitled }) },
            confirmButton = {
                TextButton(onClick = {
                    AppScope.io.launch { TrashCleanup.purgeNote(context, db, note) }
                    toPurge = null
                }) { Text(com.lucent.app.i18n.S.actionDelete) }
            },
            dismissButton = { TextButton(onClick = { toPurge = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    DraftScaffold(
        onBack = onBack,
        isEmpty = drafts.isEmpty(),
        hazeState = hazeState
    ) {
        items(drafts, key = { it.id }) { note ->
            DraftRow(
                title = note.title,
                subtitle = if (note.isChecklist) {
                    val items = Checklist.parse(note.checklist)
                    com.lucent.app.i18n.S.checklistDoneCount(items.count { it.done }, items.size)
                } else note.body,
                savedAt = note.draftSavedAt ?: note.updatedAt,
                onOpen = { onOpen(note) },
                onPromote = {
                    AppScope.io.launch {
                        db.noteDao().update(note.copy(isDraft = false, draftSavedAt = null))
                    }
                },
                onDelete = { toPurge = note },
                onGradient = onGradient,
                onGradientMuted = onGradientMuted
            )
        }
    }
}

@Composable
fun DraftTasksScreen(onBack: () -> Unit, onOpen: (Task) -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val drafts by db.taskDao().getDrafts().collectAsState(initial = emptyList())
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val hazeState = LocalHazeState.current

    var toPurge by remember { mutableStateOf<Task?>(null) }

    toPurge?.let { task ->
        AlertDialog(
            onDismissRequest = { toPurge = null },
            title = { Text(com.lucent.app.i18n.S.actionDelete) },
            text = { Text(task.title.ifBlank { com.lucent.app.i18n.S.untitled }) },
            confirmButton = {
                TextButton(onClick = {
                    AppScope.io.launch { TrashCleanup.purgeTask(context, db, task) }
                    toPurge = null
                }) { Text(com.lucent.app.i18n.S.actionDelete) }
            },
            dismissButton = { TextButton(onClick = { toPurge = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    DraftScaffold(
        onBack = onBack,
        isEmpty = drafts.isEmpty(),
        hazeState = hazeState
    ) {
        items(drafts, key = { it.id }) { task ->
            val items = remember(task.subtasks) { Checklist.parse(task.subtasks) }
            DraftRow(
                title = task.title,
                subtitle = task.notes.ifBlank {
                    if (items.isEmpty()) "" else com.lucent.app.i18n.S.checklistDoneCount(items.count { it.done }, items.size)
                },
                savedAt = task.draftSavedAt ?: task.createdAt,
                onOpen = { onOpen(task) },
                onPromote = {
                    AppScope.io.launch {
                        db.taskDao().update(task.copy(isDraft = false, draftSavedAt = null))
                    }
                },
                onDelete = { toPurge = task },
                onGradient = onGradient,
                onGradientMuted = onGradientMuted
            )
        }
    }
}

@Composable
private fun DraftScaffold(
    onBack: () -> Unit,
    isEmpty: Boolean,
    hazeState: dev.chrisbanes.haze.HazeState,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val onGradient = LocalOnGradient.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.lucent.app.i18n.S.actionBack, tint = onGradient)
            }
            Text(com.lucent.app.i18n.S.screenDrafts, color = onGradient, fontSize = 20.sp, modifier = Modifier.weight(1f))
        }

        if (isEmpty) {
            EmptyState(
                isFiltered = false,
                emptyMessage = com.lucent.app.i18n.S.draftsEmpty,
                noMatchMessage = ""
            )
            return
        }

        LazyColumn(
            state = rememberRestoredListState("DraftsScreen#1"),
            modifier = Modifier.hazeSource(state = hazeState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current),
            content = content
        )
    }
}

@Composable
private fun DraftRow(
    title: String,
    subtitle: String,
    savedAt: Long,
    onOpen: () -> Unit,
    onPromote: () -> Unit,
    onDelete: () -> Unit,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .clickable { onOpen() }
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title.ifBlank { com.lucent.app.i18n.S.untitled },
                    color = onGradient,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(formatTimestamp(savedAt), color = onGradientMuted, fontSize = 12.sp)
            }
            IconButton(onClick = onPromote) {
                Icon(Icons.Default.Unarchive, contentDescription = com.lucent.app.i18n.S.draftPromote, tint = onGradient)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteForever, contentDescription = com.lucent.app.i18n.S.actionDelete, tint = onGradientMuted)
            }
        }
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(subtitle, color = onGradientMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

object DraftRestorePrompt {
    var asked: Boolean = false
}

@Composable
fun DraftRestoreDialog(draftCount: Int, onOpenDrafts: () -> Unit) {
    var visible by remember { mutableStateOf(!DraftRestorePrompt.asked && draftCount > 0) }
    if (!visible) return
    DraftRestorePrompt.asked = true
    AlertDialog(
        onDismissRequest = { visible = false },
        title = { Text(com.lucent.app.i18n.S.draftRestoreTitle) },
        text = { Text(com.lucent.app.i18n.S.draftRestoreBody(draftCount)) },
        confirmButton = {
            TextButton(onClick = { visible = false; onOpenDrafts() }) {
                Text(com.lucent.app.i18n.S.draftOpen)
            }
        },
        dismissButton = { TextButton(onClick = { visible = false }) { Text(com.lucent.app.i18n.S.actionDismiss) } }
    )
}
