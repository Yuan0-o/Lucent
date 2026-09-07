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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
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
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Checklist
import com.lucent.app.data.Note
import com.lucent.app.data.Notebook
import com.lucent.app.data.NotebookItem
import com.lucent.app.data.Task
import com.lucent.app.data.pruneOrphans
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/**
 * The Notebooks area: a list of notebooks, each a user-named collection of notes and tasks.
 *
 * This is a shared sub-screen, hosted by both the Notes and Tasks pages (each offers "Notebooks"
 * from its overflow menu). It owns its own state: the list, the create/rename/delete dialogs, and —
 * when a notebook is opened — the detail page, which replaces the list in place so back returns to
 * the list rather than leaving the area.
 *
 * Membership is added from the Notes/Tasks selection mode (see [AddToNotebookDialog]); this screen
 * manages the notebooks themselves: create, rename, delete, and remove individual items.
 */
@Composable
fun NotebooksScreen(
    onBack: () -> Unit,
    onOpenNote: (Note) -> Unit,
    onOpenTask: (Task) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val notebooks by db.notebookDao().getAll().collectAsState(initial = emptyList())
    val counts by db.notebookDao().itemCounts().collectAsState(initial = emptyList())
    val countById = remember(counts) { counts.associate { it.notebookId to it.count } }
    val onGradient = LocalOnGradient.current

    var openNotebookId by remember { mutableStateOf<Long?>(null) }

    // Entering the area is a good moment to sweep membership rows whose note/task no longer exists
    // (a trash purge or a permanent delete can't reach into every open screen).
    LaunchedEffect(Unit) {
        runCatching { db.notebookDao().pruneOrphans(db.noteDao(), db.taskDao()) }
    }

    val open = openNotebookId
    if (open != null) {
        NotebookDetailScreen(
            notebookId = open,
            onBack = { openNotebookId = null },
            onOpenNote = onOpenNote,
            onOpenTask = onOpenTask
        )
        return
    }

    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Notebook?>(null) }
    var deleting by remember { mutableStateOf<Notebook?>(null) }

    deleting?.let { notebook ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(com.lucent.app.i18n.S.notebookDeleteTitle) },
            text = { Text(com.lucent.app.i18n.S.notebookDeleteBody(notebook.title.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle })) },
            confirmButton = {
                TextButton(onClick = {
                    val target = notebook
                    deleting = null
                    scope.launch {
                        db.notebookDao().deleteItemsForNotebook(target.id)
                        db.notebookDao().deleteById(target.id)
                        LucentToast.show(context, com.lucent.app.i18n.S.notebookDeletedToast)
                    }
                }) { Text(com.lucent.app.i18n.S.actionDelete) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(com.lucent.app.i18n.S.actionCancel) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.lucent.app.i18n.S.actionBack, tint = onGradient)
            }
            Text(com.lucent.app.i18n.S.screenNotebooks, color = onGradient, fontSize = 20.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = com.lucent.app.i18n.S.notebookNew, tint = onGradient)
            }
        }

        if (notebooks.isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(24.dp)) {
                Text(com.lucent.app.i18n.S.notebookEmpty, color = onGradient.copy(alpha = 0.65f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.hazeSource(state = LocalHazeState.current),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = LocalBottomBarInset.current)
            ) {
                items(notebooks, key = { it.id }) { notebook ->
                    NotebookRow(
                        notebook = notebook,
                        count = countById[notebook.id] ?: 0,
                        onOpen = { openNotebookId = notebook.id },
                        onRename = { renaming = notebook },
                        onDelete = { deleting = notebook }
                    )
                }
            }
        }
    }

    if (creating) {
        NotebookNameDialog(
            title = com.lucent.app.i18n.S.notebookNew,
            confirmLabel = com.lucent.app.i18n.S.notebookCreate,
            initial = "",
            onConfirm = { name ->
                scope.launch {
                    db.notebookDao().insert(Notebook(title = name.trim()))
                }
            },
            onDismiss = { creating = false }
        )
    }

    renaming?.let { notebook ->
        NotebookNameDialog(
            title = com.lucent.app.i18n.S.notebookRename,
            confirmLabel = com.lucent.app.i18n.S.notebookRenameAction,
            initial = notebook.title,
            onConfirm = { name ->
                scope.launch {
                    db.notebookDao().update(notebook.copy(title = name.trim(), updatedAt = System.currentTimeMillis()))
                    LucentToast.show(context, com.lucent.app.i18n.S.notebookRenamedToast)
                }
            },
            onDismiss = { renaming = null }
        )
    }
}

/** One notebook in the list: icon, name, item count, and a per-row rename/delete menu. */
@Composable
private fun NotebookRow(
    notebook: Notebook,
    count: Int,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var menuOpen by remember { mutableStateOf(false) }

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
        Icon(Icons.Default.Book, contentDescription = null, tint = onGradient, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                notebook.title.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle },
                color = onGradient,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                com.lucent.app.i18n.S.notebookItemsCount(count),
                color = onGradientMuted,
                fontSize = 12.sp
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = com.lucent.app.i18n.S.a11yMoreOptions, tint = onGradientMuted)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(com.lucent.app.i18n.S.notebookRename) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text(com.lucent.app.i18n.S.actionDelete) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

/** A shared title-input dialog, used for both creating and renaming a notebook. */
@Composable
private fun NotebookNameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(com.lucent.app.i18n.S.notebookName) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    LucentToast.show(context, com.lucent.app.i18n.S.notebookNameRequired)
                } else {
                    onConfirm(name)
                    onDismiss()
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(com.lucent.app.i18n.S.actionCancel) }
        }
    )
}

/**
 * A single notebook's contents: every note and task filed in it, most recently added first.
 *
 * Tapping an item opens it through the host screen's callbacks ([onOpenNote]/[onOpenTask]) — the
 * host decides whether that means an in-tab detail page or a cross-tab jump. The trailing button
 * removes the item from this notebook (the note/task itself is untouched).
 */
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

    // Resolve membership rows into the notes/tasks they name, preserving the membership order
    // (newest added first). Rows whose target no longer exists are filtered out and pruned below.
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
        // Permanently-deleted targets (trash purge while this screen was closed) leave ghost rows
        // behind; sweep them now so the next open doesn't re-resolve and re-hide the same ghosts.
        val ghosts = noteMembers.filter { it.itemId !in notes } + taskMembers.filter { it.itemId !in tasks }
        if (ghosts.isNotEmpty()) ghosts.forEach { db.notebookDao().deleteItemById(it.id) }
    }

    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(com.lucent.app.i18n.S.notebookDeleteTitle) },
            text = { Text(com.lucent.app.i18n.S.notebookDeleteBody(notebook?.title?.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle } ?: com.lucent.app.i18n.S.notebookEmptyTitle)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        db.notebookDao().deleteItemsForNotebook(notebookId)
                        db.notebookDao().deleteById(notebookId)
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.lucent.app.i18n.S.actionBack, tint = onGradient)
            }
            Text(
                notebook?.title?.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle } ?: "",
                color = onGradient,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { renaming = true }) {
                    Icon(Icons.Default.Edit, contentDescription = com.lucent.app.i18n.S.notebookRename, tint = onGradient)
                }
            }
            IconButton(onClick = { deleting = true }) {
                Icon(Icons.Default.Delete, contentDescription = com.lucent.app.i18n.S.actionDelete, tint = onGradient)
            }
        }

        if (items.isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(24.dp)) {
                Text(com.lucent.app.i18n.S.notebookDetailEmpty, color = onGradientMuted)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.hazeSource(state = LocalHazeState.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current)
        ) {
            items(items, key = { it.id }) { member ->
                when (member.itemKind) {
                    NotebookItem.KIND_NOTE -> notesById[member.itemId]?.let { note ->
                        NotebookItemRow(
                            title = note.title.ifBlank { com.lucent.app.i18n.S.untitledNote },
                            preview = notebookPreview(note),
                            kindLabel = com.lucent.app.i18n.S.notebookItemNote,
                            colorKey = note.color,
                            timestamp = note.updatedAt,
                            onOpen = { onOpenNote(note) },
                            onRemove = {
                                scope.launch {
                                    db.notebookDao().deleteItemById(member.id)
                                    LucentToast.show(context, com.lucent.app.i18n.S.notebookRemovedToast)
                                }
                            }
                        )
                    }
                    NotebookItem.KIND_TASK -> tasksById[member.itemId]?.let { task ->
                        NotebookItemRow(
                            title = task.title.ifBlank { com.lucent.app.i18n.S.untitledTask },
                            preview = taskPreview(task),
                            kindLabel = com.lucent.app.i18n.S.notebookItemTask,
                            colorKey = "",
                            timestamp = task.createdAt,
                            onOpen = { onOpenTask(task) },
                            onRemove = {
                                scope.launch {
                                    db.notebookDao().deleteItemById(member.id)
                                    LucentToast.show(context, com.lucent.app.i18n.S.notebookRemovedToast)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        NotebookNameDialog(
            title = com.lucent.app.i18n.S.notebookRename,
            confirmLabel = com.lucent.app.i18n.S.notebookRenameAction,
            initial = notebook?.title ?: "",
            onConfirm = { name ->
                val row = notebook
                if (row != null) {
                    scope.launch {
                        db.notebookDao().update(row.copy(title = name.trim(), updatedAt = System.currentTimeMillis()))
                        notebook = row.copy(title = name.trim(), updatedAt = System.currentTimeMillis())
                        LucentToast.show(context, com.lucent.app.i18n.S.notebookRenamedToast)
                    }
                }
            },
            onDismiss = { renaming = false }
        )
    }
}

/** The one-line preview a notebook item shows: checklist progress for checklist notes, else body. */
private fun notebookPreview(note: Note): String =
    if (note.isChecklist) {
        val items = Checklist.parse(note.checklist)
        if (items.isEmpty()) "" else com.lucent.app.i18n.S.checklistDoneCount(items.count { it.done }, items.size)
    } else note.body

/** The one-line preview for a task: its notes, or the due date when there are no notes. */
private fun taskPreview(task: Task): String {
    val notes = task.notes.trim()
    if (notes.isNotEmpty()) return notes
    val due = task.dueAt
    return if (due != null) com.lucent.app.i18n.S.exportDocDue(formatTimestamp(due)) else ""
}

/** A single row in the notebook detail: kind badge, title, preview, time, and remove. */
@Composable
private fun NotebookItemRow(
    title: String,
    preview: String,
    kindLabel: String,
    colorKey: String,
    timestamp: Long,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

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
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = com.lucent.app.i18n.S.notebookA11yRemove, tint = onGradientMuted)
        }
    }
}

/**
 * The "add selected items to a notebook" picker, opened from the selection-mode action bar on the
 * Notes and Tasks pages.
 *
 * Lists every notebook; tapping one files the selected notes/tasks into it and dismisses. The last
 * row offers "New notebook…", which reveals a name field and creates the notebook in the same
 * action. Items already in the target notebook are skipped rather than duplicated — a notebook is a
 * set, and adding the same note twice is almost always a mis-tap. [onAdded] is called after the
 * write completes so the host can leave selection mode.
 */
@Composable
fun AddToNotebookDialog(
    noteIds: Set<Long>,
    taskIds: Set<Long>,
    onDismiss: () -> Unit,
    onAdded: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val notebooks by db.notebookDao().getAll().collectAsState(initial = emptyList())
    val onGradient = LocalOnGradient.current

    var creatingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val total = noteIds.size + taskIds.size

    fun fileInto(notebookId: Long) {
        scope.launch {
            noteIds.forEach { id ->
                if (db.notebookDao().membershipExistsOnce(notebookId, NotebookItem.KIND_NOTE, id) == 0) {
                    db.notebookDao().insertItem(
                        NotebookItem(notebookId = notebookId, itemKind = NotebookItem.KIND_NOTE, itemId = id)
                    )
                }
            }
            taskIds.forEach { id ->
                if (db.notebookDao().membershipExistsOnce(notebookId, NotebookItem.KIND_TASK, id) == 0) {
                    db.notebookDao().insertItem(
                        NotebookItem(notebookId = notebookId, itemKind = NotebookItem.KIND_TASK, itemId = id)
                    )
                }
            }
            db.notebookDao().getByIdOnce(notebookId)?.let { row ->
                db.notebookDao().update(row.copy(updatedAt = System.currentTimeMillis()))
            }
            LucentToast.show(context, com.lucent.app.i18n.S.notebookAddedToast)
            onAdded()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(com.lucent.app.i18n.S.notebookPickTitle) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (notebooks.isEmpty() && !creatingNew) {
                    Text(com.lucent.app.i18n.S.notebookPickEmpty, color = onGradient.copy(alpha = 0.65f))
                }
                notebooks.forEach { notebook ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fileInto(notebook.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Book, contentDescription = null, tint = onGradient, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            notebook.title.ifBlank { com.lucent.app.i18n.S.notebookEmptyTitle },
                            color = onGradient,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (creatingNew) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(com.lucent.app.i18n.S.notebookName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { creatingNew = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = onGradient, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(com.lucent.app.i18n.S.notebookNewPrompt, color = onGradient, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (creatingNew) {
                TextButton(onClick = {
                    if (newName.isBlank()) {
                        LucentToast.show(context, com.lucent.app.i18n.S.notebookNameRequired)
                    } else {
                        val name = newName.trim()
                        scope.launch {
                            val id = db.notebookDao().insert(Notebook(title = name))
                            noteIds.forEach { nid ->
                                db.notebookDao().insertItem(
                                    NotebookItem(notebookId = id, itemKind = NotebookItem.KIND_NOTE, itemId = nid)
                                )
                            }
                            taskIds.forEach { tid ->
                                db.notebookDao().insertItem(
                                    NotebookItem(notebookId = id, itemKind = NotebookItem.KIND_TASK, itemId = tid)
                                )
                            }
                            LucentToast.show(context, com.lucent.app.i18n.S.notebookAddedToast)
                            onAdded()
                        }
                    }
                }) { Text(com.lucent.app.i18n.S.notebookCreateAndAdd) }
            } else {
                TextButton(onClick = onDismiss) { Text(com.lucent.app.i18n.S.actionCancel) }
            }
        },
        dismissButton = {
            if (creatingNew) {
                TextButton(onClick = { creatingNew = false; newName = "" }) { Text(com.lucent.app.i18n.S.actionCancel) }
            }
        }
    )
}
