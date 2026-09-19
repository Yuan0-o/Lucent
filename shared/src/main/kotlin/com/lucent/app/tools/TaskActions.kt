package com.lucent.app.tools

import android.content.Context
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Checklist
import com.lucent.app.data.Note
import com.lucent.app.data.NoteHistory
import com.lucent.app.data.Recurrence
import com.lucent.app.data.RepeatRule
import com.lucent.app.data.Task
import com.lucent.app.reminders.ReminderScheduler

object TaskActions {

    suspend fun complete(context: Context, db: AppDatabase, task: Task): Task? {
        val appContext = context.applicationContext

        db.taskDao().update(
            task.copy(
                isDone = true,
                completedAt = System.currentTimeMillis(),
                subtasks = Checklist.completeAll(task.subtasks)
            )
        )
        ReminderScheduler.cancel(appContext, task.id)

        val next = Recurrence.nextOccurrence(task) ?: return null
        val newId = db.taskDao().insert(next)
        val inserted = next.copy(id = newId)
        ReminderScheduler.sync(appContext, inserted)
        return inserted
    }

    suspend fun restore(context: Context, db: AppDatabase, task: Task) {
        val restored = task.copy(isDone = false, completedAt = null)
        db.taskDao().update(restored)
        ReminderScheduler.sync(context.applicationContext, restored)
    }

    suspend fun trash(context: Context, db: AppDatabase, task: Task) {
        val trashed = task.copy(trashedAt = System.currentTimeMillis())
        db.taskDao().update(trashed)
        ReminderScheduler.cancel(context.applicationContext, task.id)
    }

    suspend fun untrash(context: Context, db: AppDatabase, task: Task) {
        val restored = task.copy(trashedAt = null)
        db.taskDao().update(restored)
        ReminderScheduler.sync(context.applicationContext, restored)
    }

    suspend fun trashNote(db: AppDatabase, note: Note) {
        db.noteDao().update(note.copy(trashedAt = System.currentTimeMillis()))
    }

    suspend fun untrashNote(db: AppDatabase, note: Note) {
        db.noteDao().update(note.copy(trashedAt = null))
    }

    suspend fun updateNoteWithHistory(db: AppDatabase, existing: Note, updated: Note) {
        NoteHistory.recordIfChanged(
            db = db,
            existing = existing,
            newTitle = updated.title,
            newBody = updated.body,
            newTags = updated.tags,
            newIsChecklist = updated.isChecklist,
            newChecklist = updated.checklist
        )
        db.noteDao().update(updated.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun setSchedule(
        context: Context,
        db: AppDatabase,
        task: Task,
        dueAt: Long?,
        repeat: RepeatRule?,
        reminderEnabled: Boolean?
    ): Task {
        val rule = repeat ?: RepeatRule.fromKey(task.repeatRule)
        val updated = task.copy(
            dueAt = dueAt,
            repeatRule = if (dueAt == null) RepeatRule.NONE.key else rule.key,
            reminderEnabled = reminderEnabled ?: task.reminderEnabled
        )
        db.taskDao().update(updated)
        ReminderScheduler.sync(context.applicationContext, updated)
        return updated
    }
}
