package com.lucent.app.data

import android.content.Context

/**
 * The one body of "erase everything", shared by every path that needs it (R3 report).
 *
 * ### Why this exists
 *
 * The password "self-destruct" safety net (Settings → Security) is only as real as the code that
 * actually wipes the data: until R3 the engine ([PasswordAttempts]) and this erasure both existed,
 * but nothing ever CALLED the erasure, so the feature was a checkbox with a promise attached. This
 * file lifts the wipe out of the Settings dialog so the lock screen can run the exact same body
 * when a self-destruct threshold is crossed.
 *
 * The body mirrors the manual "Delete everything" flow (Settings → Data): reminders first (an
 * alarm must not outlive the task it points at), then every table, then preferences and the usage
 * DataStore, then files on disk, imported local models (the largest thing by orders of magnitude —
 * shut the engine down before deleting underneath a live llama context), imported fonts, startup
 * logs and encryption markers, and finally the OS-level state the app owns (share-sheet
 * integration, home-screen widgets).
 *
 * UI-side aftermath (toasts, refresh bumps, conversation reset) belongs to the caller, not here:
 * the Settings dialog shows a toast and refreshes its lists, while the lock screen unlocks into
 * the empty app.
 */
suspend fun wipeAllData(
    context: Context,
    db: com.lucent.app.data.AppDatabase,
    repo: com.lucent.app.data.SettingsRepository
) {
    // Cancel every scheduled reminder *before* the rows they point at disappear.
    db.taskDao().getAllOnce().forEach {
        com.lucent.app.reminders.ReminderScheduler.cancel(context, it.id)
    }
    // ---- Database rows ----
    db.noteVersionDao().clearAll()
    db.noteDao().clearAll()
    db.taskDao().clearAll()
    db.chatDao().clearAll()
    db.chatConversationDao().clearAll()

    // ---- Preferences ----
    repo.clearAll()
    // The usage scores live in their OWN DataStore (lucent_usage), which repo.clearAll() has
    // never touched — see UsageTracker.clearAll.
    com.lucent.app.data.UsageTracker.clearAll(context)

    // ---- Files on disk ----
    // Sweeping with an empty "referenced ids" set drops every stored attachment file.
    com.lucent.app.data.AttachmentStore.pruneOrphans(context, emptySet())
    // Decrypted attachment previews are copies of the user's files sitting in cacheDir.
    com.lucent.app.data.AttachmentAccess.clearPreviewCache(context)
    // Everything else parked in cacheDir goes too.
    context.cacheDir.listFiles()?.forEach { f -> runCatching { f.deleteRecursively() } }

    // ---- The imported local models ----
    // Shut the engine down FIRST: the active model may be resident with its file open, and
    // deleting underneath a live llama context risks a native fault.
    com.lucent.app.local.LocalLlm.shutdown()
    com.lucent.app.local.LocalModelStore.deleteAll(context)

    // ---- The imported fonts ----
    com.lucent.app.data.FontStore.deleteAll(context)
    com.lucent.app.ui.LucentFontResolver.evictAll()

    // ---- Diagnostics and one-off markers ----
    com.lucent.app.data.StartupLog.clear(context)
    com.lucent.app.data.StartupLog.setEnabled(false)
    com.lucent.app.data.DatabaseEncryption.clearLockedNotice(context)
    com.lucent.app.data.DatabaseEncryption.purgeSetAsideDatabases(context)

    // ---- OS-level state the app owns ----
    com.lucent.app.data.ShareIntegration.setEnabled(context, false)
    com.lucent.app.widget.WidgetUpdater.refreshContent(context)
}
