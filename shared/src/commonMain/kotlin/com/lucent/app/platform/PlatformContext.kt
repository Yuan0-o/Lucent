package com.lucent.app.platform

import java.io.File

expect abstract class PlatformContext {
    val applicationContext: PlatformContext
    val filesDir: File
    val cacheDir: File
    val packageName: String
}
