package com.lucent.app.harness

import com.lucent.app.platform.DesktopPlatformContext
import com.lucent.app.platform.PlatformContext

internal fun harnessTestContext(): PlatformContext {
    return DesktopPlatformContext()
}
