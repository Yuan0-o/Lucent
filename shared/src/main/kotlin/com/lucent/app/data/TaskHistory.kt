package com.lucent.app.data

object TaskHistory {

    const val MAX_VERSIONS_PER_TASK = 35

    @Volatile
    var enabled: Boolean = true

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
                savedAt = System.currentTimeMillis()
            )
        )
        db.taskVersionDao().trimTo(existing.id, MAX_VERSIONS_PER_TASK)
    }

    fun applyTo(task: Task, version: TaskVersion): Task = task.copy(
        title = version.title,
        notes = version.notes,
        subtasks = version.subtasks,
        priority = version.priority,
        dueAt = version.dueAt
    )

    suspend fun deleteAllFor(db: AppDatabase, taskId: Long) {
        db.taskVersionDao().deleteForTask(taskId)
    }
}
