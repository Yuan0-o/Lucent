package com.lucent.app.data

import com.lucent.app.LucentBuild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ReleaseAsset(
    val name: String,
    val url: String,
    val sizeBytes: Long
)

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val title: String,
    val apk: ReleaseAsset?,
    val installer: ReleaseAsset?,
    val notesUrl: String = ""
) {
    val releaseUrl: String
        get() = notesUrl.ifBlank { LucentBuild.HOMEPAGE + "/releases/tag/" + tag }
}

object UpdateChecker {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun parseVersion(text: String): List<Int>? {
        val cleaned = text.trim().trimStart('v', 'V')
        val core = cleaned.substringBefore('-').substringBefore('+')
        val parts = core.split('.')
        if (parts.isEmpty() || parts.size > 4) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        if (numbers.any { it < 0 }) return null
        return numbers
    }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parseVersion(candidate) ?: return false
        val b = parseVersion(current) ?: return false
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    suspend fun latest(currentVersion: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LucentBuild.RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Lucent")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body?.string().orEmpty()
        }
        if (body.isBlank()) return@withContext null
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext null
        val tag = root.optString("tag_name")
        if (tag.isBlank()) return@withContext null
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return@withContext null
        if (!isNewer(tag, currentVersion)) return@withContext null

        var apk: ReleaseAsset? = null
        var installer: ReleaseAsset? = null
        val assets = root.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val item = assets.optJSONObject(i) ?: continue
                val name = item.optString("name")
                val url = item.optString("browser_download_url")
                if (name.isBlank() || url.isBlank()) continue
                val asset = ReleaseAsset(name, url, item.optLong("size"))
                when {
                    name.endsWith(".apk", ignoreCase = true) && apk == null -> apk = asset
                    name.endsWith(".exe", ignoreCase = true) && installer == null -> installer = asset
                }
            }
        }

        ReleaseInfo(
            tag = tag,
            version = tag.trimStart('v', 'V'),
            title = root.optString("name").ifBlank { tag },
            apk = apk,
            installer = installer,
            notesUrl = root.optString("html_url")
        )
    }
}
