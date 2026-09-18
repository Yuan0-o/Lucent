package com.lucent.app.data

import android.content.Context
import java.io.File

/**
 * Desktop twin of the Android AppDatabase: same class name, same dao accessors, same
 * `getInstance(context)` singleton — but backed by the hand-rolled SQLite layer in [Db] instead of
 * Room. Everything above this line of the stack (tools, backup, screens) is unaware of the swap.
 */
class AppDatabase private constructor(db: Db) {

    private val notes = NoteDao(db)
    private val tasks = TaskDao(db)
    private val versions = NoteVersionDao(db)
    private val taskVersions = TaskVersionDao(db)
    private val chats = ChatDao(db)
    private val conversations = ChatConversationDao(db)
    private val notebooks = NotebookDao(db)
    private val noteEmbeddings = NoteEmbeddingDao(db)

    fun noteDao(): NoteDao = notes
    fun taskDao(): TaskDao = tasks
    fun noteVersionDao(): NoteVersionDao = versions
    fun taskVersionDao(): TaskVersionDao = taskVersions
    fun chatDao(): ChatDao = chats
    fun chatConversationDao(): ChatConversationDao = conversations
    fun notebookDao(): NotebookDao = notebooks
    fun noteEmbeddingDao(): NoteEmbeddingDao = noteEmbeddings

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // The absolute path INSTANCE was opened against. getInstance() below is a real cache keyed
        // on this path, not a "first context wins forever" latch — see its KDoc for why that
        // distinction is load-bearing and not just cosmetic.
        @Volatile private var INSTANCE_PATH: String? = null

        /** The file [Db.open] resolves for [context] — mirrors [Db]'s own private path logic. */
        private fun resolvedPath(context: Context): String =
            File(context.applicationContext.filesDir, "lucent.db").absolutePath

        /**
         * The shared instance for [context]'s database file, opening (and caching) a new one the
         * first time a given path is seen.
         *
         * This used to be a plain "first caller wins" singleton: once any [AppDatabase] existed,
         * every later call returned it regardless of [context]. That is harmless in the real app —
         * one process has exactly one `filesDir` for its whole lifetime — but it quietly broke test
         * isolation. [EmbeddingStore]'s functions call [getInstance] directly rather than taking a
         * database instance, so any test that also opens its own instance via [createForTesting] to
         * assert against ends up split across two different databases: its own inserts land in the
         * instance it holds, while [EmbeddingStore] silently keeps using whichever database the
         * first [getInstance] call in the whole test JVM happened to open. Keying the cache on the
         * resolved path — instead of just "does a cached instance exist at all" — makes each
         * distinct `filesDir` (each test's own temp directory) get its own instance, while a real
         * app process, which only ever resolves one path, still opens its database exactly once.
         */
        fun getInstance(context: Context): AppDatabase {
            val path = resolvedPath(context)
            INSTANCE?.let { if (INSTANCE_PATH == path) return it }
            synchronized(this) {
                INSTANCE?.let { if (INSTANCE_PATH == path) return it }
                val created = AppDatabase(Db.open(context.applicationContext))
                INSTANCE = created
                INSTANCE_PATH = path
                return created
            }
        }

        /**
         * Test seam (P0-4): a fresh instance for a specific context, bypassing the process-wide
         * singleton — each BackupRoundTripTest store must be its own database.
         */
        internal fun createForTesting(context: Context): AppDatabase =
            AppDatabase(Db.open(context.applicationContext))
    }
}
