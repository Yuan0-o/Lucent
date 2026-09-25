package com.lucent.app.harness.plugins

import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.harness.PluginOutcome
import com.lucent.app.harness.PluginSource
import com.lucent.app.harness.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object PluginDownload {

    private const val PROBE_BYTES = 262144

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val slowClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun probe(source: PluginSource, timeoutSeconds: Int = 8): Long = withContext(Dispatchers.IO) {
        if (!source.url.startsWith("http")) return@withContext 0L
        try {
            val request = Request.Builder()
                .url(source.url)
                .header("Range", "bytes=0-$PROBE_BYTES")
                .header("User-Agent", "Lucent/3.0")
                .build()
            val started = System.currentTimeMillis()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) return@withContext 0L
                val body = response.body ?: return@withContext 0L
                val buffer = ByteArray(32768)
                var read = 0L
                val limit = PROBE_BYTES.toLong()
                val deadline = started + timeoutSeconds * 1000L
                body.byteStream().use { input ->
                    while (read < limit && System.currentTimeMillis() < deadline) {
                        val chunk = input.read(buffer)
                        if (chunk <= 0) break
                        read += chunk
                    }
                }
                val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
                if (read < 32768) return@withContext if (response.code == 206) 0L else -1L
                read * 1000L / elapsed
            }
        } catch (t: Throwable) {
            0L
        }
    }

    suspend fun fastest(sources: List<PluginSource>): PluginSource? {
        if (sources.isEmpty()) return null
        if (sources.size == 1) return sources.first()
        val scored = coroutineScope {
            sources.map { source -> async { source to probe(source) } }.map { it.await() }
        }
        val usable = scored.filter { it.second > 0 }
        if (usable.isEmpty()) return sources.firstOrNull { it.official } ?: sources.first()
        val chosen = usable.maxByOrNull { it.second }!!
        if (!HarnessRuntime.config().fastMirror) return sources.first()
        return chosen.first
    }

    suspend fun speedTable(sources: List<PluginSource>): List<Triple<String, Long, Boolean>> = coroutineScope {
        sources.map { source -> async { Triple(source.label, probe(source), source.official) } }.map { it.await() }
    }

    suspend fun fetch(
        source: PluginSource,
        target: File,
        onProgress: (Float, String) -> Unit
    ): PluginOutcome = withContext(Dispatchers.IO) {
        if (!source.url.startsWith("http")) {
            return@withContext PluginOutcome(false, "${source.label} has no direct download; ${source.url}")
        }
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        try {
            val request = Request.Builder().url(source.url).header("User-Agent", "Lucent/3.0").build()
            slowClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext PluginOutcome(false, "${source.label} answered HTTP ${response.code}")
                }
                val body = response.body ?: return@withContext PluginOutcome(false, "${source.label} sent nothing")
                val total = if (source.bytes > 0) source.bytes else body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                val buffer = ByteArray(1024 * 1024)
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                onProgress(
                                    (written.toFloat() / total).coerceIn(0f, 1f),
                                    "${Workspace.humanSize(written)} of ${Workspace.humanSize(total)}"
                                )
                            } else {
                                onProgress(0f, Workspace.humanSize(written))
                            }
                        }
                    }
                }
                if (source.bytes > 0 && written != source.bytes) {
                    target.delete()
                    return@withContext PluginOutcome(
                        false,
                        "${source.label} sent ${Workspace.humanSize(written)} but ${Workspace.humanSize(source.bytes)} was expected"
                    )
                }
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                if (source.sha256.isNotEmpty() && !hex.equals(source.sha256, ignoreCase = true)) {
                    target.delete()
                    return@withContext PluginOutcome(false, "The download from ${source.label} does not match its checksum")
                }
                if (written < 1024) {
                    target.delete()
                    return@withContext PluginOutcome(false, "${source.label} sent something far too small to be the real file")
                }
                PluginOutcome(true, "downloaded ${Workspace.humanSize(written)} from ${source.label}", target.path)
            }
        } catch (t: Throwable) {
            try { target.delete() } catch (_: Throwable) {
            }
            PluginOutcome(false, "${source.label} failed: ${t.message ?: t::class.simpleName}")
        }
    }
}
