package com.lucent.app.platform

expect abstract class PlatformContext {
    abstract val applicationContext: PlatformContext
    abstract val filesDir: java.io.File
}

expect fun PlatformContext.appContext(): PlatformContext
