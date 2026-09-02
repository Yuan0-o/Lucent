package com.lucent.app.data

/**
 * The one place that decides when a task's previous content is worth keeping (task A19).
 *
 * The exact counterpart of [NoteHistory], and deliberately written to the same shape: every path
 * that overwrites a task — the editor, the assistant's task tools, a restore from an older
 * version — routes through [recordIfChanged], so history is captured identically no matter who did
 * the writing. Keeping that decision in one object rather than duplicating a
 * `taskVersionDao().insert(...)` at each call site is what stops the paths from drifting apart and
 * quietly leaving one of them without a safety net.
 *
 * ### Why tasks needed this too
 *
 * Notes have had revision history since the feature existed; tasks have not, on the unspoken
 * assumption that a task is a line of text you tick off. That is true of a task with three words in
 * it and false of the ones people actually lose: a task whose Details field has grown into a
 * working document, or whose subtask list is a twenty-step plan. Overwriting either of those is the
 * same unrecoverable accident the note history exists to undo — there is no undo across an app
 * restart, no OS-level file history for a row in SQLite, and nothing leaves the device to fall back
 * on.
 *
 * Everything stays local. A version is another row in the same database as the task it belongs to.
 */
object TaskHistory {

    /** Same cap and same reasoning as [NoteHistory.MAX_VERSIONS_PER_NOTE]. */
    const val MAX_VERSIONS_PER_TASK = 35

    /** Same switch, same reasoning as [NoteHistory.enabled]. */
    @Volatile
    var enabled: Boolean = true

    /**
     * Snapshot [existing] as it stands right now, but only if the incoming content actually differs.
     *
     * The guard matters as much here as it does for notes: without it, opening a task and tapping
     * Save without typing would push an identical revision onto the stack, and a few idle saves
     * would evict the genuinely-different version the user is one day going to want back.
     *
     * **What counts as a change** is title, details, subtasks, priority and due date — the fields a
     * version stores. Completion state, pin, reminders, repeat rule and attachments are excluded on
     * purpose: ticking a task off is not an edit to its content, and it must not consume a history
     * slot or, worse, be undone by a restore.
     *
     * Call this *before* writing the update, while [existing] is still the live row.
     */
    suspend fun recordIfChanged(
        db: AppDatabase,
        existing: Task,
        newTitle: String,
        newNotes: String,
        newSubtasks: String,
        newPriority: Int,
        newDueAt: Long?
    ) {
        if (!enabled) return

        val unchanged = existing.title == newTitle &&
            existing.notes == newNotes &&
            existing.subtasks == newSubtasks &&
            existing.priority == newPriority &&
            existing.dueAt == newDueAt
        if (unchanged) return

        db.taskVersionDao().insert(
            TaskVersion(
                taskId = existing.id,
                title = existing.title,
                notes = existing.notes,
                subtasks = existing.subtasks,
                priority = existing.priority,
                dueAt = existing.dueAt,
                // A task has no updatedAt column, so "now" is the honest timestamp: this row is
                // being replaced at this instant, and that is the moment the history list should
                // report. (A note uses its own updatedAt because it has one and it is more precise.)
                savedAt = System.currentTimeMillis()
            )
        )
        db.taskVersionDao().trimTo(existing.id, MAX_VERSIONS_PER_TASK)
    }

    /**
     * Apply [version] back onto [task], returning the task as it should be written.
     *
     * Restoring is itself an edit, so the caller records the current content as a fresh version
     * first — which is what makes "restore" undoable in turn, rather than a one-way trip that
     * destroys whatever you had before you restored. Someone browsing their own history is by
     * definition already unsure what they want.
     *
     * Only the fields a version stores are touched. Completion, pin, reminder, repeat rule and
     * attachments are left exactly as they are on the live task: restoring last Tuesday's wording
     * should not un-complete the task or resurrect a file that was deliberately removed since.
     */
    fun applyTo(task: Task, version: TaskVersion): Task = task.copy(
        title = version.title,
        notes = version.notes,
        subtasks = version.subtasks,
        priority = version.priority,
        dueAt = version.dueAt
    )

    /** Drop a task's entire history. Called wherever a task is *permanently* deleted. */
    suspend fun deleteAllFor(db: AppDatabase, taskId: Long) {
        db.taskVersionDao().deleteForTask(taskId)
    }
}
