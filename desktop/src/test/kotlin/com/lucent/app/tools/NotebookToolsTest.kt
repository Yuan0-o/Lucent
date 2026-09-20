package com.lucent.app.tools

import android.content.Context
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.DataKeys
import com.lucent.app.data.LocalSecrets
import com.lucent.app.data.Note
import com.lucent.app.data.Notebook
import com.lucent.app.data.NotebookItem
import com.lucent.app.data.Task
import com.lucent.app.network.ToolExecResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class NotebookToolsTest {

    private class TestContext(private val dir: File) : Context() {
        override val filesDir: File get() = dir
    }

    private fun freshDir(): File =
        File(System.getProperty("java.io.tmpdir"), "lucent-notebook-tools-test-${System.nanoTime()}")
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

    private suspend fun exec(db: AppDatabase, name: String, json: String): ToolExecResult =
        assertNotNull(
            NotebookTools.execute(db, name, JSONObject(json)),
            "$name should be handled by NotebookTools"
        )

    @Test
    fun notebookToolsAreRegisteredAndClassified() {
        val names = AppTools.definitions().map { it.name }.toSet()
        for (name in listOf(
            "list_notebooks", "create_notebook", "rename_notebook", "delete_notebook",
            "list_notebook_items", "add_to_notebook", "remove_from_notebook",
            "move_to_notebook", "search_notebook"
        )) {
            assertTrue(name in names, "$name should be a registered tool")
        }
        for (name in listOf("list_notebooks", "list_notebook_items", "search_notebook")) {
            assertFalse(AppTools.isMutating(name), "$name should be read-only")
        }
        for (name in listOf(
            "create_notebook", "rename_notebook", "delete_notebook", "add_to_notebook",
            "remove_from_notebook", "move_to_notebook"
        )) {
            assertTrue(AppTools.isMutating(name), "$name should be mutating")
        }
    }

    @Test
    fun notebookConfirmationTextAndEditableArguments() {
        val created = AppTools.describeToolCall("create_notebook", """{"title":"Trips"}""")
        assertTrue(created.contains("Trips"), "confirmation should name the notebook; got $created")
        assertEquals(listOf("title"), AppTools.editableArguments("create_notebook", """{"title":"Trips"}""").map { it.key })
        assertEquals(
            listOf("new_title"),
            AppTools.editableArguments("rename_notebook", """{"notebook":"Trips","new_title":"Travel"}""").map { it.key }
        )
        val removed = AppTools.describeToolCall(
            "remove_from_notebook",
            """{"notebook":"Trips","title":"Osaka hotel"}"""
        )
        assertTrue(removed.contains("Osaka hotel"), "confirmation should name the item; got $removed")
        assertTrue(removed.contains("Trips"), "confirmation should name the notebook; got $removed")
    }

    @Test
    fun createNotebookPersistsAndIsListed() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = AppDatabase.createForTesting(TestContext(dir))
            val created = exec(db, "create_notebook", """{"title":"Trips"}""")
            assertTrue(created.success)
            assertTrue(created.summary.contains("Trips"))
            assertEquals(1, db.notebookDao().getAllOnce().size)

            db.noteDao().insert(Note(title = "Osaka hotel", body = "booked"))
            val listed = exec(db, "list_notebooks", "{}")
            assertTrue(listed.summary.contains("Trips"), "got ${listed.summary}")
            assertTrue(listed.summary.contains("1 item"), "got ${listed.summary}")
        }
    }

    @Test
    fun blankNotebookNameIsRejected() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = AppDatabase.createForTesting(TestContext(dir))
            val created = exec(db, "create_notebook", """{"title":"   "}""")
            assertFalse(created.success)
            assertTrue(db.notebookDao().getAllOnce().isEmpty())
        }
    }

    @Test
    fun unknownNotebookReportsTheRealNames() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = AppDatabase.createForTesting(TestContext(dir))
            db.notebookDao().insert(Notebook(title = "Work"))
            val listed = exec(db, "list_notebook_items", """{"notebook":"Holiday"}""")
            assertFalse(listed.success)
            assertTrue(listed.summary.contains("Work"), "got ${listed.summary}")
        }
    }

    @Test
    fun addListRemoveAndMoveAMemberNote() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = AppDatabase.createForTesting(TestContext(dir))
            val tripsId = db.notebookDao().insert(Notebook(title = "Trips"))
            val workId = db.notebookDao().insert(Notebook(title = "Work"))
            val noteId = db.noteDao().insert(Note(title = "Osaka hotel", body = "booked"))

            val added = exec(
                db, "add_to_notebook",
                """{"notebook":"Trips","title":"Osaka hotel","item_type":"note"}"""
            )
            assertTrue(added.success, added.summary)
            assertEquals(1, db.notebookDao().membershipExistsOnce(tripsId, NotebookItem.KIND_NOTE, noteId))

            val again = exec(
                db, "add_to_notebook",
                """{"notebook":"Trips","title":"Osaka","item_type":"note"}"""
            )
            assertTrue(again.success)
            assertTrue(again.summary.contains("already"), "got ${again.summary}")
            assertEquals(1, db.notebookDao().getItemsOnce(tripsId).size)

            val listed = exec(db, "list_notebook_items", """{"notebook":"Trips"}""")
            assertTrue(listed.summary.contains("Osaka hotel"), "got ${listed.summary}")

            val moved = exec(
                db, "move_to_notebook",
                """{"title":"Osaka hotel","to_notebook":"Work","item_type":"note"}"""
            )
            assertTrue(moved.success, moved.summary)
            assertEquals(0, db.notebookDao().membershipExistsOnce(tripsId, NotebookItem.KIND_NOTE, noteId))
            assertEquals(1, db.notebookDao().membershipExistsOnce(workId, NotebookItem.KIND_NOTE, noteId))

            val removed = exec(
                db, "remove_from_notebook",
                """{"notebook":"Work","title":"Osaka hotel","item_type":"note"}"""
            )
            assertTrue(removed.success, removed.summary)
            assertEquals(0, db.notebookDao().membershipExistsOnce(workId, NotebookItem.KIND_NOTE, noteId))
            assertNotNull(db.noteDao().getByIdOnce(noteId))
        }
    }

    @Test
    fun ambiguousNoteAndTaskNeedItemType() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = AppDatabase.createForTesting(TestContext(dir))
            val workId = db.notebookDao().insert(Notebook(title = "Work"))
            val noteId = db.noteDao().insert(Note(title = "Invoice", body = ""))
            val taskId = db.taskDao().insert(Task(title = "Invoice"))

            val ambiguous = exec(db, "add_to_notebook", """{"notebook":"Work","title":"Invoice"}""")
            assertFalse(ambiguous.success)
            assertTrue(ambiguous.summary.contains("item_type"), "got ${ambiguous.summary}")
            assertEquals(0, db.notebookDao().getItemsOnce(workId).size)

            val resolved = exec(
                db, "add_to_notebook",
                """{"notebook":"Work","title":"Invoice","item_type":"task"}"""
            )
            assertTrue(resolved.success, resolved.summary)
            assertEquals(1, db.notebookDao().membershipExistsOnce(workId, NotebookItem.KIND_TASK, taskId))
            assertEquals(0, db.notebookDao().membershipExistsOnce(workId, NotebookItem.KIND_NOTE, noteId))
        }
    }

    @Test
    fun moveWithoutSourceNeedsAFiledItem() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = AppDatabase.createForTesting(TestContext(dir))
            db.notebookDao().insert(Notebook(title = "Trips"))
            db.notebookDao().insert(Notebook(title = "Work"))
            db.noteDao().insert(Note(title = "Packing list", body = "socks"))

            val moved = exec(
                db, "move_to_notebook",
                """{"title":"Packing list","to_notebook":"Work","item_type":"note"}"""
            )
            assertFalse(moved.success)
            assertTrue(moved.summary.contains("add_to_notebook"), "got ${moved.summary}")
        }
    }

    @Test
    fun searchNotebookOnlyReturnsMembers() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = AppDatabase.createForTesting(TestContext(dir))
            val tripsId = db.notebookDao().insert(Notebook(title = "Trips"))
            db.notebookDao().insert(Notebook(title = "Work"))
            val insideId = db.noteDao().insert(Note(title = "Osaka hotel", body = "booked"))
            db.noteDao().insert(Note(title = "Hotel budget", body = "too much"))

            db.notebookDao().insertItem(
                NotebookItem(notebookId = tripsId, itemKind = NotebookItem.KIND_NOTE, itemId = insideId)
            )

            val hit = exec(db, "search_notebook", """{"notebook":"Trips","query":"hotel"}""")
            assertTrue(hit.success)
            assertTrue(hit.summary.contains("Osaka hotel"), "got ${hit.summary}")
            assertFalse(hit.summary.contains("Hotel budget"), "got ${hit.summary}")

            val miss = exec(db, "search_notebook", """{"notebook":"Trips","query":"invoice"}""")
            assertTrue(miss.success)
            assertTrue(miss.summary.contains("Nothing"), "got ${miss.summary}")
        }
    }

    @Test
    fun deleteNotebookKeepsItsNotes() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = AppDatabase.createForTesting(TestContext(dir))
            val tripsId = db.notebookDao().insert(Notebook(title = "Trips"))
            val noteId = db.noteDao().insert(Note(title = "Osaka hotel", body = "booked"))
            db.notebookDao().insertItem(
                NotebookItem(notebookId = tripsId, itemKind = NotebookItem.KIND_NOTE, itemId = noteId)
            )

            val deleted = exec(db, "delete_notebook", """{"notebook":"Trips"}""")
            assertTrue(deleted.success, deleted.summary)
            assertTrue(deleted.summary.contains("not deleted"), "got ${deleted.summary}")
            assertTrue(db.notebookDao().getAllOnce().isEmpty())
            assertEquals(0, db.notebookDao().getItemsOnce(tripsId).size)
            assertNotNull(db.noteDao().getByIdOnce(noteId))
        }
    }

    @Test
    fun renameNotebookUpdatesTheStoredName() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = AppDatabase.createForTesting(TestContext(dir))
            val tripsId = db.notebookDao().insert(Notebook(title = "Trips"))

            val renamed = exec(db, "rename_notebook", """{"notebook":"Trip","new_title":"Travel"}""")
            assertTrue(renamed.success, renamed.summary)
            assertEquals("Travel", db.notebookDao().getByIdOnce(tripsId)?.title)

            val blank = exec(db, "rename_notebook", """{"notebook":"Travel","new_title":" "}""")
            assertFalse(blank.success)
        }
    }
}
