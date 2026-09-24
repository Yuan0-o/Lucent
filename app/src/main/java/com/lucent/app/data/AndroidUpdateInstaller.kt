package com.lucent.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.lucent.app.UpdateDownloadService
import java.io.File

class AndroidUpdateInstaller(private val context: Context) : AutoUpdate.Installer {

    override fun hasDownloadFolder(): Boolean = SettingsCache.autoBackup.folderUri.isNotBlank()

    override fun startDownload(info: ReleaseInfo) {
        val asset = info.apk ?: return
        val started = runCatching { UpdateDownloadService.start(context, asset) }.isSuccess
        if (!started) {
            AutoUpdate.reportDownloadFailed(com.lucent.app.i18n.S.updateDownloadFailed)
            StartupLog.event(context, "update: the download service could not be started")
        }
    }

    override fun cancelDownload(info: ReleaseInfo) {
        UpdateDownloadService.cancel(context)
    }

    override fun isDownloaded(info: ReleaseInfo): Boolean {
        val asset = info.apk ?: return false
        val file = UpdateDownloadService.partialFile(context, asset.name)
        return file.exists() && file.length() > 0L
    }

    override suspend fun install(info: ReleaseInfo): Boolean {
        val asset = info.apk ?: return false
        val file = UpdateDownloadService.partialFile(context, asset.name)
        if (!file.exists() || file.length() <= 0L) return false
        if (PrivilegedShell.isReady()) {
            AutoUpdate.markPhase(AutoUpdate.Phase.INSTALLING)
            val result = PrivilegedShell.installPackage(file.absolutePath, file.length())
            StartupLog.event(
                context,
                "update: silent install ok=${result.success} ${result.stderr.take(200).trim()}"
            )
            if (result.success) {
                discard(info)
                return true
            }
        } else {
            StartupLog.event(context, "update: no privileged shell, handing the APK to the package installer")
        }
        return openSystemInstaller(file)
    }

    override fun discard(info: ReleaseInfo) {
        val asset = info.apk ?: return
        val file = UpdateDownloadService.partialFile(context, asset.name)
        if (file.exists() && file.delete()) {
            StartupLog.event(context, "update: removed the downloaded installer")
        }
    }

    fun purgeStale(currentVersion: String, pendingTag: String?): Int {
        val keepToken = pendingTag?.trim()?.trimStart('v', 'V')?.takeIf { it.isNotBlank() }
        val files = File(context.cacheDir, "updates").listFiles() ?: return 0
        var removed = 0
        files.forEach { file ->
            val wanted = keepToken != null && file.name.contains(keepToken)
            if (!wanted && file.delete()) removed += 1
        }
        if (removed > 0) {
            StartupLog.event(context, "update: cleared $removed downloaded package(s) for $currentVersion")
        }
        return removed
    }

    private fun openSystemInstaller(file: File): Boolean = try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (t: Throwable) {
        StartupLog.event(context, "update: the package installer could not be opened (${t.message})")
        false
    }
}
