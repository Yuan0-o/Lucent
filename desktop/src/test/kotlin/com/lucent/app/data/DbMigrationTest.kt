package com.lucent.app.data

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Schema-migration tests (P0-6): build a real v11 SQLite store — the oldest shape the desktop
 * walker migrates — with sample rows in every table the steps touch, run [Db.runSchemaMigrations],
 * and assert that every step to v17 landed while the data survived untouched. Also proves the
 * walker is idempotent and a current store is skipped without touching anything.
 */
class DbMigrationTest {

    private val log = mutableListOf<String>()

    private fun freshConnection(): Pair<Connection, File> {
        val dir = File(System.getProperty("java.io.tmpdir"), "lucent-db-test-${System.nanoTime()}")
        dir.mkdirs()
        val file = File(dir, "legacy.db")
        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
        return conn to file
    }

    /** Create the tables exactly as they shipped at schema v11 (no v12+ columns). */
    private fun createV11Schema(conn: Connection) {
        conn.createStatement().use { st ->
            st.executeUpdate(
                "CREATE TABLE notes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "title TEXT NOT NULL, body TEXT NOT NULL, updatedAt INTEGER NOT NULL, " +
                    "tags TEXT NOT NULL DEFAULT '', attachments TEXT NOT NULL DEFAULT '[]', " +
                    "archived INTEGER NOT NULL DEFAULT 0, archivedAt INTEGER, " +
                    "pinned INTEGER NOT NULL DEFAULT 0, color TEXT NOT NULL DEFAULT '', " +
                    "isChecklist INTEGER NOT NULL DEFAULT 0, checklist TEXT NOT NULL DEFAULT '[]', " +
                    "trashedAt INTEGER)"
            )
            st.executeUpdate(
                "CREATE TABLE tasks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "title TEXT NOT NULL, isDone INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, " +
                    "attachments TEXT NOT NULL DEFAULT '[]', dueAt INTEGER, notes TEXT NOT NULL DEFAULT '', " +
                    "completedAt INTEGER, priority INTEGER NOT NULL DEFAULT 0, pinned INTEGER NOT NULL DEFAULT 0, " +
                    "subtasks TEXT NOT NULL DEFAULT '[]', repeatRule TEXT NOT NULL DEFAULT 'NONE', " +
                    "reminderEnabled INTEGER NOT NULL DEFAULT 0, trashedAt INTEGER)"
            )
            st.executeUpdate(
                "CREATE TABLE note_versions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, noteId INTEGER NOT NULL, " +
                    "title TEXT NOT NULL, body TEXT NOT NULL, tags TEXT NOT NULL DEFAULT '', " +
                    "isChecklist INTEGER NOT NULL DEFAULT 0, checklist TEXT NOT NULL DEFAULT '[]', " +
                    "savedAt INTEGER NOT NULL)"
            )
            st.executeUpdate(
                "CREATE TABLE chat_messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, " +
                    "timestamp INTEGER NOT NULL, attachmentMime TEXT, attachmentData TEXT, attachmentName TEXT, " +
                    "conversationId INTEGER NOT NULL DEFAULT 1, tokens INTEGER NOT NULL DEFAULT 0)"
            )
            st.executeUpdate(
                "CREATE TABLE chat_conversations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, " +
                    "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
            )
            st.execute("PRAGMA user_version=11")
        }
    }

    private fun seedV11Data(conn: Connection) {
        conn.createStatement().use { st ->
            st.executeUpdate(
                "INSERT INTO notes (title, body, updatedAt, tags, trashedAt) VALUES " +
                    "('Groceries', 'milk and eggs', 1700000000000, 'home', NULL), " +
                    "('Trashed note', 'old draft', 1700000001000, '', 1700000002000)"
            )
            st.executeUpdate(
                "INSERT INTO tasks (title, isDone, createdAt, notes, dueAt) VALUES " +
                    "('Pay rent', 0, 1700000000000, 'before the 5th', 1700000003000)"
            )
            st.executeUpdate(
                "INSERT INTO note_versions (noteId, title, body, savedAt) VALUES (1, 'Groceries', 'milk', 1699999999000)"
            )
            st.executeUpdate(
                "INSERT INTO chat_conversations (title, createdAt, updatedAt) VALUES ('Trip chat', 1700000000000, 1700000000000)"
            )
            st.executeUpdate(
                "INSERT INTO chat_messages (role, content, timestamp, conversationId) VALUES " +
                    "('user', 'hello', 1700000000000, 1), ('assistant', 'hi!', 1700000001000, 1)"
            )
        }
    }

    private fun userVersion(conn: Connection): Int =
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA user_version").use { rs -> rs.next(); rs.getInt(1) }
        }

    private fun columnNames(conn: Connection, table: String): Set<String> =
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info($table)").use { rs ->
                buildSet { while (rs.next()) add(rs.getString("name")) }
            }
        }

    private fun tableExists(conn: Connection, name: String): Boolean =
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='$name'"
            ).use { rs -> rs.next(); rs.getInt(1) > 0 }
        }

    @Test
    fun migratesV11ToCurrentPreservingData() {
        val (conn, _) = freshConnection()
        conn.use {
            createV11Schema(conn)
            seedV11Data(conn)
            log.clear()
            Db.runSchemaMigrations(conn) { log.add(it) }

            // The store reached the current schema.
            assertEquals(Db.SCHEMA_VERSION, userVersion(conn))

            // Every table that predates v12 still holds its original rows.
            conn.createStatement().use { st ->
                st.executeQuery("SELECT title, body, trashedAt FROM notes WHERE id=1").use { rs ->
                    rs.next(); assertEquals("Groceries", rs.getString(1)); assertEquals("milk and eggs", rs.getString(2))
                }
                st.executeQuery("SELECT count(*), sum(trashedAt IS NOT NULL) FROM notes").use { rs ->
                    rs.next(); assertEquals(2, rs.getInt(1)); assertEquals(1, rs.getInt(2))
                }
                st.executeQuery("SELECT title, isDone, dueAt FROM tasks WHERE id=1").use { rs ->
                    rs.next(); assertEquals("Pay rent", rs.getString(1)); assertEquals(0, rs.getInt(2)); assertEquals(1700000003000L, rs.getLong(3))
                }
                st.executeQuery("SELECT count(*) FROM chat_messages").use { rs ->
                    rs.next(); assertEquals(2, rs.getInt(1))
                }
                st.executeQuery("SELECT count(*) FROM note_versions").use { rs ->
                    rs.next(); assertEquals(1, rs.getInt(1))
                }
            }

            // v12+ columns arrived on the right tables.
            val noteCols = columnNames(conn, "notes")
            for (c in listOf("manualOrder", "isDraft", "draftSavedAt", "hidden", "isDoodle", "doodle", "bodySpans")) {
                assertTrue(c in noteCols, "notes.$c missing after migration")
            }
            val taskCols = columnNames(conn, "tasks")
            for (c in listOf("manualOrder", "isDraft", "hidden", "notesSpans")) {
                assertTrue(c in taskCols, "tasks.$c missing after migration")
            }
            val chatCols = columnNames(conn, "chat_messages")
            for (c in listOf("replyToId", "attachmentList")) {
                assertTrue(c in chatCols, "chat_messages.$c missing after migration")
            }

            // v12 and v17 tables were created.
            assertTrue(tableExists(conn, "task_versions"))
            assertTrue(tableExists(conn, "notebooks"))
            assertTrue(tableExists(conn, "notebook_items"))

            // New columns carry their defaults for rows written after the upgrade.
            conn.createStatement().use { st ->
                st.executeUpdate("INSERT INTO notes (title, body, updatedAt) VALUES ('new', 'row', 1700000004000)")
                st.executeQuery("SELECT isDraft, hidden, isDoodle, bodySpans, manualOrder FROM notes WHERE id=3").use { rs ->
                    rs.next(); assertEquals(0, rs.getInt(1)); assertEquals(0, rs.getInt(2)); assertEquals(0, rs.getInt(3)); assertEquals("", rs.getString(4)); assertEquals(0, rs.getInt(5))
                }
            }

            // Each step announced itself.
            assertTrue(log.any { it.contains("migrated to schema v12") })
            assertTrue(log.any { it.contains("migrated to schema v17") })
        }
    }

    @Test
    fun walkerIsIdempotentAndSkipsCurrentStores() {
        val (conn, _) = freshConnection()
        conn.use {
            createV11Schema(conn)
            seedV11Data(conn)
            Db.runSchemaMigrations(conn) { log.add(it) }
            assertEquals(Db.SCHEMA_VERSION, userVersion(conn))

            // Re-running on an already-current store must be a silent no-op.
            log.clear()
            Db.runSchemaMigrations(conn) { log.add(it) }
            assertEquals(Db.SCHEMA_VERSION, userVersion(conn))
            assertTrue(log.isEmpty(), "second run produced log lines: $log")

            // Data still intact after the second run.
            conn.createStatement().use { st ->
                st.executeQuery("SELECT count(*) FROM notes").use { rs -> rs.next(); assertEquals(2, rs.getInt(1)) }
            }
        }
    }

    @Test
    fun migratesPartialStoresWithFutureColumnsAlreadyPresent() {
        // A store upgraded by a newer build then reopened by an older one may already carry some
        // v13+ columns while stamped below v17 — every step must survive that (additive guards).
        val (conn, _) = freshConnection()
        conn.use {
            createV11Schema(conn)
            seedV11Data(conn)
            conn.createStatement().use { st ->
                st.executeUpdate("ALTER TABLE notes ADD COLUMN isDoodle INTEGER NOT NULL DEFAULT 0")
                st.executeUpdate("ALTER TABLE notes ADD COLUMN doodle TEXT NOT NULL DEFAULT ''")
                st.executeUpdate("ALTER TABLE chat_messages ADD COLUMN replyToId INTEGER NOT NULL DEFAULT 0")
                st.executeUpdate("PRAGMA user_version=12")
            }
            log.clear()
            Db.runSchemaMigrations(conn) { log.add(it) }
            assertEquals(Db.SCHEMA_VERSION, userVersion(conn))
            // A store that was already at 13's shape is not stamped 13 again — the walker stamps
            // the target of the step it just ran, and addColumnIfMissing returns true immediately.
            conn.createStatement().use { st ->
                st.executeQuery("SELECT title FROM notes WHERE id=1").use { rs ->
                    rs.next(); assertEquals("Groceries", rs.getString(1))
                }
            }
        }
    }
}
