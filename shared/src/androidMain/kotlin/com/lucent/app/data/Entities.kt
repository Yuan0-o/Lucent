package com.lucent.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["archived"]),
        Index(value = ["trashedAt"])
    ]
)
actual data class Note actual constructor(
    @PrimaryKey(autoGenerate = true) actual val id: Long,
    actual val title: String,
    actual val body: String,
    actual val updatedAt: Long,
    actual val tags: String,
    actual val attachments: String,
    actual val archived: Boolean,
    actual val archivedAt: Long?,
    actual val pinned: Boolean,
    actual val color: String,
    actual val isChecklist: Boolean,
    actual val checklist: String,
    actual val trashedAt: Long?,
    actual val manualOrder: Int,
    actual val isDraft: Boolean,
    actual val draftSavedAt: Long?,
    actual val hidden: Boolean,
    actual val bodySpans: String,
    actual val isDoodle: Boolean,
    actual val doodle: String,
    actual val formatOverride: String?
)

@Entity(tableName = "note_embeddings", primaryKeys = ["noteId", "model"])
actual data class NoteEmbedding actual constructor(
    actual val noteId: Long,
    actual val model: String,
    actual val dim: Int,
    actual val vec: ByteArray,
    actual val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NoteEmbedding) return false
        return noteId == other.noteId && model == other.model && dim == other.dim &&
            vec.contentEquals(other.vec) && updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = noteId.hashCode()
        result = 31 * result + model.hashCode()
        result = 31 * result + dim
        result = 31 * result + vec.contentHashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["isDone"]),
        Index(value = ["trashedAt"])
    ]
)
actual data class Task actual constructor(
    @PrimaryKey(autoGenerate = true) actual val id: Long,
    actual val title: String,
    actual val isDone: Boolean,
    actual val createdAt: Long,
    actual val attachments: String,
    actual val dueAt: Long?,
    actual val notes: String,
    actual val completedAt: Long?,
    actual val priority: Int,
    actual val pinned: Boolean,
    actual val subtasks: String,
    actual val repeatRule: String,
    actual val reminderEnabled: Boolean,
    actual val trashedAt: Long?,
    actual val manualOrder: Int,
    actual val isDraft: Boolean,
    actual val draftSavedAt: Long?,
    actual val hidden: Boolean,
    actual val notesSpans: String,
    actual val formatOverride: String?
)

@Entity(
    tableName = "note_versions",
    indices = [Index(value = ["noteId"])]
)
actual data class NoteVersion actual constructor(
    @PrimaryKey(autoGenerate = true) actual val id: Long,
    actual val noteId: Long,
    actual val title: String,
    actual val body: String,
    actual val tags: String,
    actual val isChecklist: Boolean,
    actual val checklist: String,
    actual val savedAt: Long
)

@Entity(
    tableName = "task_versions",
    indices = [Index(value = ["taskId"])]
)
actual data class TaskVersion actual constructor(
    @PrimaryKey(autoGenerate = true) actual val id: Long,
    actual val taskId: Long,
    actual val title: String,
    actual val notes: String,
    actual val subtasks: String,
    actual val priority: Int,
    actual val dueAt: Long?,
    actual val savedAt: Long
)

@Entity(
    tableName = "notebooks",
    indices = [Index(value = ["updatedAt"])]
)
actual data class Notebook actual constructor(
    @PrimaryKey(autoGenerate = true) actual val id: Long,
    actual val title: String,
    actual val createdAt: Long,
    actual val updatedAt: Long,
    actual val color: String,
    actual val manualOrder: Int,
    actual val trashedAt: Long?,
    actual val pinned: Boolean
)

@Entity(
    tableName = "notebook_items",
    indices = [
        Index(value = ["notebookId"]),
        Index(value = ["itemKind", "itemId"])
    ]
)
actual data class NotebookItem actual constructor(
    @PrimaryKey(autoGenerate = true) actual val id: Long,
    actual val notebookId: Long,
    actual val itemKind: String,
    actual val itemId: Long,
    actual val addedAt: Long
) {
    actual companion object {
        actual val KIND_NOTE = "NOTE"
        actual val KIND_TASK = "TASK"
    }
}

@Entity(tableName = "chat_messages")
actual data class ChatMessage actual constructor(
    @PrimaryKey(autoGenerate = true) actual val id: Long,
    actual val role: String,
    actual val content: String,
    actual val timestamp: Long,
    actual val attachmentMime: String?,
    actual val attachmentData: String?,
    actual val attachmentName: String?,
    actual val attachmentList: String?,
    actual val conversationId: Long,
    actual val tokens: Int,
    actual val replyToId: Long,
    actual val agentTrace: String?,
    actual val reasoningBlocks: String?,
    actual val reasoningText: String?,
    actual val quotedRole: String?,
    actual val quotedText: String?
)

@Entity(tableName = "chat_conversations")
actual data class ChatConversation actual constructor(
    @PrimaryKey(autoGenerate = true) actual val id: Long,
    actual val title: String,
    actual val createdAt: Long,
    actual val updatedAt: Long
)
