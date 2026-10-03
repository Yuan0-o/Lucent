package com.lucent.app.data

import com.lucent.app.platform.PlatformContext

actual object DatabaseEncryption {
    actual fun lockedNotice(context: PlatformContext): String? = null
    actual fun clearLockedNotice(context: PlatformContext) {  }
    actual fun purgeSetAsideDatabases(context: PlatformContext) {  }
}
