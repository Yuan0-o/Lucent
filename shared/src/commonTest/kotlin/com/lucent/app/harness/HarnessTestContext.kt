package com.lucent.app.harness

import com.lucent.app.platform.PlatformContext

internal fun harnessTestContext(): PlatformContext {
    try {
        val cls = Class.forName("com.lucent.app.platform.DesktopPlatformContext")
        val ctor = cls.getDeclaredConstructor()
        ctor.isAccessible = true
        return ctor.newInstance() as PlatformContext
    } catch (t: Throwable) {
        System.err.println("DesktopPlatformContext failed: " + t.message)
    }
    try {
        val appCls = Class.forName("android.app.Application")
        val ctor = appCls.getDeclaredConstructor()
        ctor.isAccessible = true
        return ctor.newInstance() as PlatformContext
    } catch (t: Throwable) {
        System.err.println("Application failed: " + t.message)
    }
    throw IllegalStateException("No PlatformContext available")
}
