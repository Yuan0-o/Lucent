package com.lucent.app.platform

import java.io.File

expect abstract class PlatformContext {
    abstract val applicationContext: PlatformContext
    abstract val filesDir: File
    abstract val cacheDir: File
    abstract val packageName: String
}
