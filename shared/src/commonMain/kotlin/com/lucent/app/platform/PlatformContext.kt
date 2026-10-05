package com.lucent.app.platform

expect abstract class PlatformContext {
    abstract val applicationContext: PlatformContext
}

expect fun PlatformContext.appContext(): PlatformContext
