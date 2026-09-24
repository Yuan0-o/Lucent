package com.lucent.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucent.app.AppScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ShizukuWatcher {

    private const val FIRST_CHECK_DELAY_MS = 6_000L
    private const val CHECK_INTERVAL_MS = 60_000L

    var lost by mutableStateOf(false)
        private set

    private var loop: Job? = null

    fun ensureStarted(context: Context) {
        if (loop?.isActive == true) return
        val appContext = context.applicationContext
        loop = AppScope.io.launch {
            delay(FIRST_CHECK_DELAY_MS)
            while (true) {
                if (!ShizukuShell.isReady()) {
                    SettingsRepository(appContext).setPrivilegedEnabled(false)
                    StartupLog.event(appContext, "shizuku: permission lost - the advanced switch was turned off")
                    lost = true
                    loop = null
                    return@launch
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
    }

    fun acknowledge() {
        lost = false
    }
}
