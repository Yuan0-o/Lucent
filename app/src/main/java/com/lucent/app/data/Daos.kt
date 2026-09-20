package com.lucent.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.SkipQueryVerification
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE archived = 0 AND trashedAt IS NULL AND isDraft = 0 AND hidden = 0 ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE archived = 1 AND trashedAt IS NULL AND isDraft = 0 AND hidden = 0 ORDER BY COALESCE(archivedAt, updatedAt) DESC")
    fun getArchived(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE trashedAt IS NOT NULL AND isDraft = 0 ORDER BY trashedAt DESC")
    fun getTrashed(): Flow<List<Note>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Note?

    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Note>

    @Query(
        """
        SELECT * FROM notes
        WHERE (:text = ''
                OR title LIKE '%' || :text || '%'
                OR body LIKE '%' || :text || '%'
                OR tags LIKE '%' || :text || '%'
                OR checklist LIKE '%' || :text || '%')
          AND (:tag = '' OR tags LIKE '%' || :tag || '%')
          AND isDraft = 0
          AND hidden = 0
          AND (:archived = -1 OR archived = :archived)
          AND (:trashed = -1
                OR (:trashed = 1 AND trashedAt IS NOT NULL)
                OR (:trashed = 0 AND trashedAt IS NULL))
        ORDER BY pinned DESC, updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchNotes(
        text: String,
        tag: String,
        archived: Int,
        trashed: Int,
        limit: Int
    ): List<Note>

    @Query("SELECT * FROM notes WHERE isDraft = 1 AND trashedAt IS NULL ORDER BY COALESCE(draftSavedAt, updatedAt) DESC")
    fun getDrafts(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE hidden = 1 AND isDraft = 0 AND trashedAt IS NULL ORDER BY updatedAt DESC")
    fun getHidden(): Flow<List<Note>>

    @Query("SELECT COUNT(*) FROM notes WHERE isDraft = 1 AND trashedAt IS NULL")
    suspend fun draftCountOnce(): Int

    @Query("SELECT COALESCE(MAX(manualOrder), 0) FROM notes")
    suspend fun maxManualOrderOnce(): Int

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE notes SET manualOrder = :order WHERE id = :id")
    suspend fun setManualOrder(id: Long, order: Int)

    @Delete
    suspend fun delete(note: Note)

    @Query("DELETE FROM notes")
    suspend fun clearAll()

    @SkipQueryVerification
    @Query("SELECT * FROM notes_fts WHERE notes_fts = 'rebuild'")
    suspend fun rebuildFts(): List<String>
}

@Dao
interface NoteEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: NoteEmbedding)

    @Query("SELECT * FROM note_embeddings WHERE model = :model")
    suspend fun getForModel(model: String): List<NoteEmbedding>

    @Query("SELECT * FROM note_embeddings WHERE noteId = :noteId")
    suspend fun getForNote(noteId: Long): List<NoteEmbedding>

    @Query("DELETE FROM note_embeddings WHERE noteId = :noteId AND model = :model")
    suspend fun delete(noteId: Long, model: String)

    @Query("DELETE FROM note_embeddings WHERE model = :model")
    suspend fun deleteAllForModel(model: String)

    @Query("DELETE FROM note_embeddings")
    suspend fun clearAll()
}

@Dao
interface NoteVersionDao {
    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY savedAt DESC")
    fun getForNote(noteId: Long): Flow<List<NoteVersion>>

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY savedAt DESC")
    suspend fun getForNoteOnce(noteId: Long): List<NoteVersion>

    @Query("SELECT * FROM note_versions ORDER BY savedAt DESC")
    suspend fun getAllOnce(): List<NoteVersion>

    @Query("SELECT COUNT(*) FROM note_versions WHERE noteId = :noteId")
    suspend fun countForNote(noteId: Long): Int

    @Insert
    suspend fun insert(version: NoteVersion): Long

    @Delete
    suspend fun delete(version: NoteVersion)

    @Query("DELETE FROM note_versions WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)

    @Query("DELETE FROM note_versions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "DELETE FROM note_versions WHERE noteId = :noteId AND id NOT IN (" +
            "SELECT id FROM note_versions WHERE noteId = :noteId ORDER BY savedAt DESC LIMIT :keep)"
    )
    suspend fun trimTo(noteId: Long, keep: Int)

    @Query("DELETE FROM note_versions WHERE noteId NOT IN (SELECT id FROM notes)")
    suspend fun pruneOrphaned()

    @Query("DELETE FROM note_versions")
    suspend fun clearAll()
}

@Dao
interface TaskVersionDao {
    @Query("SELECT * FROM task_versions WHERE taskId = :taskId ORDER BY savedAt DESC")
    fun getForTask(taskId: Long): Flow<List<TaskVersion>>

    @Query("SELECT * FROM task_versions WHERE taskId = :taskId ORDER BY savedAt DESC")
    suspend fun getForTaskOnce(taskId: Long): List<TaskVersion>

    @Query("SELECT * FROM task_versions ORDER BY savedAt DESC")
    suspend fun getAllOnce(): List<TaskVersion>

    @Query("SELECT COUNT(*) FROM task_versions WHERE taskId = :taskId")
    suspend fun countForTask(taskId: Long): Int

    @Insert
    suspend fun insert(version: TaskVersion): Long

    @Delete
    suspend fun delete(version: TaskVersion)

    @Query("DELETE FROM task_versions WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)

    @Query("DELETE FROM task_versions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "DELETE FROM task_versions WHERE taskId = :taskId AND id NOT IN (" +
            "SELECT id FROM task_versions WHERE taskId = :taskId ORDER BY savedAt DESC LIMIT :keep)"
    )
    suspend fun trimTo(taskId: Long, keep: Int)

    @Query("DELETE FROM task_versions WHERE taskId NOT IN (SELECT id FROM tasks)")
    suspend fun pruneOrphaned()

    @Query("DELETE FROM task_versions")
    suspend fun clearAll()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Task>

    @Query(
        """
        SELECT * FROM tasks
        WHERE (:text = ''
                OR title LIKE '%' || :text || '%'
                OR notes LIKE '%' || :text || '%'
                OR subtasks LIKE '%' || :text || '%')
          AND isDraft = 0
          AND hidden = 0
          AND (:done = -1 OR isDone = :done)
          AND (:trashed = -1
                OR (:trashed = 1 AND trashedAt IS NOT NULL)
                OR (:trashed = 0 AND trashedAt IS NULL))
          AND (:minPriority = -1 OR priority >= :minPriority)
          AND (:dueBefore = -1 OR (dueAt IS NOT NULL AND dueAt <= :dueBefore))
          AND (:dueAfter = -1 OR (dueAt IS NOT NULL AND dueAt >= :dueAfter))
        ORDER BY pinned DESC, priority DESC, COALESCE(dueAt, 9223372036854775807) ASC, createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchTasks(
        text: String,
        done: Int,
        trashed: Int,
        minPriority: Int,
        dueBefore: Long,
        dueAfter: Long,
        limit: Int
    ): List<Task>

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND trashedAt IS NULL AND isDraft = 0 AND hidden = 0 ORDER BY createdAt DESC")
    fun getActive(): Flow<List<Task>>

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 0 AND trashedAt IS NULL AND isDraft = 0 AND hidden = 0")
    suspend fun activeCountOnce(): Int

    @Query("SELECT * FROM tasks WHERE isDone = 1 AND trashedAt IS NULL AND isDraft = 0 AND hidden = 0 ORDER BY COALESCE(completedAt, createdAt) DESC")
    fun getCompleted(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE trashedAt IS NOT NULL AND isDraft = 0 ORDER BY trashedAt DESC")
    fun getTrashed(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isDraft = 1 AND trashedAt IS NULL ORDER BY COALESCE(draftSavedAt, createdAt) DESC")
    fun getDrafts(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE hidden = 1 AND isDraft = 0 AND trashedAt IS NULL ORDER BY createdAt DESC")
    fun getHidden(): Flow<List<Task>>

    @Query("SELECT COUNT(*) FROM tasks WHERE isDraft = 1 AND trashedAt IS NULL")
    suspend fun draftCountOnce(): Int

    @Query("SELECT COALESCE(MAX(manualOrder), 0) FROM tasks")
    suspend fun maxManualOrderOnce(): Int

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("UPDATE tasks SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE tasks SET manualOrder = :order WHERE id = :id")
    suspend fun setManualOrder(id: Long, order: Int)

    @Delete
    suspend fun delete(task: Task)

    @Query("DELETE FROM tasks")
    suspend fun clearAll()

    @SkipQueryVerification
    @Query("SELECT * FROM tasks_fts WHERE tasks_fts = 'rebuild'")
    suspend fun rebuildFts(): List<String>
}

data class ConversationContent(
    val conversationId: Long,
    val content: String?
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAll(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getForConversation(conversationId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getForConversationOnce(conversationId: Long): List<ChatMessage>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun countInConversation(conversationId: Long): Int

    @Query("SELECT conversationId AS conversationId, GROUP_CONCAT(content, ' ') AS content FROM chat_messages GROUP BY conversationId")
    suspend fun conversationContents(): List<ConversationContent>

    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Dao
interface ChatConversationDao {
    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ChatConversation>>

    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<ChatConversation>

    @Query("SELECT * FROM chat_conversations WHERE id = :id")
    suspend fun getById(id: Long): ChatConversation?

    @Insert
    suspend fun insert(conversation: ChatConversation): Long

    @Update
    suspend fun update(conversation: ChatConversation)

    @Delete
    suspend fun delete(conversation: ChatConversation)

    @Query("DELETE FROM chat_conversations")
    suspend fun clearAll()
}

data class NotebookCount(val notebookId: Long, val count: Int)

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Notebook>>

    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<Notebook>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Notebook?

    @Query("SELECT notebookId AS notebookId, COUNT(*) AS count FROM notebook_items GROUP BY notebookId")
    fun itemCounts(): Flow<List<NotebookCount>>

    @Query("SELECT * FROM notebook_items WHERE notebookId = :notebookId ORDER BY addedAt DESC")
    fun getItems(notebookId: Long): Flow<List<NotebookItem>>

    @Query("SELECT * FROM notebook_items WHERE notebookId = :notebookId ORDER BY addedAt DESC")
    suspend fun getItemsOnce(notebookId: Long): List<NotebookItem>

    @Query("SELECT * FROM notebook_items WHERE itemKind = :kind")
    suspend fun getItemsByKindOnce(kind: String): List<NotebookItem>

    @Query("SELECT COUNT(*) FROM notebook_items WHERE notebookId = :notebookId AND itemKind = :kind AND itemId = :itemId")
    suspend fun membershipExistsOnce(notebookId: Long, kind: String, itemId: Long): Int

    @Insert
    suspend fun insert(notebook: Notebook): Long

    @Update
    suspend fun update(notebook: Notebook)

    @Insert
    suspend fun insertItem(item: NotebookItem): Long

    @Query("DELETE FROM notebook_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)

    @Query("DELETE FROM notebook_items WHERE notebookId = :notebookId")
    suspend fun deleteItemsForNotebook(notebookId: Long)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notebooks")
    suspend fun clearAll()

    @Query("DELETE FROM notebook_items")
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
