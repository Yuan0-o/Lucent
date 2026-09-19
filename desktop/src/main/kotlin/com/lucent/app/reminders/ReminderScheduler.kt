package com.lucent.app.reminders

import android.content.Context
import com.lucent.app.AppScope
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Task
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object ReminderScheduler {

    @Volatile var notifier: ((title: String, message: String) -> Unit)? = null

    private val pending = ConcurrentHashMap<Long, Job>()

    private fun shouldFire(task: Task): Boolean {
        val due = task.dueAt ?: return false
        return task.reminderEnabled &&
            !task.isDone &&
            task.trashedAt == null &&
            due > System.currentTimeMillis()
    }

    fun sync(context: Context, task: Task) {
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

    fun cancel(context: Context, taskId: Long) {
        pending.remove(taskId)?.cancel()
    }

    suspend fun rescheduleAll(context: Context) {
        val tasks = try {
            AppDatabase.getInstance(context.applicationContext).taskDao().getAllOnce()
        } catch (t: Throwable) {
            return
        }
        tasks.forEach { sync(context, it) }
    }

    private suspend fun fire(context: Context, taskId: Long, scheduledTitle: String) {
        val task = try {
            AppDatabase.getInstance(context.applicationContext).taskDao().getByIdOnce(taskId)
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
