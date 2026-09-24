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

    var onPendingChange: ((String?) -> Unit)? = null

    fun restorePending(version: String?) {
        pendingVersion = version?.takeIf { it.isNotBlank() }
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

    fun report(text: String?) {
        message = text
    }

    fun reportProgress(fraction: Float) {
        progress = fraction.coerceIn(0f, 1f)
    }

    fun reportDownloadReady() {
        phase = Phase.IDLE
        progress = -1f
        readyForInstall = true
        hidden = false
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
            readyForInstall = true
            return true
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
