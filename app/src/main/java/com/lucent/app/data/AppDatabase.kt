package com.lucent.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN attachments TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE tasks ADD COLUMN attachments TEXT NOT NULL DEFAULT '[]'")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN dueAt INTEGER")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
    }
}

// Adds the completedAt timestamp used by the completed-tasks history page. Nullable, so old
// tasks — which never recorded a completion time — simply have null and the history page
// falls back to createdAt when sorting them.
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN completedAt INTEGER")
        // Best-effort backfill: for any task already marked done, treat createdAt as its
        // completion time. It's not accurate but it puts them in a sensible spot on the
        // history page instead of them all sharing a null.
        db.execSQL("UPDATE tasks SET completedAt = createdAt WHERE isDone = 1 AND completedAt IS NULL")
    }
}

// Adds multi-conversation support to the assistant. Existing chat rows all belong to a single
// pre-sessions history, so we create one conversation (id 1) to hold them and stamp every
// existing message with conversationId = 1. New conversations get their own ids from there.
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS chat_conversations (" +
                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL, " +
                "updatedAt INTEGER NOT NULL)"
        )
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN conversationId INTEGER NOT NULL DEFAULT 1")
        // Only seed the initial conversation if there is existing chat history to hold.
        val now = System.currentTimeMillis()
        val cursor = db.query("SELECT COUNT(*) FROM chat_messages")
        val count = if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        cursor.close()
        if (count > 0) {
            db.execSQL(
                "INSERT INTO chat_conversations (id, title, createdAt, updatedAt) " +
                    "VALUES (1, 'Conversation', $now, $now)"
            )
        }
    }
}

// Adds note archiving. `archived` flags a note as archived (hidden from the home page); nullable
// `archivedAt` records when it happened so the archive screen can sort by time. Existing notes
// default to not-archived with a null timestamp.
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN archivedAt INTEGER")
    }
}

/**
 * The maturity release, in one migration.
 *
 * Notes gain pinning, an accent colour, Keep-style checklist mode, and soft-delete (Trash).
 * Tasks gain priority, pinning, subtasks, a repeat rule, reminders, and soft-delete.
 * A new `note_versions` table stores each note's local revision history.
 *
 * Every added column carries an inert default, so an existing row upgrades to exactly the
 * behaviour it already had: unpinned, no colour, plain-text body, no priority, no subtasks, does
 * not repeat, no reminder, not trashed. The two `trashedAt` columns are nullable with no default,
 * and NULL is precisely "not in the trash", which every pre-existing row correctly is.
 *
 * `note_versions` is created with the exact column set and index Room generates for [NoteVersion]
 * on a fresh install, so a migrated database and a freshly created one are byte-for-byte the same
 * shape and Room's schema validation passes either way.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ---- Notes ----
        db.execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN color TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE notes ADD COLUMN isChecklist INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN checklist TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE notes ADD COLUMN trashedAt INTEGER")

        // ---- Tasks ----
        db.execSQL("ALTER TABLE tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN subtasks TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE tasks ADD COLUMN repeatRule TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE tasks ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN trashedAt INTEGER")

        // ---- Note revision history ----
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_versions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`noteId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, " +
                "`tags` TEXT NOT NULL, " +
                "`isChecklist` INTEGER NOT NULL, " +
                "`checklist` TEXT NOT NULL, " +
                "`savedAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_versions_noteId` ON `note_versions` (`noteId`)")
    }
}

// One combined 10 → 11 step for this whole delivery, doing two independent, loss-free things:
//  1. Adds the per-reply token estimate shown under assistant messages (assistant issue 9).
//     Nullable-free with a 0 default, so every existing message reads back as "no estimate
//     recorded" and simply shows no footnote — nothing is recomputed and no data is lost.
//  2. Adds the list-query indices (settings task 8) on the columns the home/archive/trash screens
//     filter and sort by. Index names match Room's own derivation (index_<table>_<column>) so a
//     migrated database and a freshly created one agree and schema validation passes. Creating an
//     index is pure metadata: no row is read or rewritten, so it is safe at any database size.
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN tokens INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updatedAt` ON `notes` (`updatedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_archived` ON `notes` (`archived`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_trashedAt` ON `notes` (`trashedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_createdAt` ON `tasks` (`createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_isDone` ON `tasks` (`isDone`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_trashedAt` ON `tasks` (`trashedAt`)")
    }
}

/**
 * One combined 11 → 12 step for the whole 1.1.0 group-A delivery. Four features share it on
 * purpose: each needs a schema change, and four separate migrations would mean four version bumps,
 * four chances for a partially-migrated database, and four rounds of backup-format adaptation for
 * changes that ship together anyway.
 *
 *  1. **Manual order (task A16).** `manualOrder` on both tables, defaulting to 0. Every existing
 *     row therefore ties, and the sort falls through to its usual secondary key — so switching to
 *     "custom" order shows the list exactly as it already looked instead of scrambling it. Real
 *     values are written on the first drag.
 *  2. **Drafts (task A10).** `isDraft` + `draftSavedAt`. A draft is the note/task itself at an
 *     earlier moment, so it stays in its own table and is excluded by the list queries — the same
 *     shape `trashedAt` already uses, rather than a parallel table that would have to be kept in
 *     step with every future column.
 *  3. **Hidden items (task A21).** `hidden`, excluded from every list until the user turns the
 *     hidden area on.
 *  4. **Task revision history (task A19).** The `task_versions` table, mirroring `note_versions`.
 *
 * All of it is additive: new columns carry NOT NULL defaults, the new table is created empty, and
 * no existing row is read or rewritten. A database at version 11 upgrades without touching a
 * single byte of user content, and every pre-existing row reads back as "not a draft, not hidden,
 * no manual position, no history yet" — which is exactly what it was.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ---- Notes ----
        db.execSQL("ALTER TABLE notes ADD COLUMN manualOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN isDraft INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN draftSavedAt INTEGER")
        db.execSQL("ALTER TABLE notes ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")

        // ---- Tasks ----
        db.execSQL("ALTER TABLE tasks ADD COLUMN manualOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN isDraft INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN draftSavedAt INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")

        // ---- Task revision history (task A19) ----
        // Column order, types and nullability match the TaskVersion entity exactly; Room validates
        // a migrated schema against the generated one on first open, and a mismatch here is what
        // turns an upgrade into a crash on launch.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_versions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`taskId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, " +
                "`subtasks` TEXT NOT NULL, " +
                "`priority` INTEGER NOT NULL, " +
                "`dueAt` INTEGER, " +
                "`savedAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_versions_taskId` ON `task_versions` (`taskId`)")

        // The new list queries filter on these, so index them for the same reason MIGRATION_10_11
        // indexed archived/trashedAt. Creating an index is pure metadata — no row is touched.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_isDraft` ON `notes` (`isDraft`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_hidden` ON `notes` (`hidden`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_isDraft` ON `tasks` (`isDraft`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_hidden` ON `tasks` (`hidden`)")
    }
}

/**
 * 12 → 13: doodle notes (task A22). Two additive columns whose defaults read a pre-existing row
 * back as exactly what it was — not a doodle, nothing drawn. No row is read or rewritten.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN isDoodle INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN doodle TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * 13 → 14: `chat_messages.replyToId` (B-group task 12).
 *
 * INTEGRATION NOTE: group B originally shipped this as MIGRATION_11_12. Group A had already
 * claimed 11 → 12 and 12 → 13 for drafts/hidden/manual-order/task-history/doodle, so the same
 * version number carried two different schema changes. Renumbered to 13 → 14 during integration;
 * the SQL itself is unchanged. Any database that had already run B's build in isolation is a
 * pre-release state and is not supported for in-place upgrade.
 *
 * Every existing reply keeps 0, which reads as "not part of a variant group" — so old
 * conversations render exactly as they did, with no switcher and no backfill pass over the user's
 * history.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN replyToId INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * 15: rich-text spans (C-group task 20, wired up during integration).
 *
 * Two sidecar columns, both defaulting to the empty string — which decodes to "no formatting", so
 * every existing note and task reads back exactly as it is today. `body` and `notes` themselves are
 * untouched and stay plain text; see data/RichText.kt for why that is the whole point.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN bodySpans TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE tasks ADD COLUMN notesSpans TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [
        Note::class,
        Task::class,
        NoteVersion::class,
        TaskVersion::class,
        ChatMessage::class,
        ChatConversation::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun noteVersionDao(): NoteVersionDao
    abstract fun taskVersionDao(): TaskVersionDao
    abstract fun chatDao(): ChatDao
    abstract fun chatConversationDao(): ChatConversationDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun build(appContext: Context): AppDatabase {
            // Prepare the encryption before Room is constructed, never after.
            //
            // ensureReady() migrates a plaintext database across, and — crucially — *verifies the key
            // actually opens the file* before we hand it to Room. Room's own corruption callback
            // deletes the database, and SQLCipher reports a wrong key as corruption, so letting Room
            // be the first thing to try the key would mean a Keystore hiccup silently wipes every
            // note the user has. See DatabaseEncryption for the full reasoning.
            //
            // A null passphrase means SQLCipher is unusable on this device. The database then opens
            // unencrypted — exactly as it did before this feature existed, which is a worse outcome
            // than encryption but a far better one than refusing to start.
            val passphrase = DatabaseEncryption.ensureReady(appContext)

            val builder = Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                DatabaseEncryption.DB_NAME
            ).addMigrations(
                MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                MIGRATION_13_14
            )
                // dropAllTables = true preserves the old no-arg behaviour (every table is
                // recreated) while using the non-deprecated overload.
                .fallbackToDestructiveMigration(dropAllTables = true)

            if (passphrase != null) {
                builder.openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8)))
            }
            return builder.build()
        }
    }
}
