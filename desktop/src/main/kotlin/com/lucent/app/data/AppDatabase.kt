package com.lucent.app.data

import android.content.Context
import java.io.File

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

        @Volatile private var INSTANCE_PATH: String? = null

        private fun resolvedPath(context: Context): String =
            File(context.applicationContext.filesDir, "lucent.db").absolutePath

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

        internal fun createForTesting(context: Context): AppDatabase =
            AppDatabase(Db.open(context.applicationContext))
    }
}
