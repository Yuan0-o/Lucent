package com.lucent.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val TEST_DB = "app-database-migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )


    @Test
    fun migrate2To3_addsNotesTagsColumnDefaultingToEmpty() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL("INSERT INTO notes (id, title, body, updatedAt) VALUES (1, 'Title', 'Body', 1000)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        db.query("SELECT title, tags FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Title", c.getString(0))
            assertEquals("", c.getString(1))
        }
    }


    @Test
    fun migrate3To4_addsAttachmentsColumnToNotesAndTasks() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL("INSERT INTO notes (id, title, body, updatedAt, tags) VALUES (1, 'N', 'B', 1000, '')")
            execSQL("INSERT INTO tasks (id, title, isDone, createdAt) VALUES (1, 'T', 0, 1000)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        db.query("SELECT attachments FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("[]", c.getString(0))
        }
        db.query("SELECT attachments FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("[]", c.getString(0))
        }
    }


    @Test
    fun migrate4To5_addsTasksDueAtColumnDefaultingToNull() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments) " +
                    "VALUES (1, 'T', 0, 1000, '[]')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        db.query("SELECT dueAt FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
    }


    @Test
    fun migrate5To6_addsTasksNotesColumnDefaultingToEmpty() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt) " +
                    "VALUES (1, 'T', 0, 1000, '[]', NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        db.query("SELECT notes FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("", c.getString(0))
        }
    }


    @Test
    fun migrate6To7_backfillsCompletedAtForAlreadyDoneTasksOnly() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes) " +
                    "VALUES (1, 'Done already', 1, 5000, '[]', NULL, '')"
            )
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes) " +
                    "VALUES (2, 'Still pending', 0, 6000, '[]', NULL, '')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)

        db.query("SELECT completedAt FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(5000L, c.getLong(0))
        }
        db.query("SELECT completedAt FROM tasks WHERE id = 2").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
    }


    @Test
    fun migrate7To8_seedsOneConversationAndBackfillsExistingMessagesWhenHistoryExists() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL(
                "INSERT INTO chat_messages (id, role, content, timestamp, attachmentMime, " +
                    "attachmentData, attachmentName) VALUES (1, 'user', 'hello', 1000, NULL, NULL, NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        db.query("SELECT COUNT(*) FROM chat_conversations").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
        db.query("SELECT conversationId FROM chat_messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1L, c.getLong(0))
        }
    }

    @Test
    fun migrate7To8_seedsNoConversationWhenThereIsNoExistingChatHistory() {
        helper.createDatabase(TEST_DB, 7).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        db.query("SELECT COUNT(*) FROM chat_conversations").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
        }
    }


    @Test
    fun migrate8To9_addsNotesArchivedColumns() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments) " +
                    "VALUES (1, 'N', 'B', 1000, '', '[]')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9)

        db.query("SELECT archived, archivedAt FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertTrue(c.isNull(1))
        }
    }


    @Test
    fun migrate9To10_addsNoteAndTaskOrganizationColumnsPlusUsableNoteVersionsTable() {
        helper.createDatabase(TEST_DB, 9).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, archivedAt) " +
                    "VALUES (1, 'N', 'B', 1000, '', '[]', 0, NULL)"
            )
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes, completedAt) " +
                    "VALUES (1, 'T', 0, 1000, '[]', NULL, '', NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)

        db.query(
            "SELECT pinned, color, isChecklist, checklist, trashedAt FROM notes WHERE id = 1"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals("", c.getString(1))
            assertEquals(0, c.getInt(2))
            assertEquals("[]", c.getString(3))
            assertTrue(c.isNull(4))
        }
        db.query(
            "SELECT priority, pinned, subtasks, repeatRule, reminderEnabled, trashedAt " +
                "FROM tasks WHERE id = 1"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals(0, c.getInt(1))
            assertEquals("[]", c.getString(2))
            assertEquals("NONE", c.getString(3))
            assertEquals(0, c.getInt(4))
            assertTrue(c.isNull(5))
        }

        db.execSQL(
            "INSERT INTO note_versions (id, noteId, title, body, tags, isChecklist, checklist, savedAt) " +
                "VALUES (1, 1, 'N', 'B', '', 0, '[]', 1000)"
        )
        db.query("SELECT COUNT(*) FROM note_versions WHERE noteId = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
    }


    @Test
    fun migrate10To11_addsChatMessagesTokensColumnAndListQueryIndices() {
        helper.createDatabase(TEST_DB, 10).apply {
            execSQL(
                "INSERT INTO chat_messages (id, role, content, timestamp, attachmentMime, " +
                    "attachmentData, attachmentName, conversationId) " +
                    "VALUES (1, 'user', 'hi', 1000, NULL, NULL, NULL, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        db.query("SELECT tokens FROM chat_messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
        }
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_notes_updatedAt'"
        ).use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
    }


    @Test
    fun migrate11To12_addsDraftAndHiddenColumnsPlusUsableTaskVersionsTable() {
        helper.createDatabase(TEST_DB, 11).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, " +
                    "archivedAt, pinned, color, isChecklist, checklist, trashedAt) " +
                    "VALUES (1, 'N', 'B', 1000, '', '[]', 0, NULL, 0, '', 0, '[]', NULL)"
            )
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes, " +
                    "completedAt, priority, pinned, subtasks, repeatRule, reminderEnabled, trashedAt) " +
                    "VALUES (1, 'T', 0, 1000, '[]', NULL, '', NULL, 0, 0, '[]', 'NONE', 0, NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_11_12)

        db.query("SELECT manualOrder, isDraft, draftSavedAt, hidden FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals(0, c.getInt(1))
            assertTrue(c.isNull(2))
            assertEquals(0, c.getInt(3))
        }
        db.query("SELECT manualOrder, isDraft, draftSavedAt, hidden FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals(0, c.getInt(1))
            assertTrue(c.isNull(2))
            assertEquals(0, c.getInt(3))
        }

        db.execSQL(
            "INSERT INTO task_versions (id, taskId, title, notes, subtasks, priority, dueAt, savedAt) " +
                "VALUES (1, 1, 'T', '', '[]', 0, NULL, 1000)"
        )
        db.query("SELECT COUNT(*) FROM task_versions WHERE taskId = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
    }


    @Test
    fun migrate12To13_addsNotesDoodleColumns() {
        helper.createDatabase(TEST_DB, 12).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, " +
                    "archivedAt, pinned, color, isChecklist, checklist, trashedAt, manualOrder, " +
                    "isDraft, draftSavedAt, hidden) VALUES " +
                    "(1, 'N', 'B', 1000, '', '[]', 0, NULL, 0, '', 0, '[]', NULL, 0, 0, NULL, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

        db.query("SELECT isDoodle, doodle FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals("", c.getString(1))
        }
    }


    @Test
    fun migrate13To14_addsChatMessagesReplyToIdColumn() {
        helper.createDatabase(TEST_DB, 13).apply {
            execSQL(
                "INSERT INTO chat_messages (id, role, content, timestamp, attachmentMime, " +
                    "attachmentData, attachmentName, conversationId, tokens) " +
                    "VALUES (1, 'assistant', 'hi', 1000, NULL, NULL, NULL, 1, 12)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        db.query("SELECT replyToId FROM chat_messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0L, c.getLong(0))
        }
    }


    @Test
    fun migrate14To15_addsRichTextSpanColumnsToNotesAndTasks() {
        helper.createDatabase(TEST_DB, 14).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, " +
                    "archivedAt, pinned, color, isChecklist, checklist, trashedAt, manualOrder, " +
                    "isDraft, draftSavedAt, hidden, isDoodle, doodle) VALUES " +
                    "(1, 'N', 'B', 1000, '', '[]', 0, NULL, 0, '', 0, '[]', NULL, 0, 0, NULL, 0, 0, '')"
            )
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes, " +
                    "completedAt, priority, pinned, subtasks, repeatRule, reminderEnabled, trashedAt, " +
                    "manualOrder, isDraft, draftSavedAt, hidden) VALUES " +
                    "(1, 'T', 0, 1000, '[]', NULL, '', NULL, 0, 0, '[]', 'NONE', 0, NULL, 0, 0, NULL, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 15, true, MIGRATION_14_15)

        db.query("SELECT bodySpans FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("", c.getString(0))
        }
        db.query("SELECT notesSpans FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("", c.getString(0))
        }
    }


    @Test
    fun migrate15To16_addsChatMessagesAttachmentListColumn() {
        helper.createDatabase(TEST_DB, 15).apply {
            execSQL(
                "INSERT INTO chat_messages (id, role, content, timestamp, attachmentMime, " +
                    "attachmentData, attachmentName, conversationId, tokens, replyToId) " +
                    "VALUES (1, 'user', 'hi', 1000, NULL, NULL, NULL, 1, 0, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 16, true, MIGRATION_15_16)

        db.query("SELECT attachmentList FROM chat_messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
    }


    @Test
    fun migrate16To17_createsUsableNotebookAndNotebookItemTables() {
        helper.createDatabase(TEST_DB, 16).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 17, true, MIGRATION_16_17)

        db.execSQL("INSERT INTO notebooks (id, title, createdAt, updatedAt) VALUES (1, 'Trips', 1000, 1000)")
        db.execSQL(
            "INSERT INTO notebook_items (id, notebookId, itemKind, itemId, addedAt) " +
                "VALUES (1, 1, 'NOTE', 1, 1000)"
        )

        db.query("SELECT title FROM notebooks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("Trips", c.getString(0))
        }
        db.query("SELECT itemKind, itemId FROM notebook_items WHERE notebookId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("NOTE", c.getString(0))
            assertEquals(1L, c.getLong(1))
        }
    }


    @Test
    fun migrate17To18_addsFtsTablesWhenAvailableAndNeverBreaksTheBaseTables() {
        helper.createDatabase(TEST_DB, 17).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, " +
                    "archivedAt, pinned, color, isChecklist, checklist, trashedAt, manualOrder, " +
                    "isDraft, draftSavedAt, hidden, isDoodle, doodle, bodySpans) VALUES " +
                    "(1, 'FTS seed', 'a searchable body', 1000, '', '[]', 0, NULL, 0, '', 0, '[]', " +
                    "NULL, 0, 0, NULL, 0, 0, '', '')"
            )
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes, " +
                    "completedAt, priority, pinned, subtasks, repeatRule, reminderEnabled, trashedAt, " +
                    "manualOrder, isDraft, draftSavedAt, hidden, notesSpans) VALUES " +
                    "(1, 'FTS task', 0, 1000, '[]', NULL, 'notes body', NULL, 0, 0, '[]', 'NONE', 0, " +
                    "NULL, 0, 0, NULL, 0, '')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 18, true, MIGRATION_17_18)

        db.query("SELECT title FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("FTS seed", c.getString(0))
        }
        db.query("SELECT title FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("FTS task", c.getString(0))
        }

        val ftsAvailable = try {
            db.query("SELECT name FROM sqlite_master WHERE name = 'notes_fts'").use { it.moveToFirst() }
        } catch (t: Throwable) {
            false
        }
        if (ftsAvailable) {
            db.query("SELECT title FROM notes_fts WHERE rowid = 1").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("FTS seed", c.getString(0))
            }
            db.query("SELECT title FROM tasks_fts WHERE rowid = 1").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("FTS task", c.getString(0))
            }
        }
    }


    @Test
    fun fullChain2To18_migratesSeedDataThroughEveryRegisteredStepInOrder() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL("INSERT INTO notes (id, title, body, updatedAt) VALUES (1, 'Seed note', 'Seed body', 1000)")
            execSQL("INSERT INTO tasks (id, title, isDone, createdAt) VALUES (1, 'Seed task', 1, 500)")
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 18, true,
            MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
            MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
            MIGRATION_16_17, MIGRATION_17_18
        )

        db.query("SELECT title, tags, hidden, bodySpans, doodle FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Seed note", c.getString(0))
            assertEquals("", c.getString(1))
            assertEquals(0, c.getInt(2))
            assertEquals("", c.getString(3))
            assertEquals("", c.getString(4))
        }
        db.query("SELECT title, isDone, completedAt, notesSpans FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Seed task", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertEquals(500L, c.getLong(2))
            assertEquals("", c.getString(3))
        }
    }

    @Test
    fun migrate19To20_addsChatMessagesAgentTraceColumn() {
        helper.createDatabase(TEST_DB, 19).apply {
            execSQL(
                "INSERT INTO chat_messages (id, role, content, timestamp, attachmentMime, " +
                    "attachmentData, attachmentName, attachmentList, conversationId, tokens, replyToId) " +
                    "VALUES (1, 'assistant', 'hi', 1000, NULL, NULL, NULL, NULL, 1, 0, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 20, true, MIGRATION_19_20)

        db.query("SELECT agentTrace FROM chat_messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
    }

    @Test
    fun migrate20To21_addsFormatOverrideColumnsToNotesAndTasks() {
        helper.createDatabase(TEST_DB, 20).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, " +
                    "archivedAt, pinned, color, isChecklist, checklist, trashedAt, manualOrder, " +
                    "isDraft, draftSavedAt, hidden, isDoodle, doodle, bodySpans) VALUES " +
                    "(1, 'N', 'B', 1000, '', '[]', 0, NULL, 0, '', 0, '[]', NULL, 0, 0, NULL, 0, 0, '', '')"
            )
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes, " +
                    "completedAt, priority, pinned, subtasks, repeatRule, reminderEnabled, trashedAt, " +
                    "manualOrder, isDraft, draftSavedAt, hidden, notesSpans) VALUES " +
                    "(1, 'T', 0, 1000, '[]', NULL, '', NULL, 0, 0, '[]', 'NONE', 0, NULL, 0, 0, NULL, 0, '')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 21, true, MIGRATION_20_21)

        db.query("SELECT formatOverride FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
        db.query("SELECT formatOverride FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
    }

    @Test
    fun migrate21To22_addsCoverOrderAndTrashToNotebooks() {
        helper.createDatabase(TEST_DB, 21).apply {
            execSQL(
                "INSERT INTO notebooks (id, title, createdAt, updatedAt) VALUES (1, 'Ideas', 1000, 1000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 22, true, MIGRATION_21_22)

        db.query("SELECT title, color, manualOrder, trashedAt FROM notebooks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Ideas", c.getString(0))
            assertEquals("", c.getString(1))
            assertEquals(0, c.getInt(2))
            assertTrue(c.isNull(3))
        }
    }
}
