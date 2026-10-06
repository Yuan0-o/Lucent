package com.lucent.app.harness.plugins

import com.lucent.app.harness.PluginOutcome
import com.lucent.app.harness.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import com.lucent.app.data.CryptoPlatform
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object PluginDownload {

    private const val PROBE_BYTES = 262144

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20.seconds)
            .readTimeout(60.seconds)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val slowClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20.seconds)
            .readTimeout(30.minutes)
            .writeTimeout(60.seconds)
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
                body.source().use { input ->
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

    suspend fun fastest(sources: List<PluginSource>, mirrorRegion: String = "auto"): PluginSource? {
        if (sources.isEmpty()) return null
        if (sources.size == 1) return sources.first()
        val ordered = when (mirrorRegion) {
            "cn" -> sources.sortedByDescending { it.id == "tuna" || it.id == "aliyun" }
            "global" -> sources.sortedByDescending { it.id != "tuna" && it.id != "aliyun" }
            else -> sources
        }
        val scored = coroutineScope {
            ordered.map { source -> async { source to probe(source) } }.map { it.await() }
        }
        val reachable = scored.filter { it.second != 0L }
        if (reachable.isEmpty()) return ordered.firstOrNull { it.official } ?: ordered.first()
        return reachable.maxByOrNull { it.second }!!.first
    }

    suspend fun speedTable(sources: List<PluginSource>): List<Triple<String, Long, Boolean>> = coroutineScope {
        sources.map { source -> async { Triple(source.label, probe(source), source.official) } }.map { it.await() }
    }

    suspend fun fetch(
        source: PluginSource,
        target: Path,
        onProgress: (Float, String) -> Unit
    ): PluginOutcome = withContext(Dispatchers.IO) {
        if (!source.url.startsWith("http")) {
            return@withContext PluginOutcome(false, "${source.label} has no direct download; ${source.url}")
        }
        target.parent?.let { FileSystem.SYSTEM.createDirectories(it) }

        if (source.bytes > 0L) {
            val space = target.parent?.let { java.io.File(it.toString()).usableSpace } ?: 0L
            val needed = source.bytes * 2L
            if (space in 1L..<needed) {
                return@withContext PluginOutcome(
                    false,
                    "Not enough disk space: have ${Workspace.humanSize(space)}, need ${Workspace.humanSize(needed)}"
                )
            }
        }

        val partFile = "$target.part".toPath()
        var existingLen = FileSystem.SYSTEM.metadataOrNull(partFile)?.size ?: 0L

        try {
            val reqBuilder = Request.Builder().url(source.url).header("User-Agent", "Lucent/3.0")
            if (existingLen > 0L) {
                reqBuilder.header("Range", "bytes=$existingLen-")
            }

            slowClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.code == 416) {
                    FileSystem.SYSTEM.delete(partFile)
                    return@withContext PluginOutcome(false, "${source.label} rejected the resume offset; the partial download was removed, retry to start again")
                }
                if (!response.isSuccessful) {
                    return@withContext PluginOutcome(false, "${source.label} answered HTTP ${response.code}")
                }
                
                val append = response.code == 206 && existingLen > 0L
                if (!append && existingLen > 0L) {
                    existingLen = 0L
                    FileSystem.SYSTEM.sink(partFile).use { }
                }

                val body = response.body ?: return@withContext PluginOutcome(false, "${source.label} sent nothing")
                val total = if (source.bytes > 0L) source.bytes else (body.contentLength() + existingLen)
                
                var written = existingLen
                val buffer = ByteArray(1024 * 1024)

                body.source().use { input ->
                    val sink = if (append) FileSystem.SYSTEM.appendingSink(partFile) else FileSystem.SYSTEM.sink(partFile)
                    sink.buffer().use { output ->
                        while (isActive) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0L) {
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
                if (!isActive) throw kotlinx.coroutines.CancellationException()

                if (source.bytes > 0L && written != source.bytes) {
                    FileSystem.SYSTEM.delete(partFile)
                    FileSystem.SYSTEM.delete(target)
                    return@withContext PluginOutcome(
                        false,
                        "${source.label} sent ${Workspace.humanSize(written)} but ${Workspace.humanSize(source.bytes)} was expected"
                    )
                }

                if (written < 1024L) {
                    FileSystem.SYSTEM.delete(partFile)
                    FileSystem.SYSTEM.delete(target)
                    return@withContext PluginOutcome(false, "${source.label} sent something far too small to be the real file")
                }

                val bytes = FileSystem.SYSTEM.read(partFile) { readByteArray() }
                val hex = CryptoPlatform.sha256(bytes).joinToString("") {
                    it.toUByte().toString(16).padStart(2, '0')
                }
                if (source.sha256.isNotEmpty() && !hex.equals(source.sha256, ignoreCase = true)) {
                    FileSystem.SYSTEM.delete(partFile)
                    FileSystem.SYSTEM.delete(target)
                    return@withContext PluginOutcome(false, "The download from ${source.label} does not match its checksum")
                }

                try {
                    FileSystem.SYSTEM.atomicMove(partFile, target)
                } catch (_: Exception) {
                    FileSystem.SYSTEM.read(partFile) {
                        FileSystem.SYSTEM.write(target) {
                            writeAll(this@read)
                        }
                    }
                    FileSystem.SYSTEM.delete(partFile)
                }

                PluginOutcome(true, "downloaded ${Workspace.humanSize(written)} from ${source.label}", target.toString())
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            PluginOutcome(false, "${source.label} failed: ${t.message ?: t::class.simpleName}. The download will resume on retry.")
        }
    }
}
