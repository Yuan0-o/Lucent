package com.lucent.app.harness

import kotlin.io.encoding.Base64
import com.lucent.app.network.ToolExecResult
import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer


data class HttpReply(
    val code: Int,
    val body: String,
    val error: String = "",
    val truncated: Boolean = false
) {
    val ok: Boolean get() = code in 200..299
}

data class HttpBytes(
    val code: Int,
    val bytes: ByteArray,
    val error: String = "",
    val truncated: Boolean = false
) {
    val ok: Boolean get() = code in 200..299
}

object HttpJson {

    const val REPLY_BUDGET = 4000

    const val INLINE_BUDGET = 20000

    const val MAX_BYTES = 2 * 1024 * 1024

    private const val JSON_TYPE = "application/json; charset=utf-8"

    private val jsonMedia: MediaType = JSON_TYPE.toMediaType()

    private val plain = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(60))
        .readTimeout(java.time.Duration.ofSeconds(60))
        .writeTimeout(java.time.Duration.ofSeconds(60))
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .build()

    private val following = plain.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        timeoutSeconds: Int = 60,
        followRedirects: Boolean = false
    ): HttpReply = withContext(Dispatchers.IO) {
        val outcome = try {
            exchange(method, url, headers, body, timeoutSeconds, followRedirects)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            return@withContext HttpReply(0, "", t.message ?: t::class.java.simpleName)
        }
        HttpReply(
            code = outcome.first,
            body = String(outcome.second, Charsets.UTF_8),
            truncated = outcome.third
        )
    }

    suspend fun requestBytes(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        timeoutSeconds: Int = 60,
        followRedirects: Boolean = true
    ): HttpBytes = withContext(Dispatchers.IO) {
        try {
            val outcome = exchange(method, url, headers, body, timeoutSeconds, followRedirects)
            HttpBytes(outcome.first, outcome.second, "", outcome.third)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            HttpBytes(0, ByteArray(0), t.message ?: t::class.java.simpleName)
        }
    }

    private fun exchange(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        timeoutSeconds: Int,
        followRedirects: Boolean
    ): Triple<Int, ByteArray, Boolean> {
        val verb = method.trim().uppercase().ifBlank { "GET" }
        val media = mediaFor(headers)
        val payload: RequestBody? = when {
            body != null -> body.toRequestBody(media)
            verb == "GET" || verb == "HEAD" -> null
            verb == "POST" || verb == "PUT" || verb == "PATCH" || verb == "PROPPATCH" || verb == "REPORT" ->
                "".toRequestBody(media)
            else -> null
        }
        val builder = Request.Builder().url(url)
        headers.forEach { (key, value) -> if (key.isNotBlank()) builder.header(key, value) }
        val request = builder.method(verb, payload).build()
        val seconds = timeoutSeconds.coerceIn(5, 300).toLong()
        val client = (if (followRedirects) following else plain).newBuilder()
            .callTimeout(java.time.Duration.ofSeconds(seconds))
            .build()
        client.newCall(request).execute().use { response ->
            val store = Buffer()
            var truncated = false
            val source = response.body?.source()
            if (source != null) {
                val chunk = ByteArray(16384)
                var total = 0
                while (total < MAX_BYTES) {
                    val read = source.read(chunk, 0, minOf(chunk.size, MAX_BYTES - total))
                    if (read <= 0) break
                    store.write(chunk, 0, read)
                    total += read
                }
                if (total >= MAX_BYTES && !source.exhausted()) truncated = true
            }
            return Triple(response.code, store.readByteArray(), truncated)
        }
    }

    private fun mediaFor(headers: Map<String, String>): MediaType {
        val declared = headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
        if (declared.isNullOrBlank()) return jsonMedia
        return try {
            declared.toMediaType()
        } catch (e: Exception) {
            jsonMedia
        }
    }

    fun urlProblem(url: String): String? {
        val clean = url.trim()
        if (clean.isEmpty()) return "Give the URL to request."
        val lower = clean.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return "Only http:// and https:// URLs can be requested, so $clean was left alone."
        }
        val rest = clean.substringAfter("://")
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.isBlank()) return "That URL has no host: $clean"
        if (host.any { it.isWhitespace() }) return "That URL has a space in its host: $clean"
        return null
    }

    fun action(raw: String, allowed: List<String>): String {
        val trimmed = raw.trim()
        val source = if (trimmed.any { it.isLowerCase() }) trimmed else trimmed.lowercase()
        val spaced = StringBuilder()
        source.forEachIndexed { index, ch ->
            if (index > 0 && ch.isUpperCase() && spaced.isNotEmpty() && spaced.last().isLowerCase()) {
                spaced.append('_')
            }
            spaced.append(ch.lowercaseChar())
        }
        val clean = spaced.toString().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        return if (allowed.contains(clean)) clean else ""
    }

    fun pretty(raw: String, budget: Int = REPLY_BUDGET): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        val formatted = try {
            when (text.first()) {
                '{' -> Json.parseToJsonElement(text).jsonObject.toString(2)
                '[' -> Json.parseToJsonElement(text).jsonArray.toString(2)
                else -> text
            }
        } catch (e: Exception) {
            text
        }
        return cut(formatted, budget)
    }

    fun cut(text: String, budget: Int = REPLY_BUDGET): String {
        val limit = budget.coerceAtLeast(200)
        if (text.length <= limit) return text
        return text.take(limit) + "\n… output cut at $limit of ${text.length} characters"
    }

    fun describe(code: Int): String = when (code) {
        0 -> "no response"
        400 -> "HTTP 400 (the request was malformed)"
        401 -> "HTTP 401 (the token was rejected)"
        403 -> "HTTP 403 (forbidden: missing permission, or a rate limit)"
        404 -> "HTTP 404 (not found)"
        405 -> "HTTP 405 (that method is not allowed here)"
        409 -> "HTTP 409 (a conflict, for example the item already exists)"
        410 -> "HTTP 410 (the endpoint is gone)"
        413 -> "HTTP 413 (the body was too large)"
        422 -> "HTTP 422 (the fields were rejected)"
        429 -> "HTTP 429 (rate limited, wait before retrying)"
        301, 302, 303, 307, 308 -> "HTTP $code (a redirect that was not followed)"
        in 500..599 -> "HTTP $code (the service reported a server error)"
        else -> "HTTP $code"
    }

    fun explain(body: String, budget: Int = 2000): String {
        val text = body.trim()
        if (text.isEmpty()) return ""
        val parsed = objectOf(text)
        if (parsed == null) return pretty(text, budget)
        val message = (parsed["message"]?.jsonPrimitive?.content ?: "").ifBlank { (parsed["error_description"]?.jsonPrimitive?.content ?: "") }
            .ifBlank { (parsed["error"]?.jsonPrimitive?.content ?: "") }
        val prettyBody = pretty(text, budget)
        return if (message.isBlank()) prettyBody else "$message\n$prettyBody"
    }

    fun objectOf(raw: String): JsonObject? = try {
        Json.parseToJsonElement(raw.trim().jsonObject)
    } catch (e: Exception) {
        null
    }

    fun arrayOf(raw: String): JsonArray? = try {
        Json.parseToJsonElement(raw.trim().jsonArray)
    } catch (e: Exception) {
        null
    }

    fun text(value: Any?): String = when (value) {
        null, JsonObject.NULL -> ""
        is String -> value
        is JsonObject, is JsonArray -> value.toString()
        else -> value.toString()
    }

    fun field(item: JsonObject, path: String): String {
        var current: Any? = item
        for (part in path.split('.')) {
            val node = current as? JsonObject ?: return ""
            current = node[part]
        }
        return text(current)
    }

    fun oneLine(value: String, max: Int = 160): String {
        val flat = value.replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= max) flat else flat.take(max) + "…"
    }

    fun firstLine(value: String, max: Int = 160): String {
        val line = value.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (line.length <= max) line else line.take(max) + "…"
    }

    fun rows(array: JsonArray?, limit: Int = 25, render: (JsonObject) -> String): String {
        if (array == null || array.size == 0) return "No items came back."
        val size = minOf(array.size, limit.coerceAtLeast(1))
        val out = mutableListOf<String>()
        for (i in 0 until size) {
            val item = array?.getOrNull(i)?.jsonObject ?: continue
            val line = render(item).trim()
            if (line.isNotEmpty()) out.add("- $line")
        }
        if (array.size > size) out.add("… and ${array.size - size} more")
        return if (out.isEmpty()) "No items came back." else out.joinToString("\n")
    }

    fun compactList(array: JsonArray?, fields: List<Pair<String, String>>, limit: Int = 25): String =
        rows(array, limit) { item ->
            fields.mapNotNull { (path, label) ->
                val value = field(item, path)
                if (value.isBlank()) null else "$label: $value"
            }.joinToString(" | ")
        }

    private fun urlEncode(s: String): String = buildString {
        for (c in s.toCharArray()) {
            when {
                c == ' ' -> append('+')
                c.isLetterOrDigit() || c in "-_.~" -> append(c)
                else -> {
                    val bytes = c.toString().toByteArray(Charsets.UTF_8)
                    for (b in bytes) append('%').append((b.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase())
                }
            }
        }
    }

    private fun urlDecode(s: String): String {
        val out = mutableListOf<Byte>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '+' -> { out.add(' '.code.toByte()); i++ }
                c == '%' && i + 2 < s.length -> {
                    try {
                        out.add(s.substring(i + 1, i + 3).toInt(16).toByte())
                        i += 3
                    } catch (e: Exception) {
                        out.add('%'.code.toByte())
                        i++
                    }
                }
                else -> {
                    for (b in c.toString().toByteArray(Charsets.UTF_8)) out.add(b)
                    i++
                }
            }
        }
        return out.toByteArray().toString(Charsets.UTF_8)
    }

    fun enc(value: String): String = try {
        urlEncode(value.trim()).replace("+", "%20")
    } catch (e: Exception) {
        value.trim()
    }

    fun encRaw(value: String): String = try {
        urlEncode(value.trim())
    } catch (e: Exception) {
        value.trim()
    }

    fun encPath(value: String): String =
        value.trim().trim('/').split('/').filter { it.isNotBlank() }.joinToString("/") { enc(it) }

    fun decode(value: String): String = try {
        urlDecode(value)
    } catch (e: Exception) {
        value
    }
}

object ConnectorTools : HarnessGroupTools {

    override val group = HarnessGroup.CONNECTORS

    private const val NOTION_BASE = "https://api.notion.com/v1"
    private const val NOTION_VERSION = "2022-06-28"
    private const val SLACK_BASE = "https://slack.com/api"
    private const val DRIVE_BASE = "https://www.googleapis.com/drive/v3"
    private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0/me/drive"
    private const val GITLAB_BASE = "https://gitlab.com"
    private const val LINEAR_BASE = "https://api.linear.app/graphql"

    private val NOTION_ACTIONS = listOf(
        "search", "page_get", "page_create", "page_update", "block_children", "database_query"
    )
    private val SLACK_ACTIONS = listOf("post_message", "history", "list_channels", "search")
    private val GDRIVE_ACTIONS = listOf("list", "get", "download", "upload", "create_folder")
    private val ONEDRIVE_ACTIONS = listOf("list", "get", "download", "upload")
    private val GITLAB_ACTIONS = listOf(
        "projects", "issues", "issue_create", "issue_comment", "files", "file_get", "pipelines", "pipeline_jobs"
    )
    private val JIRA_ACTIONS = listOf(
        "issue_get", "issue_create", "issue_update", "issue_comment", "search", "transitions", "transition"
    )
    private val LINEAR_ACTIONS = listOf("issues", "issue_get", "issue_create", "issue_update", "teams", "search")
    private val WEBDAV_ACTIONS = listOf("list", "get", "put", "mkcol", "delete", "move")

    private val NOTION_IDS = listOf("notion")
    private val SLACK_IDS = listOf("slack")
    private val GDRIVE_IDS = listOf("gdrive", "google_drive", "google-drive", "googledrive", "google")
    private val ONEDRIVE_IDS = listOf("onedrive", "one_drive", "one-drive", "microsoft", "graph", "sharepoint")
    private val GITLAB_IDS = listOf("gitlab", "git_lab", "git-lab")
    private val JIRA_IDS = listOf("jira", "atlassian")
    private val LINEAR_IDS = listOf("linear")
    private val WEBDAV_IDS = listOf("webdav", "web_dav", "nextcloud", "nutstore")

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "notion_api",
            group = group,
            permission = HarnessPermission.NETWORK,
            description = "Read and write Notion. Arguments: action (search, page_get, page_create, page_update, " +
                "block_children, database_query), id (page or block id), query (search text), database_id, page_id " +
                "(parent for page_create), title, properties (JSON object of Notion properties), blocks (JSON array " +
                "of child blocks). search and database_query are the way to find pages and rows.",
            params = listOf(
                HarnessSchema.text("action", "One of: search, page_get, page_create, page_update, block_children, database_query"),
                HarnessSchema.text("id", "Page or block id, for page_get and block_children", false),
                HarnessSchema.text("query", "Search text for search", false),
                HarnessSchema.text("database_id", "Database id, for database_query or as the parent of a new page", false),
                HarnessSchema.text("page_id", "Parent page id for page_create", false),
                HarnessSchema.text("title", "Title for page_create", false),
                HarnessSchema.json("properties", "Notion properties object for page_create and page_update", false),
                HarnessSchema.list("blocks", "Child blocks array for page_create", false, itemType = "object")
            )
        ),
        HarnessTool(
            name = "slack_api",
            group = group,
            permission = HarnessPermission.NETWORK,
            description = "Use Slack. Arguments: action (post_message, history, list_channels, search), channel " +
                "(channel id or name), text (message text for post_message), ts (message timestamp to reply in a " +
                "thread), query (search text). Slack's own errors are reported in plain words, including a missing " +
                "scope or an unknown channel.",
            params = listOf(
                HarnessSchema.text("action", "One of: post_message, history, list_channels, search"),
                HarnessSchema.text("channel", "Channel id or name", false),
                HarnessSchema.text("text", "Message text for post_message", false),
                HarnessSchema.text("ts", "Message timestamp, to post into that thread", false),
                HarnessSchema.text("query", "Search text for search", false)
            )
        ),
        HarnessTool(
            name = "gdrive_api",
            group = group,
            permission = HarnessPermission.NETWORK,
            description = "Use Google Drive. Arguments: action (list, get, download, upload, create_folder), file_id, " +
                "query (Drive query such as \"name contains 'report'\"), name, mime_type, content (text to upload). " +
                "download turns a Google document or sheet into text; binary files are better fetched with " +
                "http_request and save_to.",
            params = listOf(
                HarnessSchema.text("action", "One of: list, get, download, upload, create_folder"),
                HarnessSchema.text("file_id", "The Drive file id", false),
                HarnessSchema.text("query", "Drive search query for list", false),
                HarnessSchema.text("name", "File or folder name", false),
                HarnessSchema.text("mime_type", "MIME type for upload or create_folder", false),
                HarnessSchema.text("content", "Text content to upload", false)
            )
        ),
        HarnessTool(
            name = "onedrive_api",
            group = group,
            permission = HarnessPermission.NETWORK,
            description = "Use OneDrive through Microsoft Graph. Arguments: action (list, get, download, upload), " +
                "item_id, path (path from the drive root, for example \"Documents/report.txt\"), query (search text), " +
                "content (text to upload). upload writes content to the given path, creating or replacing the file.",
            params = listOf(
                HarnessSchema.text("action", "One of: list, get, download, upload"),
                HarnessSchema.text("item_id", "The OneDrive item id", false),
                HarnessSchema.text("path", "Path from the drive root", false),
                HarnessSchema.text("query", "Search text for list", false),
                HarnessSchema.text("content", "Text content to upload", false)
            )
        ),
        HarnessTool(
            name = "gitlab_api",
            group = group,
            permission = HarnessPermission.NETWORK,
            description = "Use GitLab. Arguments: action (projects, issues, issue_create, issue_comment, files, " +
                "file_get, pipelines, pipeline_jobs), project (path like \"group/project\" or a numeric id), " +
                "issue_iid, title, description (also the comment text for issue_comment), ref (branch, or the " +
                "pipeline id for pipeline_jobs), path (file or folder path).",
            params = listOf(
                HarnessSchema.text("action", "One of: projects, issues, issue_create, issue_comment, files, file_get, pipelines, pipeline_jobs"),
                HarnessSchema.text("project", "Project path or numeric id", false),
                HarnessSchema.number("issue_iid", "The project-local issue number", false),
                HarnessSchema.text("title", "Issue title for issue_create", false),
                HarnessSchema.text("description", "Issue body, or the comment text for issue_comment", false),
                HarnessSchema.text("ref", "Branch or tag, or the pipeline id for pipeline_jobs", false),
                HarnessSchema.text("path", "File or folder path inside the repository", false)
            )
        ),
        HarnessTool(
            name = "jira_api",
            group = group,
            permission = HarnessPermission.NETWORK,
            description = "Use Jira. Arguments: action (issue_get, issue_create, issue_update, issue_comment, search, " +
                "transitions, transition), project (project key), issue_key (for example \"ABC-12\"), summary, " +
                "description, jql (JQL query for search), transition (id or name). Jira's own error messages are " +
                "reported as they come.",
            params = listOf(
                HarnessSchema.text("action", "One of: issue_get, issue_create, issue_update, issue_comment, search, transitions, transition"),
                HarnessSchema.text("project", "Project key, for issue_create", false),
                HarnessSchema.text("issue_key", "Issue key such as ABC-12", false),
                HarnessSchema.text("summary", "Issue summary for create or update", false),
                HarnessSchema.text("description", "Issue description or comment text", false),
                HarnessSchema.text("jql", "JQL query for search", false),
                HarnessSchema.text("transition", "Transition id or name", false)
            )
        ),
        HarnessTool(
            name = "linear_api",
            group = group,
            permission = HarnessPermission.NETWORK,
            description = "Use Linear through its GraphQL API. Arguments: action (issues, issue_get, issue_create, " +
                "issue_update, teams, search), issue_id (an issue id or identifier such as ENG-42), team_id, title, " +
                "description, query (search text). Call teams first when you need a team_id for issue_create.",
            params = listOf(
                HarnessSchema.text("action", "One of: issues, issue_get, issue_create, issue_update, teams, search"),
                HarnessSchema.text("issue_id", "Issue id or identifier such as ENG-42", false),
                HarnessSchema.text("team_id", "Team id for issue_create", false),
                HarnessSchema.text("title", "Issue title", false),
                HarnessSchema.text("description", "Issue description", false),
                HarnessSchema.text("query", "Search text for search", false)
            )
        ),
        HarnessTool(
            name = "webdav_request",
            group = group,
            permission = HarnessPermission.NETWORK,
            description = "Talk to the WebDAV server saved in the connectors. Arguments: action (list, get, put, " +
                "mkcol, delete, move), path (path under the server root), content (text to write for put), " +
                "destination (target path for move). list shows a folder's entries and get returns a text file.",
            params = listOf(
                HarnessSchema.text("action", "One of: list, get, put, mkcol, delete, move"),
                HarnessSchema.text("path", "Path under the WebDAV server root", false),
                HarnessSchema.text("content", "Text to write for put", false),
                HarnessSchema.text("destination", "Target path for move", false)
            )
        ),
        HarnessTool(
            name = "http_request",
            group = group,
            permission = HarnessPermission.NETWORK,
            description = "Send a raw HTTP request to any http or https URL when no other tool fits. Arguments: " +
                "method (GET, POST, PUT, PATCH, DELETE and so on), url, headers (JSON object of extra headers), " +
                "body (request text), save_to (workspace path to write the response to instead of returning it). " +
                "The GitHub and connector tokens are never attached for you.",
            params = listOf(
                HarnessSchema.text("method", "HTTP method such as GET, POST, PUT, PATCH or DELETE"),
                HarnessSchema.text("url", "The full http or https URL"),
                HarnessSchema.json("headers", "Extra request headers as a JSON object", false),
                HarnessSchema.text("body", "Request body text", false),
                HarnessSchema.text("save_to", "Workspace path to write the response body to", false)
            )
        ),
        HarnessTool(
            name = "connector_status",
            group = group,
            permission = HarnessPermission.READ,
            description = "List which connectors are configured and whether each one has a token: Notion, Slack, " +
                "Google Drive, OneDrive, GitLab, Jira, Linear and WebDAV, plus whether a GitHub token is set and " +
                "which GitHub API base is used. No arguments. Tokens themselves are never printed. Call it before " +
                "telling the user a service is unavailable."
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JsonObject): ToolExecResult? {
        if (tools.none { it.name == name }) return null
        return try {
            when (name) {
                "notion_api" -> notion(args)
                "slack_api" -> slack(args)
                "gdrive_api" -> gdrive(args)
                "onedrive_api" -> onedrive(args)
                "gitlab_api" -> gitlab(args)
                "jira_api" -> jira(args)
                "linear_api" -> linear(args)
                "webdav_request" -> webdav(args)
                "http_request" -> httpRequest(ctx, args)
                "connector_status" -> status()
                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            ToolExecResult("$name failed: ${t.message ?: t::class.java.simpleName}", success = false)
        }
    }

    private fun lookup(ids: List<String>): ConnectorConfig? =
        ids.firstNotNullOfOrNull { HarnessRuntime.config().connector(it) }

    private fun absent(name: String): ToolExecResult = ToolExecResult(
        "The $name connector is not configured. Add it in Settings → Agent → Connectors.",
        success = false
    )

    private fun tokenless(name: String): ToolExecResult = ToolExecResult(
        "The $name connector is saved without a token. Add its token in Settings → Agent → Connectors.",
        success = false
    )

    private fun unknown(name: String, raw: String, actions: List<String>): ToolExecResult {
        val asked = raw.trim()
        val head = if (asked.isEmpty()) "$name needs an action." else "$name has no action called \"$asked\"."
        return ToolExecResult("$head Use one of: ${actions.joinToString(", ")}.", success = false)
    }

    private fun problem(reply: HttpReply, service: String): ToolExecResult? {
        if (reply.code == 0) return ToolExecResult("$service could not be reached: ${reply.error}", success = false)
        if (reply.ok) return null
        val detail = HttpJson.explain(reply.body)
        val head = "$service returned ${HttpJson.describe(reply.code)}."
        return ToolExecResult(if (detail.isBlank()) head else "$head\n$detail", success = false)
    }

    private suspend fun send(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String? = null,
        timeoutSeconds: Int = 60
    ): HttpReply = HttpJson.request(method, url, headers, body, timeoutSeconds, true)

    private suspend fun notion(args: JsonObject): ToolExecResult {
        val config = lookup(NOTION_IDS) ?: return absent("Notion")
        if (config.token.isBlank()) return tokenless("Notion")
        val raw = (args["action"]?.jsonPrimitive?.content ?: "")
        val action = HttpJson.action(raw, NOTION_ACTIONS)
        if (action.isEmpty()) return unknown("Notion", raw, NOTION_ACTIONS)
        val base = config.baseUrl.trim().trimEnd('/').ifBlank { NOTION_BASE }
        val headers = mapOf(
            "Authorization" to "Bearer ${config.token.trim()}",
            "Notion-Version" to NOTION_VERSION,
            "Content-Type" to "application/json; charset=utf-8"
        )
        return when (action) {
            "search" -> {
                val payload = JsonObject().put("page_size", 20)
                val query = (args["query"]?.jsonPrimitive?.content ?: "").trim()
                if (query.isNotEmpty()) payload.put("query", query)
                val reply = send("POST", "$base/search", headers, payload.toString())
                problem(reply, "Notion")?.let { return it }
                val results = HttpJson.objectOf(reply.body)?.get("results")?.jsonArray
                ToolExecResult(
                    HttpJson.rows(results, 20) { item ->
                        val kind = (item["object"]?.jsonPrimitive?.content ?: "page")
                        val id = (item["id"]?.jsonPrimitive?.content ?: "")
                        val title = notionTitle(item).ifBlank { kind }
                        "$kind $id | ${HttpJson.oneLine(title, 90)} | ${(item["url"]?.jsonPrimitive?.content ?: "")}"
                    }
                )
            }
            "page_get" -> {
                val id = notionId(args)
                if (id.isEmpty()) return ToolExecResult("page_get needs the page id in id.", success = false)
                val reply = send("GET", "$base/pages/${HttpJson.enc(id)}", headers)
                problem(reply, "Notion")?.let { return it }
                val page = HttpJson.objectOf(reply.body)
                    ?: return ToolExecResult("Notion returned something that is not a page.", success = false)
                val sb = StringBuilder()
                sb.append("title: ").append(notionTitle(page).ifBlank { "untitled" }).append('\n')
                sb.append("id: ").append((page["id"]?.jsonPrimitive?.content ?: "")).append('\n')
                sb.append("url: ").append((page["url"]?.jsonPrimitive?.content ?: "")).append('\n')
                sb.append("last edited: ").append((page["last_edited_time"]?.jsonPrimitive?.content ?: "")).append('\n')
                sb.append(notionProperties(page))
                ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
            }
            "page_create" -> {
                val databaseId = (args["database_id"]?.jsonPrimitive?.content ?: "").trim()
                val pageId = (args["page_id"]?.jsonPrimitive?.content ?: "").trim()
                val parent = JsonObject()
                when {
                    databaseId.isNotEmpty() -> parent.put("database_id", databaseId)
                    pageId.isNotEmpty() -> parent.put("page_id", pageId)
                    else -> return ToolExecResult(
                        "page_create needs database_id or page_id to say where the page goes.",
                        success = false
                    )
                }
                val properties = args?.get("properties")?.jsonObject ?: JsonObject()
                val title = (args["title"]?.jsonPrimitive?.content ?: "").trim()
                if (title.isNotEmpty() && !properties.containsKey("title")) {
                    properties.put(
                        "title",
                        JsonObject().put(
                            "title",
                            JsonArray().put(JsonObject().put("text", JsonObject().put("content", title)))
                        )
                    )
                }
                val payload = JsonObject().put("parent", parent).put("properties", properties)
                val blocks = args?.get("blocks")?.jsonArray
                if (blocks != null && blocks.size > 0) payload.put("children", blocks)
                val reply = send("POST", "$base/pages", headers, payload.toString())
                problem(reply, "Notion")?.let { return it }
                val page = HttpJson.objectOf(reply.body)
                ToolExecResult(
                    "Created page ${page?.get("id")?.jsonPrimitive?.content.orEmpty()} " +
                        "${page?.get("url")?.jsonPrimitive?.content.orEmpty()}".trim()
                )
            }
            "page_update" -> {
                val id = notionId(args)
                if (id.isEmpty()) return ToolExecResult("page_update needs the page id in id.", success = false)
                val properties = args?.get("properties")?.jsonObject
                    ?: return ToolExecResult("page_update needs properties to change.", success = false)
                val reply = send(
                    "PATCH",
                    "$base/pages/${HttpJson.enc(id)}",
                    headers,
                    JsonObject().put("properties", properties).toString()
                )
                problem(reply, "Notion")?.let { return it }
                val page = HttpJson.objectOf(reply.body)
                ToolExecResult("Updated page ${page?.get("url")?.jsonPrimitive?.content.orEmpty()}".trim())
            }
            "block_children" -> {
                val id = notionId(args)
                if (id.isEmpty()) return ToolExecResult("block_children needs the block or page id in id.", success = false)
                val reply = send("GET", "$base/blocks/${HttpJson.enc(id)}/children?page_size=50", headers)
                problem(reply, "Notion")?.let { return it }
                val results = HttpJson.objectOf(reply.body)?.get("results")?.jsonArray
                ToolExecResult(
                    HttpJson.rows(results, 50) { block ->
                        val kind = (block["type"]?.jsonPrimitive?.content ?: "block")
                        val text = notionBlockText(block)
                        "$kind ${(block["id"]?.jsonPrimitive?.content ?: "")} | ${HttpJson.oneLine(text, 120)}"
                    }
                )
            }
            else -> {
                val id = (args["database_id"]?.jsonPrimitive?.content ?: "").trim().ifBlank { notionId(args) }
                if (id.isEmpty()) return ToolExecResult("database_query needs database_id.", success = false)
                val payload = JsonObject().put("page_size", 20)
                val reply = send("POST", "$base/databases/${HttpJson.enc(id)}/query", headers, payload.toString())
                problem(reply, "Notion")?.let { return it }
                val results = HttpJson.objectOf(reply.body)?.get("results")?.jsonArray
                ToolExecResult(
                    HttpJson.rows(results, 20) { row ->
                        "${(row["id"]?.jsonPrimitive?.content ?: "")} | ${HttpJson.oneLine(notionTitle(row).ifBlank { "untitled" }, 90)}" +
                            " | ${(row["last_edited_time"]?.jsonPrimitive?.content ?: "")}"
                    }
                )
            }
        }
    }

    private fun notionId(args: JsonObject): String =
        (args["id"]?.jsonPrimitive?.content ?: "").trim().ifBlank { (args["page_id"]?.jsonPrimitive?.content ?: "").trim() }

    private fun notionTitle(item: JsonObject): String {
        val direct = item?.get("title")?.jsonArray
        if (direct != null) return notionRichText(direct)
        val properties = item?.get("properties")?.jsonObject ?: return ""
        val keys = properties.keys()
        while (keys.hasNext()) {
            val node = properties?.getOrNull(keys.next()?.jsonObject) ?: continue
            if ((node["type"]?.jsonPrimitive?.content ?: "") == "title") return notionRichText(node?.get("title")?.jsonArray)
        }
        return ""
    }

    private fun notionRichText(array: JsonArray?): String {
        if (array == null) return ""
        val sb = StringBuilder()
        for (i in 0 until array.size) {
            val node = array?.getOrNull(i)?.jsonObject ?: continue
            val plain = (node["plain_text"]?.jsonPrimitive?.content ?: "")
            sb.append(plain.ifBlank { node?.get("text")?.jsonObject?.get("content")?.jsonPrimitive?.content.orEmpty() })
        }
        return sb.toString().trim()
    }

    private fun notionBlockText(block: JsonObject): String {
        val type = (block["type"]?.jsonPrimitive?.content ?: "")
        val node = block?.getOrNull(type)?.jsonObject ?: return ""
        val rich = notionRichText(node?.get("rich_text")?.jsonArray)
        if (rich.isNotBlank()) return rich
        return (node["title"]?.jsonPrimitive?.content ?: "")
    }

    private fun notionProperties(page: JsonObject): String {
        val properties = page?.get("properties")?.jsonObject ?: return ""
        val sb = StringBuilder()
        val keys = properties.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val node = properties?.getOrNull(name)?.jsonObject ?: continue
            val value = notionValue(node)
            if (value.isBlank()) continue
            sb.append("- ").append(name).append(": ").append(HttpJson.oneLine(value, 120)).append('\n')
        }
        return sb.toString()
    }

    private fun notionValue(node: JsonObject): String = when ((node["type"]?.jsonPrimitive?.content ?: "")) {
        "title" -> notionRichText(node?.get("title")?.jsonArray)
        "rich_text" -> notionRichText(node?.get("rich_text")?.jsonArray)
        "number" -> if (node.isNull("number")) "" else HttpJson.text(node["number"])
        "select" -> node?.get("select")?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty()
        "status" -> node?.get("status")?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty()
        "multi_select" -> {
            val array = node?.get("multi_select")?.jsonArray
            val out = mutableListOf<String>()
            if (array != null) {
                for (i in 0 until array.size) {
                    val name = array?.getOrNull(i)?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty()
                    if (name.isNotBlank()) out.add(name)
                }
            }
            out.joinToString(", ")
        }
        "date" -> node?.get("date")?.jsonObject?.get("start")?.jsonPrimitive?.content.orEmpty()
        "checkbox" -> if ((node["checkbox"]?.jsonPrimitive?.booleanOrNull ?: false)) "yes" else "no"
        "url" -> (node["url"]?.jsonPrimitive?.content ?: "")
        "email" -> (node["email"]?.jsonPrimitive?.content ?: "")
        "phone_number" -> (node["phone_number"]?.jsonPrimitive?.content ?: "")
        "formula" -> {
            val formula = node?.get("formula")?.jsonObject
            if (formula == null) {
                ""
            } else {
                (formula["string"]?.jsonPrimitive?.content ?: "").ifBlank { HttpJson.text(formula["number"]) }
            }
        }
        else -> ""
    }

    private suspend fun slack(args: JsonObject): ToolExecResult {
        val config = lookup(SLACK_IDS) ?: return absent("Slack")
        if (config.token.isBlank()) return tokenless("Slack")
        val raw = (args["action"]?.jsonPrimitive?.content ?: "")
        val action = HttpJson.action(raw, SLACK_ACTIONS)
        if (action.isEmpty()) return unknown("Slack", raw, SLACK_ACTIONS)
        val base = config.baseUrl.trim().trimEnd('/').ifBlank { SLACK_BASE }
        val headers = mapOf(
            "Authorization" to "Bearer ${config.token.trim()}",
            "Content-Type" to "application/json; charset=utf-8"
        )
        val channel = (args["channel"]?.jsonPrimitive?.content ?: "").trim()
        val reply = when (action) {
            "post_message" -> {
                val text = (args["text"]?.jsonPrimitive?.content ?: "").trim()
                if (channel.isEmpty() || text.isEmpty()) {
                    return ToolExecResult("post_message needs channel and text.", success = false)
                }
                val payload = JsonObject().put("channel", channel).put("text", text)
                val ts = (args["ts"]?.jsonPrimitive?.content ?: "").trim()
                if (ts.isNotEmpty()) payload.put("thread_ts", ts)
                send("POST", "$base/chat.postMessage", headers, payload.toString())
            }
            "history" -> {
                if (channel.isEmpty()) return ToolExecResult("history needs channel.", success = false)
                send("GET", "$base/conversations.history?channel=${HttpJson.enc(channel)}&limit=20", headers)
            }
            "list_channels" -> send(
                "GET",
                "$base/conversations.list?limit=100&exclude_archived=true&types=public_channel,private_channel",
                headers
            )
            else -> {
                val query = (args["query"]?.jsonPrimitive?.content ?: "").trim()
                if (query.isEmpty()) return ToolExecResult("search needs query.", success = false)
                send("GET", "$base/search.messages?query=${HttpJson.enc(query)}&count=20", headers)
            }
        }
        if (reply.code == 0) return ToolExecResult("Slack could not be reached: ${reply.error}", success = false)
        val payload = HttpJson.objectOf(reply.body)
            ?: return ToolExecResult(
                "Slack answered ${HttpJson.describe(reply.code)} with something that is not JSON:\n" +
                    HttpJson.cut(reply.body, 1200),
                success = false
            )
        if (!(payload["ok"]?.jsonPrimitive?.booleanOrNull ?: false)) {
            val error = (payload["error"]?.jsonPrimitive?.content ?: "unknown_error")
            val needed = (payload["needed"]?.jsonPrimitive?.content ?: "")
            val provided = (payload["provided"]?.jsonPrimitive?.content ?: "")
            val scope = if (needed.isBlank()) "" else {
                " The call needs the scope $needed" +
                    (if (provided.isBlank()) "." else " and the token has $provided.")
            }
            return ToolExecResult("Slack refused that call: $error.$scope", success = false)
        }
        val text = when (action) {
            "post_message" -> "Posted to ${(payload["channel"]?.jsonPrimitive?.content ?: channel)} at ${(payload["ts"]?.jsonPrimitive?.content ?: "")}."
            "history" -> HttpJson.rows(payload?.get("messages")?.jsonArray, 20) { message ->
                "${(message["ts"]?.jsonPrimitive?.content ?: "")} | ${(message["user"]?.jsonPrimitive?.content ?: "unknown")} | " +
                    HttpJson.oneLine((message["text"]?.jsonPrimitive?.content ?: ""), 160)
            }
            "list_channels" -> HttpJson.rows(payload?.get("channels")?.jsonArray, 50) { item ->
                "${(item["id"]?.jsonPrimitive?.content ?: "")} | #${(item["name"]?.jsonPrimitive?.content ?: "")} | " +
                    "${if ((item["is_private"]?.jsonPrimitive?.booleanOrNull ?: false)) "private" else "public"}"
            }
            else -> {
                val matches = payload?.get("messages")?.jsonObject?.get("matches")?.jsonArray
                HttpJson.rows(matches, 20) { match ->
                    "${(match["channel"]?.jsonPrimitive?.content ?: "")} | ${(match["username"]?.jsonPrimitive?.content ?: "")} | " +
                        HttpJson.oneLine((match["text"]?.jsonPrimitive?.content ?: ""), 160)
                }
            }
        }
        return ToolExecResult(text)
    }

    private suspend fun gdrive(args: JsonObject): ToolExecResult {
        val config = lookup(GDRIVE_IDS) ?: return absent("Google Drive")
        if (config.token.isBlank()) return tokenless("Google Drive")
        val raw = (args["action"]?.jsonPrimitive?.content ?: "")
        val action = HttpJson.action(raw, GDRIVE_ACTIONS)
        if (action.isEmpty()) return unknown("Google Drive", raw, GDRIVE_ACTIONS)
        val base = config.baseUrl.trim().trimEnd('/').ifBlank { DRIVE_BASE }
        val uploadBase = if (base.contains("/drive/v3")) base.replace("/drive/v3", "/upload/drive/v3") else DRIVE_UPLOAD
        val headers = mapOf(
            "Authorization" to "Bearer ${config.token.trim()}",
            "Accept" to "application/json"
        )
        val fileId = (args["file_id"]?.jsonPrimitive?.content ?: "").trim()
        val name = (args["name"]?.jsonPrimitive?.content ?: "").trim()
        val mime = (args["mime_type"]?.jsonPrimitive?.content ?: "").trim().ifBlank { "text/plain" }
        val content = (args["content"]?.jsonPrimitive?.content ?: "")
        return when (action) {
            "list" -> {
                val query = (args["query"]?.jsonPrimitive?.content ?: "").trim()
                val fields = HttpJson.enc("files(id,name,mimeType,size,modifiedTime)")
                val url = if (query.isEmpty()) {
                    "$base/files?pageSize=20&orderBy=modifiedTime%20desc&fields=$fields"
                } else {
                    "$base/files?pageSize=20&fields=$fields&q=${HttpJson.enc(query)}"
                }
                val reply = send("GET", url, headers)
                problem(reply, "Google Drive")?.let { return it }
                val files = HttpJson.objectOf(reply.body)?.get("files")?.jsonArray
                ToolExecResult(
                    HttpJson.rows(files, 20) { file ->
                        "${(file["id"]?.jsonPrimitive?.content ?: "")} | ${(file["name"]?.jsonPrimitive?.content ?: "")} | " +
                            "${(file["mimeType"]?.jsonPrimitive?.content ?: "")} | ${(file["modifiedTime"]?.jsonPrimitive?.content ?: "")}"
                    }
                )
            }
            "get" -> {
                if (fileId.isEmpty()) return ToolExecResult("get needs file_id.", success = false)
                val reply = send(
                    "GET",
                    "$base/files/${HttpJson.enc(fileId)}?fields=id,name,mimeType,size,modifiedTime,webViewLink",
                    headers
                )
                problem(reply, "Google Drive")?.let { return it }
                ToolExecResult(HttpJson.pretty(reply.body))
            }
            "download" -> {
                if (fileId.isEmpty()) return ToolExecResult("download needs file_id.", success = false)
                val meta = send("GET", "$base/files/${HttpJson.enc(fileId)}?fields=name,mimeType", headers)
                problem(meta, "Google Drive")?.let { return it }
                val metaBody = HttpJson.objectOf(meta.body)
                val kind = metaBody?.get("mimeType")?.jsonPrimitive?.content.orEmpty()
                val label = metaBody?.get("name")?.jsonPrimitive?.content ?: fileId.orEmpty()
                val url = when (kind) {
                    "application/vnd.google-apps.document" -> "$base/files/${HttpJson.enc(fileId)}/export?mimeType=text/plain"
                    "application/vnd.google-apps.spreadsheet" -> "$base/files/${HttpJson.enc(fileId)}/export?mimeType=text/csv"
                    "application/vnd.google-apps.presentation" -> "$base/files/${HttpJson.enc(fileId)}/export?mimeType=text/plain"
                    else -> "$base/files/${HttpJson.enc(fileId)}?alt=media"
                }
                val reply = send("GET", url, headers, null, 120)
                problem(reply, "Google Drive")?.let { return it }
                val note = if (reply.truncated) "\n… the file was cut at 2 MiB" else ""
                ToolExecResult("$label ($kind)$note\n${HttpJson.pretty(reply.body)}")
            }
            "upload" -> {
                if (name.isEmpty()) return ToolExecResult("upload needs name.", success = false)
                val boundary = "lucent${System.currentTimeMillis()}"
                val metadata = JsonObject().put("name", name)
                val payload = multipart(boundary, metadata, mime, content)
                val reply = send(
                    "POST",
                    "$uploadBase/files?uploadType=multipart&fields=id,name,webViewLink",
                    headers + ("Content-Type" to "multipart/related; boundary=$boundary"),
                    payload,
                    120
                )
                problem(reply, "Google Drive")?.let { return it }
                val file = HttpJson.objectOf(reply.body)
                ToolExecResult(
                    "Uploaded ${file?.get("name")?.jsonPrimitive?.content ?: name.orEmpty()} " +
                        "(${content.toByteArray(Charsets.UTF_8).size} bytes) " +
                        file?.get("webViewLink")?.jsonPrimitive?.content.orEmpty()
                )
            }
            else -> {
                if (name.isEmpty()) return ToolExecResult("create_folder needs name.", success = false)
                val payload = JsonObject()
                    .put("name", name)
                    .put("mimeType", "application/vnd.google-apps.folder")
                val reply = send("POST", "$base/files?fields=id,name,webViewLink", headers, payload.toString())
                problem(reply, "Google Drive")?.let { return it }
                val folder = HttpJson.objectOf(reply.body)
                val link = folder?.get("webViewLink")?.jsonPrimitive?.content.orEmpty()
                ToolExecResult("Created folder ${folder?.get("name")?.jsonPrimitive?.content ?: name.orEmpty()} $link".trim())
            }
        }
    }

    private fun multipart(boundary: String, metadata: JsonObject, mime: String, content: String): String {
        val sb = StringBuilder()
        sb.append("--").append(boundary).append("\r\n")
        sb.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        sb.append(metadata.toString()).append("\r\n")
        sb.append("--").append(boundary).append("\r\n")
        sb.append("Content-Type: ").append(mime).append("\r\n\r\n")
        sb.append(content).append("\r\n")
        sb.append("--").append(boundary).append("--\r\n")
        return sb.toString()
    }

    private suspend fun onedrive(args: JsonObject): ToolExecResult {
        val config = lookup(ONEDRIVE_IDS) ?: return absent("OneDrive")
        if (config.token.isBlank()) return tokenless("OneDrive")
        val raw = (args["action"]?.jsonPrimitive?.content ?: "")
        val action = HttpJson.action(raw, ONEDRIVE_ACTIONS)
        if (action.isEmpty()) return unknown("OneDrive", raw, ONEDRIVE_ACTIONS)
        val base = config.baseUrl.trim().trimEnd('/').ifBlank { GRAPH_BASE }
        val headers = mapOf(
            "Authorization" to "Bearer ${config.token.trim()}",
            "Accept" to "application/json"
        )
        val itemId = (args["item_id"]?.jsonPrimitive?.content ?: "").trim()
        val path = (args["path"]?.jsonPrimitive?.content ?: "").trim().trim('/')
        val query = (args["query"]?.jsonPrimitive?.content ?: "").trim()
        val content = (args["content"]?.jsonPrimitive?.content ?: "")
        return when (action) {
            "list" -> {
                val url = when {
                    query.isNotEmpty() -> "$base/root/search(q='${HttpJson.enc(query)}')"
                    itemId.isNotEmpty() -> "$base/items/${HttpJson.enc(itemId)}/children"
                    path.isNotEmpty() -> "$base/root:/${HttpJson.encPath(path)}:/children"
                    else -> "$base/root/children"
                }
                val reply = send("GET", "$url?\$select=id,name,folder,file,size,lastModifiedDateTime&top=50", headers)
                problem(reply, "OneDrive")?.let { return it }
                val items = HttpJson.objectOf(reply.body)?.get("value")?.jsonArray
                ToolExecResult(
                    HttpJson.rows(items, 50) { item ->
                        val kind = if (item.containsKey("folder")) "folder" else "file"
                        "$kind ${(item["id"]?.jsonPrimitive?.content ?: "")} | ${(item["name"]?.jsonPrimitive?.content ?: "")} | " +
                            "${(item["size"]?.jsonPrimitive?.content ?: "")} | ${(item["lastModifiedDateTime"]?.jsonPrimitive?.content ?: "")}"
                    }
                )
            }
            "get" -> {
                val url = when {
                    itemId.isNotEmpty() -> "$base/items/${HttpJson.enc(itemId)}"
                    path.isNotEmpty() -> "$base/root:/${HttpJson.encPath(path)}"
                    else -> return ToolExecResult("get needs item_id or path.", success = false)
                }
                val reply = send("GET", url, headers)
                problem(reply, "OneDrive")?.let { return it }
                ToolExecResult(HttpJson.pretty(reply.body))
            }
            "download" -> {
                val url = when {
                    itemId.isNotEmpty() -> "$base/items/${HttpJson.enc(itemId)}/content"
                    path.isNotEmpty() -> "$base/root:/${HttpJson.encPath(path)}:/content"
                    else -> return ToolExecResult("download needs item_id or path.", success = false)
                }
                val reply = send("GET", url, headers, null, 120)
                problem(reply, "OneDrive")?.let { return it }
                val note = if (reply.truncated) "\n… the file was cut at 2 MiB" else ""
                ToolExecResult("$note\n${HttpJson.pretty(reply.body)}".trim())
            }
            else -> {
                if (path.isEmpty()) return ToolExecResult("upload needs path.", success = false)
                val reply = send(
                    "PUT",
                    "$base/root:/${HttpJson.encPath(path)}:/content",
                    headers + ("Content-Type" to "text/plain; charset=utf-8"),
                    content,
                    120
                )
                problem(reply, "OneDrive")?.let { return it }
                val item = HttpJson.objectOf(reply.body)
                ToolExecResult(
                    "Uploaded ${item?.get("name")?.jsonPrimitive?.content ?: path.orEmpty()} " +
                        "(${content.toByteArray(Charsets.UTF_8).size} bytes)"
                )
            }
        }
    }

    private suspend fun gitlab(args: JsonObject): ToolExecResult {
        val config = lookup(GITLAB_IDS) ?: return absent("GitLab")
        if (config.token.isBlank()) return tokenless("GitLab")
        val raw = (args["action"]?.jsonPrimitive?.content ?: "")
        val action = HttpJson.action(raw, GITLAB_ACTIONS)
        if (action.isEmpty()) return unknown("GitLab", raw, GITLAB_ACTIONS)
        val base = config.baseUrl.trim().trimEnd('/').ifBlank { GITLAB_BASE } + "/api/v4"
        val headers = mapOf(
            "PRIVATE-TOKEN" to config.token.trim(),
            "Content-Type" to "application/json; charset=utf-8"
        )
        val project = (args["project"]?.jsonPrimitive?.content ?: "").trim()
        val iid = (args["issue_iid"]?.jsonPrimitive?.intOrNull ?: 0)
        val ref = (args["ref"]?.jsonPrimitive?.content ?: "").trim()
        return when (action) {
            "projects" -> {
                val reply = send("GET", "$base/projects?membership=true&per_page=20&order_by=last_activity_at", headers)
                problem(reply, "GitLab")?.let { return it }
                val items = HttpJson.arrayOf(reply.body)
                ToolExecResult(
                    HttpJson.rows(items, 20) { item ->
                        "${(item["path_with_namespace"]?.jsonPrimitive?.content ?: "")} | ${HttpJson.oneLine((item["description"]?.jsonPrimitive?.content ?: ""), 90)}"
                    }
                )
            }
            "issues" -> {
                if (project.isEmpty()) return ToolExecResult("issues needs project.", success = false)
                val reply = send("GET", "$base/projects/${HttpJson.encRaw(project)}/issues?per_page=20", headers)
                problem(reply, "GitLab")?.let { return it }
                val items = HttpJson.arrayOf(reply.body)
                ToolExecResult(
                    HttpJson.rows(items, 20) { item ->
                        "!${(item["iid"]?.jsonPrimitive?.intOrNull ?: 0)} | ${(item["state"]?.jsonPrimitive?.content ?: "")} | " +
                            "${HttpJson.oneLine((item["title"]?.jsonPrimitive?.content ?: ""), 110)}"
                    }
                )
            }
            "issue_create" -> {
                val title = (args["title"]?.jsonPrimitive?.content ?: "").trim()
                if (project.isEmpty() || title.isEmpty()) {
                    return ToolExecResult("issue_create needs project and title.", success = false)
                }
                val payload = JsonObject().put("title", title)
                val description = (args["description"]?.jsonPrimitive?.content ?: "")
                if (description.isNotEmpty()) payload.put("description", description)
                val reply = send(
                    "POST",
                    "$base/projects/${HttpJson.encRaw(project)}/issues",
                    headers,
                    payload.toString()
                )
                problem(reply, "GitLab")?.let { return it }
                val issue = HttpJson.objectOf(reply.body)
                val created = issue?.get("web_url")?.jsonPrimitive?.content.orEmpty()
                val createdIid = issue?.get("iid")?.jsonPrimitive?.intOrNull ?: 0 ?: 0
                if (createdIid > 0) {
                    ToolExecResult("Created issue !$createdIid $created".trim())
                } else {
                    ToolExecResult("Created the issue. $created".trim())
                }
            }
            "issue_comment" -> {
                if (project.isEmpty() || iid <= 0) {
                    return ToolExecResult("issue_comment needs project and issue_iid.", success = false)
                }
                val body = (args["description"]?.jsonPrimitive?.content ?: "").ifBlank { (args["title"]?.jsonPrimitive?.content ?: "") }
                if (body.isBlank()) return ToolExecResult("issue_comment needs the comment text in description.", success = false)
                val reply = send(
                    "POST",
                    "$base/projects/${HttpJson.encRaw(project)}/issues/$iid/notes",
                    headers,
                    JsonObject().put("body", body).toString()
                )
                problem(reply, "GitLab")?.let { return it }
                ToolExecResult("Comment added to issue !$iid.")
            }
            "files" -> {
                if (project.isEmpty()) return ToolExecResult("files needs project.", success = false)
                val path = (args["path"]?.jsonPrimitive?.content ?: "").trim().trim('/')
                val url = StringBuilder("$base/projects/${HttpJson.encRaw(project)}/repository/tree?per_page=50")
                if (ref.isNotEmpty()) url.append("&ref=").append(HttpJson.enc(ref))
                if (path.isNotEmpty()) url.append("&path=").append(HttpJson.enc(path))
                val reply = send("GET", url.toString(), headers)
                problem(reply, "GitLab")?.let { return it }
                val items = HttpJson.arrayOf(reply.body)
                ToolExecResult(
                    HttpJson.rows(items, 50) { item ->
                        "${(item["type"]?.jsonPrimitive?.content ?: "")} ${(item["path"]?.jsonPrimitive?.content ?: "")}"
                    }
                )
            }
            "file_get" -> {
                val path = (args["path"]?.jsonPrimitive?.content ?: "").trim().trim('/')
                if (project.isEmpty() || path.isEmpty()) {
                    return ToolExecResult("file_get needs project and path.", success = false)
                }
                val url = StringBuilder("$base/projects/${HttpJson.encRaw(project)}/repository/files/")
                    .append(HttpJson.encRaw(path)).append("/raw")
                if (ref.isNotEmpty()) url.append("?ref=").append(HttpJson.enc(ref))
                val reply = send("GET", url.toString(), headers)
                problem(reply, "GitLab")?.let { return it }
                ToolExecResult(HttpJson.cut(reply.body, HttpJson.REPLY_BUDGET))
            }
            "pipelines" -> {
                if (project.isEmpty()) return ToolExecResult("pipelines needs project.", success = false)
                val reply = send("GET", "$base/projects/${HttpJson.encRaw(project)}/pipelines?per_page=10", headers)
                problem(reply, "GitLab")?.let { return it }
                val items = HttpJson.arrayOf(reply.body)
                ToolExecResult(
                    HttpJson.rows(items, 10) { item ->
                        "${(item["id"]?.jsonPrimitive?.intOrNull ?: 0)} | ${(item["status"]?.jsonPrimitive?.content ?: "")} | " +
                            "${(item["ref"]?.jsonPrimitive?.content ?: "")} | ${(item["updated_at"]?.jsonPrimitive?.content ?: "")}"
                    }
                )
            }
            else -> {
                if (project.isEmpty() || ref.isEmpty()) {
                    return ToolExecResult("pipeline_jobs needs project and the pipeline id in ref.", success = false)
                }
                val reply = send(
                    "GET",
                    "$base/projects/${HttpJson.encRaw(project)}/pipelines/${HttpJson.enc(ref)}/jobs?per_page=20",
                    headers
                )
                problem(reply, "GitLab")?.let { return it }
                val items = HttpJson.arrayOf(reply.body)
                ToolExecResult(
                    HttpJson.rows(items, 20) { item ->
                        "${(item["name"]?.jsonPrimitive?.content ?: "")} | ${(item["status"]?.jsonPrimitive?.content ?: "")} | " +
                            "${(item["stage"]?.jsonPrimitive?.content ?: "")}"
                    }
                )
            }
        }
    }

    private suspend fun jira(args: JsonObject): ToolExecResult {
        val config = lookup(JIRA_IDS) ?: return absent("Jira")
        val site = config.baseUrl.trim().trimEnd('/')
        if (site.isEmpty()) {
            return ToolExecResult(
                "The Jira connector has no site URL. Add it in Settings → Agent → Connectors.",
                success = false
            )
        }
        if (config.token.isBlank()) return tokenless("Jira")
        val raw = (args["action"]?.jsonPrimitive?.content ?: "")
        val action = HttpJson.action(raw, JIRA_ACTIONS)
        if (action.isEmpty()) return unknown("Jira", raw, JIRA_ACTIONS)
        val base = "$site/rest/api/3"
        val account = config.account.trim()
        val authorization = if (account.isEmpty()) {
            "Bearer ${config.token.trim()}"
        } else {
            "Basic " + basic(account, config.token.trim())
        }
        val headers = mapOf(
            "Authorization" to authorization,
            "Accept" to "application/json",
            "Content-Type" to "application/json; charset=utf-8"
        )
        val key = (args["issue_key"]?.jsonPrimitive?.content ?: "").trim()
        return when (action) {
            "issue_get" -> {
                if (key.isEmpty()) return ToolExecResult("issue_get needs issue_key.", success = false)
                val reply = send("GET", "$base/issue/${HttpJson.enc(key)}", headers)
                problem(reply, "Jira")?.let { return it }
                val issue = HttpJson.objectOf(reply.body)
                    ?: return ToolExecResult("Jira returned something that is not an issue.", success = false)
                val fields = issue?.get("fields")?.jsonObject
                val sb = StringBuilder()
                sb.append((issue["key"]?.jsonPrimitive?.content ?: key)).append(" — ").append(fields?.get("summary")?.jsonPrimitive?.content.orEmpty())
                sb.append('\n')
                sb.append("status: ").append(fields?.get("status")?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty())
                sb.append(" | type: ").append(fields?.get("issuetype")?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty())
                val assignee = fields?.get("assignee")?.jsonObject?.get("displayName")?.jsonPrimitive?.content.orEmpty()
                sb.append(" | assignee: ").append(assignee.ifBlank { "unassigned" })
                sb.append(" | updated: ").append(fields?.get("updated")?.jsonPrimitive?.content.orEmpty())
                sb.append('\n').append(adfText(fields?.opt("description")))
                ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
            }
            "issue_create" -> {
                val project = (args["project"]?.jsonPrimitive?.content ?: "").trim()
                val summary = (args["summary"]?.jsonPrimitive?.content ?: "").trim()
                if (project.isEmpty() || summary.isEmpty()) {
                    return ToolExecResult("issue_create needs project and summary.", success = false)
                }
                val fields = JsonObject()
                    .put("project", JsonObject().put("key", project))
                    .put("summary", summary)
                    .put("issuetype", JsonObject().put("name", "Task"))
                val description = (args["description"]?.jsonPrimitive?.content ?: "")
                if (description.isNotEmpty()) fields.put("description", adf(description))
                val reply = send("POST", "$base/issue", headers, JsonObject().put("fields", fields).toString())
                problem(reply, "Jira")?.let { return it }
                val issue = HttpJson.objectOf(reply.body)
                ToolExecResult("Created ${issue?.get("key")?.jsonPrimitive?.content.orEmpty()} ${issue?.get("self")?.jsonPrimitive?.content.orEmpty()}".trim())
            }
            "issue_update" -> {
                if (key.isEmpty()) return ToolExecResult("issue_update needs issue_key.", success = false)
                val fields = JsonObject()
                val summary = (args["summary"]?.jsonPrimitive?.content ?: "").trim()
                if (summary.isNotEmpty()) fields.put("summary", summary)
                val description = (args["description"]?.jsonPrimitive?.content ?: "")
                if (description.isNotEmpty()) fields.put("description", adf(description))
                if (fields.size == 0) {
                    return ToolExecResult("issue_update needs summary or description to change.", success = false)
                }
                val reply = send("PUT", "$base/issue/${HttpJson.enc(key)}", headers, JsonObject().put("fields", fields).toString())
                problem(reply, "Jira")?.let { return it }
                ToolExecResult("Updated $key.")
            }
            "issue_comment" -> {
                if (key.isEmpty()) return ToolExecResult("issue_comment needs issue_key.", success = false)
                val body = (args["description"]?.jsonPrimitive?.content ?: "").trim()
                if (body.isEmpty()) return ToolExecResult("issue_comment needs the comment text in description.", success = false)
                val reply = send(
                    "POST",
                    "$base/issue/${HttpJson.enc(key)}/comment",
                    headers,
                    JsonObject().put("body", adf(body)).toString()
                )
                problem(reply, "Jira")?.let { return it }
                ToolExecResult("Comment added to $key.")
            }
            "search" -> {
                val jql = (args["jql"]?.jsonPrimitive?.content ?: "").trim()
                if (jql.isEmpty()) return ToolExecResult("search needs jql.", success = false)
                val payload = JsonObject()
                    .put("jql", jql)
                    .put("maxResults", 20)
                    .put("fields", JsonArray().put("summary").put("status").put("assignee").put("updated"))
                val reply = send("POST", "$base/search/jql", headers, payload.toString())
                problem(reply, "Jira")?.let { return it }
                val issues = HttpJson.objectOf(reply.body)?.get("issues")?.jsonArray
                ToolExecResult(
                    HttpJson.rows(issues, 20) { issue ->
                        val fields = issue?.get("fields")?.jsonObject
                        "${(issue["key"]?.jsonPrimitive?.content ?: "")} | " +
                            "${fields?.get("status")?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty()} | " +
                            HttpJson.oneLine(fields?.get("summary")?.jsonPrimitive?.content.orEmpty(), 110)
                    }
                )
            }
            "transitions" -> {
                if (key.isEmpty()) return ToolExecResult("transitions needs issue_key.", success = false)
                val reply = send("GET", "$base/issue/${HttpJson.enc(key)}/transitions", headers)
                problem(reply, "Jira")?.let { return it }
                val items = HttpJson.objectOf(reply.body)?.get("transitions")?.jsonArray
                ToolExecResult(
                    HttpJson.rows(items, 30) { item ->
                        "${(item["id"]?.jsonPrimitive?.content ?: "")} | ${(item["name"]?.jsonPrimitive?.content ?: "")}"
                    }
                )
            }
            else -> {
                if (key.isEmpty()) return ToolExecResult("transition needs issue_key.", success = false)
                val wanted = (args["transition"]?.jsonPrimitive?.content ?: "").trim()
                if (wanted.isEmpty()) {
                    return ToolExecResult("transition needs the transition id or name in transition.", success = false)
                }
                var id = if (wanted.all { it.isDigit() }) wanted else ""
                if (id.isEmpty()) {
                    val list = send("GET", "$base/issue/${HttpJson.enc(key)}/transitions", headers)
                    problem(list, "Jira")?.let { return it }
                    val items = HttpJson.objectOf(list.body)?.get("transitions")?.jsonArray
                    val match = (0 until (items?.length() ?: 0))
                        .mapNotNull { items?.getOrNull(it)?.jsonObject }
                        .firstOrNull {
                            val name = (it["name"]?.jsonPrimitive?.content ?: "")
                            name.equals(wanted, ignoreCase = true) || name.contains(wanted, ignoreCase = true)
                        }
                    if (match == null) {
                        return ToolExecResult(
                            "Jira has no transition called \"$wanted\" on $key. Call transitions to see the names.",
                            success = false
                        )
                    }
                    id = (match["id"]?.jsonPrimitive?.content ?: "")
                }
                val payload = JsonObject().put("transition", JsonObject().put("id", id))
                val reply = send("POST", "$base/issue/${HttpJson.enc(key)}/transitions", headers, payload.toString())
                problem(reply, "Jira")?.let { return it }
                ToolExecResult("Moved $key with transition $wanted.")
            }
        }
    }

    private fun adf(text: String): JsonObject = JsonObject()
        .put("type", "doc")
        .put("version", 1)
        .put(
            "content",
            JsonArray().put(
                JsonObject()
                    .put("type", "paragraph")
                    .put("content", JsonArray().put(JsonObject().put("type", "text").put("text", text)))
            )
        )

    private fun adfText(node: Any?): String {
        if (node is String) return node
        val obj = node as? JsonObject ?: return ""
        val own = (obj["text"]?.jsonPrimitive?.content ?: "")
        val content = obj?.get("content")?.jsonArray
        val children = mutableListOf<String>()
        if (content != null) {
            for (i in 0 until content.size) {
                val child = adfText(content[i])
                if (child.isNotBlank()) children.add(child)
            }
        }
        val separator = if ((obj["type"]?.jsonPrimitive?.content ?: "") in setOf("doc", "bulletList", "orderedList")) "\n" else " "
        return listOf(own, children.joinToString(separator)).filter { it.isNotBlank() }.joinToString(" ").trim()
    }

    private fun basic(user: String, secret: String): String =
        Base64.Default.encode("$user:$secret".toByteArray(Charsets.UTF_8))

    private suspend fun linear(args: JsonObject): ToolExecResult {
        val config = lookup(LINEAR_IDS) ?: return absent("Linear")
        if (config.token.isBlank()) return tokenless("Linear")
        val raw = (args["action"]?.jsonPrimitive?.content ?: "")
        val action = HttpJson.action(raw, LINEAR_ACTIONS)
        if (action.isEmpty()) return unknown("Linear", raw, LINEAR_ACTIONS)
        val endpoint = config.baseUrl.trim().ifBlank { LINEAR_BASE }
        val headers = mapOf(
            "Authorization" to "Bearer ${config.token.trim()}",
            "Content-Type" to "application/json; charset=utf-8"
        )
        val issueId = (args["issue_id"]?.jsonPrimitive?.content ?: "").trim()
        val query = when (action) {
            "issues" -> "query Issues { issues(first: 25) { nodes { identifier title state { name } assignee { name } url } } }"
            "issue_get" -> {
                if (issueId.isEmpty()) return ToolExecResult("issue_get needs issue_id.", success = false)
                "query Issue(\$id: String!) { issue(id: \$id) { identifier title description state { name } " +
                    "assignee { name } url } }"
            }
            "issue_create" -> {
                val teamId = (args["team_id"]?.jsonPrimitive?.content ?: "").trim()
                val title = (args["title"]?.jsonPrimitive?.content ?: "").trim()
                if (teamId.isEmpty() || title.isEmpty()) {
                    return ToolExecResult("issue_create needs team_id and title.", success = false)
                }
                "mutation Create(\$input: IssueCreateInput!) { issueCreate(input: \$input) { success " +
                    "issue { identifier title url } } }"
            }
            "issue_update" -> {
                if (issueId.isEmpty()) return ToolExecResult("issue_update needs issue_id.", success = false)
                "mutation Update(\$id: String!, \$input: IssueUpdateInput!) { issueUpdate(id: \$id, input: \$input) " +
                    "{ success issue { identifier title url } } }"
            }
            "teams" -> "query Teams { teams { nodes { id key name } } }"
            else -> {
                val term = (args["query"]?.jsonPrimitive?.content ?: "").trim()
                if (term.isEmpty()) return ToolExecResult("search needs query.", success = false)
                "query Search(\$term: String!) { searchIssues(term: \$term) { nodes { identifier title url } } }"
            }
        }
        val variables = JsonObject()
        when (action) {
            "issue_get" -> variables.put("id", issueId)
            "issue_create" -> {
                val input = JsonObject()
                    .put("teamId", (args["team_id"]?.jsonPrimitive?.content ?: "").trim())
                    .put("title", (args["title"]?.jsonPrimitive?.content ?: "").trim())
                val description = (args["description"]?.jsonPrimitive?.content ?: "")
                if (description.isNotEmpty()) input.put("description", description)
                variables.put("input", input)
            }
            "issue_update" -> {
                val input = JsonObject()
                val title = (args["title"]?.jsonPrimitive?.content ?: "").trim()
                if (title.isNotEmpty()) input.put("title", title)
                val description = (args["description"]?.jsonPrimitive?.content ?: "")
                if (description.isNotEmpty()) input.put("description", description)
                if (input.size == 0) {
                    return ToolExecResult("issue_update needs title or description to change.", success = false)
                }
                variables.put("id", issueId).put("input", input)
            }
            "search" -> variables.put("term", (args["query"]?.jsonPrimitive?.content ?: "").trim())
            else -> {
            }
        }
        val payload = JsonObject().put("query", query)
        if (variables.size > 0) payload.put("variables", variables)
        val reply = send("POST", endpoint, headers, payload.toString())
        problem(reply, "Linear")?.let { return it }
        val body = HttpJson.objectOf(reply.body)
            ?: return ToolExecResult("Linear returned something that is not JSON.", success = false)
        val errors = body?.get("errors")?.jsonArray
        if (errors != null && errors.size > 0) {
            val messages = (0 until errors.size).mapNotNull { index ->
                errors?.getOrNull(index)?.jsonObject?.get("message")?.jsonPrimitive?.content
            }.filter { it.isNotBlank() }
            return ToolExecResult("Linear refused that query: ${messages.joinToString("; ")}", success = false)
        }
        val data = body?.get("data")?.jsonObject
            ?: return ToolExecResult("Linear answered without any data:\n${HttpJson.pretty(reply.body)}", success = false)
        return ToolExecResult(linearText(action, data))
    }

    private fun linearText(action: String, data: JsonObject): String {
        return when (action) {
            "issues" -> HttpJson.rows(data?.get("issues")?.jsonObject?.get("nodes")?.jsonArray, 25) { node ->
                "${(node["identifier"]?.jsonPrimitive?.content ?: "")} | " +
                    "${node?.get("state")?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty()} | " +
                    "${HttpJson.oneLine((node["title"]?.jsonPrimitive?.content ?: ""), 110)} | ${(node["url"]?.jsonPrimitive?.content ?: "")}"
            }
            "issue_get" -> {
                val issue = data?.get("issue")?.jsonObject
                if (issue == null) {
                    "Linear has no issue with that id."
                } else {
                    val sb = StringBuilder()
                    sb.append((issue["identifier"]?.jsonPrimitive?.content ?: "")).append(" — ")
                    sb.append((issue["title"]?.jsonPrimitive?.content ?: "")).append('\n')
                    sb.append("state: ").append(issue?.get("state")?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty())
                    val assignee = issue?.get("assignee")?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty()
                    sb.append(" | assignee: ").append(assignee.ifBlank { "none" })
                    sb.append('\n').append((issue["url"]?.jsonPrimitive?.content ?: "")).append('\n')
                    sb.append(HttpJson.cut((issue["description"]?.jsonPrimitive?.content ?: ""), 2500))
                    HttpJson.cut(sb.toString().trimEnd())
                }
            }
            "issue_create", "issue_update" -> {
                val key = if (action == "issue_create") "issueCreate" else "issueUpdate"
                val result = data?.getOrNull(key)?.jsonObject
                val issue = result?.get("issue")?.jsonObject
                if (result == null || !(result["success"]?.jsonPrimitive?.booleanOrNull ?: false) || issue == null) {
                    "Linear reported that the change did not go through."
                } else {
                    "Saved ${(issue["identifier"]?.jsonPrimitive?.content ?: "")} ${(issue["title"]?.jsonPrimitive?.content ?: "")} " +
                        (issue["url"]?.jsonPrimitive?.content ?: "")
                }
            }
            "teams" -> HttpJson.rows(data?.get("teams")?.jsonObject?.get("nodes")?.jsonArray, 50) { node ->
                "${(node["id"]?.jsonPrimitive?.content ?: "")} | ${(node["key"]?.jsonPrimitive?.content ?: "")} | ${(node["name"]?.jsonPrimitive?.content ?: "")}"
            }
            else -> HttpJson.rows(data?.get("searchIssues")?.jsonObject?.get("nodes")?.jsonArray, 25) { node ->
                "${(node["identifier"]?.jsonPrimitive?.content ?: "")} | ${HttpJson.oneLine((node["title"]?.jsonPrimitive?.content ?: ""), 110)} | " +
                    (node["url"]?.jsonPrimitive?.content ?: "")
            }
        }
    }

    private suspend fun webdav(args: JsonObject): ToolExecResult {
        val config = lookup(WEBDAV_IDS) ?: return absent("WebDAV")
        val root = config.baseUrl.trim().trimEnd('/')
        if (root.isEmpty()) {
            return ToolExecResult(
                "The WebDAV connector has no server URL. Add it in Settings → Agent → Connectors.",
                success = false
            )
        }
        if (config.token.isBlank()) return tokenless("WebDAV")
        val raw = (args["action"]?.jsonPrimitive?.content ?: "")
        val action = HttpJson.action(raw, WEBDAV_ACTIONS)
        if (action.isEmpty()) return unknown("WebDAV", raw, WEBDAV_ACTIONS)
        val headers = mutableMapOf("Authorization" to "Basic ${basic(config.account.trim(), config.token.trim())}")
        val path = (args["path"]?.jsonPrimitive?.content ?: "").trim().trim('/')
        val url = if (path.isEmpty()) root else "$root/${HttpJson.encPath(path)}"
        return when (action) {
            "list" -> {
                headers["Depth"] = "1"
                headers["Content-Type"] = "application/xml; charset=utf-8"
                val reply = send("PROPFIND", url, headers, PROPFIND_BODY, 60)
                problem(reply, "WebDAV")?.let { return it }
                ToolExecResult(davEntries(reply.body, root))
            }
            "get" -> {
                val reply = send("GET", url, headers, null, 120)
                problem(reply, "WebDAV")?.let { return it }
                ToolExecResult(HttpJson.pretty(reply.body))
            }
            "put" -> {
                val content = (args["content"]?.jsonPrimitive?.content ?: "")
                headers["Content-Type"] = "text/plain; charset=utf-8"
                val reply = send("PUT", url, headers, content, 120)
                problem(reply, "WebDAV")?.let { return it }
                ToolExecResult("Wrote ${content.toByteArray(Charsets.UTF_8).size} bytes to $path.")
            }
            "mkcol" -> {
                val reply = send("MKCOL", url, headers)
                problem(reply, "WebDAV")?.let { return it }
                ToolExecResult("Created the collection $path.")
            }
            "delete" -> {
                val reply = send("DELETE", url, headers)
                problem(reply, "WebDAV")?.let { return it }
                ToolExecResult("Deleted $path.")
            }
            else -> {
                val destination = (args["destination"]?.jsonPrimitive?.content ?: "").trim()
                if (destination.isEmpty()) return ToolExecResult("move needs destination.", success = false)
                val target = if (destination.startsWith("http")) {
                    destination
                } else {
                    "$root/${HttpJson.encPath(destination)}"
                }
                headers["Destination"] = target
                headers["Overwrite"] = "F"
                val reply = send("MOVE", url, headers)
                problem(reply, "WebDAV")?.let { return it }
                ToolExecResult("Moved $path to $destination.")
            }
        }
    }

    private fun davEntries(body: String, root: String): String {
        val pattern = Regex("<[^>]*href[^>]*>([^<]+)</", RegexOption.IGNORE_CASE)
        val out = mutableListOf<String>()
        pattern.findAll(body).forEach { match ->
            val name = davName(match.groupValues[1], root)
            if (name.isNotEmpty() && !out.contains(name)) out.add(name)
        }
        if (out.isEmpty()) return "The server listed nothing at that path."
        val head = if (out.size > 200) out.take(200) else out
        val text = head.joinToString("\n") { "- $it" }
        return if (out.size > 200) "$text\n… and ${out.size - 200} more" else text
    }

    private fun davName(href: String, root: String): String {
        val decoded = HttpJson.decode(href.trim())
        val path = decoded.substringAfter("://", decoded).substringAfter('/', "")
        val home = root.substringAfter("://", "").substringAfter('/', "").trim('/')
        val relative = if (home.isNotEmpty() && path.startsWith(home)) path.removePrefix(home) else path
        return relative.trim('/')
    }

    private suspend fun httpRequest(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val method = (args["method"]?.jsonPrimitive?.content ?: "GET").trim().uppercase().ifBlank { "GET" }
        val url = (args["url"]?.jsonPrimitive?.content ?: "").trim()
        HttpJson.urlProblem(url)?.let { return ToolExecResult(it, success = false) }
        val headers = headerMap(args)
        val body = if (args.containsKey("body")) (args["body"]?.jsonPrimitive?.content ?: "") else null
        val saveTo = (args["save_to"]?.jsonPrimitive?.content ?: "").trim()
        if (saveTo.isNotEmpty()) {
            val file = try {
                Workspace.forWriteFile(ctx, saveTo)
            } catch (e: HarnessError) {
                return ToolExecResult(e.message ?: "That path cannot be written.", success = false)
            }
            val reply = HttpJson.requestBytes(method, url, headers, body, 120, true)
            if (reply.code == 0) return ToolExecResult("Request failed: ${reply.error}", success = false)
            if (!reply.ok) {
                return ToolExecResult(
                    "The request failed with ${HttpJson.describe(reply.code)}, so nothing was saved:\n" +
                        HttpJson.cut(String(reply.bytes, Charsets.UTF_8), 1200),
                    success = false
                )
            }
            if (ctx.config.snapshots && file.exists()) Snapshots.capture(ctx, file)
            file.parentFile?.mkdirs()
            file.writeBytes(reply.bytes)
            val note = if (reply.truncated) " (cut at 2 MiB)" else ""
            return ToolExecResult("Saved ${reply.bytes.size} bytes$note to ${Workspace.display(ctx, file)}.")
        }
        val reply = HttpJson.request(method, url, headers, body, 120, true)
        if (reply.code == 0) return ToolExecResult("Request failed: ${reply.error}", success = false)
        val head = "HTTP ${reply.code}" + (if (reply.ok) " (ok)" else " — ${HttpJson.describe(reply.code)}")
        val note = if (reply.truncated) " (the body was cut at 2 MiB)" else ""
        val text = HttpJson.pretty(reply.body, HttpJson.INLINE_BUDGET)
        return ToolExecResult("$head$note\n$text".trimEnd(), success = reply.ok)
    }

    private fun headerMap(args: JsonObject): Map<String, String> {
        val obj = args?.get("headers")?.jsonObject ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.isBlank()) continue
            out[key] = HttpJson.text(obj[key])
        }
        return out
    }

    private fun status(): ToolExecResult {
        val config = HarnessRuntime.config()
        val sb = StringBuilder()
        val github = config.githubToken.isNotBlank()
        sb.append("GitHub: ").append(if (github) "token set" else "no token")
        sb.append(" (api ").append(config.githubApi.trim().ifBlank { "https://api.github.com" }).append(")\n")
        CATALOG.forEach { entry ->
            val connector = lookup(entry.second)
            sb.append(entry.first).append(" (").append(entry.second.first()).append("): ")
            when {
                connector == null -> sb.append("not configured")
                connector.token.isBlank() -> sb.append("configured, no token saved")
                else -> sb.append("configured, token saved")
            }
            if (connector != null && connector.account.isNotBlank()) {
                sb.append(" (account: ").append(connector.account.trim()).append(")")
            }
            if (connector != null && connector.baseUrl.isNotBlank()) {
                sb.append(" [").append(connector.baseUrl.trim()).append("]")
            }
            sb.append('\n')
        }
        val known = CATALOG.flatMap { it.second }.toSet()
        val extra = config.connectors.filter { it.id !in known }
        if (extra.isNotEmpty()) {
            sb.append("Other connectors: ")
            sb.append(extra.joinToString(", ") { it.id + (if (it.token.isBlank()) " (no token)" else "") })
            sb.append('\n')
        }
        return ToolExecResult(sb.toString().trimEnd())
    }

    private val CATALOG = listOf(
        "Notion" to NOTION_IDS,
        "Slack" to SLACK_IDS,
        "Google Drive" to GDRIVE_IDS,
        "OneDrive" to ONEDRIVE_IDS,
        "GitLab" to GITLAB_IDS,
        "Jira" to JIRA_IDS,
        "Linear" to LINEAR_IDS,
        "WebDAV" to WEBDAV_IDS
    )

    private const val PROPFIND_BODY =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind xmlns:d=\"DAV:\"><d:prop>" +
            "<d:displayname/><d:resourcetype/><d:getcontentlength/><d:getlastmodified/>" +
            "</d:prop></d:propfind>"
}
