package com.lucent.app.data

import com.lucent.app.platform.PlatformContext

expect object CrashShield {
    var caughtCount: Int
    var lastCaught: String?
    fun install(context: PlatformContext)
    fun isInstalled(): Boolean
}
