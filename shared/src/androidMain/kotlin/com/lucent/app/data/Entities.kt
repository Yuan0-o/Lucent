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
actual class Note actual constructor(
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
){

    actual fun copy(
        id: Long,
        title: String,
        body: String,
        updatedAt: Long,
        tags: String,
        attachments: String,
        archived: Boolean,
        archivedAt: Long?,
        pinned: Boolean,
        color: String,
        isChecklist: Boolean,
        checklist: String,
        trashedAt: Long?,
        manualOrder: Int,
        isDraft: Boolean,
        draftSavedAt: Long?,
        hidden: Boolean,
        bodySpans: String,
        isDoodle: Boolean,
        doodle: String,
        formatOverride: String?
    ): Note = Note(id = id, title = title, body = body, updatedAt = updatedAt, tags = tags, attachments = attachments, archived = archived, archivedAt = archivedAt, pinned = pinned, color = color, isChecklist = isChecklist, checklist = checklist, trashedAt = trashedAt, manualOrder = manualOrder, isDraft = isDraft, draftSavedAt = draftSavedAt, hidden = hidden, bodySpans = bodySpans, isDoodle = isDoodle, doodle = doodle, formatOverride = formatOverride)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Note) return false
        return id == other.id &&
        title == other.title &&
        body == other.body &&
        updatedAt == other.updatedAt &&
        tags == other.tags &&
        attachments == other.attachments &&
        archived == other.archived &&
        archivedAt == other.archivedAt &&
        pinned == other.pinned &&
        color == other.color &&
        isChecklist == other.isChecklist &&
        checklist == other.checklist &&
        trashedAt == other.trashedAt &&
        manualOrder == other.manualOrder &&
        isDraft == other.isDraft &&
        draftSavedAt == other.draftSavedAt &&
        hidden == other.hidden &&
        bodySpans == other.bodySpans &&
        isDoodle == other.isDoodle &&
        doodle == other.doodle &&
        formatOverride == other.formatOverride
    }

    override fun hashCode(): Int = listOf(id, title, body, updatedAt, tags, attachments, archived, archivedAt, pinned, color, isChecklist, checklist, trashedAt, manualOrder, isDraft, draftSavedAt, hidden, bodySpans, isDoodle, doodle, formatOverride).hashCode()

    override fun toString(): String = "Note(id=$id, title=$title, body=$body, updatedAt=$updatedAt, tags=$tags, attachments=$attachments, archived=$archived, archivedAt=$archivedAt, pinned=$pinned, color=$color, isChecklist=$isChecklist, checklist=$checklist, trashedAt=$trashedAt, manualOrder=$manualOrder, isDraft=$isDraft, draftSavedAt=$draftSavedAt, hidden=$hidden, bodySpans=$bodySpans, isDoodle=$isDoodle, doodle=$doodle, formatOverride=$formatOverride)"
}

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
actual class Task actual constructor(
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
){

    actual fun copy(
        id: Long,
        title: String,
        isDone: Boolean,
        createdAt: Long,
        attachments: String,
        dueAt: Long?,
        notes: String,
        completedAt: Long?,
        priority: Int,
        pinned: Boolean,
        subtasks: String,
        repeatRule: String,
        reminderEnabled: Boolean,
        trashedAt: Long?,
        manualOrder: Int,
        isDraft: Boolean,
        draftSavedAt: Long?,
        hidden: Boolean,
        notesSpans: String,
        formatOverride: String?
    ): Task = Task(id = id, title = title, isDone = isDone, createdAt = createdAt, attachments = attachments, dueAt = dueAt, notes = notes, completedAt = completedAt, priority = priority, pinned = pinned, subtasks = subtasks, repeatRule = repeatRule, reminderEnabled = reminderEnabled, trashedAt = trashedAt, manualOrder = manualOrder, isDraft = isDraft, draftSavedAt = draftSavedAt, hidden = hidden, notesSpans = notesSpans, formatOverride = formatOverride)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Task) return false
        return id == other.id &&
        title == other.title &&
        isDone == other.isDone &&
        createdAt == other.createdAt &&
        attachments == other.attachments &&
        dueAt == other.dueAt &&
        notes == other.notes &&
        completedAt == other.completedAt &&
        priority == other.priority &&
        pinned == other.pinned &&
        subtasks == other.subtasks &&
        repeatRule == other.repeatRule &&
        reminderEnabled == other.reminderEnabled &&
        trashedAt == other.trashedAt &&
        manualOrder == other.manualOrder &&
        isDraft == other.isDraft &&
        draftSavedAt == other.draftSavedAt &&
        hidden == other.hidden &&
        notesSpans == other.notesSpans &&
        formatOverride == other.formatOverride
    }

    override fun hashCode(): Int = listOf(id, title, isDone, createdAt, attachments, dueAt, notes, completedAt, priority, pinned, subtasks, repeatRule, reminderEnabled, trashedAt, manualOrder, isDraft, draftSavedAt, hidden, notesSpans, formatOverride).hashCode()

    override fun toString(): String = "Task(id=$id, title=$title, isDone=$isDone, createdAt=$createdAt, attachments=$attachments, dueAt=$dueAt, notes=$notes, completedAt=$completedAt, priority=$priority, pinned=$pinned, subtasks=$subtasks, repeatRule=$repeatRule, reminderEnabled=$reminderEnabled, trashedAt=$trashedAt, manualOrder=$manualOrder, isDraft=$isDraft, draftSavedAt=$draftSavedAt, hidden=$hidden, notesSpans=$notesSpans, formatOverride=$formatOverride)"
}

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
actual class Notebook actual constructor(
    @PrimaryKey(autoGenerate = true) actual val id: Long,
    actual val title: String,
    actual val createdAt: Long,
    actual val updatedAt: Long,
    actual val color: String,
    actual val manualOrder: Int,
    actual val trashedAt: Long?,
    actual val pinned: Boolean
){

    actual fun copy(
        id: Long,
        title: String,
        createdAt: Long,
        updatedAt: Long,
        color: String,
        manualOrder: Int,
        trashedAt: Long?,
        pinned: Boolean
    ): Notebook = Notebook(id = id, title = title, createdAt = createdAt, updatedAt = updatedAt, color = color, manualOrder = manualOrder, trashedAt = trashedAt, pinned = pinned)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Notebook) return false
        return id == other.id &&
        title == other.title &&
        createdAt == other.createdAt &&
        updatedAt == other.updatedAt &&
        color == other.color &&
        manualOrder == other.manualOrder &&
        trashedAt == other.trashedAt &&
        pinned == other.pinned
    }

    override fun hashCode(): Int = listOf(id, title, createdAt, updatedAt, color, manualOrder, trashedAt, pinned).hashCode()

    override fun toString(): String = "Notebook(id=$id, title=$title, createdAt=$createdAt, updatedAt=$updatedAt, color=$color, manualOrder=$manualOrder, trashedAt=$trashedAt, pinned=$pinned)"
}

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
actual class ChatMessage actual constructor(
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
){

    actual fun copy(
        id: Long,
        role: String,
        content: String,
        timestamp: Long,
        attachmentMime: String?,
        attachmentData: String?,
        attachmentName: String?,
        attachmentList: String?,
        conversationId: Long,
        tokens: Int,
        replyToId: Long,
        agentTrace: String?,
        reasoningBlocks: String?,
        reasoningText: String?,
        quotedRole: String?,
        quotedText: String?
    ): ChatMessage = ChatMessage(id = id, role = role, content = content, timestamp = timestamp, attachmentMime = attachmentMime, attachmentData = attachmentData, attachmentName = attachmentName, attachmentList = attachmentList, conversationId = conversationId, tokens = tokens, replyToId = replyToId, agentTrace = agentTrace, reasoningBlocks = reasoningBlocks, reasoningText = reasoningText, quotedRole = quotedRole, quotedText = quotedText)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatMessage) return false
        return id == other.id &&
        role == other.role &&
        content == other.content &&
        timestamp == other.timestamp &&
        attachmentMime == other.attachmentMime &&
        attachmentData == other.attachmentData &&
        attachmentName == other.attachmentName &&
        attachmentList == other.attachmentList &&
        conversationId == other.conversationId &&
        tokens == other.tokens &&
        replyToId == other.replyToId &&
        agentTrace == other.agentTrace &&
        reasoningBlocks == other.reasoningBlocks &&
        reasoningText == other.reasoningText &&
        quotedRole == other.quotedRole &&
        quotedText == other.quotedText
    }

    override fun hashCode(): Int = listOf(id, role, content, timestamp, attachmentMime, attachmentData, attachmentName, attachmentList, conversationId, tokens, replyToId, agentTrace, reasoningBlocks, reasoningText, quotedRole, quotedText).hashCode()

    override fun toString(): String = "ChatMessage(id=$id, role=$role, content=$content, timestamp=$timestamp, attachmentMime=$attachmentMime, attachmentData=$attachmentData, attachmentName=$attachmentName, attachmentList=$attachmentList, conversationId=$conversationId, tokens=$tokens, replyToId=$replyToId, agentTrace=$agentTrace, reasoningBlocks=$reasoningBlocks, reasoningText=$reasoningText, quotedRole=$quotedRole, quotedText=$quotedText)"
}

@Entity(tableName = "chat_conversations")
actual class ChatConversation actual constructor(
    @PrimaryKey(autoGenerate = true) actual val id: Long,
    actual val title: String,
    actual val createdAt: Long,
    actual val updatedAt: Long
) {
    actual fun copy(
        id: Long,
        title: String,
        createdAt: Long,
        updatedAt: Long
    ): ChatConversation = ChatConversation(id = id, title = title, createdAt = createdAt, updatedAt = updatedAt)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatConversation) return false
        return id == other.id &&
        title == other.title &&
        createdAt == other.createdAt &&
        updatedAt == other.updatedAt
    }

    override fun hashCode(): Int = listOf(id, title, createdAt, updatedAt).hashCode()

    override fun toString(): String = "ChatConversation(id=$id, title=$title, createdAt=$createdAt, updatedAt=$updatedAt)"
}
