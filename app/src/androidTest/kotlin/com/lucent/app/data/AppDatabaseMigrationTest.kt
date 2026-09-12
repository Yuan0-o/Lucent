package com.lucent.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0-3, task 2.1: Room migration coverage for every adjacent step from schema version 2 through the
 * current version (18) via [MigrationTestHelper], plus one test that runs the full production chain
 * in a single call, exactly as [AppDatabase]'s own `Room.databaseBuilder(...).addMigrations(...)`
 * registers it.
 *
 * ### This needs a device or emulator, and it needs schema JSON that does not exist yet
 *
 * This file could not be compiled or run in the sandbox that wrote it (no JDK, no Android SDK, no
 * emulator). It was written by reading every `MIGRATION_x_y` in AppDatabase.kt and the current
 * entity shapes in Entities.kt, then reconstructing each historical version's column set by hand:
 * every migration in this chain is purely additive (a new column with an inline SQL `DEFAULT`, or a
 * new table), so subtracting everything a *later* migration adds from the v18 shape gives exactly
 * the shape at any earlier version. That reconstruction could not be checked against a compiler, so
 * please read a seed `INSERT` once against the migration it precedes before trusting it — if
 * anything here doesn't match `app/schemas/.../<N>.json` once that exists, the schema JSON is the
 * source of truth, not this comment.
 *
 * Two things need to be true before ANY test below can run, and neither is done yet:
 *
 * 1. **[MigrationTestHelper.createDatabase] reads a version's shape from the JSON Room exports for
 *    it** (`app/schemas/com.lucent.app.data.AppDatabase/<version>.json`, per the
 *    `room.schemaLocation` ksp arg in app/build.gradle.kts). That directory is empty right now.
 *    Running `./gradlew :app:kspDebugKotlin` — the fix suggested elsewhere in this P0-3 handoff — is
 *    necessary but **not sufficient**: it only ever regenerates the JSON for whatever version
 *    `@Database(version = ...)` currently says, i.e. only `18.json`. Versions 2 through 17 have
 *    never been exported (schema export is being turned on for the first time in this very change),
 *    so `createDatabase(TEST_DB, N)` for any N below 18 fails outright until those 16 files exist by
 *    some other means — e.g. temporarily walking `@Database(version = N)` and the entities back to
 *    each historical shape and building once per version, or hand-authoring the JSON using each
 *    `MIGRATION_x_y` plus this file's reconstruction below as a cross-check. **Every test here needs
 *    both its start and end version's JSON**, since `runMigrationsAndValidate` also validates the
 *    result against the target version's file.
 * 2. **The androidTest source set needs the schemas directory on its assets path** so
 *    `MigrationTestHelper` can find those JSON files at instrumentation runtime. I added the minimal
 *    wiring for this to app/build.gradle.kts (`sourceSets.androidTest.assets.srcDirs`) alongside
 *    this file — it's a small step outside the "just add test files" framing of this handoff, but
 *    without it these tests cannot pass regardless of (1). Called out again in the delivery notes.
 *
 * Also unverified: the exact [MigrationTestHelper] constructor signature. The two-argument
 * `(instrumentation, databaseClass)` form used below has been the standard, documented one across
 * many Room releases, but I could not compile against the real `room-testing:2.8.4` artifact to
 * confirm it hasn't changed again — worth a quick check against that release's notes before relying
 * on it.
 *
 * ### Why a plain (non-SQLCipher) database here
 *
 * These tests use `MigrationTestHelper`'s default `FrameworkSQLiteOpenHelperFactory` — an
 * unencrypted database — deliberately, to keep "is the schema migration correct" separate from "is
 * SQLCipher wired correctly", the same way this handoff's task 2 already splits them into two
 * numbered items. The SQLCipher-specific behaviour (opening with [DataKeys.databasePassphrase], a
 * write/close/reopen cycle, a raw rekey) lives in [DataKeysSqlCipherTest] instead.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val TEST_DB = "app-database-migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    // ---- 2 -> 3: notes.tags -----------------------------------------------------------------

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

    // ---- 3 -> 4: notes.attachments, tasks.attachments ----------------------------------------

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

    // ---- 4 -> 5: tasks.dueAt (nullable) -------------------------------------------------------

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

    // ---- 5 -> 6: tasks.notes ------------------------------------------------------------------

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

    // ---- 6 -> 7: tasks.completedAt + backfill for already-done tasks -------------------------

    @Test
    fun migrate6To7_backfillsCompletedAtForAlreadyDoneTasksOnly() {
        helper.createDatabase(TEST_DB, 6).apply {
            // Marked done before this column existed: the backfill must stamp completedAt = createdAt.
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes) " +
                    "VALUES (1, 'Done already', 1, 5000, '[]', NULL, '')"
            )
            // Still pending: must be left alone (completedAt stays null).
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

    // ---- 7 -> 8: chat_conversations + chat_messages.conversationId ---------------------------

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
        // The seed is conditional (`if (count > 0)` in MIGRATION_7_8) precisely so a fresh-ish
        // install that never chatted doesn't get a phantom "Conversation" row with nothing in it.
        helper.createDatabase(TEST_DB, 7).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        db.query("SELECT COUNT(*) FROM chat_conversations").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
        }
    }

    // ---- 8 -> 9: notes.archived, notes.archivedAt ----------------------------------------------

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

    // ---- 9 -> 10: the maturity release (notes x5, tasks x6, note_versions table) --------------

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

        // note_versions is created empty by this migration; prove it is genuinely usable, not just
        // present in sqlite_master with the right name.
        db.execSQL(
            "INSERT INTO note_versions (id, noteId, title, body, tags, isChecklist, checklist, savedAt) " +
                "VALUES (1, 1, 'N', 'B', '', 0, '[]', 1000)"
        )
        db.query("SELECT COUNT(*) FROM note_versions WHERE noteId = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
    }

    // ---- 10 -> 11: chat_messages.tokens + list-query indices ----------------------------------

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
        // runMigrationsAndValidate's own schema comparison already checks every index exhaustively;
        // this is a cheap spot check that the migration's CREATE INDEX statements actually ran.
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_notes_updatedAt'"
        ).use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
    }

    // ---- 11 -> 12: manual order / drafts / hidden (notes x4, tasks x4) + task_versions --------

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

    // ---- 12 -> 13: notes.isDoodle, notes.doodle ------------------------------------------------

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

    // ---- 13 -> 14: chat_messages.replyToId -----------------------------------------------------

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

    // ---- 14 -> 15: notes.bodySpans, tasks.notesSpans -------------------------------------------

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

    // ---- 15 -> 16: chat_messages.attachmentList (nullable) -------------------------------------

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

    // ---- 16 -> 17: notebooks + notebook_items tables --------------------------------------------

    @Test
    fun migrate16To17_createsUsableNotebookAndNotebookItemTables() {
        helper.createDatabase(TEST_DB, 16).close() // pure additive DDL; no existing row is required

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

    // ---- 17 -> 18: FTS5 search index (optional; must never break the base tables) --------------

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

        // The base rows must survive regardless of whether this device's SQLite has FTS5 compiled
        // in -- see MIGRATION_17_18's own try/catch, which is exactly the behaviour pinned here.
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

    // ---- The full, registered chain, in one call ------------------------------------------------

    @Test
    fun fullChain2To18_migratesSeedDataThroughEveryRegisteredStepInOrder() {
        // Exercises the exact migration list AppDatabase.build() registers, in the same order, so a
        // migration that works alone but was left out of (or misordered in) that list would show up
        // here even if every individual step test above passes on its own.
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
        // The seed task was already marked done back at v2, before completedAt existed at all:
        // MIGRATION_6_7's backfill must have stamped it, and that stamp must survive the ten
        // further migrations run after it in this same call.
        db.query("SELECT title, isDone, completedAt, notesSpans FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Seed task", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertEquals(500L, c.getLong(2))
            assertEquals("", c.getString(3))
        }
    }
}
