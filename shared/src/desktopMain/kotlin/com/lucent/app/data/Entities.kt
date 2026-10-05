package com.lucent.app.data


actual data class Note(
    actual val id: Long = 0,
    actual val title: String,
    actual val body: String,
    actual val updatedAt: Long = System.currentTimeMillis(),
    actual val tags: String = "",
    actual val attachments: String = "[]",
    actual val archived: Boolean = false,
    actual val archivedAt: Long? = null,
    actual val pinned: Boolean = false,
    actual val color: String = "",
    actual val isChecklist: Boolean = false,
    actual val checklist: String = "[]",
    actual val trashedAt: Long? = null,
    actual val manualOrder: Int = 0,
    actual val isDraft: Boolean = false,
    actual val draftSavedAt: Long? = null,
    actual val hidden: Boolean = false,
    actual val bodySpans: String = "",
    actual val isDoodle: Boolean = false,
    actual val doodle: String = "",
    actual val formatOverride: String? = null
)

actual data class Task(
    actual val id: Long = 0,
    actual val title: String,
    actual val isDone: Boolean = false,
    actual val createdAt: Long = System.currentTimeMillis(),
    actual val attachments: String = "[]",
    actual val dueAt: Long? = null,
    actual val notes: String = "",
    actual val completedAt: Long? = null,
    actual val priority: Int = 0,
    actual val pinned: Boolean = false,
    actual val subtasks: String = "[]",
    actual val repeatRule: String = "NONE",
    actual val reminderEnabled: Boolean = false,
    actual val trashedAt: Long? = null,
    actual val manualOrder: Int = 0,
    actual val isDraft: Boolean = false,
    actual val draftSavedAt: Long? = null,
    actual val hidden: Boolean = false,
    actual val notesSpans: String = "",
    actual val formatOverride: String? = null
)

actual fun Task.copyForRecurrence(
    id: Long,
    isDone: Boolean,
    createdAt: Long,
    attachments: String,
    dueAt: Long?,
    completedAt: Long?,
    subtasks: String,
    trashedAt: Long?
): Task = copy(
    id = id,
    isDone = isDone,
    createdAt = createdAt,
    attachments = attachments,
    dueAt = dueAt,
    completedAt = completedAt,
    subtasks = subtasks,
    trashedAt = trashedAt
)

actual data class NoteVersion(
    actual val id: Long = 0,
    actual val noteId: Long,
    actual val title: String,
    actual val body: String,
    actual val tags: String = "",
    actual val isChecklist: Boolean = false,
    actual val checklist: String = "[]",
    actual val savedAt: Long = System.currentTimeMillis()
)

actual data class TaskVersion(
    actual val id: Long = 0,
    actual val taskId: Long,
    actual val title: String,
    actual val notes: String = "",
    actual val subtasks: String = "[]",
    actual val priority: Int = 0,
    actual val dueAt: Long? = null,
    actual val savedAt: Long = System.currentTimeMillis()
)

actual data class ChatMessage(
    actual val id: Long = 0,
    actual val role: String,
    actual val content: String,
    actual val timestamp: Long = System.currentTimeMillis(),
    actual val attachmentMime: String? = null,
    actual val attachmentData: String? = null,
    actual val attachmentName: String? = null,
    actual val attachmentList: String? = null,
    actual val conversationId: Long = 1,
    actual val tokens: Int = 0,
    actual val replyToId: Long = 0,
    actual val agentTrace: String? = null,
    actual val reasoningBlocks: String? = null,
    actual val reasoningText: String? = null,
    actual val quotedRole: String? = null,
    actual val quotedText: String? = null
)

actual data class ChatConversation(
    actual val id: Long = 0,
    actual val title: String = "New conversation",
    actual val createdAt: Long = System.currentTimeMillis(),
    actual val updatedAt: Long = System.currentTimeMillis()
)

actual data class Notebook(
    actual val id: Long = 0,
    actual val title: String,
    actual val createdAt: Long = System.currentTimeMillis(),
    actual val updatedAt: Long = System.currentTimeMillis(),
    actual val color: String = "",
    actual val manualOrder: Int = 0,
    actual val trashedAt: Long? = null,
    actual val pinned: Boolean = false
)

actual data class NotebookItem(
    actual val id: Long = 0,
    actual val notebookId: Long,
    actual val itemKind: String,
    actual val itemId: Long,
    actual val addedAt: Long = System.currentTimeMillis()
) {
    actual companion object {
        actual val KIND_NOTE = "NOTE"
        actual val KIND_TASK = "TASK"
    }
}

actual data class NoteEmbedding(
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
