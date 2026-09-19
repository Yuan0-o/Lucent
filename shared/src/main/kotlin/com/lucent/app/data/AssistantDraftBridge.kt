package com.lucent.app.data

import android.content.Context
import com.lucent.app.ui.AssistantController

object AssistantDraftBridge {

    @Volatile private var mirroredNoteId: Long? = null

    @Volatile private var mirroredTaskId: Long? = null

    fun shouldMirror(toolName: String): Boolean =
        toolName == "create_note" || toolName == "create_task"

    suspend fun mirror(
        appContext: Context,
        toolName: String,
        edits: Map<String, String>
    ) {
        if (!shouldMirror(toolName)) return
        val db = AppDatabase.getInstance(appContext)
        val now = System.currentTimeMillis()
        when (toolName) {
            "create_note" -> {
                val existing = mirroredNoteId?.let { db.noteDao().getByIdOnce(it) }
                val row = (existing ?: Note(title = "", body = "", updatedAt = now)).copy(
                    title = edits["title"].orEmpty(),
                    body = edits["body"] ?: edits["content"].orEmpty(),
                    tags = edits["tags"] ?: existing?.tags.orEmpty(),
                    checklist = edits["checklist"] ?: existing?.checklist ?: "[]",
                    isChecklist = (edits["checklist"] ?: existing?.checklist).isNullOrBlank().not() &&
                        (edits["checklist"] ?: existing?.checklist) != "[]",
                    updatedAt = now,
                    isDraft = true,
                    draftSavedAt = now
                )
                mirroredNoteId = if (existing == null) {
                    db.noteDao().insert(row)
                } else {
                    db.noteDao().update(row); existing.id
                }
            }
            "create_task" -> {
                val existing = mirroredTaskId?.let { db.taskDao().getByIdOnce(it) }
                val row = (existing ?: Task(title = "", createdAt = now)).copy(
                    title = edits["title"].orEmpty(),
                    notes = edits["notes"].orEmpty(),
                    subtasks = edits["subtasks"] ?: existing?.subtasks ?: "[]",
                    isDraft = true,
                    draftSavedAt = now
                )
                mirroredTaskId = if (existing == null) {
                    db.taskDao().insert(row)
                } else {
                    db.taskDao().update(row); existing.id
                }
            }
        }
    }

    suspend fun clear(appContext: Context) {
        val db = AppDatabase.getInstance(appContext)
        mirroredNoteId?.let { id ->
            db.noteDao().getByIdOnce(id)?.let { if (it.isDraft) db.noteDao().delete(it) }
        }
        mirroredTaskId?.let { id ->
            db.taskDao().getByIdOnce(id)?.let { if (it.isDraft) db.taskDao().delete(it) }
        }
        mirroredNoteId = null
        mirroredTaskId = null
    }

    fun forgetWithoutDeleting() {
        mirroredNoteId = null
        mirroredTaskId = null
    }
}
