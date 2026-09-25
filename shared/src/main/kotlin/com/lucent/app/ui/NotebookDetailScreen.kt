package com.lucent.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppNavigation
import com.lucent.app.AppScope
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Checklist
import com.lucent.app.data.Note
import com.lucent.app.data.Notebook
import com.lucent.app.data.NotebookItem
import com.lucent.app.data.Task
import com.lucent.app.tools.TaskActions
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@Composable
fun NotebookDetailScreen(
    notebookId: Long,
    onBack: () -> Unit,
    onOpenNote: (Note) -> Unit,
    onOpenTask: (Task) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    var notebook by remember { mutableStateOf<Notebook?>(null) }
    LaunchedEffect(notebookId) {
        notebook = db.notebookDao().getByIdOnce(notebookId)
    }
    val items by db.notebookDao().getItems(notebookId).collectAsState(initial = emptyList())

    val noteMembers = remember(items) { items.filter { it.itemKind == NotebookItem.KIND_NOTE } }
    val taskMembers = remember(items) { items.filter { it.itemKind == NotebookItem.KIND_TASK } }
    var notesById by remember { mutableStateOf(emptyMap<Long, Note>()) }
    var tasksById by remember { mutableStateOf(emptyMap<Long, Task>()) }
    LaunchedEffect(noteMembers, taskMembers) {
        val notes = if (noteMembers.isEmpty()) emptyMap()
        else db.noteDao().getByIds(noteMembers.map { it.itemId }.toSet().toList()).associateBy { it.id }
        val tasks = if (taskMembers.isEmpty()) emptyMap()
        else db.taskDao().getByIds(taskMembers.map { it.itemId }.toSet().toList()).associateBy { it.id }
        notesById = notes
        tasksById = tasks
        val ghosts = noteMembers.filter { it.itemId !in notes } + taskMembers.filter { it.itemId !in tasks }
        if (ghosts.isNotEmpty()) ghosts.forEach { db.notebookDao().deleteItemById(it.id) }
    }

    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var addMenuOpen by remember { mutableStateOf(false) }
    var composingNote by remember { mutableStateOf(false) }
    var composingTask by remember { mutableStateOf(false) }
    var noteToTrash by remember { mutableStateOf<Note?>(null) }
    var taskToTrash by remember { mutableStateOf<Task?>(null) }
    val title = notebook?.title?.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle } ?: ""

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(com.lucent.app.i18n.S.notebookDeleteTitle) },
            text = { Text(com.lucent.app.i18n.S.notebookTrashBody(title)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    AppScope.io.launch {
                        db.notebookDao().update(
                            (notebook ?: return@launch).copy(trashedAt = System.currentTimeMillis())
                        )
                        LucentToast.show(context, com.lucent.app.i18n.S.notebookDeletedToast)
                    }
                    onBack()
                }) { Text(com.lucent.app.i18n.S.actionDelete) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text(com.lucent.app.i18n.S.actionCancel) }
            }
        )
    }

    if (composingNote) {
        NotebookNewNoteDialog(
            notebookId = notebookId,
            onDismiss = { composingNote = false },
            onCreated = { composingNote = false }
        )
    }

    if (composingTask) {
        NotebookNewTaskDialog(
            notebookId = notebookId,
            onDismiss = { composingTask = false },
            onCreated = { composingTask = false }
        )
    }

    noteToTrash?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToTrash = null },
            title = { Text(com.lucent.app.i18n.S.moveToTrashTitle) },
            text = {
                Text(
                    com.lucent.app.i18n.S.moveNoteTrashBody(
                        note.title.ifBlank { com.lucent.app.i18n.S.untitledNote },
                        com.lucent.app.data.TrashCleanup.RETENTION_DAYS
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = note
                    noteToTrash = null
                    AppScope.io.launch {
                        db.noteDao().update(target.copy(trashedAt = System.currentTimeMillis()))
                        items.firstOrNull {
                            it.itemKind == NotebookItem.KIND_NOTE && it.itemId == target.id
                        }?.let { db.notebookDao().deleteItemById(it.id) }
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
                Text(
                    com.lucent.app.i18n.S.moveTaskTrashBody(
                        task.title.ifBlank { com.lucent.app.i18n.S.untitledTask },
                        com.lucent.app.data.TrashCleanup.RETENTION_DAYS
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = task
                    taskToTrash = null
                    AppScope.io.launch {
                        TaskActions.trash(context, db, target)
                        items.firstOrNull {
                            it.itemKind == NotebookItem.KIND_TASK && it.itemId == target.id
                        }?.let { db.notebookDao().deleteItemById(it.id) }
                    }
                }) { Text(com.lucent.app.i18n.S.moveToTrash) }
            },
            dismissButton = { TextButton(onClick = { taskToTrash = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    if (renaming) {
        NotebookEditorDialog(
            title = com.lucent.app.i18n.S.notebookRename,
            confirmLabel = com.lucent.app.i18n.S.notebookRenameAction,
            initialName = notebook?.title ?: "",
            initialColor = NotebookColor.fromKey(notebook?.color),
            showColorPicker = false,
            onConfirm = { name, _ ->
                val row = notebook
                if (row != null) {
                    scope.launch {
                        db.notebookDao().update(
                            row.copy(title = name.trim(), updatedAt = System.currentTimeMillis())
                        )
                        notebook = row.copy(title = name.trim(), updatedAt = System.currentTimeMillis())
                        LucentToast.show(context, com.lucent.app.i18n.S.notebookRenamedToast)
                    }
                }
            },
            onDismiss = { renaming = false }
        )
    }

    val query = searchText.trim()
    val rows = remember(items, notesById, tasksById, query) {
        items.mapNotNull { member ->
            when (member.itemKind) {
                NotebookItem.KIND_NOTE -> notesById[member.itemId]?.let { NotebookRowData.NoteRow(member, it) }
                NotebookItem.KIND_TASK -> tasksById[member.itemId]?.let { NotebookRowData.TaskRow(member, it) }
                else -> null
            }
        }.filter { row ->
            query.isEmpty() || row.matches(query)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        BackHeader(onBack = onBack)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            NotebookCover(
                colorKey = notebook?.color.orEmpty(),
                label = title,
                modifier = Modifier.width(30.dp).height(42.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = onGradient, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    com.lucent.app.i18n.S.notebookItemsCount(items.size),
                    color = onGradientMuted,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = { renaming = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = com.lucent.app.i18n.S.actionRename, tint = onGradientMuted)
            }
            Box {
                NewItemButton(
                    contentDescription = com.lucent.app.i18n.S.notebookAdd,
                    onClick = { addMenuOpen = true }
                )
                DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(com.lucent.app.i18n.S.newNote) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = { addMenuOpen = false; composingNote = true }
                    )
                    DropdownMenuItem(
                        text = { Text(com.lucent.app.i18n.S.newTask) },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                        onClick = { addMenuOpen = false; composingTask = true }
                    )
                    DropdownMenuItem(
                        text = { Text(com.lucent.app.i18n.S.actionDelete) },
                        onClick = { addMenuOpen = false; deleting = true }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = com.lucent.app.i18n.S.a11ySearchNotebooks) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (rows.isEmpty()) {
            EmptyState(
                isFiltered = query.isNotEmpty(),
                emptyMessage = com.lucent.app.i18n.S.notebookDetailEmpty,
                noMatchMessage = com.lucent.app.i18n.S.noNotesMatchSearch
            )
            return@Column
        }

        LazyColumn(
            state = rememberRestoredListState("NotebookDetailScreen#1"),
            modifier = Modifier.hazeSource(state = LocalHazeState.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current)
        ) {
            items(rows, key = { it.key }) { row ->
                NotebookMemberRow(
                    row = row,
                    onOpen = {
                        when (row) {
                            is NotebookRowData.NoteRow -> onOpenNote(row.note)
                            is NotebookRowData.TaskRow -> onOpenTask(row.task)
                        }
                    },
                    onEdit = {
                        when (row) {
                            is NotebookRowData.NoteRow -> AppNavigation.editNote(row.note.id)
                            is NotebookRowData.TaskRow -> AppNavigation.editTask(row.task.id)
                        }
                    },
                    onRemove = {
                        scope.launch {
                            db.notebookDao().deleteItemById(row.member.id)
                            LucentToast.show(context, com.lucent.app.i18n.S.notebookRemovedToast)
                        }
                    },
                    onDelete = {
                        when (row) {
                            is NotebookRowData.NoteRow -> noteToTrash = row.note
                            is NotebookRowData.TaskRow -> taskToTrash = row.task
                        }
                    }
                )
            }
        }
    }
}

private sealed interface NotebookRowData {
    val member: NotebookItem
    val key: String

    fun matches(query: String): Boolean

    data class NoteRow(override val member: NotebookItem, val note: Note) : NotebookRowData {
        override val key: String get() = "note_${note.id}"
        override fun matches(query: String): Boolean =
            note.title.contains(query, ignoreCase = true) || note.body.contains(query, ignoreCase = true)
    }

    data class TaskRow(override val member: NotebookItem, val task: Task) : NotebookRowData {
        override val key: String get() = "task_${task.id}"
        override fun matches(query: String): Boolean =
            task.title.contains(query, ignoreCase = true) || task.notes.contains(query, ignoreCase = true)
    }
}

@Composable
private fun NotebookMemberRow(
    row: NotebookRowData,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var menuOpen by remember { mutableStateOf(false) }

    val title: String
    val preview: String
    val kindLabel: String
    val colorKey: String
    val timestamp: Long
    when (row) {
        is NotebookRowData.NoteRow -> {
            title = row.note.title.ifBlank { com.lucent.app.i18n.S.untitledNote }
            preview = notebookPreview(row.note)
            kindLabel = com.lucent.app.i18n.S.notebookItemNote
            colorKey = row.note.color
            timestamp = row.note.updatedAt
        }
        is NotebookRowData.TaskRow -> {
            title = row.task.title.ifBlank { com.lucent.app.i18n.S.untitledTask }
            preview = taskPreview(row.task)
            kindLabel = com.lucent.app.i18n.S.notebookItemTask
            colorKey = ""
            timestamp = row.task.createdAt
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .clickable {
                Haptics.tick(context)
                onOpen()
            }
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoteColorDot(colorKey)
                if (colorKey.isNotEmpty()) Spacer(modifier = Modifier.width(6.dp))
                Text(kindLabel, color = onGradientMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    color = onGradient,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            if (preview.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    preview,
                    color = onGradientMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(formatTimestamp(timestamp), color = onGradientMuted, fontSize = 11.sp)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = com.lucent.app.i18n.S.a11yMoreOptions, tint = onGradientMuted)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            when (row) {
                                is NotebookRowData.NoteRow -> com.lucent.app.i18n.S.editNote
                                is NotebookRowData.TaskRow -> com.lucent.app.i18n.S.editTask
                            }
                        )
                    },
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(com.lucent.app.i18n.S.notebookRemoveItem) },
                    onClick = { menuOpen = false; onRemove() }
                )
                DropdownMenuItem(
                    text = { Text(com.lucent.app.i18n.S.actionDelete) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun NotebookNewNoteDialog(notebookId: Long, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(com.lucent.app.i18n.S.newNote) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(com.lucent.app.i18n.S.confirmEditTitleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(com.lucent.app.i18n.S.confirmEditBodyLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val noteTitle = title.trim()
                val noteBody = body
                onCreated()
                AppScope.io.launch {
                    val id = db.noteDao().insert(Note(title = noteTitle, body = noteBody))
                    db.notebookDao().insertItem(
                        NotebookItem(notebookId = notebookId, itemKind = NotebookItem.KIND_NOTE, itemId = id)
                    )
                    db.notebookDao().getByIdOnce(notebookId)?.let { row ->
                        db.notebookDao().update(row.copy(updatedAt = System.currentTimeMillis()))
                    }
                    LucentToast.show(context, com.lucent.app.i18n.S.notebookAddedToast)
                }
            }) { Text(com.lucent.app.i18n.S.notebookCreate) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(com.lucent.app.i18n.S.actionCancel) } }
    )
}

@Composable
private fun NotebookNewTaskDialog(notebookId: Long, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(com.lucent.app.i18n.S.newTask) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(com.lucent.app.i18n.S.confirmEditTitleLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val taskTitle = title.trim()
                onCreated()
                AppScope.io.launch {
                    val id = db.taskDao().insert(Task(title = taskTitle))
                    db.notebookDao().insertItem(
                        NotebookItem(notebookId = notebookId, itemKind = NotebookItem.KIND_TASK, itemId = id)
                    )
                    db.notebookDao().update(
                        (db.notebookDao().getByIdOnce(notebookId) ?: return@launch)
                            .copy(updatedAt = System.currentTimeMillis())
                    )
                    LucentToast.show(context, com.lucent.app.i18n.S.notebookAddedToast)
                }
            }) { Text(com.lucent.app.i18n.S.notebookCreate) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(com.lucent.app.i18n.S.actionCancel) } }
    )
}

private fun notebookPreview(note: Note): String =
    if (note.isChecklist) {
        val items = Checklist.parse(note.checklist)
        if (items.isEmpty()) "" else com.lucent.app.i18n.S.checklistDoneCount(items.count { it.done }, items.size)
    } else note.body

private fun taskPreview(task: Task): String {
    val notes = task.notes.trim()
    if (notes.isNotEmpty()) return notes
    val due = task.dueAt
    return if (due != null) com.lucent.app.i18n.S.exportDocDue(formatTimestamp(due)) else ""
}
