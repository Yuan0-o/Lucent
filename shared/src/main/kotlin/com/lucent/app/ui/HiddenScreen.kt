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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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

object HiddenArea {

    var visible: Boolean by mutableStateOf(false)
        private set

    var stickyForSession: Boolean = false

    fun open() { visible = true }

    fun close() {
        if (stickyForSession) return
        visible = false
    }
}

@Composable
fun HiddenNotesScreen(onBack: () -> Unit, onOpen: (com.lucent.app.data.Note) -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val hidden by db.noteDao().getHidden().collectAsState(initial = emptyList())
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val hazeState = LocalHazeState.current
    var pendingReveal by remember { mutableStateOf<(() -> Unit)?>(null) }

    HiddenGridScaffold(onBack = onBack, isEmpty = hidden.isEmpty(), hazeState = hazeState) {
        gridItems(hidden, key = { it.id }) { note ->
            HiddenNoteCard(
                title = note.title,
                subtitle = if (note.isChecklist) {
                    val items = Checklist.parse(note.checklist)
                    com.lucent.app.i18n.S.checklistDoneCount(items.count { it.done }, items.size)
                } else note.body,
                colorKey = note.color,
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
            state = rememberRestoredListState("HiddenScreen#1"),
            modifier = Modifier.hazeSource(state = hazeState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current),
            content = content
        )
    }
}

@Composable
private fun HiddenGridScaffold(
    onBack: () -> Unit,
    isEmpty: Boolean,
    hazeState: dev.chrisbanes.haze.HazeState,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.hazeSource(state = hazeState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current),
            content = content
        )
    }
}

@Composable
private fun HiddenNoteCard(
    title: String,
    subtitle: String,
    colorKey: String,
    savedAt: Long,
    onOpen: () -> Unit,
    onReveal: () -> Unit,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .frostedGlass(tint = NoteColor.fromKey(colorKey).swatch)
            .clickable { onOpen() }
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.ifBlank { com.lucent.app.i18n.S.untitled },
                color = onGradient,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onReveal, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = com.lucent.app.i18n.S.hiddenRemove,
                    tint = onGradient,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(subtitle, color = onGradientMuted, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(formatTimestamp(savedAt), color = onGradientMuted, fontSize = 11.sp)
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
