package com.lucent.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AutoUpdate {

    enum class Phase { IDLE, CHECKING, DOWNLOADING, INSTALLING }

    interface Installer {
        suspend fun download(info: ReleaseInfo): Boolean
        fun isDownloaded(info: ReleaseInfo): Boolean
        suspend fun install(info: ReleaseInfo): Boolean
        fun discard(info: ReleaseInfo)
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
        phase = Phase.IDLE
    }

    fun report(text: String?) {
        message = text
    }

    fun dismiss() {
        offered = null
        readyForInstall = false
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

    suspend fun downloadOffered(): Boolean {
        val info = offered ?: return false
        val engine = installer ?: return false
        if (engine.isDownloaded(info)) {
            readyForInstall = true
            return true
        }
        phase = Phase.DOWNLOADING
        val ok = try {
            engine.download(info)
        } catch (t: Throwable) {
            false
        }
        phase = Phase.IDLE
        readyForInstall = ok
        if (!ok) {
            engine.discard(info)
            message = com.lucent.app.i18n.S.updateDownloadFailed
        }
        return ok
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
        phase = Phase.IDLE
    }
}
