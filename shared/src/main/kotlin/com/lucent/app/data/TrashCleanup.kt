package com.lucent.app.data

import android.content.Context
import com.lucent.app.reminders.ReminderScheduler

object TrashCleanup {

    const val RETENTION_DAYS = 30
    private const val RETENTION_MILLIS = RETENTION_DAYS * 24L * 60 * 60 * 1000

    suspend fun purgeExpired(context: Context) {
        val appContext = context.applicationContext
        val db = AppDatabase.getInstance(appContext)
        val cutoff = System.currentTimeMillis() - RETENTION_MILLIS

        db.noteDao().getAllOnce().forEach { note ->
            val trashedAt = note.trashedAt ?: return@forEach
            if (trashedAt >= cutoff) return@forEach
            purgeNote(appContext, db, note)
        }

        db.taskDao().getAllOnce().forEach { task ->
            val trashedAt = task.trashedAt ?: return@forEach
            if (trashedAt >= cutoff) return@forEach
            purgeTask(appContext, db, task)
        }

        db.noteVersionDao().pruneOrphaned()
        db.taskVersionDao().pruneOrphaned()

    }

    suspend fun purgeNote(context: Context, db: AppDatabase, note: Note) {
        val appContext = context.applicationContext
        Attachments.parse(note.attachments).forEach { att ->
            if (AttachmentStore.looksLikeId(att.data)) AttachmentStore.delete(appContext, att.data)
        }
        NoteHistory.deleteAllFor(db, note.id)
        db.noteDao().delete(note)
    }

    suspend fun purgeTask(context: Context, db: AppDatabase, task: Task) {
        val appContext = context.applicationContext
        Attachments.parse(task.attachments).forEach { att ->
            if (AttachmentStore.looksLikeId(att.data)) AttachmentStore.delete(appContext, att.data)
        }
        ReminderScheduler.cancel(appContext, task.id)
        TaskHistory.deleteAllFor(db, task.id)
        db.taskDao().delete(task)
    }
}
