package com.lucent.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lucent.app.AppNavigation
import com.lucent.app.AppScope
import com.lucent.app.Screen
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Note
import com.lucent.app.data.StartupLog
import com.lucent.app.data.Task
import com.lucent.app.data.TrashCleanup
import com.lucent.app.tools.TaskActions
import kotlinx.coroutines.launch

@Composable
fun HomePanelPage(panel: HomePanel, from: Screen) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var mode by rememberSaveable(panel) { mutableStateOf(LastScreen.homeMode) }
    var noteToTrash by remember { mutableStateOf<Note?>(null) }
    var taskToTrash by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(panel, mode) {
        StartupLog.event(context, "panel: showing ${panel.logKey} for ${mode.name.lowercase()}")
    }

    val back: () -> Unit = { AppNavigation.requestScreen(mode.screen) }
    val openNote: (Note) -> Unit = { AppNavigation.openNote(it.id, from = from) }
    val openTask: (Task) -> Unit = { AppNavigation.openTask(it.id, from = from) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeModeSwitcher(mode = mode, onSelect = { mode = it })
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (mode) {
                HomeMode.Notes -> when (panel) {
                    HomePanel.Drafts -> DraftNotesScreen(onBack = back, onOpen = { AppNavigation.editNote(it.id) })
                    HomePanel.Archive -> ArchivedNotesScreen(onBack = back, onOpen = openNote, onDeleteRequest = { noteToTrash = it })
                    HomePanel.Trash -> TrashNotesScreen(onBack = back)
                    HomePanel.Hidden -> HiddenNotesScreen(onBack = back, onOpen = openNote)
                }
                HomeMode.Tasks -> when (panel) {
                    HomePanel.Drafts -> DraftTasksScreen(onBack = back, onOpen = { AppNavigation.editTask(it.id) })
                    HomePanel.Archive -> CompletedTasksScreen(onBack = back, onOpen = openTask, onDeleteRequest = { taskToTrash = it })
                    HomePanel.Trash -> TrashTasksScreen(onBack = back)
                    HomePanel.Hidden -> HiddenTasksScreen(onBack = back, onOpen = openTask)
                }
            }
        }
    }

    noteToTrash?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToTrash = null },
            title = { Text(com.lucent.app.i18n.S.moveToTrashTitle) },
            text = {
                Text(com.lucent.app.i18n.S.moveNoteTrashBody(note.title.ifBlank { com.lucent.app.i18n.S.untitledNote }, TrashCleanup.RETENTION_DAYS))
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = note
                    noteToTrash = null
                    AppScope.io.launch {
                        db.noteDao().update(target.copy(trashedAt = System.currentTimeMillis()))
                        StartupLog.event(context, "panel: moved an archived note to the trash")
                    }
                }) { Text(com.lucent.app.i18n.S.moveToTrash) }
            },
            dismissButton = { TextButton(onClick = { noteToTrash = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    taskToTrash?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToTrash = null },
            title = { Text(com.lucent.app.i18n.S.moveToTrashTitle) },
            text = {
                Text(com.lucent.app.i18n.S.moveTaskTrashBody(task.title.ifBlank { com.lucent.app.i18n.S.untitledTask }, TrashCleanup.RETENTION_DAYS))
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = task
                    taskToTrash = null
                    AppScope.io.launch {
                        TaskActions.trash(context, db, target)
                        StartupLog.event(context, "panel: moved a completed task to the trash")
                    }
                }) { Text(com.lucent.app.i18n.S.moveToTrash) }
            },
            dismissButton = { TextButton(onClick = { taskToTrash = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }
}
