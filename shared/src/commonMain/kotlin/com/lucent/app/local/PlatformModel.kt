package com.lucent.app.local

import com.lucent.app.platform.PlatformContext
import com.lucent.app.platform.PlatformInputStream

expect class PlatformModelSource

expect fun openModelSource(context: PlatformContext, source: PlatformModelSource): PlatformInputStream?

expect fun modelSourceDisplayName(context: PlatformContext, source: PlatformModelSource): String?
