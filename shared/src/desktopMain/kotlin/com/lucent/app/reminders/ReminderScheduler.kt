package com.lucent.app.reminders
import com.lucent.app.platform.applicationContext

import com.lucent.app.platform.PlatformContext
import com.lucent.app.AppScope
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Task
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import com.lucent.app.data.createAppDatabase

actual object ReminderScheduler {

    actual val EXTRA_TASK_ID = "task_id"
    actual val EXTRA_TASK_TITLE = "task_title"

    @Volatile var notifier: ((title: String, message: String) -> Unit)? = null

    private val pending = ConcurrentHashMap<Long, Job>()

    private fun shouldFire(task: Task): Boolean {
        val due = task.dueAt ?: return false
        return task.reminderEnabled &&
            !task.isDone &&
            task.trashedAt == null &&
            due > System.currentTimeMillis()
    }

    actual fun sync(context: PlatformContext, task: Task) {
        cancel(context, task.id)
        if (!shouldFire(task)) return
        val due = task.dueAt ?: return
        val id = task.id
        val title = task.title
        val job = AppScope.io.launch {
            val wait = due - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            pending.remove(id)
            fire(context, id, title)
        }
        pending[id] = job
    }

    actual fun cancel(context: PlatformContext, taskId: Long) {
        pending.remove(taskId)?.cancel()
    }

    actual suspend fun rescheduleAll(context: PlatformContext) {
        val tasks = try {
            createAppDatabase(context.applicationContext).taskDao.getAllOnce()
        } catch (t: Throwable) {
            return
        }
        tasks.forEach { sync(context, it) }
    }

    private suspend fun fire(context: PlatformContext, taskId: Long, scheduledTitle: String) {
        val task = try {
            createAppDatabase(context.applicationContext).taskDao.getByIdOnce(taskId)
        } catch (t: Throwable) {
            null
        }
        if (task != null && !shouldStillNotify(task)) return
        val title = task?.title ?: scheduledTitle
        notifier?.invoke(com.lucent.app.i18n.S.tabTasks, title)
    }

    private fun shouldStillNotify(task: Task): Boolean =
        task.reminderEnabled && !task.isDone && task.trashedAt == null && task.dueAt != null
}
