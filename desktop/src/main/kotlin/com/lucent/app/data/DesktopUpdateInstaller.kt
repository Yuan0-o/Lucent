package com.lucent.app.data

import android.content.DesktopContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class DesktopUpdateInstaller : AutoUpdate.Installer {

    override suspend fun install(info: ReleaseInfo): Boolean {
        val asset = info.installer ?: return false
        AutoUpdate.setPhase(AutoUpdate.Phase.DOWNLOADING)
        val file = withContext(Dispatchers.IO) { download(asset) } ?: return false
        log("update: downloaded ${asset.name} (${file.length()} bytes)")
        AutoUpdate.setPhase(AutoUpdate.Phase.INSTALLING)
        val result = PrivilegedShell.installPackage(file.absolutePath, file.length())
        log("update: installer started ok=${result.success} ${result.stderr.take(200).trim()}")
        return result.success
    }

    private fun directory(): File =
        File(System.getProperty("java.io.tmpdir"), "lucent-updates").apply { mkdirs() }

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
            log("update: download failed (${t::class.simpleName}: ${t.message})")
            null
        }
    }

    private fun log(message: String) {
        StartupLog.event(DesktopContext, message)
    }
}
