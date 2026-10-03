package com.lucent.app.data

import com.lucent.app.platform.PlatformContext

expect object StartupLog {
    fun setEnabled(value: Boolean)
    fun isEnabled(): Boolean
    fun event(context: PlatformContext, message: String)
    fun readAll(context: PlatformContext): String
    fun buildExport(context: PlatformContext): String
    fun hasEntries(context: PlatformContext): Boolean
    fun clear(context: PlatformContext)
}
