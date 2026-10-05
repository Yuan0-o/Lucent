package com.lucent.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucent.app.platform.platformElapsedRealtime

object AppLockController {

    private const val GRACE_MS = 30_000L

    var enabled by mutableStateOf(false)
    var locked by mutableStateOf(false)

    private var processStarted = false
    private var backgroundedAt = 0L

    fun markProcessStarted(lockEnabled: Boolean) {
        enabled = lockEnabled
        if (!processStarted) {
            processStarted = true
            locked = lockEnabled
        }
    }

    fun onStop() {
        backgroundedAt = platformElapsedRealtime()
    }

    fun onStart() {
        if (enabled && backgroundedAt != 0L &&
            platformElapsedRealtime() - backgroundedAt > GRACE_MS
        ) {
            locked = true
        }
        backgroundedAt = 0L
    }

    fun unlock() { locked = false }
}
