package com.lucent.app.harness

import com.lucent.app.platform.PlatformContext

internal fun harnessTestContext(): PlatformContext {
    val cl = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
    try {
        val cls = Class.forName("com.lucent.app.platform.DesktopPlatformContext", true, cl)
        val ctor = cls.getDeclaredConstructor()
        ctor.isAccessible = true
        return ctor.newInstance() as PlatformContext
    } catch (t: Throwable) {
    }
    throw IllegalStateException("No PlatformContext available")
}
