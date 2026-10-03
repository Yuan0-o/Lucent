package com.lucent.app.harness.plugins

import com.lucent.app.harness.HarnessConfig
import com.lucent.app.harness.HarnessRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object PluginCatalogRemote {

    private var memoryCache: List<PluginSpec>? = null
    private var memoryCacheUrl: String? = null

    fun merge(static: List<PluginSpec>, remoteJson: String): List<PluginSpec> {
        val parsedRemote = mutableListOf<PluginSpec>()
        try {
            val arr = JSONArray(remoteJson)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                if (id.isBlank()) continue
                val sourcesArr = obj.optJSONArray("sources")
                val sourcesList = mutableListOf<PluginSource>()
                if (sourcesArr != null) {
                    for (j in 0 until sourcesArr.length()) {
                        val so = sourcesArr.optJSONObject(j) ?: continue
                        val sid = so.optString("id", "")
                        val surl = so.optString("url", "")
                        if (sid.isNotBlank() && surl.isNotBlank()) {
                            sourcesList.add(
                                PluginSource(
                                    id = sid,
                                    label = so.optString("label", ""),
                                    url = surl,
                                    official = so.optBoolean("official", false),
                                    sha256 = so.optString("sha256", ""),
                                    bytes = so.optLong("bytes", 0L)
                                )
                            )
                        }
                    }
                }
                
                parsedRemote.add(
                    PluginSpec(
                        id = id,
                        name = obj.optString("name", obj.optString("title", "")),
                        summary = obj.optString("summary", ""),
                        android = obj.optBoolean("android", true),
                        desktop = obj.optBoolean("desktop", true),
                        bytes = obj.optLong("bytes", 0L),
                        sources = sourcesList,
                        detectCommand = obj.optString("detectCommand", obj.optString("detect", "")),
                        installScript = obj.optString("installScript", ""),
                        removeScript = obj.optString("removeScript", ""),
                        licence = obj.optString("licence", ""),
                        homepage = obj.optString("homepage", ""),
                        needsShell = obj.optBoolean("needsShell", true),
                        windowsDetect = obj.optString("windowsDetect", ""),
                        windowsInstall = obj.optString("windowsInstall", ""),
                        windowsRemove = obj.optString("windowsRemove", "")
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

        val cacheFile = File(HarnessRuntime.downloadsDir(), "catalog-cache.json")
        val now = System.currentTimeMillis()
        val epoch = config.pluginCatalogCacheEpoch

        if (epoch > 0 && (now - epoch) < 6 * 60 * 60 * 1000L && cacheFile.exists()) {
            val cachedJson = withContext(Dispatchers.IO) {
                try {
                    cacheFile.readText()
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
                val connection = URL(urlStr).openConnection() as HttpsURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                
                cacheFile.writeText(json)
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
