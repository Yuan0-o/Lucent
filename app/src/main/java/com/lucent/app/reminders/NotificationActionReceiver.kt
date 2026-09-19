package com.lucent.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.lucent.app.AppScope
import com.lucent.app.data.AppDatabase
import com.lucent.app.tools.TaskActions
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_DONE) return
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return

        val appContext = context.applicationContext
        val pending = goAsync()
        AppScope.io.launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                db.taskDao().getByIdOnce(taskId)?.let { task ->
                    if (!task.isDone && task.trashedAt == null) {
                        TaskActions.complete(appContext, db, task)
                    }
                }
                NotificationManagerCompat.from(appContext).cancel(taskId.toInt())
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_DONE = "com.lucent.app.action.MARK_TASK_DONE"
    }
}
