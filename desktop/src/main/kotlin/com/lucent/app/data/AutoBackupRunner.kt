package com.lucent.app.data

import com.lucent.app.AppScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Automatic backup, task 14 — the half that runs.
 *
 * ### Why this file had to exist at all
 *
 * [AutoBackup] has been in the tree for some time and is a complete, well-tested *policy*: when a
 * run is due, what to call the file, which old files may be deleted. It had no callers whatsoever.
 * Every rule was decided and nothing ever executed them, which is the most expensive kind of
 * unfinished feature — it reads as done.
 *
 * ### The schedule, and its honest limits
 *
 * A loop on the app's own scope, waking every [CHECK_INTERVAL_MS] to ask [AutoBackup.isDue]. That
 * means backups happen **while Lucent is running**, and a due backup is taken promptly the next time
 * it starts (a never-run configuration reports due immediately by [AutoBackup.nextDueAt], so
 * switching the feature on produces a file while the user is still looking at the screen that
 * switched it on).
 *
 * It does **not** wake a closed app. Doing that properly means `WorkManager` or `AlarmManager` on
 * Android and a service on Windows, each with its own permissions, manifest entries and failure
 * modes. That is a worthwhile addition and it is deliberately not smuggled in here: the interval
 * floor is six hours and a notes app is opened rather more often than that, so the loop covers the
 * realistic case, and what it does not cover is stated rather than implied.
 *
 * ### Failures are recorded, not thrown
 *
 * A backup that has been failing for a month in silence is worse than no backup, so every failure is
 * written to [AutoBackup.State.lastError] and surfaced in Settings. [AutoBackup.State.lastRunAt] is
 * advanced **only** on success, so a failing run is retried at the next check rather than counting
 * as this interval's backup.
 */
object AutoBackupRunner {

    /** How often the loop wakes to ask whether a run is due. */
    private const val CHECK_INTERVAL_MS = 15L * 60L * 1000L

    private var loop: Job? = null

    /**
     * Start the checking loop if it is not already running. Idempotent, so every plausible entry
     * point may call it without coordinating with the others.
     */
    fun ensureStarted(context: PlatformContext) {
        if (loop?.isActive == true) return
        val appContext = appContextOf(context)
        loop = AppScope.io.launch {
            while (true) {
                try {
                    val settings = SettingsRepository(appContext)
                    val state = settings.autoBackupOnce()
                    if (AutoBackup.isDue(state)) runOnce(appContext, settings, state)
                } catch (_: Throwable) {
                    // Never let one bad cycle kill the loop: the next check is the retry.
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * Run a backup now regardless of whether one is due — what the "Back up now" button calls.
     * Returns the error text, or null on success, so the caller can show it immediately instead of
     * waiting for the state flow to come back round.
     */
    suspend fun runNow(context: PlatformContext): String? {
        val appContext = appContextOf(context)
        val settings = SettingsRepository(appContext)
        val state = settings.autoBackupOnce()
        if (state.folderUri.isBlank()) return "no folder"
        return runOnce(appContext, settings, state)
    }

    private suspend fun runOnce(
        appContext: PlatformContext,
        settings: SettingsRepository,
        state: AutoBackup.State
    ): String? {
        val now = System.currentTimeMillis()
        val name = AutoBackup.fileNameFor(now)
        return try {
            writeBackup(appContext, state.folderUri, name)
            // Retention runs only AFTER a successful write — a sweep that ran first would, on a
            // failing run, leave the user with fewer backups than they started with.
            val existing = listOurFiles(appContext, state.folderUri)
            AutoBackup.filesToDelete(existing, state.keep).forEach {
                runCatching { deleteFile(appContext, state.folderUri, it) }
            }
            settings.setAutoBackup(state.copy(lastRunAt = now, lastError = ""))
            null
        } catch (t: Throwable) {
            val reason = t.message ?: t::class.java.simpleName
            // lastRunAt is deliberately NOT advanced: a failure is not this interval's backup.
            settings.setAutoBackup(state.copy(lastError = reason))
            reason
        }
    }
}

private typealias PlatformContext = android.content.Context

private fun appContextOf(context: PlatformContext): PlatformContext = context

/** The desktop twin: [folderUri] is an ordinary absolute path, so this is plain file I/O. */
private suspend fun writeBackup(context: PlatformContext, folderUri: String, name: String) {
    val dir = java.io.File(folderUri)
    if (!dir.isDirectory && !dir.mkdirs()) throw java.io.IOException("no folder $folderUri")
    val db = AppDatabase.getInstance(context)
    val settings = SettingsRepository(context)
    java.io.FileOutputStream(java.io.File(dir, name)).use { out ->
        BackupManager.exportEncrypted(context, db, settings, out, null)
    }
}

private fun listOurFiles(context: PlatformContext, folderUri: String): List<String> =
    java.io.File(folderUri).listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()

private fun deleteFile(context: PlatformContext, folderUri: String, name: String) {
    java.io.File(java.io.File(folderUri), name).delete()
}
