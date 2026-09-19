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
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val tags: String = "",
    val attachments: String = "[]",
    val archived: Boolean = false,
    val archivedAt: Long? = null,
    val pinned: Boolean = false,
    val color: String = "",
    val isChecklist: Boolean = false,
    val checklist: String = "[]",
    val trashedAt: Long? = null,
    val manualOrder: Int = 0,
    val isDraft: Boolean = false,
    val draftSavedAt: Long? = null,
    val hidden: Boolean = false,
    val bodySpans: String = "",
    val isDoodle: Boolean = false,
    val doodle: String = ""
)

@Entity(tableName = "note_embeddings", primaryKeys = ["noteId", "model"])
data class NoteEmbedding(
    val noteId: Long,
    val model: String,
    val dim: Int,
    val vec: ByteArray,
    val updatedAt: Long
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
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val attachments: String = "[]",
    val dueAt: Long? = null,
    val notes: String = "",
    val completedAt: Long? = null,
    val priority: Int = 0,
    val pinned: Boolean = false,
    val subtasks: String = "[]",
    val repeatRule: String = "NONE",
    val reminderEnabled: Boolean = false,
    val trashedAt: Long? = null,
    val manualOrder: Int = 0,
    val isDraft: Boolean = false,
    val draftSavedAt: Long? = null,
    val hidden: Boolean = false,
    val notesSpans: String = ""
)

@Entity(
    tableName = "note_versions",
    indices = [Index(value = ["noteId"])]
)
data class NoteVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val title: String,
    val body: String,
    val tags: String = "",
    val isChecklist: Boolean = false,
    val checklist: String = "[]",
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "task_versions",
    indices = [Index(value = ["taskId"])]
)
data class TaskVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val notes: String = "",
    val subtasks: String = "[]",
    val priority: Int = 0,
    val dueAt: Long? = null,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notebooks",
    indices = [Index(value = ["updatedAt"])]
)
data class Notebook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notebook_items",
    indices = [
        Index(value = ["notebookId"]),
        Index(value = ["itemKind", "itemId"])
    ]
)
data class NotebookItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val itemKind: String,
    val itemId: Long,
    val addedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val KIND_NOTE = "NOTE"
        const val KIND_TASK = "TASK"
    }
}

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentMime: String? = null,
    val attachmentData: String? = null,
    val attachmentName: String? = null,
    val attachmentList: String? = null,
    val conversationId: Long = 1,
    val tokens: Int = 0,
    val replyToId: Long = 0
)

@Entity(tableName = "chat_conversations")
data class ChatConversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "New conversation",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
