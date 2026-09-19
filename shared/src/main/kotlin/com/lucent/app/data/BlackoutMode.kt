package com.lucent.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object BlackoutMode {

    var active: Boolean by mutableStateOf(false)
        private set

    fun hydrate(enabled: Boolean) {
        active = enabled
    }

    fun networkAllowed(): Boolean = !active

    fun cloudSurfacesFrozen(): Boolean = active

    fun appLockLocked(): Boolean = active

    const val GRACE_MS_WHEN_ACTIVE: Long = 0L
}
