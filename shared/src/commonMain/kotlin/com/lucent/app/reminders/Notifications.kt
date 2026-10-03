package com.lucent.app.reminders

import com.lucent.app.platform.PlatformContext

expect object Notifications {
    val CHANNEL_ID: String
    fun ensureChannel(context: PlatformContext)
    fun canPost(context: PlatformContext): Boolean
}
