package com.lucent.app.data

import com.lucent.app.AppScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

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

private fun appContextOf(context: PlatformContext): PlatformContext = context.applicationContext

/**
 * Write one backup into the user's chosen tree.
 *
 * Uses `DocumentsContract` directly rather than the `documentfile` support library, because the app
 * does not depend on it and a scheduled backup is not worth a new dependency. The tree Uri is
 * whatever the folder picker returned and its read/write grant was persisted at pick time.
 */
private suspend fun writeBackup(context: PlatformContext, folderUri: String, name: String) {
    val tree = android.net.Uri.parse(folderUri)
    val resolver = context.contentResolver
    val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(
        tree, android.provider.DocumentsContract.getTreeDocumentId(tree)
    )
    val file = android.provider.DocumentsContract.createDocument(
        resolver, parent, "application/octet-stream", name
    ) ?: throw java.io.IOException("could not create $name")
    val db = AppDatabase.getInstance(context)
    val settings = SettingsRepository(context)
    resolver.openOutputStream(file)?.use { out ->
        // v2.7.5: tee the same bytes for the cloud mirror (the folder picker's stream is
        // write-only, so the backup is captured as it is written).
        val tee = java.io.ByteArrayOutputStream()
        val mirrored = object : java.io.OutputStream() {
            override fun write(b: Int) { out.write(b); tee.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); tee.write(b, off, len) }
            override fun flush() = out.flush()
            override fun close() = out.close()
        }
        BackupManager.exportEncrypted(context, db, settings, mirrored, null)
        val cloudOn = runCatching {
            val repo = SettingsRepository(context)
            repo.cloudEnabled.first() && repo.cloudAutoBackup.first() &&
                repo.cloudUrl.first().isNotBlank() && repo.cloudUser.first().isNotBlank()
        }.getOrDefault(false)
        if (cloudOn) {
            runCatching {
                val repo = SettingsRepository(context)
                val cfg = com.lucent.app.data.CloudSync.Config(
                    url = repo.cloudUrl.first(),
                    user = repo.cloudUser.first(),
                    password = com.lucent.app.data.CryptoUtil.decrypt(repo.cloudPasswordEnc.first()),
                    folder = repo.cloudFolder.first().ifBlank { "Lucent" }
                )
                com.lucent.app.data.CloudSync.upload(cfg, name, tee.toByteArray())
            }
            // A failed cloud upload never fails the local backup - it is a mirror, not a hostage.
        }
    } ?: throw java.io.IOException("could not open $name")
}

private fun listOurFiles(context: PlatformContext, folderUri: String): List<String> {
    val tree = android.net.Uri.parse(folderUri)
    val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
        tree, android.provider.DocumentsContract.getTreeDocumentId(tree)
    )
    val out = ArrayList<String>()
    context.contentResolver.query(
        children,
        arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null, null, null
    )?.use { c -> while (c.moveToNext()) out.add(c.getString(0) ?: "") }
    return out
}

private fun deleteFile(context: PlatformContext, folderUri: String, name: String) {
    val tree = android.net.Uri.parse(folderUri)
    val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
        tree, android.provider.DocumentsContract.getTreeDocumentId(tree)
    )
    context.contentResolver.query(
        children,
        arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
        ),
        null, null, null
    )?.use { c ->
        while (c.moveToNext()) {
            if (c.getString(1) == name) {
                val doc = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                android.provider.DocumentsContract.deleteDocument(context.contentResolver, doc)
                return
            }
        }
    }
}
