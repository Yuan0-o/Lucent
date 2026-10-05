package com.lucent.app.platform

expect abstract class PlatformContext

expect val PlatformContext.applicationContext: PlatformContext
expect val PlatformContext.filesDir: java.io.File

expect fun PlatformContext.appContext(): PlatformContext
