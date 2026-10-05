package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import com.lucent.app.reminders.ReminderScheduler
import com.lucent.app.local.LocalLlm
import com.lucent.app.local.LocalModelStore

actual suspend fun cancelTaskReminder(context: PlatformContext, taskId: Long) {
    ReminderScheduler.cancel(context, taskId)
}

actual suspend fun clearCacheDir(context: PlatformContext) {
    context.cacheDir.listFiles()?.forEach { f -> runCatching { f.deleteRecursively() } }
}

actual suspend fun wipeLocalModels(context: PlatformContext) {
    LocalLlm.shutdown()
    LocalModelStore.deleteAll(context)
}

actual suspend fun purgeDatabaseEncryptionLeftovers(context: PlatformContext) {
    DatabaseEncryption.clearLockedNotice(context)
    DatabaseEncryption.purgeSetAsideDatabases(context)
}

actual suspend fun disableShareIntegration(context: PlatformContext) {
}

actual suspend fun refreshWidgets(context: PlatformContext) {
}
