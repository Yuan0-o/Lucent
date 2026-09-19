package com.lucent.app.reminders

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lucent.app.MainActivity
import com.lucent.app.R
import com.lucent.app.data.SettingsRepository

class ReminderReceiver : BroadcastReceiver() {

    private companion object {
        const val DONE_REQUEST_OFFSET = 1_000_000
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return

        try {
            com.lucent.app.i18n.L.apply(
                kotlinx.coroutines.runBlocking { SettingsRepository(context).appLanguageOnce() }
            )
        } catch (_: Throwable) { }

        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_TITLE)
            ?.takeIf { it.isNotBlank() } ?: com.lucent.app.i18n.S.untitledTask

        Notifications.ensureChannel(context)
        if (!Notifications.canPost(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_DONE
            putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt() + DONE_REQUEST_OFFSET,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(com.lucent.app.i18n.S.notifTaskDue)
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_notification, com.lucent.app.i18n.S.notifMarkDone, donePendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
        } catch (t: SecurityException) {
        }
    }
}
