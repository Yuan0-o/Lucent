package com.lucent.app.local

import com.lucent.app.platform.PlatformContext

interface ImportSource {
    fun displayName(context: PlatformContext): String?
    fun openStream(context: PlatformContext): java.io.InputStream?
}
