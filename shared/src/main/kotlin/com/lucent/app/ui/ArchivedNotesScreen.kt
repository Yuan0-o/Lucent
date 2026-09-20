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
import androidx.compose.material.icons.filled.Unarchive
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
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

private enum class ArchiveGrouping { TIME, TAG }

private const val UNTAGGED_KEY = "\u0000untagged"

@Composable
fun ArchivedNotesScreen(
    onBack: () -> Unit,
    onOpen: (Note) -> Unit,
    onDeleteRequest: (Note) -> Unit,
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val archived by db.noteDao().getArchived().collectAsState(initial = emptyList())
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val hazeState = LocalHazeState.current

    var searchQuery by remember { mutableStateOf("") }
    var grouping by remember { mutableStateOf(ArchiveGrouping.TIME) }
    var noteToRestore by remember { mutableStateOf<Note?>(null) }

    noteToRestore?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToRestore = null },
            title = { Text(com.lucent.app.i18n.S.restoreNoteTitle) },
            text = {
                Text(com.lucent.app.i18n.S.restoreNoteArchiveBody(note.title.ifBlank { com.lucent.app.i18n.S.untitledNote }))
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = note
                    noteToRestore = null
                    scope.launch { db.noteDao().update(target.copy(archived = false, archivedAt = null)) }
                }) { Text(com.lucent.app.i18n.S.actionRestore) }
            },
            dismissButton = { TextButton(onClick = { noteToRestore = null }) { Text(com.lucent.app.i18n.S.actionCancel) } }
        )
    }

    val filtered = remember(archived, searchQuery) {
        archived.filter { note ->
            searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.body.contains(searchQuery, ignoreCase = true) ||
                note.tags.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.lucent.app.i18n.S.actionBack, tint = onGradient)
            }
            Text(com.lucent.app.i18n.S.screenArchivedNotes, color = onGradient, fontSize = 20.sp, modifier = Modifier.weight(1f))
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(com.lucent.app.i18n.S.searchArchive) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(com.lucent.app.i18n.S.groupBy, color = onGradientMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = grouping == ArchiveGrouping.TIME,
                onClick = { grouping = ArchiveGrouping.TIME },
                label = { Text(com.lucent.app.i18n.S.filterTime) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = grouping == ArchiveGrouping.TAG,
                onClick = { grouping = ArchiveGrouping.TAG },
                label = { Text(com.lucent.app.i18n.S.filterTag) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filtered.isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(24.dp)) {
                Text(
                    if (archived.isEmpty()) com.lucent.app.i18n.S.archivedEmpty
                    else com.lucent.app.i18n.S.archivedNoMatch,
                    color = onGradientMuted
                )
            }
            return
        }

        val restore: (Note) -> Unit = { note -> noteToRestore = note }

        when (grouping) {
            ArchiveGrouping.TIME -> {
                LazyColumn(
                    state = rememberRestoredListState("ArchivedNotesScreen#1"),
                    modifier = Modifier.hazeSource(state = hazeState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = LocalBottomBarInset.current)
                ) {
                    items(filtered, key = { it.id }) { note ->
                        ArchivedNoteCard(
                            note = note,
                            onOpen = { onOpen(note) },
                            onRestore = { restore(note) },
                            onDelete = { onDeleteRequest(note) },
                            onGradient = onGradient,
                            onGradientMuted = onGradientMuted
                        )
                    }
                }
            }
            ArchiveGrouping.TAG -> {
                val groups = remember(filtered) { buildTagGroups(filtered) }
                LazyColumn(
                    state = rememberRestoredListState("ArchivedNotesScreen#2"),
                    modifier = Modifier.hazeSource(state = hazeState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = LocalBottomBarInset.current)
                ) {
                    groups.forEach { (tag, notesForTag) ->
                        item(key = "header_$tag") {
                            Text(
                                if (tag == UNTAGGED_KEY) com.lucent.app.i18n.S.untaggedLabel else tag,
                                color = onGradient,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(notesForTag, key = { "${tag}_${it.id}" }) { note ->
                            ArchivedNoteCard(
                                note = note,
                                onOpen = { onOpen(note) },
                                onRestore = { restore(note) },
                                onDelete = { onDeleteRequest(note) },
                                onGradient = onGradient,
                                onGradientMuted = onGradientMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildTagGroups(notes: List<Note>): List<Pair<String, List<Note>>> {
    val tagged = linkedMapOf<String, MutableList<Note>>()
    val untagged = mutableListOf<Note>()
    for (note in notes) {
        val tags = note.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tags.isEmpty()) {
            untagged += note
        } else {
            for (tag in tags) {
                tagged.getOrPut(tag) { mutableListOf() }.add(note)
            }
        }
    }
    val result = tagged.entries
        .sortedBy { it.key.lowercase() }
        .map { it.key to it.value.toList() }
        .toMutableList()
    if (untagged.isNotEmpty()) {
        result += UNTAGGED_KEY to untagged.toList()
    }
    return result
}

@Composable
private fun ArchivedNoteCard(
    note: Note,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .clickable { onOpen() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NoteColorDot(note.color)
                    if (NoteColor.fromKey(note.color) != NoteColor.DEFAULT) {
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (note.pinned) {
                        PinnedMarker(modifier = Modifier.padding(end = 4.dp))
                    }
                    Text(
                        note.title.ifBlank { com.lucent.app.i18n.S.untitledNote },
                        color = onGradient,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val stamp = note.archivedAt ?: note.updatedAt
                Text(com.lucent.app.i18n.S.archivedOn(formatTimestamp(stamp)), color = onGradientMuted, fontSize = 12.sp)
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Filled.Unarchive, contentDescription = com.lucent.app.i18n.S.a11yRestoreToNotes, tint = onGradient)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = com.lucent.app.i18n.S.actionDelete, tint = onGradient)
            }
        }
        val preview = if (note.isChecklist) {
            val items = Checklist.parse(note.checklist)
            if (items.isEmpty()) "" else com.lucent.app.i18n.S.checklistDoneCount(items.count { it.done }, items.size)
        } else {
            note.body
        }
        if (preview.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                preview,
                color = onGradientMuted,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        val tags = note.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                tags.joinToString("  ") { "#$it" },
                color = onGradientMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
