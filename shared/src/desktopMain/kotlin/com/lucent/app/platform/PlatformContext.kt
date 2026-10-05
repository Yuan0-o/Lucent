package com.lucent.app.platform

import java.io.File

actual abstract class PlatformContext {
    abstract val applicationContext: PlatformContext
    abstract val filesDir: File
    abstract val cacheDir: File
    abstract val packageName: String
}

class DesktopPlatformContext : PlatformContext() {
    override val applicationContext: PlatformContext get() = this

    override val filesDir: File by lazy {
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

    override val cacheDir: File by lazy { File(filesDir, "cache").apply { mkdirs() } }

    override val packageName: String get() = "com.lucent.desktop"
}

val desktopPlatformContext: PlatformContext = DesktopPlatformContext()

actual fun PlatformContext.appContext(): PlatformContext = applicationContext
