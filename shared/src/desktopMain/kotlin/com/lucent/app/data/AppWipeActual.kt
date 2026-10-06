package com.lucent.app.data
import com.lucent.app.platform.cacheDir

import com.lucent.app.platform.PlatformContext
import com.lucent.app.reminders.ReminderScheduler

actual suspend fun cancelTaskReminder(context: PlatformContext, taskId: Long) {
    ReminderScheduler.cancel(context, taskId)
}

actual suspend fun clearCacheDir(context: PlatformContext) {
    okio.FileSystem.SYSTEM.listOrNull(context.cacheDir)?.forEach { f -> runCatching { okio.FileSystem.SYSTEM.deleteRecursively(f) } }
}

actual suspend fun wipeLocalModels(context: PlatformContext) {
    com.lucent.app.local.LocalLlm.shutdown()
    com.lucent.app.local.LocalModelStore.deleteAll(context)
}

actual suspend fun purgeDatabaseEncryptionLeftovers(context: PlatformContext) {
    DatabaseEncryption.clearLockedNotice(context)
    DatabaseEncryption.purgeSetAsideDatabases(context)
}

actual suspend fun disableShareIntegration(context: PlatformContext) {
}

actual suspend fun refreshWidgets(context: PlatformContext) {
}
