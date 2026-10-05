package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import com.lucent.app.ui.LucentFontResolver

expect suspend fun cancelTaskReminder(context: PlatformContext, taskId: Long)
expect suspend fun clearCacheDir(context: PlatformContext)
expect suspend fun wipeLocalModels(context: PlatformContext)
expect suspend fun purgeDatabaseEncryptionLeftovers(context: PlatformContext)
expect suspend fun disableShareIntegration(context: PlatformContext)
expect suspend fun refreshWidgets(context: PlatformContext)

suspend fun wipeAllData(
    context: PlatformContext,
    db: AppDatabase,
    repo: SettingsRepository
) {
    db.taskDao.getAllOnce().forEach { cancelTaskReminder(context, it.id) }
    db.noteVersionDao.clearAll()
    db.noteDao.clearAll()
    db.taskVersionDao.clearAll()
    db.taskDao.clearAll()
    db.notebookDao.clearAllItems()
    db.notebookDao.clearAll()
    db.chatDao.clearAll()
    db.chatConversationDao.clearAll()
    EmbeddingStore.clearAll(context)
    repo.clearAll()
    UsageTracker.clearAll(context)
    AttachmentStore.pruneOrphans(context, emptySet())
    AttachmentAccess.clearPreviewCache(context)
    clearCacheDir(context)
    wipeLocalModels(context)
    FontStore.deleteAll(context)
    LucentFontResolver.evictAll()
    StartupLog.clear(context)
    StartupLog.setEnabled(false)
    purgeDatabaseEncryptionLeftovers(context)
    disableShareIntegration(context)
    refreshWidgets(context)
}
