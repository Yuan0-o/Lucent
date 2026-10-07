@file:JvmName("PlatformContextAndroidKt")

package com.lucent.app.platform

import okio.Path.Companion.toPath

actual typealias PlatformContext = android.content.Context


actual fun PlatformContext.getApplicationContext(): PlatformContext = this.applicationContext
actual fun PlatformContext.getFilesDir(): okio.Path = this.filesDir.absolutePath.toPath()
actual fun PlatformContext.getCacheDir(): okio.Path = this.cacheDir.absolutePath.toPath()

actual fun PlatformContext.appContext(): PlatformContext = applicationContext
