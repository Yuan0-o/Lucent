package com.lucent.desktop.platform

import java.io.File

object DesktopCloudFolders {

    data class CloudFolder(val name: String, val dir: File)

    fun available(): List<CloudFolder> {
        val home = File(System.getProperty("user.home") ?: return emptyList())
        val found = LinkedHashMap<String, File>()

        fun offer(name: String, dir: File?) {
            if (dir == null) return
            if (!dir.isDirectory) return
            found.putIfAbsent(name, dir)
        }

        offer("Google Drive", File(home, "Google Drive"))
        offer("Google Drive", File(home, "GoogleDrive"))
        offer("Google Drive", File(home, "My Drive"))

        offer("OneDrive", System.getenv("OneDrive")?.let { File(it) })
        offer("OneDrive", System.getenv("OneDriveConsumer")?.let { File(it) })
        offer("OneDrive", System.getenv("OneDriveCommercial")?.let { File(it) })
        offer("OneDrive", File(home, "OneDrive"))

        offer("Dropbox", File(home, "Dropbox"))
        offer("iCloud Drive", File(home, "iCloudDrive"))

        return found.map { (name, dir) -> CloudFolder(name, dir) }
    }

    fun firstAvailable(): File? = available().firstOrNull()?.dir
}
