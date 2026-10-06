@file:JvmName("PlatformContextCommonKt")
package com.lucent.app.platform

expect abstract class PlatformContext

expect fun PlatformContext.getApplicationContext(): PlatformContext
expect fun PlatformContext.getFilesDir(): okio.Path
expect fun PlatformContext.getCacheDir(): okio.Path

val PlatformContext.applicationContext: PlatformContext get() = getApplicationContext()
val PlatformContext.filesDir: okio.Path get() = getFilesDir()
val PlatformContext.cacheDir: okio.Path get() = getCacheDir()

expect fun PlatformContext.appContext(): PlatformContext
