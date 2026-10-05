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
    val notesUrl: String = "",
    val channel: String = "stable",
    val apkSha: String? = null,
    val exeSha: String? = null,
    val apkVersion: String? = null,
    val exeVersion: String? = null
) {
    val identity: String
        get() = version

    val releaseUrl: String
        get() = notesUrl.ifBlank { LucentBuild.HOMEPAGE + "/releases/tag/" + tag }
}

object UpdateChecker {

    internal val client: OkHttpClient by lazy {
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

    private suspend fun fetchJson(url: String): JSONObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Lucent")
            .build()
        val body = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull() ?: return@withContext null
        if (body.isBlank()) return@withContext null
        runCatching { JSONObject(body) }.getOrNull()
    }

    private suspend fun fetchTagCommit(tag: String): String? {
        val ref = fetchJson(LucentBuild.REPO_API + "/git/ref/tags/" + tag) ?: return null
        val obj = ref.optJSONObject("object") ?: return null
        val sha = obj.optString("sha")
        if (sha.isBlank()) return null
        if (obj.optString("type") == "tag") {
            val tagObject = fetchJson(LucentBuild.REPO_API + "/git/tags/" + sha) ?: return null
            return tagObject.optJSONObject("object")?.optString("sha")?.ifBlank { null }
        }
        return sha
    }

    private fun parseAssets(root: JSONObject): Pair<ReleaseAsset?, ReleaseAsset?> {
        var apk: ReleaseAsset? = null
        var installer: ReleaseAsset? = null
        val assets = root.optJSONArray("assets") ?: return null to null
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
        return apk to installer
    }

    suspend fun latest(currentVersion: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        var stable: ReleaseInfo? = null
        var stableTag = ""
        val root = fetchJson(LucentBuild.RELEASES_API)
        if (root != null) {
            val tag = root.optString("tag_name")
            val isDraftOrPrerelease = root.optBoolean("draft") || root.optBoolean("prerelease")
            if (tag.isNotBlank() && !isDraftOrPrerelease) {
                stableTag = tag
                if (isNewer(tag, currentVersion)) {
                    val (apk, installer) = parseAssets(root)
                    stable = ReleaseInfo(
                        tag = tag,
                        version = tag.trimStart('v', 'V'),
                        title = root.optString("name").ifBlank { tag },
                        apk = apk,
                        installer = installer,
                        notesUrl = root.optString("html_url"),
                        channel = "stable"
                    )
                }
            }
        }

        var candidate = stable
        if (SettingsCache.updateChannel == "preview") {
            val preview = PreviewChecker.getPreviewCandidate(client)
            if (preview != null) {
                val stableNow = stable
                candidate = when {
                    stableNow == null -> preview
                    isNewer(AutoUpdate.installer?.versionOf(preview) ?: preview.version, AutoUpdate.installer?.versionOf(stableNow) ?: stableNow.version) -> preview
                    else -> stableNow
                }
            }
        }

        var result = candidate ?: return@withContext null
        val engine = AutoUpdate.installer
        if (engine != null && !engine.hasAsset(result)) {
            val fallback = stable
            if (result.channel == "preview" && fallback != null && engine.hasAsset(fallback)) {
                result = fallback
            } else {
                return@withContext null
            }
        }
        val candidateVersion = engine?.versionOf(result) ?: result.version
        val candidateIdentity = engine?.identityOf(result) ?: result.identity
        val installedIdentity = SettingsCache.installedPreviewIdentity
        if (installedIdentity.isNotBlank() && candidateIdentity == installedIdentity) return@withContext null
        if (candidateIdentity == currentVersion) return@withContext null
        if (result.channel == "preview") {
            if (parseVersion(candidateVersion) == null) return@withContext null
            if (isNewer(currentVersion, candidateVersion)) return@withContext null
            if (candidateVersion == currentVersion && stableTag.isNotBlank()) {
                val tagCommit = fetchTagCommit(stableTag)
                if (tagCommit != null && candidateIdentity == tagCommit) return@withContext null
            }
        }
        result
    }
}
