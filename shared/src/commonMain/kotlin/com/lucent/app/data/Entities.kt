package com.lucent.app.data

expect class Note(
    id: Long = 0,
    title: String,
    body: String,
    updatedAt: Long = 0,
    tags: String = "",
    attachments: String = "[]",
    archived: Boolean = false,
    archivedAt: Long? = null,
    pinned: Boolean = false,
    color: String = "",
    isChecklist: Boolean = false,
    checklist: String = "[]",
    trashedAt: Long? = null,
    manualOrder: Int = 0,
    isDraft: Boolean = false,
    draftSavedAt: Long? = null,
    hidden: Boolean = false,
    bodySpans: String = "",
    isDoodle: Boolean = false,
    doodle: String = "",
    formatOverride: String? = null
) {
    val id: Long
    val title: String
    val body: String
    val updatedAt: Long
    val tags: String
    val attachments: String
    val archived: Boolean
    val archivedAt: Long?
    val pinned: Boolean
    val color: String
    val isChecklist: Boolean
    val checklist: String
    val trashedAt: Long?
    val manualOrder: Int
    val isDraft: Boolean
    val draftSavedAt: Long?
    val hidden: Boolean
    val bodySpans: String
    val isDoodle: Boolean
    val doodle: String
    val formatOverride: String?

    fun copy(
        id: Long = this.id,
        title: String = this.title,
        body: String = this.body,
        updatedAt: Long = this.updatedAt,
        tags: String = this.tags,
        attachments: String = this.attachments,
        archived: Boolean = this.archived,
        archivedAt: Long? = this.archivedAt,
        pinned: Boolean = this.pinned,
        color: String = this.color,
        isChecklist: Boolean = this.isChecklist,
        checklist: String = this.checklist,
        trashedAt: Long? = this.trashedAt,
        manualOrder: Int = this.manualOrder,
        isDraft: Boolean = this.isDraft,
        draftSavedAt: Long? = this.draftSavedAt,
        hidden: Boolean = this.hidden,
        bodySpans: String = this.bodySpans,
        isDoodle: Boolean = this.isDoodle,
        doodle: String = this.doodle,
        formatOverride: String? = this.formatOverride
    ): Note
}

expect class NoteEmbedding(
    noteId: Long,
    model: String,
    dim: Int,
    vec: ByteArray,
    updatedAt: Long
) {
    val noteId: Long
    val model: String
    val dim: Int
    val vec: ByteArray
    val updatedAt: Long
}

expect class Task(
    id: Long = 0,
    title: String,
    isDone: Boolean = false,
    createdAt: Long = 0,
    attachments: String = "[]",
    dueAt: Long? = null,
    notes: String = "",
    completedAt: Long? = null,
    priority: Int = 0,
    pinned: Boolean = false,
    subtasks: String = "[]",
    repeatRule: String = "",
    reminderEnabled: Boolean = false,
    trashedAt: Long? = null,
    manualOrder: Int = 0,
    isDraft: Boolean = false,
    draftSavedAt: Long? = null,
    hidden: Boolean = false,
    notesSpans: String = "",
    formatOverride: String? = null
) {
    val id: Long
    val title: String
    val isDone: Boolean
    val createdAt: Long
    val attachments: String
    val dueAt: Long?
    val notes: String
    val completedAt: Long?
    val priority: Int
    val pinned: Boolean
    val subtasks: String
    val repeatRule: String
    val reminderEnabled: Boolean
    val trashedAt: Long?
    val manualOrder: Int
    val isDraft: Boolean
    val draftSavedAt: Long?
    val hidden: Boolean
    val notesSpans: String
    val formatOverride: String?

    fun copy(
        id: Long = this.id,
        title: String = this.title,
        isDone: Boolean = this.isDone,
        createdAt: Long = this.createdAt,
        attachments: String = this.attachments,
        dueAt: Long? = this.dueAt,
        notes: String = this.notes,
        completedAt: Long? = this.completedAt,
        priority: Int = this.priority,
        pinned: Boolean = this.pinned,
        subtasks: String = this.subtasks,
        repeatRule: String = this.repeatRule,
        reminderEnabled: Boolean = this.reminderEnabled,
        trashedAt: Long? = this.trashedAt,
        manualOrder: Int = this.manualOrder,
        isDraft: Boolean = this.isDraft,
        draftSavedAt: Long? = this.draftSavedAt,
        hidden: Boolean = this.hidden,
        notesSpans: String = this.notesSpans,
        formatOverride: String? = this.formatOverride
    ): Task
}

expect class NoteVersion(
    id: Long = 0,
    noteId: Long,
    title: String,
    body: String,
    tags: String = "",
    isChecklist: Boolean = false,
    checklist: String = "[]",
    savedAt: Long = System.currentTimeMillis()
) {
    val id: Long
    val noteId: Long
    val title: String
    val body: String
    val tags: String
    val isChecklist: Boolean
    val checklist: String
    val savedAt: Long
}

expect class TaskVersion(
    id: Long = 0,
    taskId: Long,
    title: String,
    notes: String = "",
    subtasks: String = "[]",
    priority: Int = 0,
    dueAt: Long? = null,
    savedAt: Long = System.currentTimeMillis()
) {
    val id: Long
    val taskId: Long
    val title: String
    val notes: String
    val subtasks: String
    val priority: Int
    val dueAt: Long?
    val savedAt: Long
}

expect class Notebook(
    id: Long = 0,
    title: String,
    createdAt: Long = System.currentTimeMillis(),
    updatedAt: Long = System.currentTimeMillis(),
    color: String = "",
    manualOrder: Int = 0,
    trashedAt: Long? = null,
    pinned: Boolean = false
) {
    val id: Long
    val title: String
    val createdAt: Long
    val updatedAt: Long
    val color: String
    val manualOrder: Int
    val trashedAt: Long?
    val pinned: Boolean

    fun copy(
        id: Long = this.id,
        title: String = this.title,
        createdAt: Long = this.createdAt,
        updatedAt: Long = this.updatedAt,
        color: String = this.color,
        manualOrder: Int = this.manualOrder,
        trashedAt: Long? = this.trashedAt,
        pinned: Boolean = this.pinned
    ): Notebook
}

expect class NotebookItem(
    id: Long = 0,
    notebookId: Long,
    itemKind: String,
    itemId: Long,
    addedAt: Long = System.currentTimeMillis()
) {
    val id: Long
    val notebookId: Long
    val itemKind: String
    val itemId: Long
    val addedAt: Long
    companion object {
        val KIND_NOTE: String
        val KIND_TASK: String
    }
}

expect class ChatMessage(
    id: Long = 0,
    role: String,
    content: String,
    timestamp: Long = System.currentTimeMillis(),
    attachmentMime: String? = null,
    attachmentData: String? = null,
    attachmentName: String? = null,
    attachmentList: String? = null,
    conversationId: Long = 1,
    tokens: Int = 0,
    replyToId: Long = 0,
    agentTrace: String? = null,
    reasoningBlocks: String? = null,
    reasoningText: String? = null,
    quotedRole: String? = null,
    quotedText: String? = null
) {
    val id: Long
    val role: String
    val content: String
    val timestamp: Long
    val attachmentMime: String?
    val attachmentData: String?
    val attachmentName: String?
    val attachmentList: String?
    val conversationId: Long
    val tokens: Int
    val replyToId: Long
    val agentTrace: String?
    val reasoningBlocks: String?
    val reasoningText: String?
    val quotedRole: String?
    val quotedText: String?

    fun copy(
        id: Long = this.id,
        role: String = this.role,
        content: String = this.content,
        timestamp: Long = this.timestamp,
        attachmentMime: String? = this.attachmentMime,
        attachmentData: String? = this.attachmentData,
        attachmentName: String? = this.attachmentName,
        attachmentList: String? = this.attachmentList,
        conversationId: Long = this.conversationId,
        tokens: Int = this.tokens,
        replyToId: Long = this.replyToId,
        agentTrace: String? = this.agentTrace,
        reasoningBlocks: String? = this.reasoningBlocks,
        reasoningText: String? = this.reasoningText,
        quotedRole: String? = this.quotedRole,
        quotedText: String? = this.quotedText
    ): ChatMessage
}

expect class ChatConversation(
    id: Long = 0,
    title: String = "New conversation",
    createdAt: Long = System.currentTimeMillis(),
    updatedAt: Long = System.currentTimeMillis()
) {
    val id: Long
    val title: String
    val createdAt: Long
    val updatedAt: Long

    fun copy(
        id: Long = this.id,
        title: String = this.title,
        createdAt: Long = this.createdAt,
        updatedAt: Long = this.updatedAt
    ): ChatConversation
}
