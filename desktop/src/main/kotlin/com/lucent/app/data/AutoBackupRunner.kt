package com.lucent.app.data

import com.lucent.app.AppScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

object AutoBackupRunner {

    private const val CHECK_INTERVAL_MS = 15L * 60L * 1000L

    private var loop: Job? = null

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
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

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
            val existing = listOurFiles(appContext, state.folderUri)
            AutoBackup.filesToDelete(existing, state.keep).forEach {
                runCatching { deleteFile(appContext, state.folderUri, it) }
            }
            settings.setAutoBackup(state.copy(lastRunAt = now, lastError = ""))
            null
        } catch (t: Throwable) {
            val reason = t.message ?: t::class.java.simpleName
            settings.setAutoBackup(state.copy(lastError = reason))
            reason
        }
    }
}

private typealias PlatformContext = android.content.Context

private fun appContextOf(context: PlatformContext): PlatformContext = context

private suspend fun writeBackup(context: PlatformContext, folderUri: String, name: String) {
    val dir = java.io.File(folderUri)
    if (!dir.isDirectory && !dir.mkdirs()) throw java.io.IOException("no folder $folderUri")
    val db = AppDatabase.getInstance(context)
    val settings = SettingsRepository(context)
    java.io.FileOutputStream(java.io.File(dir, name)).use { out ->
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
        }
    }
}

private fun listOurFiles(context: PlatformContext, folderUri: String): List<String> =
    java.io.File(folderUri).listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()

private fun deleteFile(context: PlatformContext, folderUri: String, name: String) {
    java.io.File(java.io.File(folderUri), name).delete()
}
