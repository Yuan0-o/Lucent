package com.lucent.app.harness

import com.lucent.app.platform.PlatformContext

internal fun harnessTestContext(): PlatformContext {
    // Try desktop PlatformContext via constructor
    try {
        val cls = Class.forName("com.lucent.app.platform.DesktopPlatformContext")
        return cls.getDeclaredConstructor().newInstance() as PlatformContext
    } catch (t: Throwable) {
        // Fall through
    }
    // Try Android Application
    try {
        val appCls = Class.forName("android.app.Application")
        return appCls.getDeclaredConstructor().newInstance() as PlatformContext
    } catch (t: Throwable) {
        // Fall through
    }
    throw IllegalStateException("This platform gives tests no way to make a PlatformContext")
}
