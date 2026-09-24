package com.lucent.app.data

import android.content.DesktopContext
import com.lucent.app.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class DesktopUpdateInstaller : AutoUpdate.Installer {

    private companion object {
        const val DOWNLOAD_TIMEOUT_MS = 20L * 60L * 1000L
        const val COPY_BUFFER_BYTES = 64 * 1024
    }

    private var job: Job? = null

    override fun hasDownloadFolder(): Boolean = SettingsCache.autoBackup.folderUri.isNotBlank()

    override fun startDownload(info: ReleaseInfo) {
        val asset = info.installer ?: return
        job?.cancel()
        job = AppScope.io.launch {
            val target = partialFile(asset.name)
            val finished = try {
                withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) { fetch(asset, target) }
            } catch (t: Throwable) {
                log("update: download failed (${t::class.simpleName}: ${t.message})")
                null
            }
            if (!isActive) {
                target.delete()
                return@launch
            }
            if (finished != true || target.length() <= 0L) {
                target.delete()
                AutoUpdate.reportDownloadFailed(com.lucent.app.i18n.S.updateDownloadFailed)
                log("update: the download did not finish, partial file removed")
                return@launch
            }
            log("update: downloaded ${asset.name} (${target.length()} bytes)")
            if (copyToBackupFolder(target, asset.name)) {
                log("update: the installer was placed in the backup folder")
            }
            AutoUpdate.reportProgress(1f)
            AutoUpdate.reportDownloadReady()
            AutoUpdate.report(null)
        }
    }

    override fun cancelDownload(info: ReleaseInfo) {
        job?.cancel()
        job = null
        info.installer?.let { partialFile(it.name).delete() }
    }

    override fun isDownloaded(info: ReleaseInfo): Boolean {
        val asset = info.installer ?: return false
        val file = partialFile(asset.name)
        return file.exists() && file.length() > 0L
    }

    override suspend fun install(info: ReleaseInfo): Boolean {
        val asset = info.installer ?: return false
        val file = partialFile(asset.name)
        if (!file.exists() || file.length() <= 0L) return false
        AutoUpdate.markPhase(AutoUpdate.Phase.INSTALLING)
        val result = PrivilegedShell.installPackage(file.absolutePath, file.length())
        log("update: installer started ok=${result.success} ${result.stderr.take(200).trim()}")
        return result.success
    }

    override fun discard(info: ReleaseInfo) {
        val asset = info.installer ?: return
        val file = partialFile(asset.name)
        if (file.exists() && file.delete()) log("update: removed the downloaded installer")
    }

    fun purgeStale(currentVersion: String, pendingTag: String?): Int {
        val keepToken = pendingTag?.trim()?.trimStart('v', 'V')?.takeIf { it.isNotBlank() }
        val files = directory().listFiles() ?: return 0
        var removed = 0
        files.forEach { file ->
            val wanted = keepToken != null && file.name.contains(keepToken)
            if (!wanted && file.delete()) removed += 1
        }
        if (removed > 0) log("update: cleared $removed downloaded package(s) for $currentVersion")
        return removed
    }

    private fun directory(): File =
        File(System.getProperty("java.io.tmpdir"), "lucent-updates").apply { mkdirs() }

    private fun partialFile(name: String): File = File(directory(), name)

    private fun copyToBackupFolder(source: File, name: String): Boolean {
        val folder = SettingsCache.autoBackup.folderUri
        if (folder.isBlank()) return false
        return try {
            val dir = File(folder)
            if (!dir.isDirectory && !dir.mkdirs()) return false
            source.copyTo(File(dir, name), overwrite = true)
            true
        } catch (t: Throwable) {
            log("update: the installer could not be copied to the backup folder (${t.message})")
            false
        }
    }

    private suspend fun fetch(asset: ReleaseAsset, target: File): Boolean = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(DOWNLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder().url(asset.url).header("User-Agent", "Lucent").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                log("update: the download answered ${response.code}")
                return@withContext false
            }
            val body = response.body ?: return@withContext false
            val total = body.contentLength().takeIf { it > 0L }
            var copied = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        coroutineContext.ensureActive()
                        if (total != null) {
                            AutoUpdate.reportProgress((copied.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        target.length() > 0L
    }

    private fun log(message: String) {
        StartupLog.event(DesktopContext, message)
    }
}
