package com.lucent.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AutoUpdate {

    enum class Phase { IDLE, CHECKING, DOWNLOADING, INSTALLING }

    interface Installer {
        fun hasDownloadFolder(): Boolean
        fun startDownload(info: ReleaseInfo)
        fun isDownloaded(info: ReleaseInfo): Boolean
        suspend fun install(info: ReleaseInfo): Boolean
        fun discard(info: ReleaseInfo)
        fun cancelDownload(info: ReleaseInfo)
        fun purgeStaged(files: List<String>): Int
        fun identityOf(info: ReleaseInfo): String = info.identity
        fun versionOf(info: ReleaseInfo): String = info.version
        fun hasAsset(info: ReleaseInfo): Boolean = info.apk != null || info.installer != null
    }

    var installer: Installer? = null

    var phase by mutableStateOf(Phase.IDLE)
        private set

    var offered by mutableStateOf<ReleaseInfo?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var readyForInstall by mutableStateOf(false)
        private set

    var progress by mutableStateOf(-1f)
        private set

    var awaitingFolder by mutableStateOf(false)
        private set

    var hidden by mutableStateOf(false)
        private set

    var lastCheckFailed: Boolean = false
        private set

    var pendingVersion: String? = null
        private set

    var stagedVersion: String? = null
        private set

    var stagedFiles: List<String> = emptyList()
        private set

    var onPendingChange: ((String?) -> Unit)? = null

    var onStagedChange: ((String?, List<String>) -> Unit)? = null
    var onPreviewInstalled: ((String) -> Unit)? = null
    var onStagedIdentityChange: ((String) -> Unit)? = null

    fun restorePending(version: String?) {
        pendingVersion = version?.takeIf { it.isNotBlank() }
    }

    fun restoreStaged(version: String?, files: List<String>) {
        stagedVersion = version?.takeIf { it.isNotBlank() }
        stagedFiles = files.filter { it.isNotBlank() }
    }

    fun recordStaged(version: String, files: List<String>) {
        val tag = version.takeIf { it.isNotBlank() } ?: return
        stagedVersion = tag
        stagedFiles = files.filter { it.isNotBlank() }
        onStagedChange?.invoke(stagedVersion, stagedFiles)
    }

    private fun clearStaged() {
        stagedVersion = null
        stagedFiles = emptyList()
        onStagedChange?.invoke(null, emptyList())
        SettingsCache.stagedUpdateIdentity = ""
        SettingsCache.stagedUpdateVersion = ""
        onStagedIdentityChange?.invoke("")
    }

    fun stagedTagFor(runningVersion: String): String? {
        val staged = stagedVersion ?: return null
        return if (UpdateChecker.isNewer(staged, runningVersion)) null else staged
    }

    fun cleanUpAfterUpdate(runningVersion: String): Int {
        val staged = stagedTagFor(runningVersion) ?: return 0
        if (SettingsCache.stagedUpdateIdentity.isNotBlank()) {
            onPreviewInstalled?.invoke(SettingsCache.stagedUpdateIdentity)
        }
        val removed = installer?.purgeStaged(stagedFiles) ?: 0
        clearStaged()
        return removed
    }

    fun markPhase(next: Phase) {
        phase = next
    }

    fun offer(info: ReleaseInfo) {
        offered = info
        readyForInstall = installer?.isDownloaded(info) == true
        awaitingFolder = false
        hidden = false
        progress = -1f
        phase = Phase.IDLE
    }

    fun hide() {
        hidden = true
    }

    fun unhide() {
        hidden = false
    }

    fun report(text: String?) {
        message = text
    }

    fun reportProgress(fraction: Float) {
        progress = fraction.coerceIn(0f, 1f)
    }

    fun reportDownloadReady(files: List<String> = emptyList()) {
        phase = Phase.IDLE
        progress = -1f
        readyForInstall = true
        hidden = false
        offered?.let { info ->
            if (files.isNotEmpty()) {
                val identity = installer?.identityOf(info) ?: info.identity
                SettingsCache.stagedUpdateIdentity = identity
                SettingsCache.stagedUpdateVersion = info.version
                onStagedIdentityChange?.invoke(identity)
                recordStaged(info.tag, files)
            }
        }
    }

    fun reportDownloadFailed(text: String?) {
        phase = Phase.IDLE
        progress = -1f
        readyForInstall = false
        if (text != null) message = text
    }

    fun reportDownloadCancelled() {
        phase = Phase.IDLE
        progress = -1f
        readyForInstall = false
    }

    fun dismiss() {
        offered = null
        readyForInstall = false
        awaitingFolder = false
        hidden = false
        progress = -1f
        phase = Phase.IDLE
    }

    fun later() {
        pendingVersion = offered?.tag
        onPendingChange?.invoke(pendingVersion)
        dismiss()
    }

    private fun clearPending() {
        pendingVersion = null
        onPendingChange?.invoke(null)
    }

    fun downloadFolderChosen() {
        awaitingFolder = false
    }

    fun cancelDownload() {
        val info = offered ?: return
        installer?.cancelDownload(info)
        installer?.discard(info)
        reportDownloadCancelled()
        message = com.lucent.app.i18n.S.updateDownloadCancelled
    }

    suspend fun check(currentVersion: String, notifyWhenCurrent: Boolean = false): ReleaseInfo? {
        phase = Phase.CHECKING
        lastCheckFailed = false
        val found = try {
            UpdateChecker.latest(currentVersion)
        } catch (t: Throwable) {
            lastCheckFailed = true
            null
        }
        phase = Phase.IDLE
        if (found != null) {
            offer(found)
        } else if (notifyWhenCurrent && !lastCheckFailed) {
            message = com.lucent.app.i18n.S.aboutUpToDate
        }
        return found
    }

    fun downloadOffered(): Boolean {
        val info = offered ?: return false
        val engine = installer ?: return false
        if (engine.isDownloaded(info)) {
            val stagedIdentity = SettingsCache.stagedUpdateIdentity
            if (stagedIdentity.isNotBlank() && stagedIdentity != engine.identityOf(info)) {
                val stagedVersion = SettingsCache.stagedUpdateVersion
                if (stagedVersion.isNotBlank() && stagedVersion != info.version) {
                    engine.discard(info)
                } else {
                    readyForInstall = true
                    return true
                }
            } else {
                readyForInstall = true
                return true
            }
        }
        if (!engine.hasDownloadFolder()) {
            awaitingFolder = true
            message = null
            return false
        }
        awaitingFolder = false
        message = null
        readyForInstall = false
        progress = 0f
        phase = Phase.DOWNLOADING
        engine.startDownload(info)
        return true
    }

    suspend fun installOffered(): Boolean {
        val info = offered ?: return false
        val engine = installer ?: return false
        phase = Phase.INSTALLING
        val ok = try {
            engine.install(info)
        } catch (t: Throwable) {
            false
        }
        phase = Phase.IDLE
        if (ok) {
            readyForInstall = false
            offered = null
            clearPending()
        }
        return ok
    }

    fun reset() {
        offered = null
        readyForInstall = false
        awaitingFolder = false
        progress = -1f
        phase = Phase.IDLE
    }
}
