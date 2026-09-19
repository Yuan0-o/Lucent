package com.lucent.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lucent.app.AppScope
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val relevant = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        if (!relevant) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        AppScope.io.launch {
            try {
                ReminderScheduler.rescheduleAll(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
