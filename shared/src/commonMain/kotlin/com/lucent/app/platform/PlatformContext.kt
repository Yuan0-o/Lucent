@file:JvmName("PlatformContextCommonKt")
package com.lucent.app.platform

expect abstract class PlatformContext

expect fun PlatformContext.getApplicationContext(): PlatformContext
expect fun PlatformContext.getFilesDir(): java.io.File

val PlatformContext.applicationContext: PlatformContext get() = getApplicationContext()
val PlatformContext.filesDir: java.io.File get() = getFilesDir()

expect fun PlatformContext.appContext(): PlatformContext
