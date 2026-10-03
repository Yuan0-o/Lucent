package com.lucent.app.data

import kotlinx.coroutines.flow.Flow

interface NoteDao {
    fun getAll(): Flow<List<Note>>
    fun getArchived(): Flow<List<Note>>
    fun getTrashed(): Flow<List<Note>>
    suspend fun getAllOnce(): List<Note>
    suspend fun getByIdOnce(id: Long): Note?
    suspend fun getByIds(ids: List<Long>): List<Note>
    suspend fun searchNotes(
        text: String,
        tag: String,
        archived: Int,
        trashed: Int,
        limit: Int
    ): List<Note>
    fun getDrafts(): Flow<List<Note>>
    fun getHidden(): Flow<List<Note>>
    suspend fun draftCountOnce(): Int
    suspend fun maxManualOrderOnce(): Int
    suspend fun insert(note: Note): Long
    suspend fun update(note: Note)
    suspend fun setPinned(id: Long, pinned: Boolean)
    suspend fun setManualOrder(id: Long, order: Int)
    suspend fun delete(note: Note)
    suspend fun clearAll()
    suspend fun rebuildFts(): List<String>
}

interface NoteEmbeddingDao {
    suspend fun upsert(embedding: NoteEmbedding)
    suspend fun getForModel(model: String): List<NoteEmbedding>
    suspend fun getForNote(noteId: Long): List<NoteEmbedding>
    suspend fun delete(noteId: Long, model: String)
    suspend fun deleteAllForModel(model: String)
    suspend fun clearAll()
}

interface NoteVersionDao {
    fun getForNote(noteId: Long): Flow<List<NoteVersion>>
    suspend fun getForNoteOnce(noteId: Long): List<NoteVersion>
    suspend fun getAllOnce(): List<NoteVersion>
    suspend fun countForNote(noteId: Long): Int
    suspend fun insert(version: NoteVersion): Long
    suspend fun delete(version: NoteVersion)
    suspend fun deleteForNote(noteId: Long)
    suspend fun deleteById(id: Long)
    suspend fun trimTo(noteId: Long, keep: Int)
    suspend fun pruneOrphaned()
    suspend fun clearAll()
}

interface TaskVersionDao {
    fun getForTask(taskId: Long): Flow<List<TaskVersion>>
    suspend fun getForTaskOnce(taskId: Long): List<TaskVersion>
    suspend fun getAllOnce(): List<TaskVersion>
    suspend fun countForTask(taskId: Long): Int
    suspend fun insert(version: TaskVersion): Long
    suspend fun delete(version: TaskVersion)
    suspend fun deleteForTask(taskId: Long)
    suspend fun deleteById(id: Long)
    suspend fun trimTo(taskId: Long, keep: Int)
    suspend fun pruneOrphaned()
    suspend fun clearAll()
}

interface TaskDao {
    fun getAll(): Flow<List<Task>>
    suspend fun getAllOnce(): List<Task>
    suspend fun getByIdOnce(id: Long): Task?
    suspend fun getByIds(ids: List<Long>): List<Task>
    suspend fun searchTasks(
        text: String,
        done: Int,
        trashed: Int,
        minPriority: Int,
        dueBefore: Long,
        dueAfter: Long,
        limit: Int
    ): List<Task>
    fun getActive(): Flow<List<Task>>
    suspend fun activeCountOnce(): Int
    fun getCompleted(): Flow<List<Task>>
    fun getTrashed(): Flow<List<Task>>
    fun getDrafts(): Flow<List<Task>>
    fun getHidden(): Flow<List<Task>>
    suspend fun draftCountOnce(): Int
    suspend fun maxManualOrderOnce(): Int
    suspend fun insert(task: Task): Long
    suspend fun update(task: Task)
    suspend fun setPinned(id: Long, pinned: Boolean)
    suspend fun setManualOrder(id: Long, order: Int)
    suspend fun delete(task: Task)
    suspend fun clearAll()
    suspend fun rebuildFts(): List<String>
}

data class ConversationContent(
    val conversationId: Long,
    val content: String?
)

interface ChatDao {
    fun getAll(): Flow<List<ChatMessage>>
    suspend fun getAllOnce(): List<ChatMessage>
    fun getForConversation(conversationId: Long): Flow<List<ChatMessage>>
    suspend fun getForConversationOnce(conversationId: Long): List<ChatMessage>
    suspend fun countInConversation(conversationId: Long): Int
    suspend fun conversationContents(): List<ConversationContent>
    suspend fun insert(message: ChatMessage): Long
    suspend fun deleteByIds(ids: List<Long>)
    suspend fun clearConversation(conversationId: Long)
    suspend fun clearAll()
}

interface ChatConversationDao {
    fun getAll(): Flow<List<ChatConversation>>
    suspend fun getAllOnce(): List<ChatConversation>
    suspend fun getById(id: Long): ChatConversation?
    suspend fun insert(conversation: ChatConversation): Long
    suspend fun update(conversation: ChatConversation)
    suspend fun delete(conversation: ChatConversation)
    suspend fun clearAll()
}

data class NotebookCount(val notebookId: Long, val tasks: Int, val notes: Int)

interface NotebookDao {
    fun getAll(): Flow<List<Notebook>>
    suspend fun getAllOnce(): List<Notebook>
    suspend fun getAllIncludingTrashedOnce(): List<Notebook>
    fun getTrashed(): Flow<List<Notebook>>
    suspend fun getTrashedOnce(): List<Notebook>
    suspend fun getByIdOnce(id: Long): Notebook?
    fun itemCounts(): Flow<List<NotebookCount>>
    fun getItems(notebookId: Long): Flow<List<NotebookItem>>
    suspend fun getItemsOnce(notebookId: Long): List<NotebookItem>
    suspend fun getItemsByKindOnce(kind: String): List<NotebookItem>
    suspend fun getAllItemsOnce(): List<NotebookItem>
    suspend fun membershipExistsOnce(notebookId: Long, kind: String, itemId: Long): Int
    suspend fun insert(notebook: Notebook): Long
    suspend fun update(notebook: Notebook)
    suspend fun insertItem(item: NotebookItem): Long
    suspend fun deleteItemById(itemId: Long)
    suspend fun deleteItemsForNotebook(notebookId: Long)
    suspend fun deleteById(id: Long)
    suspend fun purgeTrashedBefore(cutoff: Long)
    suspend fun clearAll()
    suspend fun clearAllItems()
}

suspend fun NotebookDao.pruneOrphans(noteDao: NoteDao, taskDao: TaskDao) {
    val noteMembers = getItemsByKindOnce(NotebookItem.KIND_NOTE)
    if (noteMembers.isNotEmpty()) {
        val alive = noteDao.getByIds(noteMembers.map { it.itemId }.toSet().toList()).map { it.id }.toHashSet()
        noteMembers.filter { it.itemId !in alive }.forEach { deleteItemById(it.id) }
    }
    val taskMembers = getItemsByKindOnce(NotebookItem.KIND_TASK)
    if (taskMembers.isNotEmpty()) {
        val alive = taskDao.getByIds(taskMembers.map { it.itemId }.toSet().toList()).map { it.id }.toHashSet()
        taskMembers.filter { it.itemId !in alive }.forEach { deleteItemById(it.id) }
    }
}
