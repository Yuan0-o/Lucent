package com.lucent.app.data

import android.content.Context
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * P2-1 follow-up: regression coverage for [NoteDao.searchNotes] / [TaskDao.searchTasks] at the
 * actual SQL layer — the level [SearchQueryTest] never reaches, because [SearchQuery.matches] is
 * pure Kotlin over rows the database already returned. No test exercised that boundary before this
 * file, which is exactly how the FTS5-MATCH-first attempt shipped, ran in CI green, and still
 * silently dropped results for the two cases below.
 *
 * Both cases mirror a real failure, not a hypothetical one: a MATCH-based query never throws for
 * either, so a fallback that only triggers `catch (_: Exception)` never fires.
 */
class SearchDaoTest {

    private class TestContext(private val dir: File) : Context() {
        override val filesDir: File get() = dir
    }

    private fun freshDir(): File =
        File(System.getProperty("java.io.tmpdir"), "lucent-search-dao-test-${System.nanoTime()}")
            .apply { mkdirs() }

    private suspend fun use(dir: File, block: suspend () -> Unit) {
        LocalSecrets.filesDirOverride = dir
        LocalSecrets.resetForTesting()
        DataKeys.resetCacheForTesting()
        try {
            block()
        } finally {
            LocalSecrets.filesDirOverride = null
            LocalSecrets.resetForTesting()
            DataKeys.resetCacheForTesting()
        }
    }

    // -----------------------------------------------------------------------------------------
    // Case 1: a CJK substring buried inside a longer, unspaced run of Han characters.
    //
    // unicode61/simple (SQLite's built-in FTS5 tokenisers) have no CJK word boundaries, so a whole
    // run of Han characters becomes ONE token. `notes_fts MATCH '"两个字"'` against a row whose only
    // token is the full 13-character sentence below does not error — it just matches nothing, which
    // is why the old code's `catch (_: Exception)` never saw it.
    // -----------------------------------------------------------------------------------------

    @Test
    fun searchNotesFindsCjkSubstringBuriedInLongerRun() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = AppDatabase.createForTesting(TestContext(dir))
            db.noteDao().insert(Note(title = "礼物清单", body = "今天买了两个字的礼物送给朋友"))

            val results = db.noteDao().searchNotes(
                text = "两个字", tag = "", archived = -1, trashed = -1, limit = 50
            )

            assertTrue(
                results.any { it.body.contains("两个字") },
                "expected the CJK substring match to be found; got ${results.map { it.body }}"
            )
        }
    }

    @Test
    fun searchTasksFindsCjkSubstringBuriedInLongerRun() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = AppDatabase.createForTesting(TestContext(dir))
            db.taskDao().insert(Task(title = "旅行安排", notes = "记得提前两天预订新干线车票"))

            val results = db.taskDao().searchTasks(
                text = "两天预订", done = -1, trashed = -1, minPriority = -1,
                dueBefore = -1, dueAfter = -1, limit = 50
            )

            assertTrue(
                results.any { it.notes.contains("两天预订") },
                "expected the CJK substring match to be found; got ${results.map { it.notes }}"
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // Case 2: a match that lives only in a column notes_fts/tasks_fts never indexed (tags and
    // checklist for notes; subtasks for tasks). Plain ASCII, no CJK involved — this one is purely
    // about index coverage. FTS still returns a (empty, wrong) result set without throwing, so the
    // same unreachable fallback problem applies.
    // -----------------------------------------------------------------------------------------

    @Test
    fun searchNotesFindsMatchInTagsWhenTitleAndBodyDoNotContainIt() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = AppDatabase.createForTesting(TestContext(dir))
            db.noteDao().insert(Note(title = "Reading list", body = "Some books to read", tags = "urgent-followup"))

            val results = db.noteDao().searchNotes(
                text = "followup", tag = "", archived = -1, trashed = -1, limit = 50
            )

            assertTrue(
                results.any { it.tags.contains("followup") },
                "expected the tags-only match to be found; got ${results.map { it.tags }}"
            )
        }
    }

    @Test
    fun searchNotesFindsMatchInChecklistWhenTitleAndBodyDoNotContainIt() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = AppDatabase.createForTesting(TestContext(dir))
            db.noteDao().insert(
                Note(
                    title = "Errands",
                    body = "Weekend list",
                    isChecklist = true,
                    checklist = """[{"text":"pick up the dry cleaning","done":false}]"""
                )
            )

            val results = db.noteDao().searchNotes(
                text = "dry cleaning", tag = "", archived = -1, trashed = -1, limit = 50
            )

            assertTrue(
                results.any { it.checklist.contains("dry cleaning") },
                "expected the checklist-only match to be found; got ${results.map { it.checklist }}"
            )
        }
    }

    @Test
    fun searchTasksFindsMatchInSubtasksWhenTitleAndNotesDoNotContainIt() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = AppDatabase.createForTesting(TestContext(dir))
            db.taskDao().insert(
                Task(
                    title = "Grocery run",
                    notes = "",
                    subtasks = """[{"text":"call the dentist to reschedule","done":false}]"""
                )
            )

            val results = db.taskDao().searchTasks(
                text = "dentist", done = -1, trashed = -1, minPriority = -1,
                dueBefore = -1, dueAfter = -1, limit = 50
            )

            assertTrue(
                results.any { it.subtasks.contains("dentist") },
                "expected the subtasks-only match to be found; got ${results.map { it.subtasks }}"
            )
        }
    }
}
