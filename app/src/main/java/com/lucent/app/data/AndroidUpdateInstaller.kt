package com.lucent.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class AndroidUpdateInstaller(private val context: Context) : AutoUpdate.Installer {

    private companion object {
        const val DOWNLOAD_TIMEOUT_MS = 20L * 60L * 1000L
        const val COPY_BUFFER_BYTES = 64 * 1024
    }

    override suspend fun download(info: ReleaseInfo): Boolean {
        val asset = info.apk ?: return false
        val file = withContext(Dispatchers.IO) { download(asset) } ?: return false
        StartupLog.event(context, "update: downloaded ${asset.name} (${file.length()} bytes)")
        return true
    }

    override fun isDownloaded(info: ReleaseInfo): Boolean {
        val asset = info.apk ?: return false
        val file = File(directory(), asset.name)
        return file.exists() && file.length() > 0L
    }

    override suspend fun install(info: ReleaseInfo): Boolean {
        val asset = info.apk ?: return false
        val file = File(directory(), asset.name)
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
        val file = File(directory(), asset.name)
        if (file.exists() && file.delete()) {
            StartupLog.event(context, "update: removed the downloaded installer")
        }
    }

    fun purgeStale(currentVersion: String, pendingTag: String?): Int {
        val keepToken = pendingTag?.trim()?.trimStart('v', 'V')?.takeIf { it.isNotBlank() }
        val files = directory().listFiles() ?: return 0
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

    private fun directory(): File = File(context.cacheDir, "updates").apply { mkdirs() }

    private suspend fun download(asset: ReleaseAsset): File? {
        val target = File(directory(), asset.name)
        return try {
            withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) { fetch(asset, target) }
        } catch (t: Throwable) {
            StartupLog.event(context, "update: download failed (${t::class.simpleName}: ${t.message})")
            target.delete()
            null
        } ?: run {
            StartupLog.event(context, "update: download did not finish in time, removing the partial file")
            target.delete()
            null
        }
    }

    private suspend fun fetch(asset: ReleaseAsset, target: File): File? = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(DOWNLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder().url(asset.url).header("User-Agent", "Lucent").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                StartupLog.event(context, "update: download answered ${response.code}")
                return@withContext null
            }
            val body = response.body ?: return@withContext null
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        coroutineContext.ensureActive()
                    }
                }
            }
        }
        if (target.length() <= 0L) null else target
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
