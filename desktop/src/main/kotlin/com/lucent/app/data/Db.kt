package com.lucent.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

class Db private constructor(private val connection: Connection) {

    private val mutex = Mutex()

    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 64)

    suspend fun <T> use(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block(connection) }
    }

    fun close() {
        runCatching { connection.close() }
    }

    suspend fun <T> write(vararg tables: String, block: (Connection) -> T): T {
        val result = use(block)
        tables.forEach { changes.tryEmit(it) }
        return result
    }

    fun <T> watch(vararg tables: String, query: suspend () -> T): Flow<T> =
        changes
            .filter { it in tables }
            .onStart { emit(tables.first()) }
            .map { query() }
            .distinctUntilChanged()
            .conflate()
            .flowOn(Dispatchers.IO)

    companion object {

        internal const val SCHEMA_VERSION = 24

        fun open(context: Context): Db {
            val file = File(context.filesDir, "lucent.db")
            file.parentFile?.mkdirs()
            Class.forName("org.sqlite.JDBC")
            val conn = openConnection(context, file)
            conn.createStatement().use { st ->
                st.execute("PRAGMA journal_mode=WAL")
                st.execute("PRAGMA foreign_keys=ON")
            }
            createSchema(conn)
            migrateSchema(context, conn)
            createIndices(conn)
            return Db(conn)
        }

        private val PLAINTEXT_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)

        private fun isPlaintextDatabase(file: File): Boolean {
            if (!file.exists() || file.length() < PLAINTEXT_HEADER.size) return false
            val head = ByteArray(PLAINTEXT_HEADER.size)
            file.inputStream().use { if (it.read(head) != head.size) return false }
            return head.contentEquals(PLAINTEXT_HEADER)
        }

        private fun openConnection(context: Context, file: File): Connection {
            val passphrase = try {
                DataKeys.databasePassphrase(context)
            } catch (t: Throwable) {
                EncryptionStatus.reportDatabase(
                    EncryptionStatus.State.PLAINTEXT, "key unavailable: ${t.message}"
                )
                StartupLog.event(context, "db: key unavailable (${t.message}); opening unencrypted")
                return DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
            }
            val hexKey = passphrase.removePrefix("x'").removeSuffix("'")
            if (!hexKey.matches(Regex("[0-9a-fA-F]{64}"))) {
                EncryptionStatus.reportDatabase(
                    EncryptionStatus.State.PLAINTEXT, "key had an unexpected form"
                )
                StartupLog.event(context, "db: key had an unexpected form; opening unencrypted")
                return DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
            }

            if (isPlaintextDatabase(file)) {
                val conn = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
                val core = probeCipherCore(conn)
                val rekeyed = try {
                    conn.createStatement().use { st ->
                        st.execute("PRAGMA journal_mode=DELETE")
                        st.execute("PRAGMA cipher='sqlcipher'")
                        st.execute("PRAGMA legacy=4")
                        st.execute("PRAGMA rekey=\"$passphrase\"")
                    }
                    conn.createStatement().use { it.executeQuery("SELECT count(*) FROM sqlite_master").close() }
                    true
                } catch (t: Throwable) {
                    EncryptionStatus.reportDatabase(
                        EncryptionStatus.State.PLAINTEXT, "in-place encryption failed: ${t.message}"
                    )
                    StartupLog.event(context, "db: in-place encryption failed (${t.message}); opening unencrypted")
                    false
                }
                when {
                    !rekeyed -> Unit
                    core == null ->
                        StartupLog.event(context, "db: cipher core NOT identified by any probe — at-rest encryption may not be active on this driver")
                    else ->
                        StartupLog.event(context, "db: pre-release plaintext database encrypted in place ($core)")
                }
                if (rekeyed && core != null && isPlaintextDatabase(file)) {
                    StartupLog.event(context, "db: WARNING — file header still plaintext after rekey; encryption did NOT engage")
                }
                return conn
            }

            val existed = file.exists()
            val conn = try {
                DriverManager.getConnection(keyedSqliteUrl(file, hexKey))
            } catch (t: Throwable) {
                if (existed) EncryptionStatus.reportDatabase(
                    EncryptionStatus.State.LOCKED_OUT, "existing database rejected this machine's key"
                )
                if (existed) throw IllegalStateException(
                    "The Lucent database at ${file.absolutePath} could not be unlocked with this " +
                        "machine's key. If the key files under ${File(context.filesDir, "keys")} were " +
                        "deleted or replaced, restore from a .lcb backup.", t
                )
                throw t
            }
            val core = probeCipherCore(conn)
            if (core == null) {
                EncryptionStatus.reportDatabase(
                    EncryptionStatus.State.PLAINTEXT, "cipher core not identified by any probe"
                )
                StartupLog.event(context, "db: cipher core NOT identified by any probe — at-rest encryption may not be active on this driver")
            } else {
                EncryptionStatus.reportDatabase(EncryptionStatus.State.ENCRYPTED, core)
            }
            return conn
        }

        private fun migrateSchema(context: Context, conn: Connection) {
            runSchemaMigrations(conn) { StartupLog.event(context, it) }
        }

        internal fun runSchemaMigrations(conn: Connection, eventLog: (String) -> Unit = {}) {
            val current = try {
                conn.createStatement().use { st ->
                    st.executeQuery("PRAGMA user_version").use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                }
            } catch (t: Throwable) {
                eventLog("db: could not read user_version (${t.message}); skipping migrations")
                return
            }
            if (current >= SCHEMA_VERSION) return

            var version = BASE_MIGRATABLE_VERSION

            while (version < SCHEMA_VERSION) {
                val next = version + 1
                val ok = try {
                    when (next) {
                        12 -> {
                            var good = true
                            for ((table, column, decl) in listOf(
                                Triple("notes", "manualOrder", "INTEGER NOT NULL DEFAULT 0"),
                                Triple("notes", "isDraft", "INTEGER NOT NULL DEFAULT 0"),
                                Triple("notes", "draftSavedAt", "INTEGER"),
                                Triple("notes", "hidden", "INTEGER NOT NULL DEFAULT 0"),
                                Triple("tasks", "manualOrder", "INTEGER NOT NULL DEFAULT 0"),
                                Triple("tasks", "isDraft", "INTEGER NOT NULL DEFAULT 0"),
                                Triple("tasks", "draftSavedAt", "INTEGER"),
                                Triple("tasks", "hidden", "INTEGER NOT NULL DEFAULT 0")
                            )) {
                                if (!addColumnIfMissing(conn, table, column, decl)) good = false
                            }
                            conn.createStatement().use { st ->
                                st.executeUpdate(
                                    "CREATE TABLE IF NOT EXISTS task_versions (" +
                                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "taskId INTEGER NOT NULL, " +
                                        "title TEXT NOT NULL, " +
                                        "notes TEXT NOT NULL DEFAULT '', " +
                                        "subtasks TEXT NOT NULL DEFAULT '[]', " +
                                        "priority INTEGER NOT NULL DEFAULT 0, " +
                                        "dueAt INTEGER, " +
                                        "savedAt INTEGER NOT NULL)"
                                )
                                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_task_versions_taskId ON task_versions (taskId)")
                                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_notes_isDraft ON notes (isDraft)")
                                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_notes_hidden ON notes (hidden)")
                                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_tasks_isDraft ON tasks (isDraft)")
                                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_tasks_hidden ON tasks (hidden)")
                            }
                            good
                        }
                        13 -> addColumnIfMissing(conn, "notes", "isDoodle", "INTEGER NOT NULL DEFAULT 0") &&
                            addColumnIfMissing(conn, "notes", "doodle", "TEXT NOT NULL DEFAULT ''")
                        14 -> addColumnIfMissing(conn, "chat_messages", "replyToId", "INTEGER NOT NULL DEFAULT 0")
                        15 -> addColumnIfMissing(conn, "notes", "bodySpans", "TEXT NOT NULL DEFAULT ''") &&
                            addColumnIfMissing(conn, "tasks", "notesSpans", "TEXT NOT NULL DEFAULT ''")
                        16 -> addColumnIfMissing(conn, "chat_messages", "attachmentList", "TEXT")
                        17 -> {
                            conn.createStatement().use { st ->
                                st.executeUpdate(
                                    "CREATE TABLE IF NOT EXISTS notebooks (" +
                                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "title TEXT NOT NULL, " +
                                        "createdAt INTEGER NOT NULL, " +
                                        "updatedAt INTEGER NOT NULL)"
                                )
                                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_notebooks_updatedAt ON notebooks (updatedAt)")
                                st.executeUpdate(
                                    "CREATE TABLE IF NOT EXISTS notebook_items (" +
                                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "notebookId INTEGER NOT NULL, " +
                                        "itemKind TEXT NOT NULL, " +
                                        "itemId INTEGER NOT NULL, " +
                                        "addedAt INTEGER NOT NULL)"
                                )
                                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_notebook_items_notebookId ON notebook_items (notebookId)")
                                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_notebook_items_itemKind_itemId ON notebook_items (itemKind, itemId)")
                            }
                            true
                        }
                        18 -> {
                            conn.createStatement().use { st ->
                                st.executeUpdate(
                                    "CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts5(" +
                                        "title, body, content='notes', content_rowid='id')"
                                )
                                st.executeUpdate(
                                    "CREATE VIRTUAL TABLE IF NOT EXISTS tasks_fts USING fts5(" +
                                        "title, notes, content='tasks', content_rowid='id')"
                                )
                                st.executeUpdate(
                                    "CREATE TRIGGER IF NOT EXISTS notes_fts_ai AFTER INSERT ON notes BEGIN " +
                                        "INSERT INTO notes_fts(rowid, title, body) VALUES (new.id, new.title, new.body); END"
                                )
                                st.executeUpdate(
                                    "CREATE TRIGGER IF NOT EXISTS notes_fts_ad AFTER DELETE ON notes BEGIN " +
                                        "INSERT INTO notes_fts(notes_fts, rowid, title, body) VALUES ('delete', old.id, old.title, old.body); END"
                                )
                                st.executeUpdate(
                                    "CREATE TRIGGER IF NOT EXISTS notes_fts_au AFTER UPDATE ON notes BEGIN " +
                                        "INSERT INTO notes_fts(notes_fts, rowid, title, body) VALUES ('delete', old.id, old.title, old.body); " +
                                        "INSERT INTO notes_fts(rowid, title, body) VALUES (new.id, new.title, new.body); END"
                                )
                                st.executeUpdate(
                                    "CREATE TRIGGER IF NOT EXISTS tasks_fts_ai AFTER INSERT ON tasks BEGIN " +
                                        "INSERT INTO tasks_fts(rowid, title, notes) VALUES (new.id, new.title, new.notes); END"
                                )
                                st.executeUpdate(
                                    "CREATE TRIGGER IF NOT EXISTS tasks_fts_ad AFTER DELETE ON tasks BEGIN " +
                                        "INSERT INTO tasks_fts(tasks_fts, rowid, title, notes) VALUES ('delete', old.id, old.title, old.notes); END"
                                )
                                st.executeUpdate(
                                    "CREATE TRIGGER IF NOT EXISTS tasks_fts_au AFTER UPDATE ON tasks BEGIN " +
                                        "INSERT INTO tasks_fts(tasks_fts, rowid, title, notes) VALUES ('delete', old.id, old.title, old.notes); " +
                                        "INSERT INTO tasks_fts(rowid, title, notes) VALUES (new.id, new.title, new.notes); END"
                                )
                                st.executeUpdate("INSERT INTO notes_fts(notes_fts) VALUES('rebuild')")
                                st.executeUpdate("INSERT INTO tasks_fts(tasks_fts) VALUES('rebuild')")
                            }
                            true
                        }
                        19 -> {
                            conn.createStatement().use { st ->
                                st.executeUpdate(
                                    "CREATE TABLE IF NOT EXISTS note_embeddings (" +
                                        "noteId INTEGER NOT NULL, model TEXT NOT NULL, dim INTEGER NOT NULL, " +
                                        "vec BLOB NOT NULL, updatedAt INTEGER NOT NULL, " +
                                        "PRIMARY KEY (noteId, model))"
                                )
                                st.executeUpdate(
                                    "CREATE TRIGGER IF NOT EXISTS note_embeddings_cleanup AFTER DELETE ON notes BEGIN " +
                                        "DELETE FROM note_embeddings WHERE noteId = old.id; END"
                                )
                            }
                            true
                        }
                        20 -> addColumnIfMissing(conn, "chat_messages", "agentTrace", "TEXT")
                        21 -> addColumnIfMissing(conn, "notes", "formatOverride", "TEXT") &&
                            addColumnIfMissing(conn, "tasks", "formatOverride", "TEXT")
                        22 -> addColumnIfMissing(conn, "notebooks", "color", "TEXT NOT NULL DEFAULT ''") &&
                            addColumnIfMissing(conn, "notebooks", "manualOrder", "INTEGER NOT NULL DEFAULT 0") &&
                            addColumnIfMissing(conn, "notebooks", "trashedAt", "INTEGER")
                        23 -> addColumnIfMissing(conn, "notebooks", "pinned", "INTEGER NOT NULL DEFAULT 0")
                        24 -> addColumnIfMissing(conn, "chat_messages", "reasoningBlocks", "TEXT") &&
                            addColumnIfMissing(conn, "chat_messages", "reasoningText", "TEXT")
                        else -> true
                    }
                } catch (t: Throwable) {
                    eventLog("db: migration to v$next FAILED (${t.message}); will retry next launch")
                    false
                }
                if (!ok) return
                try {
                    conn.createStatement().use { it.executeUpdate("PRAGMA user_version=$next") }
                } catch (t: Throwable) {
                    eventLog("db: could not stamp user_version=$next (${t.message})")
                    return
                }
                eventLog("db: migrated to schema v$next")
                version = next
            }
        }

        internal const val BASE_MIGRATABLE_VERSION = 11

        private fun addColumnIfMissing(
            conn: Connection,
            table: String,
            column: String,
            definition: String
        ): Boolean {
            val present = try {
                conn.createStatement().use { st ->
                    st.executeQuery("PRAGMA table_info($table)").use { rs ->
                        generateSequence { if (rs.next()) rs.getString("name") else null }
                            .any { it.equals(column, ignoreCase = true) }
                    }
                }
            } catch (t: Throwable) {
                return false
            }
            if (present) return true
            return try {
                conn.createStatement().use { it.executeUpdate("ALTER TABLE $table ADD COLUMN $column $definition") }
                true
            } catch (t: Throwable) {
                false
            }
        }

        private fun createIndices(conn: Connection) {
            conn.createStatement().use { st ->
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_notes_updatedAt ON notes (updatedAt)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_notes_archived ON notes (archived)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_notes_trashedAt ON notes (trashedAt)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_tasks_createdAt ON tasks (createdAt)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_tasks_isDone ON tasks (isDone)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_tasks_trashedAt ON tasks (trashedAt)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_note_versions_noteId ON note_versions (noteId)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_task_versions_taskId ON task_versions (taskId)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_notes_isDraft ON notes (isDraft)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_notes_hidden ON notes (hidden)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_tasks_isDraft ON tasks (isDraft)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_tasks_hidden ON tasks (hidden)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_notebooks_updatedAt ON notebooks (updatedAt)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_notebook_items_notebookId ON notebook_items (notebookId)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS index_notebook_items_itemKind_itemId ON notebook_items (itemKind, itemId)")
            }
        }

        private fun createSchema(conn: Connection) {
            conn.createStatement().use { st ->
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS notes (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "body TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "tags TEXT NOT NULL DEFAULT '', " +
                        "attachments TEXT NOT NULL DEFAULT '[]', " +
                        "archived INTEGER NOT NULL DEFAULT 0, " +
                        "archivedAt INTEGER, " +
                        "pinned INTEGER NOT NULL DEFAULT 0, " +
                        "color TEXT NOT NULL DEFAULT '', " +
                        "isChecklist INTEGER NOT NULL DEFAULT 0, " +
                        "checklist TEXT NOT NULL DEFAULT '[]', " +
                        "trashedAt INTEGER, " +
                        "manualOrder INTEGER NOT NULL DEFAULT 0, " +
                        "isDraft INTEGER NOT NULL DEFAULT 0, " +
                        "draftSavedAt INTEGER, " +
                        "hidden INTEGER NOT NULL DEFAULT 0, " +
                        "isDoodle INTEGER NOT NULL DEFAULT 0, " +
                        "doodle TEXT NOT NULL DEFAULT '', " +
                        "bodySpans TEXT NOT NULL DEFAULT '', " +
                        "formatOverride TEXT)"
                )
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS tasks (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "isDone INTEGER NOT NULL DEFAULT 0, " +
                        "createdAt INTEGER NOT NULL, " +
                        "attachments TEXT NOT NULL DEFAULT '[]', " +
                        "dueAt INTEGER, " +
                        "notes TEXT NOT NULL DEFAULT '', " +
                        "completedAt INTEGER, " +
                        "priority INTEGER NOT NULL DEFAULT 0, " +
                        "pinned INTEGER NOT NULL DEFAULT 0, " +
                        "subtasks TEXT NOT NULL DEFAULT '[]', " +
                        "repeatRule TEXT NOT NULL DEFAULT 'NONE', " +
                        "reminderEnabled INTEGER NOT NULL DEFAULT 0, " +
                        "trashedAt INTEGER, " +
                        "manualOrder INTEGER NOT NULL DEFAULT 0, " +
                        "isDraft INTEGER NOT NULL DEFAULT 0, " +
                        "draftSavedAt INTEGER, " +
                        "hidden INTEGER NOT NULL DEFAULT 0, " +
                        "notesSpans TEXT NOT NULL DEFAULT '', " +
                        "formatOverride TEXT)"
                )
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS note_versions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "noteId INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "body TEXT NOT NULL, " +
                        "tags TEXT NOT NULL DEFAULT '', " +
                        "isChecklist INTEGER NOT NULL DEFAULT 0, " +
                        "checklist TEXT NOT NULL DEFAULT '[]', " +
                        "savedAt INTEGER NOT NULL)"
                )
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS task_versions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "taskId INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "notes TEXT NOT NULL DEFAULT '', " +
                        "subtasks TEXT NOT NULL DEFAULT '[]', " +
                        "priority INTEGER NOT NULL DEFAULT 0, " +
                        "dueAt INTEGER, " +
                        "savedAt INTEGER NOT NULL)"
                )
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS chat_messages (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "role TEXT NOT NULL, " +
                        "content TEXT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "attachmentMime TEXT, " +
                        "attachmentData TEXT, " +
                        "attachmentName TEXT, " +
                        "conversationId INTEGER NOT NULL DEFAULT 1, " +
                        "tokens INTEGER NOT NULL DEFAULT 0, " +
                        "replyToId INTEGER NOT NULL DEFAULT 0, " +
                        "agentTrace TEXT)"
                )
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS chat_conversations (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS notebooks (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "color TEXT NOT NULL DEFAULT '', " +
                        "manualOrder INTEGER NOT NULL DEFAULT 0, " +
                        "trashedAt INTEGER, " +
                        "pinned INTEGER NOT NULL DEFAULT 0)"
                )
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS notebook_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "notebookId INTEGER NOT NULL, " +
                        "itemKind TEXT NOT NULL, " +
                        "itemId INTEGER NOT NULL, " +
                        "addedAt INTEGER NOT NULL)"
                )
                st.executeUpdate(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts5(" +
                        "title, body, content='notes', content_rowid='id')"
                )
                st.executeUpdate(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS tasks_fts USING fts5(" +
                        "title, notes, content='tasks', content_rowid='id')"
                )
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS note_embeddings (" +
                        "noteId INTEGER NOT NULL, model TEXT NOT NULL, dim INTEGER NOT NULL, " +
                        "vec BLOB NOT NULL, updatedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY (noteId, model))"
                )
                st.executeUpdate(
                    "CREATE TRIGGER IF NOT EXISTS note_embeddings_cleanup AFTER DELETE ON notes BEGIN " +
                        "DELETE FROM note_embeddings WHERE noteId = old.id; END"
                )
            }
        }
    }
}


internal fun keyedSqliteUrl(file: File, hexKey: String): String {
    val p = file.absolutePath.replace('\\', '/')
        .replace("%", "%25").replace("?", "%3F").replace("#", "%23").replace(" ", "%20")
    return "jdbc:sqlite:file:$p?cipher=sqlcipher&legacy=4&hexkey=$hexKey"
}

internal fun probeCipherCore(conn: Connection): String? {
    val probes = arrayOf(
        "SELECT sqlite3mc_version()",
        "PRAGMA cipher",
        "PRAGMA cipher_version"
    )
    for (sql in probes) {
        try {
            conn.createStatement().use { st ->
                st.executeQuery(sql).use { rs ->
                    if (rs.next()) {
                        val value = rs.getString(1)
                        if (!value.isNullOrBlank()) return "$sql -> $value"
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }
    return null
}


internal fun PreparedStatement.bindLongOrNull(index: Int, value: Long?) {
    if (value == null) setNull(index, java.sql.Types.INTEGER) else setLong(index, value)
}

internal fun PreparedStatement.bindStringOrNull(index: Int, value: String?) {
    if (value == null) setNull(index, java.sql.Types.VARCHAR) else setString(index, value)
}

internal fun ResultSet.longOrNull(column: String): Long? {
    val v = getLong(column)
    return if (wasNull()) null else v
}

internal fun ResultSet.stringOrNull(column: String): String? = getString(column)

internal fun <T> ResultSet.mapAll(mapper: (ResultSet) -> T): List<T> {
    val out = ArrayList<T>()
    while (next()) out.add(mapper(this))
    close()
    return out
}
