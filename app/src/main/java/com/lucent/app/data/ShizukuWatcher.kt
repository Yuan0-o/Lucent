package com.lucent.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucent.app.AppScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

object ShizukuWatcher {

    private const val FIRST_CHECK_DELAY_MS = 6_000L
    private const val CHECK_INTERVAL_MS = 60_000L

    var notice by mutableStateOf<ShizukuState?>(null)
        private set

    private var loop: Job? = null

    fun stateOf(context: Context): ShizukuState = when {
        !ShizukuShell.isInstalled(context) -> ShizukuState.NOT_INSTALLED
        !ShizukuShell.isServiceRunning() -> ShizukuState.NOT_RUNNING
        !ShizukuShell.hasPermission() -> ShizukuState.NO_PERMISSION
        else -> ShizukuState.READY
    }

    fun ensureStarted(context: Context) {
        if (loop?.isActive == true) return
        val appContext = context.applicationContext
        loop = AppScope.io.launch {
            delay(FIRST_CHECK_DELAY_MS)
            while (true) {
                if (flagIfNotReady(appContext)) {
                    loop = null
                    return@launch
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun checkNow(context: Context) {
        val appContext = context.applicationContext
        if (stateOf(appContext) == ShizukuState.READY) return
        stop()
        AppScope.io.launch { flagIfNotReady(appContext) }
    }

    fun stop() {
        loop?.cancel()
        loop = null
    }

    fun acknowledge() {
        notice = null
    }

    private suspend fun flagIfNotReady(context: Context): Boolean {
        val state = stateOf(context)
        if (state == ShizukuState.READY) return false
        SettingsRepository(context).setPrivilegedEnabled(false)
        StartupLog.event(context, "shizuku: ${state.name.lowercase()} - the advanced switch was turned off")
        notice = state
        return true
    }
}
