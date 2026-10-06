package com.lucent.app.data

import com.lucent.app.LucentBuild
import com.lucent.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object PreviewChecker {

    private suspend fun fetch(url: String, client: OkHttpClient): JsonObject? = withContext(Dispatchers.IO) {
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
        runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
    }

    private fun field(body: String, name: String): String? {
        return Regex("(?m)^" + Regex.escape(name) + ":\\s*(\\S+)").find(body)?.groupValues?.get(1)?.ifBlank { null }
    }

    private fun newestVersion(first: String?, second: String?): String {
        if (first == null) return second ?: "preview"
        if (second == null) return first
        return if (UpdateChecker.isNewer(second, first)) second else first
    }

    private fun parsePrerelease(root: JsonObject): ReleaseInfo? {
        if (root["tag_name"]?.jsonPrimitive?.content != "preview") return null
        var apk: ReleaseAsset? = null
        var installer: ReleaseAsset? = null
        val assets = root["assets"]?.jsonArray
        if (assets != null) {
            for (i in 0 until assets.size) {
                val item = assets[i] as? JsonObject ?: continue
                val name = item["name"]?.jsonPrimitive?.content ?: ""
                val url = item["browser_download_url"]?.jsonPrimitive?.content ?: ""
                if (name.isBlank() || url.isBlank()) continue
                val asset = ReleaseAsset(name, url, item["size"]?.jsonPrimitive?.longOrNull ?: 0L)
                when {
                    name.endsWith(".apk", ignoreCase = true) && apk == null -> apk = asset
                    name.endsWith(".exe", ignoreCase = true) && installer == null -> installer = asset
                }
            }
        }
        if (apk == null && installer == null) return null
        val body = root["body"]?.jsonPrimitive?.content ?: ""
        val genericSha = field(body, "Commit")
        val genericVersion = field(body, "Version")
        val apkVersion = field(body, "APK-Version") ?: genericVersion
        val exeVersion = field(body, "EXE-Version") ?: genericVersion
        val genericBuildId = field(body, "Build-ID")
        val buildId = genericBuildId ?: field(body, "APK-Build-ID") ?: field(body, "EXE-Build-ID") ?: field(body, "APK-Commit") ?: field(body, "EXE-Commit") ?: genericSha
        return ReleaseInfo(
            tag = "preview",
            version = newestVersion(apkVersion, exeVersion),
            title = (root["name"]?.jsonPrimitive?.content?.ifBlank { null } ?: "Preview") + " - " + S.previewBuildLabel,
            apk = apk,
            installer = installer,
            notesUrl = root["html_url"]?.jsonPrimitive?.content ?: "",
            channel = "preview",
            apkSha = field(body, "APK-Commit") ?: genericSha,
            exeSha = field(body, "EXE-Commit") ?: genericSha,
            apkVersion = apkVersion,
            exeVersion = exeVersion,
            buildId = buildId
        )
    }

    suspend fun getPreviewCandidate(client: OkHttpClient): ReleaseInfo? {
        val candidate = fetch(LucentBuild.REPO_API + "/releases/tags/preview", client)
            ?.let { parsePrerelease(it) } ?: return null
        val runsRoot = fetch(LucentBuild.REPO_API + "/actions/runs?status=success&per_page=20", client)
        val runs = runsRoot?.get("workflow_runs")?.jsonArray
        var newestApkSha: String? = null
        var newestExeSha: String? = null
        if (runs != null) {
            for (i in 0 until runs.size) {
                val run = runs[i] as? JsonObject ?: continue
                when (run["name"]?.jsonPrimitive?.content) {
                    "Build APK only" -> if (newestApkSha == null) newestApkSha = run["head_sha"]?.jsonPrimitive?.content?.ifBlank { null }
                    "Build EXE only" -> if (newestExeSha == null) newestExeSha = run["head_sha"]?.jsonPrimitive?.content?.ifBlank { null }
                    "Build Internal (APK+EXE)" -> {
                        val sha = run["head_sha"]?.jsonPrimitive?.content?.ifBlank { null }
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
