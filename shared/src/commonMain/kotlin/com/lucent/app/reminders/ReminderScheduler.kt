package com.lucent.app.reminders

import com.lucent.app.data.Task
import com.lucent.app.platform.PlatformContext

const val EXTRA_TASK_ID = "task_id"
const val EXTRA_TASK_TITLE = "task_title"

expect object ReminderScheduler {
    fun sync(context: PlatformContext, task: Task)
    fun cancel(context: PlatformContext, taskId: Long)
    suspend fun rescheduleAll(context: PlatformContext)
}
