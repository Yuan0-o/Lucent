package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import com.lucent.app.platform.PlatformInputStream

expect class PlatformFontSource

expect fun openFontSource(context: PlatformContext, source: PlatformFontSource): PlatformInputStream?

expect fun fontSourceDisplayName(context: PlatformContext, source: PlatformFontSource): String?
