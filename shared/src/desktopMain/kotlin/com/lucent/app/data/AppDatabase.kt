package com.lucent.app.data
import com.lucent.app.platform.applicationContext
import com.lucent.app.platform.filesDir

import com.lucent.app.platform.PlatformContext
import java.io.File

class DesktopAppDatabase private constructor(private val db: Db) : AppDatabase {

    override val noteDao: NoteDao = DesktopNoteDao(db)
    override val noteEmbeddingDao: NoteEmbeddingDao = DesktopNoteEmbeddingDao(db)
    override val noteVersionDao: NoteVersionDao = DesktopNoteVersionDao(db)
    override val taskVersionDao: TaskVersionDao = DesktopTaskVersionDao(db)
    override val taskDao: TaskDao = DesktopTaskDao(db)
    override val chatDao: ChatDao = DesktopChatDao(db)
    override val chatConversationDao: ChatConversationDao = DesktopChatConversationDao(db)
    override val notebookDao: NotebookDao = DesktopNotebookDao(db)

    companion object {
        @Volatile private var INSTANCE: DesktopAppDatabase? = null

        @Volatile private var INSTANCE_PATH: String? = null

        private fun resolvedPath(context: PlatformContext): String =
            (context.applicationContext.filesDir / "lucent.db").toString()

        fun getInstance(context: PlatformContext): DesktopAppDatabase {
            val path = resolvedPath(context)
            INSTANCE?.let { if (INSTANCE_PATH == path) return it }
            synchronized(this) {
                INSTANCE?.let { if (INSTANCE_PATH == path) return it }
                val created = DesktopAppDatabase(Db.open(context.applicationContext))
                INSTANCE = created
                INSTANCE_PATH = path
                return created
            }
        }

        fun createForTesting(context: PlatformContext): DesktopAppDatabase =
            DesktopAppDatabase(Db.open(context.applicationContext))
    }
}

actual fun createAppDatabase(context: PlatformContext): AppDatabase {
    return DesktopAppDatabase.getInstance(context)
}
