package com.lucent.app.harness

import com.lucent.app.platform.PlatformContext

internal fun harnessTestContext(): PlatformContext {
    try {
        val cls = Class.forName("com.lucent.app.platform.DesktopPlatformContext")
        return cls.getDeclaredConstructor().newInstance() as PlatformContext
    } catch (t: Throwable) {
    }
    try {
        val appCls = Class.forName("android.app.Application")
        return appCls.getDeclaredConstructor().newInstance() as PlatformContext
    } catch (t: Throwable) {
    }
    throw IllegalStateException("This platform gives tests no way to make a PlatformContext")
}
