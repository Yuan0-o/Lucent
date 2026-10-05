package com.lucent.app.platform

actual typealias PlatformContext = android.content.Context

actual fun PlatformContext.appContext(): PlatformContext = applicationContext
