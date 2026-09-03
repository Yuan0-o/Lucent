package com.lucent.app.data

import kotlinx.coroutines.flow.Flow
import java.sql.ResultSet

// Desktop twins of the Android Room DAOs (app/.../data/Daos.kt), method-for-method: same names,
// same signatures, same SQL semantics (the queries are copied from the Room annotations), same
// reactive behaviour through Db.watch. Everything that calls a DAO — the assistant tools, backup,
// search, every screen — compiles against these exactly as it does against Room.

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
    // 1.1.0 group A. Read positionally by NAME, like every other column here, so the mapper is
    // insensitive to where ALTER TABLE happened to append them.
    manualOrder = rs.getInt("manualOrder"),
    isDraft = rs.getInt("isDraft") != 0,
    draftSavedAt = rs.longOrNull("draftSavedAt"),
    hidden = rs.getInt("hidden") != 0,
    isDoodle = rs.getInt("isDoodle") != 0,
    doodle = rs.getString("doodle"),
    // INTEGRATION (C task 20) — read by name like the rest, so it does not matter where the ALTER
    // appended it. Nullable-safe: a row written before the column existed reads back as "".
    bodySpans = rs.getString("bodySpans") ?: ""
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
    // INTEGRATION (C task 20) — the task twin of Note.bodySpans.
    notesSpans = rs.getString("notesSpans") ?: ""
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
    replyToId = rs.getLong("replyToId")
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

    /** Task A10 — the draft area, a sibling of the trash. */
    fun getDrafts(): Flow<List<Note>> = db.watch("notes") {
        db.use { c ->
            c.prepareStatement(
                "SELECT * FROM notes WHERE isDraft = 1 AND trashedAt IS NULL " +
                    "ORDER BY COALESCE(draftSavedAt, updatedAt) DESC"
            ).executeQuery().mapAll(::noteOf)
        }
    }

    /** Task A21 — the hidden area; only read once the user has unlocked it in Settings. */
    fun getHidden(): Flow<List<Note>> = db.watch("notes") {
        db.use { c ->
            c.prepareStatement(
                "SELECT * FROM notes WHERE hidden = 1 AND isDraft = 0 AND trashedAt IS NULL ORDER BY updatedAt DESC"
            ).executeQuery().mapAll(::noteOf)
        }
    }

    /** Task A10 — is there anything to offer to restore on the next launch? */
    suspend fun draftCountOnce(): Int = db.use { c ->
        c.prepareStatement("SELECT COUNT(*) FROM notes WHERE isDraft = 1 AND trashedAt IS NULL")
            .executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    /** Task A16 — highest order in use, so a drag can append without renumbering the table. */
    suspend fun maxManualOrderOnce(): Int = db.use { c ->
        c.prepareStatement("SELECT COALESCE(MAX(manualOrder), 0) FROM notes")
            .executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    suspend fun insert(note: Note): Long = db.write("notes") { c ->
        val ps = c.prepareStatement(
            "INSERT INTO notes (title, body, updatedAt, tags, attachments, archived, archivedAt, " +
                "pinned, color, isChecklist, checklist, trashedAt, manualOrder, isDraft, " +
                "draftSavedAt, hidden, isDoodle, doodle, bodySpans) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
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
        ps.setString(19, note.bodySpans)
        ps.executeUpdate()
        ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
    }

    suspend fun update(note: Note) {
        db.write("notes") { c ->
            val ps = c.prepareStatement(
                "UPDATE notes SET title=?, body=?, updatedAt=?, tags=?, attachments=?, archived=?, " +
                    "archivedAt=?, pinned=?, color=?, isChecklist=?, checklist=?, trashedAt=?, " +
                    "manualOrder=?, isDraft=?, draftSavedAt=?, hidden=?, isDoodle=?, doodle=?, " +
                    "bodySpans=? WHERE id=?"
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
            ps.setString(19, note.bodySpans)
            ps.setLong(20, note.id)
            ps.executeUpdate()
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

    /** Task A19 — delete one revision on the user's explicit instruction. */
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

/**
 * Task A19 — desktop twin of the Android TaskVersionDao, method-for-method, so the history screens
 * and BackupManager compile against one shape on both platforms.
 */
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

    /** Task A19 — delete one revision on the user's explicit instruction. */
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

    /** Task A10 — the draft area for tasks; mirrors [NoteDao.getDrafts]. */
    fun getDrafts(): Flow<List<Task>> = db.watch("tasks") {
        db.use { c ->
            c.prepareStatement(
                "SELECT * FROM tasks WHERE isDraft = 1 AND trashedAt IS NULL " +
                    "ORDER BY COALESCE(draftSavedAt, createdAt) DESC"
            ).executeQuery().mapAll(::taskOf)
        }
    }

    /** Task A21 — the hidden area for tasks. */
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

    /** Task A16 — see [NoteDao.maxManualOrderOnce]. */
    suspend fun maxManualOrderOnce(): Int = db.use { c ->
        c.prepareStatement("SELECT COALESCE(MAX(manualOrder), 0) FROM tasks")
            .executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    suspend fun insert(task: Task): Long = db.write("tasks") { c ->
        val ps = c.prepareStatement(
            "INSERT INTO tasks (title, isDone, createdAt, attachments, dueAt, notes, completedAt, " +
                "priority, pinned, subtasks, repeatRule, reminderEnabled, trashedAt, manualOrder, " +
                "isDraft, draftSavedAt, hidden, notesSpans) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
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
        ps.setString(18, task.notesSpans)
        ps.executeUpdate()
        ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
    }

    suspend fun update(task: Task) {
        db.write("tasks") { c ->
            val ps = c.prepareStatement(
                "UPDATE tasks SET title=?, isDone=?, createdAt=?, attachments=?, dueAt=?, notes=?, " +
                    "completedAt=?, priority=?, pinned=?, subtasks=?, repeatRule=?, reminderEnabled=?, " +
                    "trashedAt=?, manualOrder=?, isDraft=?, draftSavedAt=?, hidden=?, " +
                    "notesSpans=? WHERE id=?"
            )
            ps.setString(1, task.title); ps.setInt(2, if (task.isDone) 1 else 0); ps.setLong(3, task.createdAt)
            ps.setString(4, task.attachments); ps.bindLongOrNull(5, task.dueAt); ps.setString(6, task.notes)
            ps.bindLongOrNull(7, task.completedAt); ps.setInt(8, task.priority)
            ps.setInt(9, if (task.pinned) 1 else 0); ps.setString(10, task.subtasks)
            ps.setString(11, task.repeatRule); ps.setInt(12, if (task.reminderEnabled) 1 else 0)
            ps.bindLongOrNull(13, task.trashedAt)
            ps.setInt(14, task.manualOrder); ps.setInt(15, if (task.isDraft) 1 else 0)
            ps.bindLongOrNull(16, task.draftSavedAt); ps.setInt(17, if (task.hidden) 1 else 0)
            ps.setString(18, task.notesSpans)
            ps.setLong(19, task.id)
            ps.executeUpdate()
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
}

/** Projection for [ChatDao.conversationContents] — see the Android original. */
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

    /** Insert one message, returning its new row id — see the Android twin for why the id matters. */
    suspend fun insert(message: ChatMessage): Long = db.write("chat_messages") { c ->
        val ps = c.prepareStatement(
            "INSERT INTO chat_messages (role, content, timestamp, attachmentMime, attachmentData, " +
                "attachmentName, attachmentList, conversationId, tokens, replyToId) VALUES (?,?,?,?,?,?,?,?,?,?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        )
        ps.setString(1, message.role); ps.setString(2, message.content); ps.setLong(3, message.timestamp)
        ps.bindStringOrNull(4, message.attachmentMime); ps.bindStringOrNull(5, message.attachmentData)
        ps.bindStringOrNull(6, message.attachmentName); ps.bindStringOrNull(7, message.attachmentList)
        ps.setLong(8, message.conversationId); ps.setInt(9, message.tokens); ps.setLong(10, message.replyToId)
        ps.executeUpdate()
        ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
    }

    /**
     * Delete a specific set of messages (B-group task 11). The id list is interpolated rather than
     * bound because JDBC has no parameter form for `IN (?)` — safe here precisely because these are
     * Longs: they come from ChatMessage.id, and a Long cannot carry SQL. An empty set is a no-op
     * rather than an `IN ()`, which SQLite rejects outright.
     */
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
