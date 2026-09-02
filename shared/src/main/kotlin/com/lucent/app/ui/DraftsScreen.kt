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

/**
 * The draft area (task A10) — a sibling of the trash, and shaped like it on purpose.
 *
 * ### What a draft is, and why it is a flag rather than a place
 *
 * A draft is the note or task itself, at an earlier moment, held back from the main list until the
 * user says it is ready. That is the same relationship the trash has to a deleted item, so it is
 * stored the same way: a column on the row (`isDraft`), not a separate table. One schema, one write
 * path, and nothing extra to keep in step the next time either entity gains a field.
 *
 * ### Two ways in
 *
 *  - **Deliberately**, with the "Save to drafts" button beside Save in either composer.
 *  - **Automatically**, when Lucent leaves the foreground with an editor still open and dirty (see
 *    `UnsavedChangesGuard.autoDraft` and `MainActivity.onStop`). This is the case the task list
 *    describes as "closes abnormally": a stopped Android process can be killed without another
 *    line of our code running, and the edit used to be gone. Now a copy is already here.
 *
 * Restoring is the inverse of exactly one thing — the flag — so a restored draft returns with its
 * attachments, pin, colour and everything else intact.
 */
@Composable
fun DraftNotesScreen(onBack: () -> Unit, onOpen: (Note) -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val drafts by db.noteDao().getDrafts().collectAsState(initial = emptyList())
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val hazeState = LocalHazeState.current

    var toPurge by remember { mutableStateOf<Note?>(null) }

    // Deleting a draft is deleting content, so it asks — and it purges rather than trashing,
    // because a draft that "went to the trash" would be in two holding areas at once, which is one
    // more than anybody can keep track of.
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
                    // Out of drafts and into the ordinary list — the single flag flipped back.
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

/** The task side of the draft area; identical in every respect that isn't the row's own fields. */
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

/** Header, empty state and list container — shared so the two draft screens cannot drift apart. */
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
            modifier = Modifier.hazeSource(state = hazeState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current),
            content = content
        )
    }
}

/**
 * One draft: what it says, when it was parked, and the two things you can do with it.
 *
 * Tapping the row opens it back in the editor, because that is what a draft is *for* — the list is
 * a way back to unfinished work, not an archive to admire. "Move out of drafts" is the second-best
 * action (the content is already fine, it just shouldn't be hidden any more), and delete is last
 * and muted.
 */
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

/**
 * The "you had unfinished edits last time" prompt (task A10).
 *
 * ### Why it asks once per launch, from whichever tab you land on
 *
 * The obvious home for this is a global dialog in MainActivity, but the useful *action* — open the
 * draft area — lives inside a tab, so a global dialog would have to reach across into one and drive
 * its navigation. Instead each home screen offers its own drafts, and [asked] makes sure only the
 * first one to compose actually speaks up. You get one question per launch, and it takes you
 * straight to the thing it is asking about.
 *
 * The flag is process-scoped on purpose. It resets when the process does, which is exactly when the
 * question becomes worth asking again.
 */
object DraftRestorePrompt {
    /** Set once the question has been put to the user this launch, answered or dismissed. */
    var asked: Boolean = false
}

/**
 * Renders the prompt if this screen has drafts and nobody has asked yet. Dismissing it counts as an
 * answer: the drafts are not going anywhere, they are one menu entry away, and re-asking on every
 * tab switch would turn a safety net into nagging.
 */
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
