package com.lucent.app.harness

import com.lucent.app.platform.PlatformContext

internal fun harnessTestContext(): PlatformContext {
    return Class.forName("com.lucent.app.platform.DesktopPlatformContext").getDeclaredConstructor().newInstance() as PlatformContext
}
