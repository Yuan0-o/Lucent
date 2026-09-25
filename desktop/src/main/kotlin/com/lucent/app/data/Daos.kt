package com.lucent.app.data

import kotlinx.coroutines.flow.Flow
import java.sql.ResultSet


private fun noteOf(rs: ResultSet) = Note(
    id = rs.getLong("id"),
    title = rs.getString("title"),
    body = rs.getString("body"),
    updatedAt = rs.getLong("updatedAt"),
    tags = rs.getString("tags"),
    attachments = rs.getString("attachments"),
    archived = rs.getInt("archived") != 0,
    archivedAt = rs.longOrNull("archivedAt"),
    pinned = rs.getInt("pinned") != 0,
    color = rs.getString("color"),
    isChecklist = rs.getInt("isChecklist") != 0,
    checklist = rs.getString("checklist"),
    trashedAt = rs.longOrNull("trashedAt"),
    manualOrder = rs.getInt("manualOrder"),
    isDraft = rs.getInt("isDraft") != 0,
    draftSavedAt = rs.longOrNull("draftSavedAt"),
    hidden = rs.getInt("hidden") != 0,
    isDoodle = rs.getInt("isDoodle") != 0,
    doodle = rs.getString("doodle"),
    bodySpans = rs.getString("bodySpans") ?: "",
    formatOverride = rs.stringOrNull("formatOverride")
)

private fun noteEmbeddingOf(rs: ResultSet) = NoteEmbedding(
    noteId = rs.getLong("noteId"),
    model = rs.getString("model"),
    dim = rs.getInt("dim"),
    vec = rs.getBytes("vec"),
    updatedAt = rs.getLong("updatedAt")
)

private fun taskOf(rs: ResultSet) = Task(
    id = rs.getLong("id"),
    title = rs.getString("title"),
    isDone = rs.getInt("isDone") != 0,
    createdAt = rs.getLong("createdAt"),
    attachments = rs.getString("attachments"),
    dueAt = rs.longOrNull("dueAt"),
    notes = rs.getString("notes"),
    completedAt = rs.longOrNull("completedAt"),
    priority = rs.getInt("priority"),
    pinned = rs.getInt("pinned") != 0,
    subtasks = rs.getString("subtasks"),
    repeatRule = rs.getString("repeatRule"),
    reminderEnabled = rs.getInt("reminderEnabled") != 0,
    trashedAt = rs.longOrNull("trashedAt"),
    manualOrder = rs.getInt("manualOrder"),
    isDraft = rs.getInt("isDraft") != 0,
    draftSavedAt = rs.longOrNull("draftSavedAt"),
    hidden = rs.getInt("hidden") != 0,
    notesSpans = rs.getString("notesSpans") ?: "",
    formatOverride = rs.stringOrNull("formatOverride")
)

private fun versionOf(rs: ResultSet) = NoteVersion(
    id = rs.getLong("id"),
    noteId = rs.getLong("noteId"),
    title = rs.getString("title"),
    body = rs.getString("body"),
    tags = rs.getString("tags"),
    isChecklist = rs.getInt("isChecklist") != 0,
    checklist = rs.getString("checklist"),
    savedAt = rs.getLong("savedAt")
)

private fun messageOf(rs: ResultSet) = ChatMessage(
    id = rs.getLong("id"),
    role = rs.getString("role"),
    content = rs.getString("content"),
    timestamp = rs.getLong("timestamp"),
    attachmentMime = rs.stringOrNull("attachmentMime"),
    attachmentData = rs.stringOrNull("attachmentData"),
    attachmentName = rs.stringOrNull("attachmentName"),
    attachmentList = rs.stringOrNull("attachmentList"),
    conversationId = rs.getLong("conversationId"),
    tokens = rs.getInt("tokens"),
    replyToId = rs.getLong("replyToId"),
    agentTrace = rs.stringOrNull("agentTrace")
)

private fun conversationOf(rs: ResultSet) = ChatConversation(
    id = rs.getLong("id"),
    title = rs.getString("title"),
    createdAt = rs.getLong("createdAt"),
    updatedAt = rs.getLong("updatedAt")
)

class NoteDao internal constructor(private val db: Db) {

    fun getAll(): Flow<List<Note>> = db.watch("notes") { getAllActiveOnce() }

    fun getArchived(): Flow<List<Note>> = db.watch("notes") {
        db.use { c ->
            c.prepareStatement(
                "SELECT * FROM notes WHERE archived = 1 AND trashedAt IS NULL AND isDraft = 0 AND hidden = 0 " +
                    "ORDER BY COALESCE(archivedAt, updatedAt) DESC"
            ).executeQuery().mapAll(::noteOf)
        }
    }

    fun getTrashed(): Flow<List<Note>> = db.watch("notes") {
        db.use { c ->
            c.prepareStatement("SELECT * FROM notes WHERE trashedAt IS NOT NULL AND isDraft = 0 ORDER BY trashedAt DESC")
                .executeQuery().mapAll(::noteOf)
        }
    }

    private suspend fun getAllActiveOnce(): List<Note> = db.use { c ->
        c.prepareStatement("SELECT * FROM notes WHERE archived = 0 AND trashedAt IS NULL AND isDraft = 0 AND hidden = 0 ORDER BY updatedAt DESC")
            .executeQuery().mapAll(::noteOf)
    }

    suspend fun getAllOnce(): List<Note> = db.use { c ->
        c.prepareStatement("SELECT * FROM notes ORDER BY updatedAt DESC").executeQuery().mapAll(::noteOf)
    }

    suspend fun getByIdOnce(id: Long): Note? = db.use { c ->
        c.prepareStatement("SELECT * FROM notes WHERE id = ?").apply { setLong(1, id) }
            .executeQuery().mapAll(::noteOf).firstOrNull()
    }

    suspend fun getByIds(ids: List<Long>): List<Note> {
        if (ids.isEmpty()) return emptyList()
        val list = ids.distinct().joinToString(",")
        return db.use { c ->
            c.createStatement().executeQuery("SELECT * FROM notes WHERE id IN ($list)").mapAll(::noteOf)
        }
    }

    suspend fun searchNotes(text: String, tag: String, archived: Int, trashed: Int, limit: Int): List<Note> =
        db.use { c ->
            c.prepareStatement(
                """
                SELECT * FROM notes
                WHERE (? = ''
                        OR title LIKE '%' || ? || '%'
                        OR body LIKE '%' || ? || '%'
                        OR tags LIKE '%' || ? || '%'
                        OR checklist LIKE '%' || ? || '%')
                  AND (? = '' OR tags LIKE '%' || ? || '%')
                  AND isDraft = 0
                  AND hidden = 0
                  AND (? = -1 OR archived = ?)
                  AND (? = -1
                        OR (? = 1 AND trashedAt IS NOT NULL)
                        OR (? = 0 AND trashedAt IS NULL))
                ORDER BY pinned DESC, updatedAt DESC
                LIMIT ?
                """.trimIndent()
            ).apply {
                setString(1, text); setString(2, text); setString(3, text); setString(4, text); setString(5, text)
                setString(6, tag); setString(7, tag)
                setInt(8, archived); setInt(9, archived)
                setInt(10, trashed); setInt(11, trashed); setInt(12, trashed)
                setInt(13, limit)
            }.executeQuery().mapAll(::noteOf)
        }

    fun getDrafts(): Flow<List<Note>> = db.watch("notes") {
        db.use { c ->
            c.prepareStatement(
                "SELECT * FROM notes WHERE isDraft = 1 AND trashedAt IS NULL " +
                    "ORDER BY COALESCE(draftSavedAt, updatedAt) DESC"
            ).executeQuery().mapAll(::noteOf)
        }
    }

    fun getHidden(): Flow<List<Note>> = db.watch("notes") {
        db.use { c ->
            c.prepareStatement(
                "SELECT * FROM notes WHERE hidden = 1 AND isDraft = 0 AND trashedAt IS NULL ORDER BY updatedAt DESC"
            ).executeQuery().mapAll(::noteOf)
        }
    }

    suspend fun draftCountOnce(): Int = db.use { c ->
        c.prepareStatement("SELECT COUNT(*) FROM notes WHERE isDraft = 1 AND trashedAt IS NULL")
            .executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    suspend fun maxManualOrderOnce(): Int = db.use { c ->
        c.prepareStatement("SELECT COALESCE(MAX(manualOrder), 0) FROM notes")
            .executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    suspend fun insert(note: Note): Long = db.write("notes") { c ->
        val ps = c.prepareStatement(
            "INSERT INTO notes (title, body, updatedAt, tags, attachments, archived, archivedAt, " +
                "pinned, color, isChecklist, checklist, trashedAt, manualOrder, isDraft, " +
                "draftSavedAt, hidden, isDoodle, doodle, bodySpans, formatOverride) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        )
        ps.setString(1, note.title); ps.setString(2, note.body); ps.setLong(3, note.updatedAt)
        ps.setString(4, note.tags); ps.setString(5, note.attachments)
        ps.setInt(6, if (note.archived) 1 else 0); ps.bindLongOrNull(7, note.archivedAt)
        ps.setInt(8, if (note.pinned) 1 else 0); ps.setString(9, note.color)
        ps.setInt(10, if (note.isChecklist) 1 else 0); ps.setString(11, note.checklist)
        ps.bindLongOrNull(12, note.trashedAt)
        ps.setInt(13, note.manualOrder); ps.setInt(14, if (note.isDraft) 1 else 0)
        ps.bindLongOrNull(15, note.draftSavedAt); ps.setInt(16, if (note.hidden) 1 else 0)
        ps.setInt(17, if (note.isDoodle) 1 else 0); ps.setString(18, note.doodle)
        ps.setString(19, note.bodySpans); ps.bindStringOrNull(20, note.formatOverride)
        ps.executeUpdate()
        ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
    }

    suspend fun update(note: Note) {
        db.write("notes") { c ->
            val ps = c.prepareStatement(
                "UPDATE notes SET title=?, body=?, updatedAt=?, tags=?, attachments=?, archived=?, " +
                    "archivedAt=?, pinned=?, color=?, isChecklist=?, checklist=?, trashedAt=?, " +
                    "manualOrder=?, isDraft=?, draftSavedAt=?, hidden=?, isDoodle=?, doodle=?, " +
                    "bodySpans=?, formatOverride=? WHERE id=?"
            )
            ps.setString(1, note.title); ps.setString(2, note.body); ps.setLong(3, note.updatedAt)
            ps.setString(4, note.tags); ps.setString(5, note.attachments)
            ps.setInt(6, if (note.archived) 1 else 0); ps.bindLongOrNull(7, note.archivedAt)
            ps.setInt(8, if (note.pinned) 1 else 0); ps.setString(9, note.color)
            ps.setInt(10, if (note.isChecklist) 1 else 0); ps.setString(11, note.checklist)
            ps.bindLongOrNull(12, note.trashedAt)
            ps.setInt(13, note.manualOrder); ps.setInt(14, if (note.isDraft) 1 else 0)
            ps.bindLongOrNull(15, note.draftSavedAt); ps.setInt(16, if (note.hidden) 1 else 0)
            ps.setInt(17, if (note.isDoodle) 1 else 0); ps.setString(18, note.doodle)
            ps.setString(19, note.bodySpans); ps.bindStringOrNull(20, note.formatOverride)
            ps.setLong(21, note.id)
            ps.executeUpdate()
        }
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        db.write("notes") { c ->
            c.prepareStatement("UPDATE notes SET pinned=? WHERE id=?").apply {
                setInt(1, if (pinned) 1 else 0); setLong(2, id)
            }.executeUpdate()
        }
    }

    suspend fun setManualOrder(id: Long, order: Int) {
        db.write("notes") { c ->
            c.prepareStatement("UPDATE notes SET manualOrder=? WHERE id=?").apply {
                setInt(1, order); setLong(2, id)
            }.executeUpdate()
        }
    }

    suspend fun delete(note: Note) {
        db.write("notes") { c ->
            c.prepareStatement("DELETE FROM notes WHERE id=?").apply { setLong(1, note.id) }.executeUpdate()
        }
    }

    suspend fun clearAll() {
        db.write("notes") { c -> c.createStatement().use { it.executeUpdate("DELETE FROM notes") } }
    }

    suspend fun rebuildFts() {
        db.use { c ->
            c.createStatement().use { st ->
                st.executeUpdate("INSERT INTO notes_fts(notes_fts) VALUES('rebuild')")
            }
        }
    }
}

class NoteEmbeddingDao internal constructor(private val db: Db) {

    suspend fun upsert(embedding: NoteEmbedding): Unit = db.write("note_embeddings") { c ->
        c.prepareStatement(
            "INSERT OR REPLACE INTO note_embeddings (noteId, model, dim, vec, updatedAt) VALUES (?, ?, ?, ?, ?)"
        ).apply {
            setLong(1, embedding.noteId)
            setString(2, embedding.model)
            setInt(3, embedding.dim)
            setBytes(4, embedding.vec)
            setLong(5, embedding.updatedAt)
        }.executeUpdate()
        Unit
    }

    suspend fun getForModel(model: String): List<NoteEmbedding> = db.use { c ->
        c.prepareStatement("SELECT * FROM note_embeddings WHERE model = ?").apply { setString(1, model) }
            .executeQuery().mapAll(::noteEmbeddingOf)
    }

    suspend fun getForNote(noteId: Long): List<NoteEmbedding> = db.use { c ->
        c.prepareStatement("SELECT * FROM note_embeddings WHERE noteId = ?").apply { setLong(1, noteId) }
            .executeQuery().mapAll(::noteEmbeddingOf)
    }

    suspend fun delete(noteId: Long, model: String): Unit = db.write("note_embeddings") { c ->
        c.prepareStatement("DELETE FROM note_embeddings WHERE noteId = ? AND model = ?").apply {
            setLong(1, noteId)
            setString(2, model)
        }.executeUpdate()
        Unit
    }

    suspend fun deleteAllForModel(model: String): Unit = db.write("note_embeddings") { c ->
        c.prepareStatement("DELETE FROM note_embeddings WHERE model = ?").apply { setString(1, model) }.executeUpdate()
        Unit
    }

    suspend fun clearAll(): Unit = db.write("note_embeddings") { c ->
        c.createStatement().use { st -> st.executeUpdate("DELETE FROM note_embeddings") }
        Unit
    }
}

class NoteVersionDao internal constructor(private val db: Db) {

    fun getForNote(noteId: Long): Flow<List<NoteVersion>> = db.watch("note_versions") { getForNoteOnce(noteId) }

    suspend fun getForNoteOnce(noteId: Long): List<NoteVersion> = db.use { c ->
        c.prepareStatement("SELECT * FROM note_versions WHERE noteId = ? ORDER BY savedAt DESC")
            .apply { setLong(1, noteId) }.executeQuery().mapAll(::versionOf)
    }

    suspend fun getAllOnce(): List<NoteVersion> = db.use { c ->
        c.prepareStatement("SELECT * FROM note_versions ORDER BY savedAt DESC").executeQuery().mapAll(::versionOf)
    }

    suspend fun countForNote(noteId: Long): Int = db.use { c ->
        c.prepareStatement("SELECT COUNT(*) FROM note_versions WHERE noteId = ?")
            .apply { setLong(1, noteId) }.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    suspend fun insert(version: NoteVersion): Long = db.write("note_versions") { c ->
        val ps = c.prepareStatement(
            "INSERT INTO note_versions (noteId, title, body, tags, isChecklist, checklist, savedAt) " +
                "VALUES (?,?,?,?,?,?,?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        )
        ps.setLong(1, version.noteId); ps.setString(2, version.title); ps.setString(3, version.body)
        ps.setString(4, version.tags); ps.setInt(5, if (version.isChecklist) 1 else 0)
        ps.setString(6, version.checklist); ps.setLong(7, version.savedAt)
        ps.executeUpdate()
        ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
    }

    suspend fun delete(version: NoteVersion) {
        db.write("note_versions") { c ->
            c.prepareStatement("DELETE FROM note_versions WHERE id=?").apply { setLong(1, version.id) }.executeUpdate()
        }
    }

    suspend fun deleteForNote(noteId: Long) {
        db.write("note_versions") { c ->
            c.prepareStatement("DELETE FROM note_versions WHERE noteId=?").apply { setLong(1, noteId) }.executeUpdate()
        }
    }

    suspend fun deleteById(id: Long) {
        db.write("note_versions") { c ->
            c.prepareStatement("DELETE FROM note_versions WHERE id=?").apply { setLong(1, id) }.executeUpdate()
        }
    }

    suspend fun trimTo(noteId: Long, keep: Int) {
        db.write("note_versions") { c ->
            c.prepareStatement(
                "DELETE FROM note_versions WHERE noteId = ? AND id NOT IN (" +
                    "SELECT id FROM note_versions WHERE noteId = ? ORDER BY savedAt DESC LIMIT ?)"
            ).apply { setLong(1, noteId); setLong(2, noteId); setInt(3, keep) }.executeUpdate()
        }
    }

    suspend fun pruneOrphaned() {
        db.write("note_versions") { c ->
            c.createStatement().use {
                it.executeUpdate("DELETE FROM note_versions WHERE noteId NOT IN (SELECT id FROM notes)")
            }
        }
    }

    suspend fun clearAll() {
        db.write("note_versions") { c -> c.createStatement().use { it.executeUpdate("DELETE FROM note_versions") } }
    }
}

private fun taskVersionOf(rs: ResultSet) = TaskVersion(
    id = rs.getLong("id"),
    taskId = rs.getLong("taskId"),
    title = rs.getString("title"),
    notes = rs.getString("notes"),
    subtasks = rs.getString("subtasks"),
    priority = rs.getInt("priority"),
    dueAt = rs.longOrNull("dueAt"),
    savedAt = rs.getLong("savedAt")
)

class TaskVersionDao internal constructor(private val db: Db) {

    fun getForTask(taskId: Long): Flow<List<TaskVersion>> = db.watch("task_versions") { getForTaskOnce(taskId) }

    suspend fun getForTaskOnce(taskId: Long): List<TaskVersion> = db.use { c ->
        c.prepareStatement("SELECT * FROM task_versions WHERE taskId = ? ORDER BY savedAt DESC")
            .apply { setLong(1, taskId) }.executeQuery().mapAll(::taskVersionOf)
    }

    suspend fun getAllOnce(): List<TaskVersion> = db.use { c ->
        c.prepareStatement("SELECT * FROM task_versions ORDER BY savedAt DESC").executeQuery().mapAll(::taskVersionOf)
    }

    suspend fun countForTask(taskId: Long): Int = db.use { c ->
        c.prepareStatement("SELECT COUNT(*) FROM task_versions WHERE taskId = ?")
            .apply { setLong(1, taskId) }.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    suspend fun insert(version: TaskVersion): Long = db.write("task_versions") { c ->
        val ps = c.prepareStatement(
            "INSERT INTO task_versions (taskId, title, notes, subtasks, priority, dueAt, savedAt) " +
                "VALUES (?,?,?,?,?,?,?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        )
        ps.setLong(1, version.taskId); ps.setString(2, version.title); ps.setString(3, version.notes)
        ps.setString(4, version.subtasks); ps.setInt(5, version.priority)
        ps.bindLongOrNull(6, version.dueAt); ps.setLong(7, version.savedAt)
        ps.executeUpdate()
        ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
    }

    suspend fun delete(version: TaskVersion) {
        db.write("task_versions") { c ->
            c.prepareStatement("DELETE FROM task_versions WHERE id=?").apply { setLong(1, version.id) }.executeUpdate()
        }
    }

    suspend fun deleteForTask(taskId: Long) {
        db.write("task_versions") { c ->
            c.prepareStatement("DELETE FROM task_versions WHERE taskId=?").apply { setLong(1, taskId) }.executeUpdate()
        }
    }

    suspend fun deleteById(id: Long) {
        db.write("task_versions") { c ->
            c.prepareStatement("DELETE FROM task_versions WHERE id=?").apply { setLong(1, id) }.executeUpdate()
        }
    }

    suspend fun trimTo(taskId: Long, keep: Int) {
        db.write("task_versions") { c ->
            c.prepareStatement(
                "DELETE FROM task_versions WHERE taskId = ? AND id NOT IN (" +
                    "SELECT id FROM task_versions WHERE taskId = ? ORDER BY savedAt DESC LIMIT ?)"
            ).apply { setLong(1, taskId); setLong(2, taskId); setInt(3, keep) }.executeUpdate()
        }
    }

    suspend fun pruneOrphaned() {
        db.write("task_versions") { c ->
            c.createStatement().use { it.executeUpdate("DELETE FROM task_versions WHERE taskId NOT IN (SELECT id FROM tasks)") }
        }
    }

    suspend fun clearAll() {
        db.write("task_versions") { c -> c.createStatement().use { it.executeUpdate("DELETE FROM task_versions") } }
    }
}

class TaskDao internal constructor(private val db: Db) {

    fun getAll(): Flow<List<Task>> = db.watch("tasks") { getAllOnce() }

    suspend fun getAllOnce(): List<Task> = db.use { c ->
        c.prepareStatement("SELECT * FROM tasks ORDER BY createdAt DESC").executeQuery().mapAll(::taskOf)
    }

    suspend fun getByIdOnce(id: Long): Task? = db.use { c ->
        c.prepareStatement("SELECT * FROM tasks WHERE id = ?").apply { setLong(1, id) }
            .executeQuery().mapAll(::taskOf).firstOrNull()
    }

    suspend fun getByIds(ids: List<Long>): List<Task> {
        if (ids.isEmpty()) return emptyList()
        val list = ids.distinct().joinToString(",")
        return db.use { c ->
            c.createStatement().executeQuery("SELECT * FROM tasks WHERE id IN ($list)").mapAll(::taskOf)
        }
    }

    suspend fun searchTasks(
        text: String,
        done: Int,
        trashed: Int,
        minPriority: Int,
        dueBefore: Long,
        dueAfter: Long,
        limit: Int
    ): List<Task> = db.use { c ->
        c.prepareStatement(
            """
            SELECT * FROM tasks
            WHERE (? = ''
                    OR title LIKE '%' || ? || '%'
                    OR notes LIKE '%' || ? || '%'
                    OR subtasks LIKE '%' || ? || '%')
              AND isDraft = 0
              AND hidden = 0
              AND (? = -1 OR isDone = ?)
              AND (? = -1
                    OR (? = 1 AND trashedAt IS NOT NULL)
                    OR (? = 0 AND trashedAt IS NULL))
              AND (? = -1 OR priority >= ?)
              AND (? = -1 OR (dueAt IS NOT NULL AND dueAt <= ?))
              AND (? = -1 OR (dueAt IS NOT NULL AND dueAt >= ?))
            ORDER BY pinned DESC, priority DESC, COALESCE(dueAt, 9223372036854775807) ASC, createdAt DESC
            LIMIT ?
            """.trimIndent()
        ).apply {
            setString(1, text); setString(2, text); setString(3, text); setString(4, text)
            setInt(5, done); setInt(6, done)
            setInt(7, trashed); setInt(8, trashed); setInt(9, trashed)
            setInt(10, minPriority); setInt(11, minPriority)
            setLong(12, dueBefore); setLong(13, dueBefore)
            setLong(14, dueAfter); setLong(15, dueAfter)
            setInt(16, limit)
        }.executeQuery().mapAll(::taskOf)
    }

    fun getActive(): Flow<List<Task>> = db.watch("tasks") {
        db.use { c ->
            c.prepareStatement("SELECT * FROM tasks WHERE isDone = 0 AND trashedAt IS NULL AND isDraft = 0 AND hidden = 0 ORDER BY createdAt DESC")
                .executeQuery().mapAll(::taskOf)
        }
    }

    suspend fun activeCountOnce(): Int = db.use { c ->
        c.prepareStatement("SELECT COUNT(*) FROM tasks WHERE isDone = 0 AND trashedAt IS NULL AND isDraft = 0 AND hidden = 0")
            .executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    fun getCompleted(): Flow<List<Task>> = db.watch("tasks") {
        db.use { c ->
            c.prepareStatement(
                "SELECT * FROM tasks WHERE isDone = 1 AND trashedAt IS NULL AND isDraft = 0 AND hidden = 0 " +
                    "ORDER BY COALESCE(completedAt, createdAt) DESC"
            ).executeQuery().mapAll(::taskOf)
        }
    }

    fun getTrashed(): Flow<List<Task>> = db.watch("tasks") {
        db.use { c ->
            c.prepareStatement("SELECT * FROM tasks WHERE trashedAt IS NOT NULL AND isDraft = 0 ORDER BY trashedAt DESC")
                .executeQuery().mapAll(::taskOf)
        }
    }

    fun getDrafts(): Flow<List<Task>> = db.watch("tasks") {
        db.use { c ->
            c.prepareStatement(
                "SELECT * FROM tasks WHERE isDraft = 1 AND trashedAt IS NULL " +
                    "ORDER BY COALESCE(draftSavedAt, createdAt) DESC"
            ).executeQuery().mapAll(::taskOf)
        }
    }

    fun getHidden(): Flow<List<Task>> = db.watch("tasks") {
        db.use { c ->
            c.prepareStatement(
                "SELECT * FROM tasks WHERE hidden = 1 AND isDraft = 0 AND trashedAt IS NULL ORDER BY createdAt DESC"
            ).executeQuery().mapAll(::taskOf)
        }
    }

    suspend fun draftCountOnce(): Int = db.use { c ->
        c.prepareStatement("SELECT COUNT(*) FROM tasks WHERE isDraft = 1 AND trashedAt IS NULL")
            .executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    suspend fun maxManualOrderOnce(): Int = db.use { c ->
        c.prepareStatement("SELECT COALESCE(MAX(manualOrder), 0) FROM tasks")
            .executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    suspend fun insert(task: Task): Long = db.write("tasks") { c ->
        val ps = c.prepareStatement(
            "INSERT INTO tasks (title, isDone, createdAt, attachments, dueAt, notes, completedAt, " +
                "priority, pinned, subtasks, repeatRule, reminderEnabled, trashedAt, manualOrder, " +
                "isDraft, draftSavedAt, hidden, notesSpans, formatOverride) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        )
        ps.setString(1, task.title); ps.setInt(2, if (task.isDone) 1 else 0); ps.setLong(3, task.createdAt)
        ps.setString(4, task.attachments); ps.bindLongOrNull(5, task.dueAt); ps.setString(6, task.notes)
        ps.bindLongOrNull(7, task.completedAt); ps.setInt(8, task.priority)
        ps.setInt(9, if (task.pinned) 1 else 0); ps.setString(10, task.subtasks)
        ps.setString(11, task.repeatRule); ps.setInt(12, if (task.reminderEnabled) 1 else 0)
        ps.bindLongOrNull(13, task.trashedAt)
        ps.setInt(14, task.manualOrder); ps.setInt(15, if (task.isDraft) 1 else 0)
        ps.bindLongOrNull(16, task.draftSavedAt); ps.setInt(17, if (task.hidden) 1 else 0)
        ps.setString(18, task.notesSpans); ps.bindStringOrNull(19, task.formatOverride)
        ps.executeUpdate()
        ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
    }

    suspend fun update(task: Task) {
        db.write("tasks") { c ->
            val ps = c.prepareStatement(
                "UPDATE tasks SET title=?, isDone=?, createdAt=?, attachments=?, dueAt=?, notes=?, " +
                    "completedAt=?, priority=?, pinned=?, subtasks=?, repeatRule=?, reminderEnabled=?, " +
                    "trashedAt=?, manualOrder=?, isDraft=?, draftSavedAt=?, hidden=?, " +
                    "notesSpans=?, formatOverride=? WHERE id=?"
            )
            ps.setString(1, task.title); ps.setInt(2, if (task.isDone) 1 else 0); ps.setLong(3, task.createdAt)
            ps.setString(4, task.attachments); ps.bindLongOrNull(5, task.dueAt); ps.setString(6, task.notes)
            ps.bindLongOrNull(7, task.completedAt); ps.setInt(8, task.priority)
            ps.setInt(9, if (task.pinned) 1 else 0); ps.setString(10, task.subtasks)
            ps.setString(11, task.repeatRule); ps.setInt(12, if (task.reminderEnabled) 1 else 0)
            ps.bindLongOrNull(13, task.trashedAt)
            ps.setInt(14, task.manualOrder); ps.setInt(15, if (task.isDraft) 1 else 0)
            ps.bindLongOrNull(16, task.draftSavedAt); ps.setInt(17, if (task.hidden) 1 else 0)
            ps.setString(18, task.notesSpans); ps.bindStringOrNull(19, task.formatOverride)
            ps.setLong(20, task.id)
            ps.executeUpdate()
        }
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        db.write("tasks") { c ->
            c.prepareStatement("UPDATE tasks SET pinned=? WHERE id=?").apply {
                setInt(1, if (pinned) 1 else 0); setLong(2, id)
            }.executeUpdate()
        }
    }

    suspend fun setManualOrder(id: Long, order: Int) {
        db.write("tasks") { c ->
            c.prepareStatement("UPDATE tasks SET manualOrder=? WHERE id=?").apply {
                setInt(1, order); setLong(2, id)
            }.executeUpdate()
        }
    }

    suspend fun delete(task: Task) {
        db.write("tasks") { c ->
            c.prepareStatement("DELETE FROM tasks WHERE id=?").apply { setLong(1, task.id) }.executeUpdate()
        }
    }

    suspend fun clearAll() {
        db.write("tasks") { c -> c.createStatement().use { it.executeUpdate("DELETE FROM tasks") } }
    }

    suspend fun rebuildFts() {
        db.use { c ->
            c.createStatement().use { st ->
                st.executeUpdate("INSERT INTO tasks_fts(tasks_fts) VALUES('rebuild')")
            }
        }
    }
}

data class ConversationContent(
    val conversationId: Long,
    val content: String?
)

class ChatDao internal constructor(private val db: Db) {

    fun getAll(): Flow<List<ChatMessage>> = db.watch("chat_messages") { getAllOnce() }

    suspend fun getAllOnce(): List<ChatMessage> = db.use { c ->
        c.prepareStatement("SELECT * FROM chat_messages ORDER BY timestamp ASC").executeQuery().mapAll(::messageOf)
    }

    fun getForConversation(conversationId: Long): Flow<List<ChatMessage>> =
        db.watch("chat_messages") { getForConversationOnce(conversationId) }

    suspend fun getForConversationOnce(conversationId: Long): List<ChatMessage> = db.use { c ->
        c.prepareStatement("SELECT * FROM chat_messages WHERE conversationId = ? ORDER BY timestamp ASC")
            .apply { setLong(1, conversationId) }.executeQuery().mapAll(::messageOf)
    }

    suspend fun countInConversation(conversationId: Long): Int = db.use { c ->
        c.prepareStatement("SELECT COUNT(*) FROM chat_messages WHERE conversationId = ?")
            .apply { setLong(1, conversationId) }.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    suspend fun conversationContents(): List<ConversationContent> = db.use { c ->
        c.prepareStatement(
            "SELECT conversationId AS conversationId, GROUP_CONCAT(content, ' ') AS content " +
                "FROM chat_messages GROUP BY conversationId"
        ).executeQuery().mapAll { rs -> ConversationContent(rs.getLong("conversationId"), rs.stringOrNull("content")) }
    }

    suspend fun insert(message: ChatMessage): Long = db.write("chat_messages") { c ->
        val ps = c.prepareStatement(
            "INSERT INTO chat_messages (role, content, timestamp, attachmentMime, attachmentData, " +
                "attachmentName, attachmentList, conversationId, tokens, replyToId, agentTrace) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        )
        ps.setString(1, message.role); ps.setString(2, message.content); ps.setLong(3, message.timestamp)
        ps.bindStringOrNull(4, message.attachmentMime); ps.bindStringOrNull(5, message.attachmentData)
        ps.bindStringOrNull(6, message.attachmentName); ps.bindStringOrNull(7, message.attachmentList)
        ps.setLong(8, message.conversationId); ps.setInt(9, message.tokens); ps.setLong(10, message.replyToId)
        ps.bindStringOrNull(11, message.agentTrace)
        ps.executeUpdate()
        ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
    }

    suspend fun deleteByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        val list = ids.joinToString(",")
        db.write("chat_messages") { c ->
            c.createStatement().use { it.executeUpdate("DELETE FROM chat_messages WHERE id IN ($list)") }
        }
    }

    suspend fun clearConversation(conversationId: Long) {
        db.write("chat_messages") { c ->
            c.prepareStatement("DELETE FROM chat_messages WHERE conversationId = ?")
                .apply { setLong(1, conversationId) }.executeUpdate()
        }
    }

    suspend fun clearAll() {
        db.write("chat_messages") { c -> c.createStatement().use { it.executeUpdate("DELETE FROM chat_messages") } }
    }
}

class ChatConversationDao internal constructor(private val db: Db) {

    fun getAll(): Flow<List<ChatConversation>> = db.watch("chat_conversations") { getAllOnce() }

    suspend fun getAllOnce(): List<ChatConversation> = db.use { c ->
        c.prepareStatement("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
            .executeQuery().mapAll(::conversationOf)
    }

    suspend fun getById(id: Long): ChatConversation? = db.use { c ->
        c.prepareStatement("SELECT * FROM chat_conversations WHERE id = ?").apply { setLong(1, id) }
            .executeQuery().mapAll(::conversationOf).firstOrNull()
    }

    suspend fun insert(conversation: ChatConversation): Long = db.write("chat_conversations") { c ->
        val ps = c.prepareStatement(
            "INSERT INTO chat_conversations (title, createdAt, updatedAt) VALUES (?,?,?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        )
        ps.setString(1, conversation.title); ps.setLong(2, conversation.createdAt); ps.setLong(3, conversation.updatedAt)
        ps.executeUpdate()
        ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
    }

    suspend fun update(conversation: ChatConversation) {
        db.write("chat_conversations") { c ->
            c.prepareStatement("UPDATE chat_conversations SET title=?, createdAt=?, updatedAt=? WHERE id=?")
                .apply {
                    setString(1, conversation.title); setLong(2, conversation.createdAt)
                    setLong(3, conversation.updatedAt); setLong(4, conversation.id)
                }.executeUpdate()
        }
    }

    suspend fun delete(conversation: ChatConversation) {
        db.write("chat_conversations") { c ->
            c.prepareStatement("DELETE FROM chat_conversations WHERE id=?")
                .apply { setLong(1, conversation.id) }.executeUpdate()
        }
    }

    suspend fun clearAll() {
        db.write("chat_conversations") { c ->
            c.createStatement().use { it.executeUpdate("DELETE FROM chat_conversations") }
        }
    }
}

private fun notebookOf(rs: ResultSet) = Notebook(
    id = rs.getLong("id"),
    title = rs.getString("title"),
    createdAt = rs.getLong("createdAt"),
    updatedAt = rs.getLong("updatedAt"),
    color = rs.getString("color") ?: "",
    manualOrder = rs.getInt("manualOrder"),
    trashedAt = rs.getLong("trashedAt").let { if (rs.wasNull()) null else it }
)

private fun notebookItemOf(rs: ResultSet) = NotebookItem(
    id = rs.getLong("id"),
    notebookId = rs.getLong("notebookId"),
    itemKind = rs.getString("itemKind"),
    itemId = rs.getLong("itemId"),
    addedAt = rs.getLong("addedAt")
)

data class NotebookCount(val notebookId: Long, val count: Int)

class NotebookDao internal constructor(private val db: Db) {

    fun getAll(): Flow<List<Notebook>> = db.watch("notebooks") { getAllOnce() }

    suspend fun getAllOnce(): List<Notebook> = db.use { c ->
        c.prepareStatement(
            "SELECT * FROM notebooks WHERE trashedAt IS NULL ORDER BY updatedAt DESC"
        ).executeQuery().mapAll(::notebookOf)
    }

    suspend fun getAllIncludingTrashedOnce(): List<Notebook> = db.use { c ->
        c.prepareStatement("SELECT * FROM notebooks ORDER BY updatedAt DESC").executeQuery().mapAll(::notebookOf)
    }

    fun getTrashed(): Flow<List<Notebook>> = db.watch("notebooks") { getTrashedOnce() }

    suspend fun getTrashedOnce(): List<Notebook> = db.use { c ->
        c.prepareStatement(
            "SELECT * FROM notebooks WHERE trashedAt IS NOT NULL ORDER BY trashedAt DESC"
        ).executeQuery().mapAll(::notebookOf)
    }

    suspend fun getByIdOnce(id: Long): Notebook? = db.use { c ->
        c.prepareStatement("SELECT * FROM notebooks WHERE id = ?").apply { setLong(1, id) }
            .executeQuery().mapAll(::notebookOf).firstOrNull()
    }

    fun itemCounts(): Flow<List<NotebookCount>> = db.watch("notebook_items") {
        db.use { c ->
            c.createStatement().executeQuery(
                "SELECT notebookId AS notebookId, COUNT(*) AS count FROM notebook_items GROUP BY notebookId"
            ).mapAll { rs -> NotebookCount(rs.getLong("notebookId"), rs.getInt("count")) }
        }
    }

    fun getItems(notebookId: Long): Flow<List<NotebookItem>> =
        db.watch("notebook_items") { getItemsOnce(notebookId) }

    suspend fun getItemsOnce(notebookId: Long): List<NotebookItem> = db.use { c ->
        c.prepareStatement("SELECT * FROM notebook_items WHERE notebookId = ? ORDER BY addedAt DESC")
            .apply { setLong(1, notebookId) }.executeQuery().mapAll(::notebookItemOf)
    }

    suspend fun getItemsByKindOnce(kind: String): List<NotebookItem> = db.use { c ->
        c.prepareStatement("SELECT * FROM notebook_items WHERE itemKind = ?")
            .apply { setString(1, kind) }.executeQuery().mapAll(::notebookItemOf)
    }

    suspend fun membershipExistsOnce(notebookId: Long, kind: String, itemId: Long): Int = db.use { c ->
        c.prepareStatement(
            "SELECT COUNT(*) FROM notebook_items WHERE notebookId = ? AND itemKind = ? AND itemId = ?"
        ).apply { setLong(1, notebookId); setString(2, kind); setLong(3, itemId) }
            .executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    suspend fun insert(notebook: Notebook): Long = db.write("notebooks", "notebook_items") { c ->
        val ps = c.prepareStatement(
            "INSERT INTO notebooks (title, createdAt, updatedAt, color, manualOrder, trashedAt) VALUES (?,?,?,?,?,?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        )
        ps.setString(1, notebook.title); ps.setLong(2, notebook.createdAt); ps.setLong(3, notebook.updatedAt)
        ps.setString(4, notebook.color); ps.setInt(5, notebook.manualOrder)
        if (notebook.trashedAt == null) ps.setNull(6, java.sql.Types.INTEGER)
        else ps.setLong(6, notebook.trashedAt)
        ps.executeUpdate()
        ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
    }

    suspend fun update(notebook: Notebook) {
        db.write("notebooks") { c ->
            c.prepareStatement(
                "UPDATE notebooks SET title=?, createdAt=?, updatedAt=?, color=?, manualOrder=?, trashedAt=? WHERE id=?"
            ).apply {
                setString(1, notebook.title); setLong(2, notebook.createdAt)
                setLong(3, notebook.updatedAt); setString(4, notebook.color)
                setInt(5, notebook.manualOrder)
                if (notebook.trashedAt == null) setNull(6, java.sql.Types.INTEGER)
                else setLong(6, notebook.trashedAt)
                setLong(7, notebook.id)
            }.executeUpdate()
        }
    }

    suspend fun purgeTrashedBefore(cutoff: Long) {
        db.write("notebooks", "notebook_items") { c ->
            c.prepareStatement("SELECT id FROM notebooks WHERE trashedAt IS NOT NULL AND trashedAt < ?")
                .apply { setLong(1, cutoff) }.executeQuery().mapAll { it.getLong("id") }
                .forEach { id ->
                    c.prepareStatement("DELETE FROM notebook_items WHERE notebookId=?").apply { setLong(1, id) }.executeUpdate()
                    c.prepareStatement("DELETE FROM notebooks WHERE id=?").apply { setLong(1, id) }.executeUpdate()
                }
        }
    }

    suspend fun insertItem(item: NotebookItem): Long = db.write("notebook_items", "notebooks") { c ->
        val ps = c.prepareStatement(
            "INSERT INTO notebook_items (notebookId, itemKind, itemId, addedAt) VALUES (?,?,?,?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        )
        ps.setLong(1, item.notebookId); ps.setString(2, item.itemKind)
        ps.setLong(3, item.itemId); ps.setLong(4, item.addedAt)
        ps.executeUpdate()
        ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
    }

    suspend fun deleteItemById(itemId: Long) {
        db.write("notebook_items", "notebooks") { c ->
            c.prepareStatement("DELETE FROM notebook_items WHERE id=?")
                .apply { setLong(1, itemId) }.executeUpdate()
        }
    }

    suspend fun deleteItemsForNotebook(notebookId: Long) {
        db.write("notebook_items", "notebooks") { c ->
            c.prepareStatement("DELETE FROM notebook_items WHERE notebookId=?")
                .apply { setLong(1, notebookId) }.executeUpdate()
        }
    }

    suspend fun deleteById(notebookId: Long) {
        db.write("notebooks") { c ->
            c.prepareStatement("DELETE FROM notebooks WHERE id=?")
                .apply { setLong(1, notebookId) }.executeUpdate()
        }
    }

    suspend fun clearAll() {
        db.write("notebooks", "notebook_items") { c ->
            c.createStatement().use {
                it.executeUpdate("DELETE FROM notebook_items")
                it.executeUpdate("DELETE FROM notebooks")
            }
        }
    }

    suspend fun clearAllItems() {
        db.write("notebook_items") { c ->
            c.createStatement().use { it.executeUpdate("DELETE FROM notebook_items") }
        }
    }
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
