package com.lucent.app.data

import com.lucent.app.LucentBuild
import com.lucent.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object PreviewChecker {

    private suspend fun fetch(url: String, client: OkHttpClient): JSONObject? = withContext(Dispatchers.IO) {
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

    private fun field(body: String, name: String): String? {
        return Regex("(?m)^" + Regex.escape(name) + ":\\s*(\\S+)").find(body)?.groupValues?.get(1)?.ifBlank { null }
    }

    private fun newestVersion(first: String?, second: String?): String {
        if (first == null) return second ?: "preview"
        if (second == null) return first
        return if (UpdateChecker.isNewer(second, first)) second else first
    }

    private fun parsePrerelease(root: JSONObject): ReleaseInfo? {
        if (root.optString("tag_name") != "preview") return null
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
        if (apk == null && installer == null) return null
        val body = root.optString("body")
        val genericSha = field(body, "Commit")
        val genericVersion = field(body, "Version")
        val apkVersion = field(body, "APK-Version") ?: genericVersion
        val exeVersion = field(body, "EXE-Version") ?: genericVersion
        return ReleaseInfo(
            tag = "preview",
            version = newestVersion(apkVersion, exeVersion),
            title = root.optString("name").ifBlank { "Preview" } + " - " + S.previewBuildLabel,
            apk = apk,
            installer = installer,
            notesUrl = root.optString("html_url"),
            channel = "preview",
            apkSha = field(body, "APK-Commit") ?: genericSha,
            exeSha = field(body, "EXE-Commit") ?: genericSha,
            apkVersion = apkVersion,
            exeVersion = exeVersion
        )
    }

    suspend fun getPreviewCandidate(client: OkHttpClient): ReleaseInfo? {
        val candidate = fetch(LucentBuild.REPO_API + "/releases/tags/preview", client)
            ?.let { parsePrerelease(it) } ?: return null
        val runsRoot = fetch(LucentBuild.REPO_API + "/actions/runs?status=success&per_page=20", client)
        val runs = runsRoot?.optJSONArray("workflow_runs")
        var newestApkSha: String? = null
        var newestExeSha: String? = null
        if (runs != null) {
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                when (run.optString("name")) {
                    "Build APK only" -> if (newestApkSha == null) newestApkSha = run.optString("head_sha").ifBlank { null }
                    "Build EXE only" -> if (newestExeSha == null) newestExeSha = run.optString("head_sha").ifBlank { null }
                    "Build Internal (APK+EXE)" -> {
                        val sha = run.optString("head_sha").ifBlank { null }
                        if (newestApkSha == null) newestApkSha = sha
                        if (newestExeSha == null) newestExeSha = sha
                    }
                }
            }
        }
        var apk = candidate.apk
        var installer = candidate.installer
        if (apk != null && newestApkSha != null && candidate.apkSha != null && candidate.apkSha != newestApkSha) apk = null
        if (installer != null && newestExeSha != null && candidate.exeSha != null && candidate.exeSha != newestExeSha) installer = null
        if (apk == null && installer == null) return null
        if (apk == candidate.apk && installer == candidate.installer) return candidate
        return candidate.copy(apk = apk, installer = installer)
    }
}
