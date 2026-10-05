package com.lucent.app.data

import com.lucent.app.platform.PlatformContext

expect object AutoBackupRunner {
    fun ensureStarted(context: PlatformContext)
    suspend fun runNow(context: PlatformContext): String?
}
