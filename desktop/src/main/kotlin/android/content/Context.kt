package android.content

import java.io.File

open class Context {

    open val applicationContext: Context get() = this

    open val filesDir: File by lazy {
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

    open val cacheDir: File by lazy { File(filesDir, "cache").apply { mkdirs() } }

    open val packageName: String get() = "com.lucent.desktop"
}

object DesktopContext : Context()
