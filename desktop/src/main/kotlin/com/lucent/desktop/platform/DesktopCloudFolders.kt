package com.lucent.desktop.platform

import java.io.File

/**
 * Finds the user's cloud-storage sync folders on this machine (B-group task 16).
 *
 * ### Why a folder and not a picker
 *
 * On Android, "attach from Google Drive" is a real, enumerable thing: Drive ships a
 * DocumentsProvider and the Storage Access Framework lists it alongside every other provider. On
 * Windows there is no equivalent — the desktop clients for Drive, OneDrive and Dropbox all work by
 * syncing a *folder* into the filesystem. So the honest desktop counterpart of that Android entry
 * is not a second picker but the ordinary file dialog, opened in the right place.
 *
 * ### How the folders are found
 *
 * By looking, not by guessing. Every candidate below is checked against the filesystem and only
 * returned if it actually exists as a directory, so a machine with no cloud client installed gets
 * an empty list and the menu entry disables itself with an explanation, rather than dropping the
 * user into a path that isn't there.
 *
 * OneDrive is the one case where an environment variable is authoritative: the Windows client sets
 * `OneDrive` (and `OneDriveCommercial` for work accounts) to the real sync root, which is often
 * NOT under the home directory on managed machines. That is checked first for exactly that reason.
 *
 * Localized folder names are a known limitation: a Chinese-language Windows may name the Drive
 * folder "我的云端硬盘" rather than "My Drive". The parent folders this looks for ("Google Drive",
 * "OneDrive", "Dropbox") are client-created and stay in English, so the common cases are covered;
 * anything missed simply falls back to the normal file dialog, which still works.
 */
object DesktopCloudFolders {

    /** One detected sync root: where it is, and which service it belongs to. */
    data class CloudFolder(val name: String, val dir: File)

    /**
     * Every cloud sync folder that exists on this machine, in a stable order (Drive, OneDrive,
     * Dropbox, iCloud). Empty when none is installed.
     */
    fun available(): List<CloudFolder> {
        val home = File(System.getProperty("user.home") ?: return emptyList())
        val found = LinkedHashMap<String, File>()

        fun offer(name: String, dir: File?) {
            if (dir == null) return
            if (!dir.isDirectory) return
            found.putIfAbsent(name, dir)
        }

        // Google Drive for desktop: a folder under home, or a mounted virtual drive letter.
        offer("Google Drive", File(home, "Google Drive"))
        offer("Google Drive", File(home, "GoogleDrive"))
        offer("Google Drive", File(home, "My Drive"))

        // OneDrive: the client's own environment variables are authoritative on Windows, because a
        // managed machine can put the sync root anywhere.
        offer("OneDrive", System.getenv("OneDrive")?.let { File(it) })
        offer("OneDrive", System.getenv("OneDriveConsumer")?.let { File(it) })
        offer("OneDrive", System.getenv("OneDriveCommercial")?.let { File(it) })
        offer("OneDrive", File(home, "OneDrive"))

        offer("Dropbox", File(home, "Dropbox"))
        offer("iCloud Drive", File(home, "iCloudDrive"))

        return found.map { (name, dir) -> CloudFolder(name, dir) }
    }

    /** The first available sync folder, or null when no cloud client is installed. */
    fun firstAvailable(): File? = available().firstOrNull()?.dir
}
