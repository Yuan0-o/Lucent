package com.lucent.app.data

import android.content.Context
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

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
