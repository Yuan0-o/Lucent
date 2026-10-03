package com.lucent.app.data

import com.lucent.app.platform.PlatformContext

expect object DatabaseEncryption {
    fun lockedNotice(context: PlatformContext): String?
    fun clearLockedNotice(context: PlatformContext)
    fun purgeSetAsideDatabases(context: PlatformContext)
}
