package com.lucent.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AutoUpdate {

    enum class Phase { IDLE, CHECKING, DOWNLOADING, INSTALLING }

    interface Installer {
        suspend fun install(info: ReleaseInfo): Boolean
    }

    var installer: Installer? = null

    var phase by mutableStateOf(Phase.IDLE)
        private set

    var offered by mutableStateOf<ReleaseInfo?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var lastCheckFailed: Boolean = false
        private set

    fun markPhase(next: Phase) {
        phase = next
    }

    fun offer(info: ReleaseInfo) {
        offered = info
        phase = Phase.IDLE
    }

    fun report(text: String?) {
        message = text
    }

    fun dismiss() {
        offered = null
        phase = Phase.IDLE
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
        if (ok) offered = null
        return ok
    }

    fun reset() {
        offered = null
        phase = Phase.IDLE
    }
}
