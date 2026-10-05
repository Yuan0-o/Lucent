package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import com.lucent.app.reminders.ReminderScheduler

actual suspend fun cancelTaskReminder(context: PlatformContext, taskId: Long) {
    ReminderScheduler.cancel(context, taskId)
}

actual suspend fun clearCacheDir(context: PlatformContext) {
    context.cacheDir.listFiles()?.forEach { f -> runCatching { f.deleteRecursively() } }
}

actual suspend fun wipeLocalModels(context: PlatformContext) {
}

actual suspend fun purgeDatabaseEncryptionLeftovers(context: PlatformContext) {
    DatabaseEncryption.clearLockedNotice(context)
    DatabaseEncryption.purgeSetAsideDatabases(context)
}

actual suspend fun disableShareIntegration(context: PlatformContext) {
    ShareIntegration.setEnabled(context, false)
}

actual suspend fun refreshWidgets(context: PlatformContext) {
}
