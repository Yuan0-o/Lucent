@file:JvmName("PlatformContextCommonKt")
package com.lucent.app.platform

expect abstract class PlatformContext

expect fun PlatformContext.getApplicationContext(): PlatformContext
expect fun PlatformContext.getFilesDir(): java.io.File
expect fun PlatformContext.getCacheDir(): java.io.File

val PlatformContext.applicationContext: PlatformContext get() = getApplicationContext()
val PlatformContext.filesDir: java.io.File get() = getFilesDir()
val PlatformContext.cacheDir: java.io.File get() = getCacheDir()

expect fun PlatformContext.appContext(): PlatformContext
