package com.lucent.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Task

object ReminderScheduler {

    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_TASK_TITLE = "task_title"
    private const val ACTION_TASK_REMINDER = "com.lucent.app.action.TASK_REMINDER"

    private fun pendingIntentFor(context: Context, taskId: Long, title: String, allowCreate: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (allowCreate) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, taskId.toInt(), intent, flags)
    }

    private fun shouldFire(task: Task): Boolean {
        val due = task.dueAt ?: return false
        return task.reminderEnabled &&
            !task.isDone &&
            task.trashedAt == null &&
            due > System.currentTimeMillis()
    }

    fun sync(context: Context, task: Task) {
        val appContext = context.applicationContext
        if (!shouldFire(task)) {
            cancel(appContext, task.id)
            return
        }
        val due = task.dueAt ?: return

        Notifications.ensureChannel(appContext)
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntentFor(appContext, task.id, task.title, allowCreate = true) ?: return

        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending)
            }
        } catch (t: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending)
            } catch (ignored: Throwable) {
            }
        }
    }

    fun cancel(context: Context, taskId: Long) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val existing = pendingIntentFor(appContext, taskId, "", allowCreate = false) ?: return
        alarmManager.cancel(existing)
        existing.cancel()
    }

    suspend fun rescheduleAll(context: Context) {
        val appContext = context.applicationContext
        val db = AppDatabase.getInstance(appContext)
        db.taskDao().getAllOnce().forEach { sync(appContext, it) }
    }
}
