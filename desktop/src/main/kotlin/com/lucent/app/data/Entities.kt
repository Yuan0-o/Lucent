package com.lucent.app.data


data class Note(
    val id: Long = 0,
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
    val doodle: String = "",
    val formatOverride: String? = null
)

data class Task(
    val id: Long = 0,
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
    val notesSpans: String = "",
    val formatOverride: String? = null
)

data class NoteVersion(
    val id: Long = 0,
    val noteId: Long,
    val title: String,
    val body: String,
    val tags: String = "",
    val isChecklist: Boolean = false,
    val checklist: String = "[]",
    val savedAt: Long = System.currentTimeMillis()
)

data class TaskVersion(
    val id: Long = 0,
    val taskId: Long,
    val title: String,
    val notes: String = "",
    val subtasks: String = "[]",
    val priority: Int = 0,
    val dueAt: Long? = null,
    val savedAt: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: Long = 0,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentMime: String? = null,
    val attachmentData: String? = null,
    val attachmentName: String? = null,
    val attachmentList: String? = null,
    val conversationId: Long = 1,
    val tokens: Int = 0,
    val replyToId: Long = 0,
    val agentTrace: String? = null
)

data class ChatConversation(
    val id: Long = 0,
    val title: String = "New conversation",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Notebook(
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val color: String = "",
    val manualOrder: Int = 0,
    val trashedAt: Long? = null
)

data class NotebookItem(
    val id: Long = 0,
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
