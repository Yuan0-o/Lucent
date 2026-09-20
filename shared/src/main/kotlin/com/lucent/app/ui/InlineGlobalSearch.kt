package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Note
import com.lucent.app.data.SearchQuery
import com.lucent.app.data.Task
import kotlinx.coroutines.delay

internal const val INLINE_CANDIDATE_LIMIT = 300

internal const val INLINE_RESULT_LIMIT = 100

internal data class InlineGlobalResults(
    val notes: List<Note> = emptyList(),
    val tasks: List<Task> = emptyList()
) {
    val hasResults: Boolean get() = notes.isNotEmpty() || tasks.isNotEmpty()
}

@Composable
internal fun rememberInlineGlobalResults(raw: String): InlineGlobalResults {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val query = remember(raw) { SearchQuery.parse(raw) }
    var results by remember { mutableStateOf(InlineGlobalResults()) }

    LaunchedEffect(query) {
        if (query.isEmpty) {
            results = InlineGlobalResults()
            return@LaunchedEffect
        }
        delay(180)
        val now = System.currentTimeMillis()

        val notes = if (query.isTaskOnly) {
            emptyList()
        } else {
            db.noteDao()
                .searchNotes(
                    text = query.sqlText,
                    tag = query.sqlTag,
                    archived = query.sqlArchived,
                    trashed = query.sqlTrashed,
                    limit = INLINE_CANDIDATE_LIMIT
                )
                .filter { query.matches(it) }
                .map { it to query.rank(it) }
                .sortedWith(compareByDescending<Pair<Note, Int>> { it.second }.thenByDescending { it.first.updatedAt })
                .take(INLINE_RESULT_LIMIT)
                .map { it.first }
        }

        val tasks = if (query.isNoteOnly) {
            emptyList()
        } else {
            db.taskDao()
                .searchTasks(
                    text = query.sqlText,
                    done = query.sqlDone,
                    trashed = query.sqlTrashed,
                    minPriority = query.sqlMinPriority,
                    dueBefore = query.sqlDueBefore(now),
                    dueAfter = query.sqlDueAfter(now),
                    limit = INLINE_CANDIDATE_LIMIT
                )
                .filter { query.matches(it, now) }
                .map { it to query.rank(it) }
                .sortedWith(compareByDescending<Pair<Task, Int>> { it.second }.thenByDescending { it.first.createdAt })
                .take(INLINE_RESULT_LIMIT)
                .map { it.first }
        }

        results = InlineGlobalResults(notes = notes, tasks = tasks)
    }

    return results
}

@Composable
internal fun GlobalNotesHeader(count: Int) {
    SectionHeader(com.lucent.app.i18n.S.tabNotes, count, Icons.AutoMirrored.Filled.Notes)
}

@Composable
internal fun GlobalTasksHeader(count: Int) {
    SectionHeader(com.lucent.app.i18n.S.tabTasks, count, Icons.Default.CheckCircle)
}

@Composable
internal fun GlobalSearchDivider() {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(onGradient.copy(alpha = 0.6f)))
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.TravelExplore,
                contentDescription = null,
                tint = onGradient,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                com.lucent.app.i18n.S.searchEverything.uppercase(),
                color = onGradient,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(onGradientMuted.copy(alpha = 0.55f)))
    }
}
