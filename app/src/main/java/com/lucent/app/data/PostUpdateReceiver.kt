package com.lucent.app.data

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lucent.app.MainActivity
import com.lucent.app.R

class PostUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val appContext = context.applicationContext
        val version = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull().orEmpty()
        StartupLog.event(appContext, "update: package replaced (now $version), trying to reopen")

        val reopened = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(appContext)) {
            openApp(appContext)
        } else {
            false
        }
        if (reopened) {
            StartupLog.event(appContext, "update: Lucent reopened automatically after the update")
        } else {
            notifyTapToOpen(appContext, version)
        }
        AutoUpdate.dismiss()
    }

    private fun openApp(context: Context): Boolean = runCatching {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
        true
    }.getOrElse {
        StartupLog.event(context, "update: could not reopen automatically (${it::class.simpleName})")
        false
    }

    private fun notifyTapToOpen(context: Context, version: String) {
        if (!canPost(context)) return
        val channel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(com.lucent.app.i18n.S.updateChannelName)
            .build()
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(channel)
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getActivity(context, 0, open, flags)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(com.lucent.app.i18n.S.updateRelaunchTitle)
            .setContentText(
                if (version.isBlank()) com.lucent.app.i18n.S.updateRelaunchBody
                else com.lucent.app.i18n.S.updateRelaunchBodyVersion(version)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val CHANNEL_ID = "updates"
        const val NOTIFICATION_ID = 4201
    }
}
