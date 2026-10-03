package com.lucent.app.data

import com.lucent.app.data.createAppDatabase

import com.lucent.app.platform.PlatformContext
import com.lucent.app.reminders.ReminderScheduler

object TrashCleanup {

    const val RETENTION_DAYS = 30
    private const val RETENTION_MILLIS = RETENTION_DAYS * 24L * 60 * 60 * 1000

    suspend fun purgeExpired(context: PlatformContext) {
                val db = createAppDatabase(context)
        val cutoff = System.currentTimeMillis() - RETENTION_MILLIS

        db.noteDao.getAllOnce().forEach { note ->
            val trashedAt = note.trashedAt ?: return@forEach
            if (trashedAt >= cutoff) return@forEach
            purgeNote(context, db, note)
        }

        db.taskDao.getAllOnce().forEach { task ->
            val trashedAt = task.trashedAt ?: return@forEach
            if (trashedAt >= cutoff) return@forEach
            purgeTask(context, db, task)
        }

        db.notebookDao.purgeTrashedBefore(cutoff)

        db.noteVersionDao.pruneOrphaned()
        db.taskVersionDao.pruneOrphaned()

    }

    suspend fun purgeNote(context: PlatformContext, db: AppDatabase, note: Note) {
                Attachment.parse(note.attachments).forEach { att ->
            if (AttachmentStore.looksLikeId(att.data)) AttachmentStore.delete(context, att.data)
        }
        NoteHistory.deleteAllFor(db, note.id)
        db.noteDao.delete(note)
    }

    suspend fun purgeTask(context: PlatformContext, db: AppDatabase, task: Task) {
                Attachment.parse(task.attachments).forEach { att ->
            if (AttachmentStore.looksLikeId(att.data)) AttachmentStore.delete(context, att.data)
        }
        ReminderScheduler.cancel(context, task.id)
        TaskHistory.deleteAllFor(db, task.id)
        db.taskDao.delete(task)
    }
}
