package com.lucent.app.ui

import android.content.Context
import androidx.compose.ui.Modifier
import com.lucent.app.platform.PlatformContext

actual object Haptics {
    actual fun tick(context: PlatformContext) { }
    actual fun typingTick(context: PlatformContext) { }
    actual fun finishBuzz(context: PlatformContext) { }
}

actual fun Modifier.hapticClickable(onClick: () -> Unit): Modifier = this
