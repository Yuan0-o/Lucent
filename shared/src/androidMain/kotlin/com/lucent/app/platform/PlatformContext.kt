@file:JvmName("PlatformContextAndroidKt")

package com.lucent.app.platform

actual typealias PlatformContext = android.content.Context

actual fun PlatformContext.getApplicationContext(): PlatformContext = this.applicationContext
actual fun PlatformContext.getFilesDir(): java.io.File = this.filesDir
actual fun PlatformContext.getCacheDir(): java.io.File = this.cacheDir

actual fun PlatformContext.appContext(): PlatformContext = applicationContext
