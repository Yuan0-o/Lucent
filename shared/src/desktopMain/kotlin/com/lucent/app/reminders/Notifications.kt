package com.lucent.app.reminders

import com.lucent.app.platform.PlatformContext

actual object Notifications {
    actual val CHANNEL_ID: String = "task_reminders"
    actual fun ensureChannel(context: PlatformContext) {
    }
    actual fun canPost(context: PlatformContext): Boolean = true
}
