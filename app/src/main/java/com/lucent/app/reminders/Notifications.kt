package com.lucent.app.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifications {

    const val CHANNEL_ID = "task_reminders"
    private val CHANNEL_NAME: String get() = com.lucent.app.i18n.S.notifChannelName
    private val CHANNEL_DESC: String get() = com.lucent.app.i18n.S.notifChannelDesc

    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(CHANNEL_NAME)
            .setDescription(CHANNEL_DESC)
            .setVibrationEnabled(true)
            .build()
        NotificationManagerCompat.from(context.applicationContext).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
