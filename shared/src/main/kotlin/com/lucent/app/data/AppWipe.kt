package com.lucent.app.data

import android.content.Context

suspend fun wipeAllData(
    context: Context,
    db: com.lucent.app.data.AppDatabase,
    repo: com.lucent.app.data.SettingsRepository
) {
    db.taskDao().getAllOnce().forEach {
        com.lucent.app.reminders.ReminderScheduler.cancel(context, it.id)
    }
    db.noteVersionDao().clearAll()
    db.noteDao().clearAll()
    db.taskVersionDao().clearAll()
    db.taskDao().clearAll()
    db.notebookDao().clearAllItems()
    db.notebookDao().clearAll()
    db.chatDao().clearAll()
    db.chatConversationDao().clearAll()
    com.lucent.app.data.EmbeddingStore.clearAll(context)

    repo.clearAll()
    com.lucent.app.data.UsageTracker.clearAll(context)

    com.lucent.app.data.AttachmentStore.pruneOrphans(context, emptySet())
    com.lucent.app.data.AttachmentAccess.clearPreviewCache(context)
    context.cacheDir.listFiles()?.forEach { f -> runCatching { f.deleteRecursively() } }

    com.lucent.app.local.LocalLlm.shutdown()
    com.lucent.app.local.LocalModelStore.deleteAll(context)

    com.lucent.app.data.FontStore.deleteAll(context)
    com.lucent.app.ui.LucentFontResolver.evictAll()

    com.lucent.app.data.StartupLog.clear(context)
    com.lucent.app.data.StartupLog.setEnabled(false)
    com.lucent.app.data.DatabaseEncryption.clearLockedNotice(context)
    com.lucent.app.data.DatabaseEncryption.purgeSetAsideDatabases(context)

    com.lucent.app.data.ShareIntegration.setEnabled(context, false)
    com.lucent.app.widget.WidgetUpdater.refreshContent(context)
}
