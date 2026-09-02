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
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Checklist
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/**
 * The hidden area (task A21) — a third holding place beside the trash and the drafts, and the only
 * one that is invisible until asked for.
 *
 * ### Why the switch lives in memory and not in settings storage
 *
 * The requirement is that the area turns itself back off "the next time the software starts". A
 * persisted preference cannot express that: it would survive the restart and have to be actively
 * reset by something, and anything that can reset it can fail to. A plain process-scoped flag gets
 * it right by construction — the flag dies with the process, so a fresh launch is *always* a
 * launch with the hidden area closed. There is no state to migrate, no preference to forget to
 * clear, and no way for a crash to leave it stuck open.
 *
 * The one exception the task calls for — staying open if the user was still editing something
 * hidden — is [stickyForSession]: set while a hidden item is open in an editor, so a trip to
 * another app and back doesn't slam the door on work in progress. It, too, dies with the process.
 */
object HiddenArea {

    /** True while the user has unlocked the hidden area during *this* run of the app. */
    var visible: Boolean by mutableStateOf(false)
        private set

    /**
     * Set while a hidden item is open in an editor. The startup close is unconditional either way —
     * this only prevents the area collapsing underneath an edit that is still in progress within
     * the same run.
     */
    var stickyForSession: Boolean = false

    fun open() { visible = true }

    fun close() {
        if (stickyForSession) return
        visible = false
    }
}

/**
 * The hidden notes list. Deliberately plain: no search, no sort, no bulk actions. This is a place
 * you visit to take something out of, and every control that isn't "show me" or "put it back"
 * would be another thing on screen while the screen is open.
 */
@Composable
fun HiddenNotesScreen(onBack: () -> Unit, onOpen: (com.lucent.app.data.Note) -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val hidden by db.noteDao().getHidden().collectAsState(initial = emptyList())
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val hazeState = LocalHazeState.current
    // Task 7 — see [RevealConfirmDialog]. What is held is the *action*, not the item, so one dialog
    // serves the whole list instead of every row carrying its own copy of the state.
    var pendingReveal by remember { mutableStateOf<(() -> Unit)?>(null) }

    HiddenScaffold(onBack = onBack, isEmpty = hidden.isEmpty(), hazeState = hazeState) {
        items(hidden, key = { it.id }) { note ->
            HiddenRow(
                title = note.title,
                subtitle = if (note.isChecklist) {
                    val items = Checklist.parse(note.checklist)
                    com.lucent.app.i18n.S.checklistDoneCount(items.count { it.done }, items.size)
                } else note.body,
                savedAt = note.updatedAt,
                onOpen = { onOpen(note) },
                onReveal = {
                    pendingReveal = {
                        AppScope.io.launch { db.noteDao().update(note.copy(hidden = false)) }
                    }
                },
                onGradient = onGradient,
                onGradientMuted = onGradientMuted
            )
        }
    }

    RevealConfirmDialog(pending = pendingReveal, onDismiss = { pendingReveal = null })
}

/** The task side of the hidden area; identical but for the row's own fields. */
@Composable
fun HiddenTasksScreen(onBack: () -> Unit, onOpen: (com.lucent.app.data.Task) -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val hidden by db.taskDao().getHidden().collectAsState(initial = emptyList())
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val hazeState = LocalHazeState.current
    var pendingReveal by remember { mutableStateOf<(() -> Unit)?>(null) }

    HiddenScaffold(onBack = onBack, isEmpty = hidden.isEmpty(), hazeState = hazeState) {
        items(hidden, key = { it.id }) { task ->
            val items = remember(task.subtasks) { Checklist.parse(task.subtasks) }
            HiddenRow(
                title = task.title,
                subtitle = task.notes.ifBlank {
                    if (items.isEmpty()) "" else com.lucent.app.i18n.S.checklistDoneCount(items.count { it.done }, items.size)
                },
                savedAt = task.createdAt,
                onOpen = { onOpen(task) },
                onReveal = {
                    pendingReveal = {
                        AppScope.io.launch { db.taskDao().update(task.copy(hidden = false)) }
                    }
                },
                onGradient = onGradient,
                onGradientMuted = onGradientMuted
            )
        }
    }

    RevealConfirmDialog(pending = pendingReveal, onDismiss = { pendingReveal = null })
}

/**
 * Task 7 — confirmation before an item leaves the hidden area.
 *
 * The reveal icon sits on every row, a single tap from the same finger that is scrolling past it,
 * and the action it performs is invisible from here: the item vanishes from this list and reappears
 * in a list this screen is not showing. There is no undo and no toast to catch it. Every other
 * one-tap change of this kind in the app already asks (delete, empty trash, batch delete), and this
 * one is the only one where the cost of a mistake is that something the user deliberately hid is now
 * on display — which is precisely the thing the hidden area exists to prevent.
 *
 * Nothing guards the opposite direction: moving an item *into* the hidden area is still immediate.
 * Hiding something by accident is recoverable in one tap from this very screen, so a prompt there
 * would be ceremony rather than protection.
 */
@Composable
private fun RevealConfirmDialog(pending: (() -> Unit)?, onDismiss: () -> Unit) {
    if (pending == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(com.lucent.app.i18n.S.hiddenRemoveConfirmTitle) },
        text = { Text(com.lucent.app.i18n.S.hiddenRemoveConfirmBody) },
        confirmButton = {
            TextButton(onClick = { pending(); onDismiss() }) {
                Text(com.lucent.app.i18n.S.hiddenRemoveConfirmAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(com.lucent.app.i18n.S.actionCancel) }
        }
    )
}

@Composable
private fun HiddenScaffold(
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
            Text(com.lucent.app.i18n.S.screenHidden, color = onGradient, fontSize = 20.sp, modifier = Modifier.weight(1f))
        }
        if (isEmpty) {
            EmptyState(isFiltered = false, emptyMessage = com.lucent.app.i18n.S.hiddenEmpty, noMatchMessage = "")
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

@Composable
private fun HiddenRow(
    title: String,
    subtitle: String,
    savedAt: Long,
    onOpen: () -> Unit,
    onReveal: () -> Unit,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier.fillMaxWidth().frostedGlass().clickable { onOpen() }.padding(12.dp)
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
            IconButton(onClick = onReveal) {
                Icon(Icons.Default.VisibilityOff, contentDescription = com.lucent.app.i18n.S.hiddenRemove, tint = onGradient)
            }
        }
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(subtitle, color = onGradientMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
