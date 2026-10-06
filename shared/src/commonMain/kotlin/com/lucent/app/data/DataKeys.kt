package com.lucent.app.data

import com.lucent.app.platform.PlatformContext

expect object DataKeys {
    fun attachmentKey(context: PlatformContext): ByteArray
    fun databasePassphrase(context: PlatformContext): String
    fun hasDatabaseKey(context: PlatformContext): Boolean
    fun resetCacheForTesting()
}
