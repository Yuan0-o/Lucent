@file:JvmName("PlatformContextAndroidKt")

package com.lucent.app.platform

actual typealias PlatformContext = android.content.Context

actual fun PlatformContext.getApplicationContext(): PlatformContext = this.applicationContext
actual fun PlatformContext.getFilesDir(): okio.Path = okio.Path.Companion.toPath(this.filesDir.absolutePath)
actual fun PlatformContext.getCacheDir(): okio.Path = okio.Path.Companion.toPath(this.cacheDir.absolutePath)

actual fun PlatformContext.appContext(): PlatformContext = applicationContext
