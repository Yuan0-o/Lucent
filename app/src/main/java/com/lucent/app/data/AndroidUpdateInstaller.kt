package com.lucent.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class AndroidUpdateInstaller(private val context: Context) : AutoUpdate.Installer {

    override suspend fun install(info: ReleaseInfo): Boolean {
        val asset = info.apk ?: return false
        AutoUpdate.markPhase(AutoUpdate.Phase.DOWNLOADING)
        val file = withContext(Dispatchers.IO) { download(asset) } ?: return false
        StartupLog.event(context, "update: downloaded ${asset.name} (${file.length()} bytes)")
        if (PrivilegedShell.isReady()) {
            AutoUpdate.markPhase(AutoUpdate.Phase.INSTALLING)
            val result = PrivilegedShell.installPackage(file.absolutePath, file.length())
            StartupLog.event(
                context,
                "update: silent install ok=${result.success} ${result.stderr.take(200).trim()}"
            )
            if (result.success) return true
        } else {
            StartupLog.event(context, "update: no privileged shell, handing the APK to the package installer")
        }
        return openSystemInstaller(file)
    }

    private fun directory(): File = File(context.cacheDir, "updates").apply { mkdirs() }

    private fun download(asset: ReleaseAsset): File? {
        val target = File(directory(), asset.name)
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(asset.url).header("User-Agent", "Lucent").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                body.byteStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (target.length() <= 0L) null else target
        } catch (t: Throwable) {
            StartupLog.event(context, "update: download failed (${t::class.simpleName}: ${t.message})")
            null
        }
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
