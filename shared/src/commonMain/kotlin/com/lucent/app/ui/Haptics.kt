package com.lucent.app.ui

import androidx.compose.ui.Modifier
import com.lucent.app.platform.PlatformContext

expect object Haptics {
    fun tick(context: PlatformContext)
    fun typingTick(context: PlatformContext)
    fun finishBuzz(context: PlatformContext)
}

expect fun Modifier.hapticClickable(onClick: () -> Unit): Modifier
