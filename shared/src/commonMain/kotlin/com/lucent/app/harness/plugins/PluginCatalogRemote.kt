package com.lucent.app.harness.plugins

import com.lucent.app.harness.HarnessConfig
import com.lucent.app.harness.HarnessRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okio.FileSystem
import okio.Path.Companion.toPath
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Duration.Companion.seconds

object PluginCatalogRemote {

    private var memoryCache: List<PluginSpec>? = null
    private var memoryCacheUrl: String? = null

    fun merge(static: List<PluginSpec>, remoteJson: String): List<PluginSpec> {
        val parsedRemote = mutableListOf<PluginSpec>()
        try {
            val arr = Json.parseToJsonElement(remoteJson).jsonArray
            for (i in 0 until arr.size) {
                val obj = arr[i] as? JsonObject ?: continue
                val id = obj["id"]?.jsonPrimitive?.content ?: ""
                if (id.isBlank()) continue
                val sourcesArr = obj["sources"]?.jsonArray
                val sourcesList = mutableListOf<PluginSource>()
                if (sourcesArr != null) {
                    for (j in 0 until sourcesArr.size) {
                        val so = sourcesArr[j] as? JsonObject ?: continue
                        val sid = so["id"]?.jsonPrimitive?.content ?: ""
                        val surl = so["url"]?.jsonPrimitive?.content ?: ""
                        if (sid.isNotBlank() && surl.isNotBlank()) {
                            sourcesList.add(
                                PluginSource(
                                    id = sid,
                                    label = so["label"]?.jsonPrimitive?.content ?: "",
                                    url = surl,
                                    official = so["official"]?.jsonPrimitive?.booleanOrNull ?: false,
                                    sha256 = so["sha256"]?.jsonPrimitive?.content ?: "",
                                    bytes = so["bytes"]?.jsonPrimitive?.longOrNull ?: 0L
                                )
                            )
                        }
                    }
                }
                
                parsedRemote.add(
                    PluginSpec(
                        id = id,
                        name = obj["name"]?.jsonPrimitive?.content ?: (obj["title"]?.jsonPrimitive?.content ?: ""),
                        summary = obj["summary"]?.jsonPrimitive?.content ?: "",
                        android = obj["android"]?.jsonPrimitive?.booleanOrNull ?: true,
                        desktop = obj["desktop"]?.jsonPrimitive?.booleanOrNull ?: true,
                        bytes = obj["bytes"]?.jsonPrimitive?.longOrNull ?: 0L,
                        sources = sourcesList,
                        detectCommand = obj["detectCommand"]?.jsonPrimitive?.content ?: (obj["detect"]?.jsonPrimitive?.content ?: ""),
                        installScript = obj["installScript"]?.jsonPrimitive?.content ?: "",
                        removeScript = obj["removeScript"]?.jsonPrimitive?.content ?: "",
                        licence = obj["licence"]?.jsonPrimitive?.content ?: "",
                        homepage = obj["homepage"]?.jsonPrimitive?.content ?: "",
                        needsShell = obj["needsShell"]?.jsonPrimitive?.booleanOrNull ?: true,
                        windowsDetect = obj["windowsDetect"]?.jsonPrimitive?.content ?: "",
                        windowsInstall = obj["windowsInstall"]?.jsonPrimitive?.content ?: "",
                        windowsRemove = obj["windowsRemove"]?.jsonPrimitive?.content ?: ""
                    )
                )
            }
        } catch (_: Exception) {
        }

        val result = static.toMutableList()
        for (remote in parsedRemote) {
            val existingIdx = result.indexOfFirst { it.id == remote.id }
            if (existingIdx >= 0) {
                val existing = result[existingIdx]
                val existingUrls = existing.sources.map { it.url }.toSet()
                val newSources = remote.sources.filter { it.url !in existingUrls && it.sha256.isNotBlank() }
                if (newSources.isNotEmpty()) {
                    result[existingIdx] = existing.copy(sources = existing.sources + newSources)
                }
            } else {
                result.add(remote)
            }
        }
        return result
    }

    suspend fun fetchEffective(config: HarnessConfig): List<PluginSpec> {
        val static = PluginCatalog.all()
        val urlStr = config.pluginCatalogUrl
        if (urlStr.isBlank() || !urlStr.startsWith("https:" + "//")) {
            return static
        }

        if (memoryCacheUrl == urlStr) memoryCache?.let { return it }

        val cacheFile = HarnessRuntime.downloadsDirPath().toPath() / "catalog-cache.json"
        val now = System.currentTimeMillis()
        val epoch = config.pluginCatalogCacheEpoch

        if (epoch > 0 && (now - epoch) < 6 * 60 * 60 * 1000L && FileSystem.SYSTEM.exists(cacheFile)) {
            val cachedJson = withContext(Dispatchers.IO) {
                try {
                    FileSystem.SYSTEM.read(cacheFile) { readUtf8() }
                } catch (_: Exception) {
                    ""
                }
            }
            if (cachedJson.isNotBlank()) {
                val merged = merge(static, cachedJson)
                memoryCache = merged
                memoryCacheUrl = urlStr
                return merged
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10.seconds)
                    .readTimeout(10.seconds)
                    .build()
                val request = Request.Builder().url(urlStr).build()
                val json = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.string() ?: throw Exception("Empty body")
                }
                
                FileSystem.SYSTEM.write(cacheFile) { writeUtf8(json) }
                val newEpoch = System.currentTimeMillis()

                val nextConfig = HarnessRuntime.config().copy(pluginCatalogCacheEpoch = newEpoch)
                HarnessRuntime.update(nextConfig)

                val merged = merge(static, json)
                memoryCache = merged
                memoryCacheUrl = urlStr
                merged
            } catch (_: Exception) {
                static
            }
        }
    }
    
    fun clearCache() {
        memoryCache = null
        memoryCacheUrl = null
    }
}
