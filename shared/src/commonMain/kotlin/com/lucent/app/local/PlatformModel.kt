package com.lucent.app.local

import com.lucent.app.platform.PlatformContext

expect fun openModelSource(context: PlatformContext, source: Any): java.io.InputStream?

expect fun modelSourceDisplayName(context: PlatformContext, source: Any): String?
