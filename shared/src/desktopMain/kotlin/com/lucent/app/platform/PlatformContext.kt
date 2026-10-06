package com.lucent.app.platform

import java.io.File

actual abstract class PlatformContext

actual fun PlatformContext.getApplicationContext(): PlatformContext = (this as DesktopPlatformContext).applicationContext
actual fun PlatformContext.getFilesDir(): java.io.File = (this as DesktopPlatformContext).filesDir
actual fun PlatformContext.getCacheDir(): java.io.File = (this as DesktopPlatformContext).cacheDir

actual fun PlatformContext.appContext(): PlatformContext = applicationContext

class DesktopPlatformContext : PlatformContext() {
    val applicationContext: PlatformContext get() = this

    val filesDir: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        val base: File = when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) File(appData) else File(home, "AppData/Roaming")
            }
            os.contains("mac") -> File(home, "Library/Application Support")
            else -> File(home, ".local/share")
        }
        File(base, "Lucent").apply { mkdirs() }
    }

    val cacheDir: File by lazy { File(filesDir, "cache").apply { mkdirs() } }

    val packageName: String get() = "com.lucent.desktop"
}

val desktopPlatformContext: PlatformContext = DesktopPlatformContext()
