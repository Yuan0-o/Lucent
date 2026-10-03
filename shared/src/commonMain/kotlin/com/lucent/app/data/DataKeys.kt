package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import javax.crypto.SecretKey

expect object DataKeys {
    fun attachmentKey(context: PlatformContext): SecretKey
    fun databasePassphrase(context: PlatformContext): String
    fun hasDatabaseKey(context: PlatformContext): Boolean
    fun resetCacheForTesting()
}
