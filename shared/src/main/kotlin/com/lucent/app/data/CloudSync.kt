package com.lucent.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * v2.7.5 — the cloud storage module, spoken in WebDAV.
 *
 * Why WebDAV: it is the one protocol every mainstream cloud that wants to be used by third-party
 * software already speaks — 坚果云 (Nutstore), Nextcloud, ownCloud, Koofr, Seafile — and it needs
 * no OAuth client registration, no app secrets buried in the APK, and no redirect dance. The user
 * brings an endpoint, a username and an app password (the kind every provider issues for exactly
 * this purpose), and optionally chooses a folder. On phones whose vendor cloud pickers refuse to
 * open — which is a known Huawei party trick — this module is the dependable way in.
 *
 * The client is deliberately small: PROPFIND to list, PUT to upload, GET to download, and an
 * OPTIONS/PROPFIND probe to test a configuration. XML responses are parsed with a tolerant regex
 * scan rather than a schema: WebDAV servers disagree about namespaces, and we only need <href>.
 */
object CloudSync {

    /** Presets the settings page offers. The third entry is "custom". */
    val PRESETS: List<Pair<String, String>> = listOf(
        "Nutstore" to "https://dav.jianguoyun.com/dav/",
        "Nextcloud" to "https://cloud.example.com/remote.php/dav/files/",
        "Koofr" to "https://app.koofr.eu/dav/",
        "Custom" to ""
    )

    data class Config(
        val url: String,
        val user: String,
        val password: String,
        val folder: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** The folder URL with a trailing slash, created on demand by [ensureFolder]. */
    fun folderUrl(config: Config, extra: String = ""): String {
        var base = config.url.trim()
        if (base.isBlank()) return ""
        if (!base.endsWith("/")) base += "/"
        val folder = config.folder.trim().trim('/')
        val path = if (folder.isEmpty()) "" else folder + "/"
        return base + path + extra
    }

    private fun request(config: Config, url: String, method: String): Request.Builder =
        Request.Builder()
            .url(url)
            .method(method, null)
            .header("Authorization", Credentials.basic(config.user, config.password))

    /** Whether the configuration answers at all — the "test connection" button. */
    suspend fun test(config: Config): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = folderUrl(config)
            require(url.isNotBlank()) { "empty URL" }
            // PROPFIND depth 0 asks "is this directory there"; 207 means "yes, Multi-Status".
            val req = request(config, url, "PROPFIND")
                .header("Depth", "0")
                .build()
            client.newCall(req).execute().use { resp ->
                when {
                    resp.code == 207 || resp.code == 200 -> "Connected — ${url}"
                    resp.code == 401 -> throw IOException("Authentication failed (401). Use the account's app password, not the login password.")
                    resp.code == 404 -> "Connected, but the folder is missing — it will be created on first backup."
                    else -> throw IOException("Server answered HTTP ${resp.code}")
                }
            }
        }
    }

    /** Create the folder if missing (MKCOL; a 405 "already exists" is fine). */
    suspend fun ensureFolder(config: Config): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = folderUrl(config)
            require(url.isNotBlank()) { "empty URL" }
            val req = request(config, url, "MKCOL").build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 405 || resp.code == 201 || resp.code == 200 || resp.code == 301 || resp.code == 302) return@use
                if (resp.code == 401) throw IOException("Authentication failed (401)")
                if (resp.code in 400..499) throw IOException("Server refused to create the folder (HTTP ${resp.code})")
            }
        }
    }

    /** Upload one file into the configured folder. */
    suspend fun upload(config: Config, name: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureFolder(config).getOrThrow()
            val req = request(config, folderUrl(config, name), "PUT")
                .put(bytes.toRequestBody("application/octet-stream".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code !in 200..299) throw IOException("Upload failed (HTTP ${resp.code})")
            }
        }
    }

    /** List files in the configured folder (names only; the picker shows the filename). */
    suspend fun list(config: Config, keepExtension: String = ".lcb"): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = request(config, folderUrl(config), "PROPFIND")
                .header("Depth", "1")
                .build()
            val body = client.newCall(req).execute().use { resp ->
                if (resp.code == 401) throw IOException("Authentication failed (401)")
                if (resp.code !in 200..299) throw IOException("List failed (HTTP ${resp.code})")
                resp.body?.string() ?: ""
            }
            regexHrefs(body)
                .map { href -> href.trimEnd('/').substringAfterLast('/') }
                .filter { it.isNotBlank() && it.endsWith(keepExtension) }
                .distinct()
        }
    }

    /** Download one file from the configured folder. */
    suspend fun download(config: Config, name: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val req = request(config, folderUrl(config, name), "GET").build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 401) throw IOException("Authentication failed (401)")
                if (resp.code !in 200..299) throw IOException("Download failed (HTTP ${resp.code})")
                resp.body?.bytes() ?: throw IOException("Empty response")
            }
        }
    }

    private val hrefRegex = Regex("<(?:D:|d:)?href[^>]*>([^<]+)</(?:D:|d:)?href>")

    private fun regexHrefs(body: String): List<String> = hrefRegex.findAll(body).map { it.groupValues[1] }.toList()
}
