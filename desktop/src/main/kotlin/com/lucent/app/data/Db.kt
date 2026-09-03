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

/**
 * The desktop persistence core: one SQLite connection, serialized access, and Room-style
 * "invalidation" flows so the DAO surface in Daos.kt can offer the exact same reactive API the
 * Android app gets from Room.
 *
 * ### Encryption at rest
 *
 * The bundled JDBC driver is `io.github.willena:sqlite-jdbc` — a drop-in build of the Xerial driver
 * whose SQLite core is SQLite3MultipleCiphers, which speaks the **SQLCipher** scheme. The database
 * is keyed with the same raw-key form the Android build feeds SQLCipher (`x'<64 hex>'`, minted and
 * stored by [DataKeys]), so the desktop database enjoys the same at-rest encryption the phone has.
 * If the cipher PRAGMAs are ever unavailable (a swapped-in plain driver), the store degrades to an
 * unencrypted file with a loud [StartupLog] entry rather than refusing to start — the same
 * "degrade one notch, never strand the data" policy the Android key handling follows.
 *
 * ### Why one connection and a mutex
 *
 * SQLite serializes writers anyway; funnelling every statement through one connection guarded by a
 * [Mutex] makes cross-thread misuse impossible by construction (the same reasoning LocalLlm applies
 * to its native session) and keeps transactions trivial. All calls run on [Dispatchers.IO].
 */
class Db private constructor(private val connection: Connection) {

    private val mutex = Mutex()

    // Table-change bus. extraBufferCapacity keeps emitters from suspending; watchers conflate, so a
    // burst of writes collapses into one re-query — exactly Room's invalidation behaviour.
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** Run [block] with exclusive access to the connection, off the caller's thread. */
    suspend fun <T> use(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block(connection) }
    }

    /** Run [block] like [use], then announce that [tables] changed so watchers re-query. */
    suspend fun <T> write(vararg tables: String, block: (Connection) -> T): T {
        val result = use(block)
        tables.forEach { changes.tryEmit(it) }
        return result
    }

    /**
     * A cold flow that emits [query]'s result immediately and again whenever any of [tables]
     * changes. Conflated and de-duplicated, mirroring Room's Flow queries closely enough that the
     * shared screens can't tell the difference.
     */
    fun <T> watch(vararg tables: String, query: suspend () -> T): Flow<T> =
        changes
            .filter { it in tables }
            .onStart { emit(tables.first()) }
            .map { query() }
            .distinctUntilChanged()
            .conflate()
            .flowOn(Dispatchers.IO)

    companion object {

        /**
         * Schema version this build writes. Matches the Android Room schema (version 15).
         *
         * INTEGRATION NOTE: group A took 12 and 13 (drafts / hidden / manual order / task history /
         * doodle) and group B took a second 12 (chat_messages.replyToId). B's step was renumbered
         * to 14 so the two chains no longer collide; see [migrateSchema] and AppDatabase.kt.
         */
        private const val SCHEMA_VERSION = 16

        fun open(context: Context): Db {
            val file = File(context.applicationContext.filesDir, "lucent.db")
            file.parentFile?.mkdirs()
            // Load the driver class explicitly so a missing dependency fails with a clear message.
            Class.forName("org.sqlite.JDBC")
            val conn = openConnection(context, file)
            conn.createStatement().use { st ->
                st.execute("PRAGMA journal_mode=WAL")
                st.execute("PRAGMA foreign_keys=ON")
            }
            createSchema(conn)
            // An EXISTING store gets its new columns here — createSchema cannot, see its closing
            // comment. Group A's introspective upgrade and group B's versioned walker were merged
            // into this one function during integration.
            migrateSchema(context, conn)
            return Db(conn)
        }

        /** The 16-byte header every UNENCRYPTED SQLite file starts with; an encrypted page 1 never has it. */
        private val PLAINTEXT_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)

        /** True when [file] exists and carries the plain-SQLite header — i.e. a legacy unencrypted store. */
        private fun isPlaintextDatabase(file: File): Boolean {
            if (!file.exists() || file.length() < PLAINTEXT_HEADER.size) return false
            val head = ByteArray(PLAINTEXT_HEADER.size)
            file.inputStream().use { if (it.read(head) != head.size) return false }
            return head.contentEquals(PLAINTEXT_HEADER)
        }

        /**
         * Open the JDBC connection with at-rest encryption in force.
         *
         * The hard-won rule (CI red of 2026-07-22 09:05): this driver's connection constructor runs
         * `SQLiteConfig.apply` — a batch of init pragmas — the moment the file opens, and preparing
         * the first of them against an encrypted database already dies with SQLITE_NOTADB. A key
         * applied by a post-connect `PRAGMA key` therefore arrives TOO LATE for any database that
         * is already encrypted; it only ever worked for brand-new files. The key must travel IN THE
         * URL: SQLite3MultipleCiphers reads its `cipher`, `legacy`, and `hexkey` URI parameters
         * during open itself, so page 1 is decryptable before the driver's own pragmas run — see
         * [keyedSqliteUrl], which the CI gate (CipherSelfCheck) shares, so the build proves the
         * exact mechanism the app uses.
         *
         * Cases, told apart by the FILE HEADER before anything opens — a plaintext SQLite file
         * always begins "SQLite format 3\0", an encrypted one never does:
         *
         *  1. **Fresh or already-encrypted file** — open with the keyed URL. A connect failure on
         *     an EXISTING file means a genuinely wrong key (the header already ruled out "it's
         *     plaintext"), rethrown with a message a person can act on; a brand-new file failing to
         *     CREATE is a driver problem, not a key problem, and is surfaced raw.
         *  2. **Plaintext file** — written by a pre-release build from the brief org.xerial era;
         *     nothing was ever published, so it can only be a developer's own working data. Opened
         *     plain (a keyed open would refuse it), then encrypted IN PLACE, once, silently: leave
         *     WAL (rekey refuses a WAL database), set the cipher shape, then `PRAGMA rekey`. Data
         *     is never copied out or set aside; a failed rekey degrades to plaintext with a loud
         *     [StartupLog] line rather than stranding the store.
         *  3. **Diagnostics** — [probeCipherCore] identifies the cipher core for the LOG LINES
         *     only; behaviour never depends on it. An unidentified core, or a header still
         *     plaintext after a rekey, is announced loudly so nobody mistakes a plaintext store
         *     for an encrypted one.
         */
        private fun openConnection(context: Context, file: File): Connection {
            val passphrase = try {
                DataKeys.databasePassphrase(context) // "x'<64 hex>'"
            } catch (t: Throwable) {
                // C-group task 17: a degradation to plaintext must be RECORDED, not just logged
                // to a file the user may not have enabled. EncryptionStatus drives the Settings
                // banner too, so this is visible without reading a log at all.
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
                // Case 2: pre-release plaintext store — open plain, encrypt in place, keep going.
                val conn = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
                val core = probeCipherCore(conn)
                val rekeyed = try {
                    conn.createStatement().use { st ->
                        // rekey refuses WAL, and prior (unencrypted) runs always left WAL on;
                        // switching first also checkpoints any leftover -wal file.
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
                    !rekeyed -> Unit // already logged; the data stays reachable, exactly as before
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

            // Case 1: fresh file, or an existing encrypted store — the key rides the URL.
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

        /**
         * Create every table and index at the shape Room's schema v11 has on Android — same names,
         * same columns, same defaults — so the two stores stay structurally interchangeable.
         * Idempotent: IF NOT EXISTS everywhere, and user_version records what is on disk.
         */
        /**
         * Bring an EXISTING database up to [SCHEMA_VERSION] — the desktop counterpart of Room's
         * migration chain on Android.
         *
         * ### Why this had to exist
         *
         * `createSchema` is `CREATE TABLE IF NOT EXISTS` throughout, which is exactly right for a
         * fresh install and does precisely nothing for an existing one. Adding a column to a table
         * that already exists was therefore impossible on desktop: the new column would appear on
         * new installs and be silently missing on every upgrade, and the first query naming it
         * would throw. `PRAGMA user_version` was already being written but never read.
         *
         * ### Integration note (three chains became one)
         *
         * Group B introduced the versioned walker; group A independently introduced an
         * introspection-based `upgradeSchema`. They are merged here, keeping the strengths of both:
         * the walker's structure (adding a future step is one `when` branch) and the
         * introspection's safety (every step checks the store before it changes it).
         *
         * Two consequences of the merge, both deliberate:
         *
         *  1. **The stamp is a fast path, not the source of truth.** Any store not already at
         *     [SCHEMA_VERSION] replays *every* step from 11, and every step is guarded by
         *     `PRAGMA table_info` / `IF NOT EXISTS`. B's original rule — "a store stamped 0 is
         *     current, run only the newest step" — was correct while exactly one step existed and
         *     becomes wrong the moment there are three: a store written by a build that had only
         *     some of them would skip the rest forever. Replaying guarded steps costs a handful of
         *     pragmas per launch and cannot be wrong.
         *  2. **createSchema no longer stamps.** It used to stamp unconditionally at the end, which
         *     on an existing store meant "did nothing, then declared itself up to date" — the store
         *     would be missing columns and permanently marked as migrated. The stamp lives here now,
         *     written only after the steps have actually run.
         *
         * ### Failure policy
         *
         * A failed step is logged and swallowed, and the version is NOT stamped, so the next launch
         * tries again. It never throws: refusing to start is a far worse outcome than running one
         * version behind, and every column added here is optional at each of its read sites.
         */
        private fun migrateSchema(context: Context, conn: Connection) {
            val current = try {
                conn.createStatement().use { st ->
                    st.executeQuery("PRAGMA user_version").use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                }
            } catch (t: Throwable) {
                StartupLog.event(context, "db: could not read user_version (${t.message}); skipping migrations")
                return
            }
            if (current >= SCHEMA_VERSION) return

            // See point 1 above: the stamp only tells us whether we can skip entirely. Once we are
            // running at all, we run every guarded step, whatever the stamp claimed.
            var version = BASE_MIGRATABLE_VERSION

            while (version < SCHEMA_VERSION) {
                val next = version + 1
                val ok = try {
                    when (next) {
                        // v12 (group A): manual order, drafts, hidden items, task revision history.
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
                            // The table and indices are IF NOT EXISTS, so replaying is a no-op.
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
                        // v13 (group A): doodle notes.
                        13 -> addColumnIfMissing(conn, "notes", "isDoodle", "INTEGER NOT NULL DEFAULT 0") &&
                            addColumnIfMissing(conn, "notes", "doodle", "TEXT NOT NULL DEFAULT ''")
                        // v14 (group B, originally numbered 12): chat_messages.replyToId — pairs a
                        // reply to the question it answers, so alternative answers can be grouped.
                        14 -> addColumnIfMissing(conn, "chat_messages", "replyToId", "INTEGER NOT NULL DEFAULT 0")
                        // v15 (C group task 20, wired up during integration): rich-text sidecars.
                        15 -> addColumnIfMissing(conn, "notes", "bodySpans", "TEXT NOT NULL DEFAULT ''") &&
                            addColumnIfMissing(conn, "tasks", "notesSpans", "TEXT NOT NULL DEFAULT ''")
                        // v16 (R3 task #15): multi-attachment chat messages — a nullable JSON list
                        // beside the legacy single-attachment trio (see ChatMessage.allAttachments).
                        16 -> addColumnIfMissing(conn, "chat_messages", "attachmentList", "TEXT")
                        else -> true   // no step for this version
                    }
                } catch (t: Throwable) {
                    StartupLog.event(context, "db: migration to v$next FAILED (${t.message}); will retry next launch")
                    false
                }
                if (!ok) return
                try {
                    conn.createStatement().use { it.executeUpdate("PRAGMA user_version=$next") }
                } catch (t: Throwable) {
                    StartupLog.event(context, "db: could not stamp user_version=$next (${t.message})")
                    return
                }
                StartupLog.event(context, "db: migrated to schema v$next")
                version = next
            }
        }

        /**
         * The version every replay starts from. 11 is the last shape that shipped before this
         * release, and every step from here on is written to survive being applied to a store that
         * already has it — so starting low is free and starting high can strand a column.
         */
        private const val BASE_MIGRATABLE_VERSION = 11

        /** Add [column] to [table] unless it is already there. Returns false only on a real failure. */
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
                        // 1.1.0 group A (tasks A16 / A10 / A21)
                        "manualOrder INTEGER NOT NULL DEFAULT 0, " +
                        "isDraft INTEGER NOT NULL DEFAULT 0, " +
                        "draftSavedAt INTEGER, " +
                        "hidden INTEGER NOT NULL DEFAULT 0, " +
                        "isDoodle INTEGER NOT NULL DEFAULT 0, " +
                        "doodle TEXT NOT NULL DEFAULT '', " +
                        // INTEGRATION (C task 20) — rich-text sidecar; see data/RichText.kt.
                        "bodySpans TEXT NOT NULL DEFAULT '')"
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
                        "notesSpans TEXT NOT NULL DEFAULT '')"
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
                // Task A19 — task revision history, mirroring note_versions.
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
                        // B-group task 12 — see ChatMessage.replyToId.
                        "replyToId INTEGER NOT NULL DEFAULT 0)"
                )
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS chat_conversations (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
                // Same list-query indices Room's schema carries, under Room's own names.
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
                // The version stamp moved to migrateSchema, which is the only place that knows the
                // store is genuinely at the current shape. Stamping here would mark an existing
                // database as up to date after doing nothing to it — every statement above is
                // CREATE ... IF NOT EXISTS, which no-ops against tables that already exist.
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cipher-core probe — shared by Db.openConnection (for its log lines) and the CI gate
// (com.lucent.desktop.CipherSelfCheck), so the build verifies the exact probe the app runs.
// ---------------------------------------------------------------------------------------------

/**
 * Try to positively identify the SQLite3MultipleCiphers core behind [conn]; null when it can't be.
 *
 * Written the hard way after the 2026-07-22 08:36 CI red taught two lessons at once: the cipher
 * core's vocabulary is not SQLCipher's (`PRAGMA cipher_version` goes unanswered), and this JDBC
 * driver's `executeQuery` THROWS ("query does not return ResultSet") for any statement yielding
 * zero columns instead of returning an empty set — which is also what every unknown pragma yields
 * on stock SQLite. So: several vocabularies, most reliable first, each attempt individually
 * caught, and "threw" and "no value" both simply mean "this probe didn't identify it". A null
 * result is "unidentified", not proof of absence — callers treat it as a reason to WARN, never as
 * a reason to skip the keying attempt (which is a harmless no-op on a plain driver).
 */
/**
 * The JDBC URL that hands SQLite3MultipleCiphers its key AT OPEN TIME, before the driver's own
 * init pragmas run (`SQLiteConfig.apply` prepares statements during the connection constructor —
 * the 2026-07-22 09:05 CI red). `hexkey` takes the raw 64-hex key without quoting acrobatics;
 * `cipher`/`legacy` pin the SQLCipher-compatible on-disk shape, matching Android. The path is
 * percent-encoded just enough for SQLite's URI parser (%, ?, #, and spaces), and backslashes
 * become the forward slashes URIs expect. Shared by Db and the CI gate so both key identically.
 */
internal fun keyedSqliteUrl(file: File, hexKey: String): String {
    val p = file.absolutePath.replace('\\', '/')
        .replace("%", "%25").replace("?", "%3F").replace("#", "%23").replace(" ", "%20")
    return "jdbc:sqlite:file:$p?cipher=sqlcipher&legacy=4&hexkey=$hexKey"
}

internal fun probeCipherCore(conn: Connection): String? {
    val probes = arrayOf(
        "SELECT sqlite3mc_version()", // the MC core's own SQL function
        "PRAGMA cipher",              // MC answers with the current cipher's name
        "PRAGMA cipher_version"       // SQLCipher's word, kept for compat builds that adopt it
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
            // Unanswered in this dialect, or the driver threw on a zero-column statement: try the next.
        }
    }
    return null
}

// ---------------------------------------------------------------------------------------------
// Small JDBC helpers shared by the DAOs. Kept here so the DAO bodies read as query + mapping.
// ---------------------------------------------------------------------------------------------

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
