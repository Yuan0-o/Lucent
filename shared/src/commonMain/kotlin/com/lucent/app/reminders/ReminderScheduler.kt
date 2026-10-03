package com.lucent.app.reminders

import com.lucent.app.data.Task
import com.lucent.app.platform.PlatformContext

expect object ReminderScheduler {
    const val EXTRA_TASK_ID: String
    const val EXTRA_TASK_TITLE: String
    fun sync(context: PlatformContext, task: Task)
    fun cancel(context: PlatformContext, taskId: Long)
    suspend fun rescheduleAll(context: PlatformContext)
}
