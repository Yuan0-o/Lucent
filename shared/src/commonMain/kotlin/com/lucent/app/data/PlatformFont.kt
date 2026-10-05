package com.lucent.app.data

import com.lucent.app.platform.PlatformContext

expect fun openFontSource(context: PlatformContext, source: Any): java.io.InputStream?

expect fun fontSourceDisplayName(context: PlatformContext, source: Any): String?
