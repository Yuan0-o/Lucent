package com.lucent.app.platform

actual typealias PlatformContext = android.content.Context

actual val PlatformContext.applicationContext: PlatformContext get() = this.applicationContext
actual val PlatformContext.filesDir: java.io.File get() = this.filesDir

actual fun PlatformContext.appContext(): PlatformContext = applicationContext
