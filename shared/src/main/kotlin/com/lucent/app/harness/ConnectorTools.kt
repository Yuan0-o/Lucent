package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

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
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
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
            body = String(outcome.second, StandardCharsets.UTF_8),
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
            .callTimeout(seconds, TimeUnit.SECONDS)
            .build()
        client.newCall(request).execute().use { response ->
            val store = ByteArrayOutputStream()
            var truncated = false
            val stream = response.body?.byteStream()
            if (stream != null) {
                val chunk = ByteArray(16384)
                var total = 0
                while (total < MAX_BYTES) {
                    val read = stream.read(chunk, 0, minOf(chunk.size, MAX_BYTES - total))
                    if (read <= 0) break
                    store.write(chunk, 0, read)
                    total += read
                }
                if (total >= MAX_BYTES && stream.read() >= 0) truncated = true
            }
            return Triple(response.code, store.toByteArray(), truncated)
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
        val spaced = StringBuilder()
        raw.trim().forEachIndexed { index, ch ->
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
                '{' -> JSONObject(text).toString(2)
                '[' -> JSONArray(text).toString(2)
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
        val message = parsed.optString("message", "").ifBlank { parsed.optString("error_description", "") }
            .ifBlank { parsed.optString("error", "") }
        val prettyBody = pretty(text, budget)
        return if (message.isBlank()) prettyBody else "$message\n$prettyBody"
    }

    fun objectOf(raw: String): JSONObject? = try {
        JSONObject(raw.trim())
    } catch (e: Exception) {
        null
    }

    fun arrayOf(raw: String): JSONArray? = try {
        JSONArray(raw.trim())
    } catch (e: Exception) {
        null
    }

    fun text(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is String -> value
        is JSONObject, is JSONArray -> value.toString()
        else -> value.toString()
    }

    fun field(item: JSONObject, path: String): String {
        var current: Any? = item
        for (part in path.split('.')) {
            val node = current as? JSONObject ?: return ""
            current = node.opt(part)
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

    fun rows(array: JSONArray?, limit: Int = 25, render: (JSONObject) -> String): String {
        if (array == null || array.length() == 0) return "No items came back."
        val size = minOf(array.length(), limit.coerceAtLeast(1))
        val out = mutableListOf<String>()
        for (i in 0 until size) {
            val item = array.optJSONObject(i) ?: continue
            val line = render(item).trim()
            if (line.isNotEmpty()) out.add("- $line")
        }
        if (array.length() > size) out.add("… and ${array.length() - size} more")
        return if (out.isEmpty()) "No items came back." else out.joinToString("\n")
    }

    fun compactList(array: JSONArray?, fields: List<Pair<String, String>>, limit: Int = 25): String =
        rows(array, limit) { item ->
            fields.mapNotNull { (path, label) ->
                val value = field(item, path)
                if (value.isBlank()) null else "$label: $value"
            }.joinToString(" | ")
        }

    fun enc(value: String): String = try {
        URLEncoder.encode(value.trim(), "UTF-8").replace("+", "%20")
    } catch (e: Exception) {
        value.trim()
    }

    fun encRaw(value: String): String = try {
        URLEncoder.encode(value.trim(), "UTF-8")
    } catch (e: Exception) {
        value.trim()
    }

    fun encPath(value: String): String =
        value.trim().trim('/').split('/').filter { it.isNotBlank() }.joinToString("/") { enc(it) }

    fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
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
                HarnessSchema.list("blocks", "Child blocks array for page_create", false)
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

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? {
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

    private suspend fun notion(args: JSONObject): ToolExecResult {
        val config = lookup(NOTION_IDS) ?: return absent("Notion")
        if (config.token.isBlank()) return tokenless("Notion")
        val raw = args.optString("action", "")
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
                val payload = JSONObject().put("page_size", 20)
                val query = args.optString("query", "").trim()
                if (query.isNotEmpty()) payload.put("query", query)
                val reply = send("POST", "$base/search", headers, payload.toString())
                problem(reply, "Notion")?.let { return it }
                val results = HttpJson.objectOf(reply.body)?.optJSONArray("results")
                ToolExecResult(
                    HttpJson.rows(results, 20) { item ->
                        val kind = item.optString("object", "page")
                        val id = item.optString("id", "")
                        val title = notionTitle(item).ifBlank { kind }
                        "$kind $id | ${HttpJson.oneLine(title, 90)} | ${item.optString("url", "")}"
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
                sb.append("id: ").append(page.optString("id", "")).append('\n')
                sb.append("url: ").append(page.optString("url", "")).append('\n')
                sb.append("last edited: ").append(page.optString("last_edited_time", "")).append('\n')
                sb.append(notionProperties(page))
                ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
            }
            "page_create" -> {
                val databaseId = args.optString("database_id", "").trim()
                val pageId = args.optString("page_id", "").trim()
                val parent = JSONObject()
                when {
                    databaseId.isNotEmpty() -> parent.put("database_id", databaseId)
                    pageId.isNotEmpty() -> parent.put("page_id", pageId)
                    else -> return ToolExecResult(
                        "page_create needs database_id or page_id to say where the page goes.",
                        success = false
                    )
                }
                val properties = args.optJSONObject("properties") ?: JSONObject()
                val title = args.optString("title", "").trim()
                if (title.isNotEmpty() && !properties.has("title")) {
                    properties.put(
                        "title",
                        JSONObject().put(
                            "title",
                            JSONArray().put(JSONObject().put("text", JSONObject().put("content", title)))
                        )
                    )
                }
                val payload = JSONObject().put("parent", parent).put("properties", properties)
                val blocks = args.optJSONArray("blocks")
                if (blocks != null && blocks.length() > 0) payload.put("children", blocks)
                val reply = send("POST", "$base/pages", headers, payload.toString())
                problem(reply, "Notion")?.let { return it }
                val page = HttpJson.objectOf(reply.body)
                ToolExecResult(
                    "Created page ${page?.optString("id", "").orEmpty()} " +
                        "${page?.optString("url", "").orEmpty()}".trim()
                )
            }
            "page_update" -> {
                val id = notionId(args)
                if (id.isEmpty()) return ToolExecResult("page_update needs the page id in id.", success = false)
                val properties = args.optJSONObject("properties")
                    ?: return ToolExecResult("page_update needs properties to change.", success = false)
                val reply = send(
                    "PATCH",
                    "$base/pages/${HttpJson.enc(id)}",
                    headers,
                    JSONObject().put("properties", properties).toString()
                )
                problem(reply, "Notion")?.let { return it }
                val page = HttpJson.objectOf(reply.body)
                ToolExecResult("Updated page ${page?.optString("url", "").orEmpty()}".trim())
            }
            "block_children" -> {
                val id = notionId(args)
                if (id.isEmpty()) return ToolExecResult("block_children needs the block or page id in id.", success = false)
                val reply = send("GET", "$base/blocks/${HttpJson.enc(id)}/children?page_size=50", headers)
                problem(reply, "Notion")?.let { return it }
                val results = HttpJson.objectOf(reply.body)?.optJSONArray("results")
                ToolExecResult(
                    HttpJson.rows(results, 50) { block ->
                        val kind = block.optString("type", "block")
                        val text = notionBlockText(block)
                        "$kind ${block.optString("id", "")} | ${HttpJson.oneLine(text, 120)}"
                    }
                )
            }
            else -> {
                val id = args.optString("database_id", "").trim().ifBlank { notionId(args) }
                if (id.isEmpty()) return ToolExecResult("database_query needs database_id.", success = false)
                val payload = JSONObject().put("page_size", 20)
                val reply = send("POST", "$base/databases/${HttpJson.enc(id)}/query", headers, payload.toString())
                problem(reply, "Notion")?.let { return it }
                val results = HttpJson.objectOf(reply.body)?.optJSONArray("results")
                ToolExecResult(
                    HttpJson.rows(results, 20) { row ->
                        "${row.optString("id", "")} | ${HttpJson.oneLine(notionTitle(row).ifBlank { "untitled" }, 90)}" +
                            " | ${row.optString("last_edited_time", "")}"
                    }
                )
            }
        }
    }

    private fun notionId(args: JSONObject): String =
        args.optString("id", "").trim().ifBlank { args.optString("page_id", "").trim() }

    private fun notionTitle(item: JSONObject): String {
        val direct = item.optJSONArray("title")
        if (direct != null) return notionRichText(direct)
        val properties = item.optJSONObject("properties") ?: return ""
        val keys = properties.keys()
        while (keys.hasNext()) {
            val node = properties.optJSONObject(keys.next()) ?: continue
            if (node.optString("type", "") == "title") return notionRichText(node.optJSONArray("title"))
        }
        return ""
    }

    private fun notionRichText(array: JSONArray?): String {
        if (array == null) return ""
        val sb = StringBuilder()
        for (i in 0 until array.length()) {
            val node = array.optJSONObject(i) ?: continue
            val plain = node.optString("plain_text", "")
            sb.append(plain.ifBlank { node.optJSONObject("text")?.optString("content", "").orEmpty() })
        }
        return sb.toString().trim()
    }

    private fun notionBlockText(block: JSONObject): String {
        val type = block.optString("type", "")
        val node = block.optJSONObject(type) ?: return ""
        val rich = notionRichText(node.optJSONArray("rich_text"))
        if (rich.isNotBlank()) return rich
        return node.optString("title", "")
    }

    private fun notionProperties(page: JSONObject): String {
        val properties = page.optJSONObject("properties") ?: return ""
        val sb = StringBuilder()
        val keys = properties.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val node = properties.optJSONObject(name) ?: continue
            val value = notionValue(node)
            if (value.isBlank()) continue
            sb.append("- ").append(name).append(": ").append(HttpJson.oneLine(value, 120)).append('\n')
        }
        return sb.toString()
    }

    private fun notionValue(node: JSONObject): String = when (node.optString("type", "")) {
        "title" -> notionRichText(node.optJSONArray("title"))
        "rich_text" -> notionRichText(node.optJSONArray("rich_text"))
        "number" -> if (node.isNull("number")) "" else HttpJson.text(node.opt("number"))
        "select" -> node.optJSONObject("select")?.optString("name", "").orEmpty()
        "status" -> node.optJSONObject("status")?.optString("name", "").orEmpty()
        "multi_select" -> {
            val array = node.optJSONArray("multi_select")
            val out = mutableListOf<String>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val name = array.optJSONObject(i)?.optString("name", "").orEmpty()
                    if (name.isNotBlank()) out.add(name)
                }
            }
            out.joinToString(", ")
        }
        "date" -> node.optJSONObject("date")?.optString("start", "").orEmpty()
        "checkbox" -> if (node.optBoolean("checkbox", false)) "yes" else "no"
        "url" -> node.optString("url", "")
        "email" -> node.optString("email", "")
        "phone_number" -> node.optString("phone_number", "")
        "formula" -> {
            val formula = node.optJSONObject("formula")
            if (formula == null) {
                ""
            } else {
                formula.optString("string", "").ifBlank { HttpJson.text(formula.opt("number")) }
            }
        }
        else -> ""
    }

    private suspend fun slack(args: JSONObject): ToolExecResult {
        val config = lookup(SLACK_IDS) ?: return absent("Slack")
        if (config.token.isBlank()) return tokenless("Slack")
        val raw = args.optString("action", "")
        val action = HttpJson.action(raw, SLACK_ACTIONS)
        if (action.isEmpty()) return unknown("Slack", raw, SLACK_ACTIONS)
        val base = config.baseUrl.trim().trimEnd('/').ifBlank { SLACK_BASE }
        val headers = mapOf(
            "Authorization" to "Bearer ${config.token.trim()}",
            "Content-Type" to "application/json; charset=utf-8"
        )
        val channel = args.optString("channel", "").trim()
        val reply = when (action) {
            "post_message" -> {
                val text = args.optString("text", "").trim()
                if (channel.isEmpty() || text.isEmpty()) {
                    return ToolExecResult("post_message needs channel and text.", success = false)
                }
                val payload = JSONObject().put("channel", channel).put("text", text)
                val ts = args.optString("ts", "").trim()
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
                val query = args.optString("query", "").trim()
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
        if (!payload.optBoolean("ok", false)) {
            val error = payload.optString("error", "unknown_error")
            val needed = payload.optString("needed", "")
            val provided = payload.optString("provided", "")
            val scope = if (needed.isBlank()) "" else {
                " The call needs the scope $needed" +
                    (if (provided.isBlank()) "." else " and the token has $provided.")
            }
            return ToolExecResult("Slack refused that call: $error.$scope", success = false)
        }
        val text = when (action) {
            "post_message" -> "Posted to ${payload.optString("channel", channel)} at ${payload.optString("ts", "")}."
            "history" -> HttpJson.rows(payload.optJSONArray("messages"), 20) { message ->
                "${message.optString("ts", "")} | ${message.optString("user", "unknown")} | " +
                    HttpJson.oneLine(message.optString("text", ""), 160)
            }
            "list_channels" -> HttpJson.rows(payload.optJSONArray("channels"), 50) { item ->
                "${item.optString("id", "")} | #${item.optString("name", "")} | " +
                    "${if (item.optBoolean("is_private", false)) "private" else "public"}"
            }
            else -> {
                val matches = payload.optJSONObject("messages")?.optJSONArray("matches")
                HttpJson.rows(matches, 20) { match ->
                    "${match.optString("channel", "")} | ${match.optString("username", "")} | " +
                        HttpJson.oneLine(match.optString("text", ""), 160)
                }
            }
        }
        return ToolExecResult(text)
    }

    private suspend fun gdrive(args: JSONObject): ToolExecResult {
        val config = lookup(GDRIVE_IDS) ?: return absent("Google Drive")
        if (config.token.isBlank()) return tokenless("Google Drive")
        val raw = args.optString("action", "")
        val action = HttpJson.action(raw, GDRIVE_ACTIONS)
        if (action.isEmpty()) return unknown("Google Drive", raw, GDRIVE_ACTIONS)
        val base = config.baseUrl.trim().trimEnd('/').ifBlank { DRIVE_BASE }
        val uploadBase = if (base.contains("/drive/v3")) base.replace("/drive/v3", "/upload/drive/v3") else DRIVE_UPLOAD
        val headers = mapOf(
            "Authorization" to "Bearer ${config.token.trim()}",
            "Accept" to "application/json"
        )
        val fileId = args.optString("file_id", "").trim()
        val name = args.optString("name", "").trim()
        val mime = args.optString("mime_type", "").trim().ifBlank { "text/plain" }
        val content = args.optString("content", "")
        return when (action) {
            "list" -> {
                val query = args.optString("query", "").trim()
                val fields = HttpJson.enc("files(id,name,mimeType,size,modifiedTime)")
                val url = if (query.isEmpty()) {
                    "$base/files?pageSize=20&orderBy=modifiedTime%20desc&fields=$fields"
                } else {
                    "$base/files?pageSize=20&fields=$fields&q=${HttpJson.enc(query)}"
                }
                val reply = send("GET", url, headers)
                problem(reply, "Google Drive")?.let { return it }
                val files = HttpJson.objectOf(reply.body)?.optJSONArray("files")
                ToolExecResult(
                    HttpJson.rows(files, 20) { file ->
                        "${file.optString("id", "")} | ${file.optString("name", "")} | " +
                            "${file.optString("mimeType", "")} | ${file.optString("modifiedTime", "")}"
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
                val kind = metaBody?.optString("mimeType", "").orEmpty()
                val label = metaBody?.optString("name", fileId).orEmpty()
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
                val metadata = JSONObject().put("name", name)
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
                    "Uploaded ${file?.optString("name", name).orEmpty()} " +
                        "(${content.toByteArray(StandardCharsets.UTF_8).size} bytes) " +
                        file?.optString("webViewLink", "").orEmpty()
                )
            }
            else -> {
                if (name.isEmpty()) return ToolExecResult("create_folder needs name.", success = false)
                val payload = JSONObject()
                    .put("name", name)
                    .put("mimeType", "application/vnd.google-apps.folder")
                val reply = send("POST", "$base/files?fields=id,name,webViewLink", headers, payload.toString())
                problem(reply, "Google Drive")?.let { return it }
                val folder = HttpJson.objectOf(reply.body)
                val link = folder?.optString("webViewLink", "").orEmpty()
                ToolExecResult("Created folder ${folder?.optString("name", name).orEmpty()} $link".trim())
            }
        }
    }

    private fun multipart(boundary: String, metadata: JSONObject, mime: String, content: String): String {
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

    private suspend fun onedrive(args: JSONObject): ToolExecResult {
        val config = lookup(ONEDRIVE_IDS) ?: return absent("OneDrive")
        if (config.token.isBlank()) return tokenless("OneDrive")
        val raw = args.optString("action", "")
        val action = HttpJson.action(raw, ONEDRIVE_ACTIONS)
        if (action.isEmpty()) return unknown("OneDrive", raw, ONEDRIVE_ACTIONS)
        val base = config.baseUrl.trim().trimEnd('/').ifBlank { GRAPH_BASE }
        val headers = mapOf(
            "Authorization" to "Bearer ${config.token.trim()}",
            "Accept" to "application/json"
        )
        val itemId = args.optString("item_id", "").trim()
        val path = args.optString("path", "").trim().trim('/')
        val query = args.optString("query", "").trim()
        val content = args.optString("content", "")
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
                val items = HttpJson.objectOf(reply.body)?.optJSONArray("value")
                ToolExecResult(
                    HttpJson.rows(items, 50) { item ->
                        val kind = if (item.has("folder")) "folder" else "file"
                        "$kind ${item.optString("id", "")} | ${item.optString("name", "")} | " +
                            "${item.optString("size", "")} | ${item.optString("lastModifiedDateTime", "")}"
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
                    "Uploaded ${item?.optString("name", path).orEmpty()} " +
                        "(${content.toByteArray(StandardCharsets.UTF_8).size} bytes)"
                )
            }
        }
    }

    private suspend fun gitlab(args: JSONObject): ToolExecResult {
        val config = lookup(GITLAB_IDS) ?: return absent("GitLab")
        if (config.token.isBlank()) return tokenless("GitLab")
        val raw = args.optString("action", "")
        val action = HttpJson.action(raw, GITLAB_ACTIONS)
        if (action.isEmpty()) return unknown("GitLab", raw, GITLAB_ACTIONS)
        val base = config.baseUrl.trim().trimEnd('/').ifBlank { GITLAB_BASE } + "/api/v4"
        val headers = mapOf(
            "PRIVATE-TOKEN" to config.token.trim(),
            "Content-Type" to "application/json; charset=utf-8"
        )
        val project = args.optString("project", "").trim()
        val iid = args.optInt("issue_iid", 0)
        val ref = args.optString("ref", "").trim()
        return when (action) {
            "projects" -> {
                val reply = send("GET", "$base/projects?membership=true&per_page=20&order_by=last_activity_at", headers)
                problem(reply, "GitLab")?.let { return it }
                val items = HttpJson.arrayOf(reply.body)
                ToolExecResult(
                    HttpJson.rows(items, 20) { item ->
                        "${item.optString("path_with_namespace", "")} | ${HttpJson.oneLine(item.optString("description", ""), 90)}"
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
                        "!${item.optInt("iid", 0)} | ${item.optString("state", "")} | " +
                            "${HttpJson.oneLine(item.optString("title", ""), 110)}"
                    }
                )
            }
            "issue_create" -> {
                val title = args.optString("title", "").trim()
                if (project.isEmpty() || title.isEmpty()) {
                    return ToolExecResult("issue_create needs project and title.", success = false)
                }
                val payload = JSONObject().put("title", title)
                val description = args.optString("description", "")
                if (description.isNotEmpty()) payload.put("description", description)
                val reply = send(
                    "POST",
                    "$base/projects/${HttpJson.encRaw(project)}/issues",
                    headers,
                    payload.toString()
                )
                problem(reply, "GitLab")?.let { return it }
                val issue = HttpJson.objectOf(reply.body)
                ToolExecResult(
                    "Created issue !${issue?.optInt("iid", 0)} ${issue?.optString("web_url", "").orEmpty()}".trim()
                )
            }
            "issue_comment" -> {
                if (project.isEmpty() || iid <= 0) {
                    return ToolExecResult("issue_comment needs project and issue_iid.", success = false)
                }
                val body = args.optString("description", "").ifBlank { args.optString("title", "") }
                if (body.isBlank()) return ToolExecResult("issue_comment needs the comment text in description.", success = false)
                val reply = send(
                    "POST",
                    "$base/projects/${HttpJson.encRaw(project)}/issues/$iid/notes",
                    headers,
                    JSONObject().put("body", body).toString()
                )
                problem(reply, "GitLab")?.let { return it }
                ToolExecResult("Comment added to issue !$iid.")
            }
            "files" -> {
                if (project.isEmpty()) return ToolExecResult("files needs project.", success = false)
                val path = args.optString("path", "").trim().trim('/')
                val url = StringBuilder("$base/projects/${HttpJson.encRaw(project)}/repository/tree?per_page=50")
                if (ref.isNotEmpty()) url.append("&ref=").append(HttpJson.enc(ref))
                if (path.isNotEmpty()) url.append("&path=").append(HttpJson.enc(path))
                val reply = send("GET", url.toString(), headers)
                problem(reply, "GitLab")?.let { return it }
                val items = HttpJson.arrayOf(reply.body)
                ToolExecResult(
                    HttpJson.rows(items, 50) { item ->
                        "${item.optString("type", "")} ${item.optString("path", "")}"
                    }
                )
            }
            "file_get" -> {
                val path = args.optString("path", "").trim().trim('/')
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
                        "${item.optInt("id", 0)} | ${item.optString("status", "")} | " +
                            "${item.optString("ref", "")} | ${item.optString("updated_at", "")}"
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
                        "${item.optString("name", "")} | ${item.optString("status", "")} | " +
                            "${item.optString("stage", "")}"
                    }
                )
            }
        }
    }

    private suspend fun jira(args: JSONObject): ToolExecResult {
        val config = lookup(JIRA_IDS) ?: return absent("Jira")
        val site = config.baseUrl.trim().trimEnd('/')
        if (site.isEmpty()) {
            return ToolExecResult(
                "The Jira connector has no site URL. Add it in Settings → Agent → Connectors.",
                success = false
            )
        }
        if (config.token.isBlank()) return tokenless("Jira")
        val raw = args.optString("action", "")
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
        val key = args.optString("issue_key", "").trim()
        return when (action) {
            "issue_get" -> {
                if (key.isEmpty()) return ToolExecResult("issue_get needs issue_key.", success = false)
                val reply = send("GET", "$base/issue/${HttpJson.enc(key)}", headers)
                problem(reply, "Jira")?.let { return it }
                val issue = HttpJson.objectOf(reply.body)
                    ?: return ToolExecResult("Jira returned something that is not an issue.", success = false)
                val fields = issue.optJSONObject("fields")
                val sb = StringBuilder()
                sb.append(issue.optString("key", key)).append(" — ").append(fields?.optString("summary", "").orEmpty())
                sb.append('\n')
                sb.append("status: ").append(fields?.optJSONObject("status")?.optString("name", "").orEmpty())
                sb.append(" | type: ").append(fields?.optJSONObject("issuetype")?.optString("name", "").orEmpty())
                val assignee = fields?.optJSONObject("assignee")?.optString("displayName", "").orEmpty()
                sb.append(" | assignee: ").append(assignee.ifBlank { "unassigned" })
                sb.append(" | updated: ").append(fields?.optString("updated", "").orEmpty())
                sb.append('\n').append(adfText(fields?.opt("description")))
                ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
            }
            "issue_create" -> {
                val project = args.optString("project", "").trim()
                val summary = args.optString("summary", "").trim()
                if (project.isEmpty() || summary.isEmpty()) {
                    return ToolExecResult("issue_create needs project and summary.", success = false)
                }
                val fields = JSONObject()
                    .put("project", JSONObject().put("key", project))
                    .put("summary", summary)
                    .put("issuetype", JSONObject().put("name", "Task"))
                val description = args.optString("description", "")
                if (description.isNotEmpty()) fields.put("description", adf(description))
                val reply = send("POST", "$base/issue", headers, JSONObject().put("fields", fields).toString())
                problem(reply, "Jira")?.let { return it }
                val issue = HttpJson.objectOf(reply.body)
                ToolExecResult("Created ${issue?.optString("key", "").orEmpty()} ${issue?.optString("self", "").orEmpty()}".trim())
            }
            "issue_update" -> {
                if (key.isEmpty()) return ToolExecResult("issue_update needs issue_key.", success = false)
                val fields = JSONObject()
                val summary = args.optString("summary", "").trim()
                if (summary.isNotEmpty()) fields.put("summary", summary)
                val description = args.optString("description", "")
                if (description.isNotEmpty()) fields.put("description", adf(description))
                if (fields.length() == 0) {
                    return ToolExecResult("issue_update needs summary or description to change.", success = false)
                }
                val reply = send("PUT", "$base/issue/${HttpJson.enc(key)}", headers, JSONObject().put("fields", fields).toString())
                problem(reply, "Jira")?.let { return it }
                ToolExecResult("Updated $key.")
            }
            "issue_comment" -> {
                if (key.isEmpty()) return ToolExecResult("issue_comment needs issue_key.", success = false)
                val body = args.optString("description", "").trim()
                if (body.isEmpty()) return ToolExecResult("issue_comment needs the comment text in description.", success = false)
                val reply = send(
                    "POST",
                    "$base/issue/${HttpJson.enc(key)}/comment",
                    headers,
                    JSONObject().put("body", adf(body)).toString()
                )
                problem(reply, "Jira")?.let { return it }
                ToolExecResult("Comment added to $key.")
            }
            "search" -> {
                val jql = args.optString("jql", "").trim()
                if (jql.isEmpty()) return ToolExecResult("search needs jql.", success = false)
                val payload = JSONObject()
                    .put("jql", jql)
                    .put("maxResults", 20)
                    .put("fields", JSONArray().put("summary").put("status").put("assignee").put("updated"))
                val reply = send("POST", "$base/search/jql", headers, payload.toString())
                problem(reply, "Jira")?.let { return it }
                val issues = HttpJson.objectOf(reply.body)?.optJSONArray("issues")
                ToolExecResult(
                    HttpJson.rows(issues, 20) { issue ->
                        val fields = issue.optJSONObject("fields")
                        "${issue.optString("key", "")} | " +
                            "${fields?.optJSONObject("status")?.optString("name", "").orEmpty()} | " +
                            HttpJson.oneLine(fields?.optString("summary", "").orEmpty(), 110)
                    }
                )
            }
            "transitions" -> {
                if (key.isEmpty()) return ToolExecResult("transitions needs issue_key.", success = false)
                val reply = send("GET", "$base/issue/${HttpJson.enc(key)}/transitions", headers)
                problem(reply, "Jira")?.let { return it }
                val items = HttpJson.objectOf(reply.body)?.optJSONArray("transitions")
                ToolExecResult(
                    HttpJson.rows(items, 30) { item ->
                        "${item.optString("id", "")} | ${item.optString("name", "")}"
                    }
                )
            }
            else -> {
                if (key.isEmpty()) return ToolExecResult("transition needs issue_key.", success = false)
                val wanted = args.optString("transition", "").trim()
                if (wanted.isEmpty()) {
                    return ToolExecResult("transition needs the transition id or name in transition.", success = false)
                }
                var id = if (wanted.all { it.isDigit() }) wanted else ""
                if (id.isEmpty()) {
                    val list = send("GET", "$base/issue/${HttpJson.enc(key)}/transitions", headers)
                    problem(list, "Jira")?.let { return it }
                    val items = HttpJson.objectOf(list.body)?.optJSONArray("transitions")
                    val match = (0 until (items?.length() ?: 0))
                        .mapNotNull { items?.optJSONObject(it) }
                        .firstOrNull {
                            val name = it.optString("name", "")
                            name.equals(wanted, ignoreCase = true) || name.contains(wanted, ignoreCase = true)
                        }
                    if (match == null) {
                        return ToolExecResult(
                            "Jira has no transition called \"$wanted\" on $key. Call transitions to see the names.",
                            success = false
                        )
                    }
                    id = match.optString("id", "")
                }
                val payload = JSONObject().put("transition", JSONObject().put("id", id))
                val reply = send("POST", "$base/issue/${HttpJson.enc(key)}/transitions", headers, payload.toString())
                problem(reply, "Jira")?.let { return it }
                ToolExecResult("Moved $key with transition $wanted.")
            }
        }
    }

    private fun adf(text: String): JSONObject = JSONObject()
        .put("type", "doc")
        .put("version", 1)
        .put(
            "content",
            JSONArray().put(
                JSONObject()
                    .put("type", "paragraph")
                    .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
            )
        )

    private fun adfText(node: Any?): String {
        if (node is String) return node
        val obj = node as? JSONObject ?: return ""
        val own = obj.optString("text", "")
        val content = obj.optJSONArray("content")
        val children = mutableListOf<String>()
        if (content != null) {
            for (i in 0 until content.length()) {
                val child = adfText(content.opt(i))
                if (child.isNotBlank()) children.add(child)
            }
        }
        val separator = if (obj.optString("type", "") in setOf("doc", "bulletList", "orderedList")) "\n" else " "
        return listOf(own, children.joinToString(separator)).filter { it.isNotBlank() }.joinToString(" ").trim()
    }

    private fun basic(user: String, secret: String): String = android.util.Base64.encodeToString(
        "$user:$secret".toByteArray(StandardCharsets.UTF_8),
        android.util.Base64.NO_WRAP
    )

    private suspend fun linear(args: JSONObject): ToolExecResult {
        val config = lookup(LINEAR_IDS) ?: return absent("Linear")
        if (config.token.isBlank()) return tokenless("Linear")
        val raw = args.optString("action", "")
        val action = HttpJson.action(raw, LINEAR_ACTIONS)
        if (action.isEmpty()) return unknown("Linear", raw, LINEAR_ACTIONS)
        val endpoint = config.baseUrl.trim().ifBlank { LINEAR_BASE }
        val headers = mapOf(
            "Authorization" to "Bearer ${config.token.trim()}",
            "Content-Type" to "application/json; charset=utf-8"
        )
        val issueId = args.optString("issue_id", "").trim()
        val query = when (action) {
            "issues" -> "query Issues { issues(first: 25) { nodes { identifier title state { name } assignee { name } url } } }"
            "issue_get" -> {
                if (issueId.isEmpty()) return ToolExecResult("issue_get needs issue_id.", success = false)
                "query Issue(\$id: String!) { issue(id: \$id) { identifier title description state { name } " +
                    "assignee { name } url } }"
            }
            "issue_create" -> {
                val teamId = args.optString("team_id", "").trim()
                val title = args.optString("title", "").trim()
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
                val term = args.optString("query", "").trim()
                if (term.isEmpty()) return ToolExecResult("search needs query.", success = false)
                "query Search(\$term: String!) { searchIssues(term: \$term) { nodes { identifier title url } } }"
            }
        }
        val variables = JSONObject()
        when (action) {
            "issue_get" -> variables.put("id", issueId)
            "issue_create" -> {
                val input = JSONObject()
                    .put("teamId", args.optString("team_id", "").trim())
                    .put("title", args.optString("title", "").trim())
                val description = args.optString("description", "")
                if (description.isNotEmpty()) input.put("description", description)
                variables.put("input", input)
            }
            "issue_update" -> {
                val input = JSONObject()
                val title = args.optString("title", "").trim()
                if (title.isNotEmpty()) input.put("title", title)
                val description = args.optString("description", "")
                if (description.isNotEmpty()) input.put("description", description)
                if (input.length() == 0) {
                    return ToolExecResult("issue_update needs title or description to change.", success = false)
                }
                variables.put("id", issueId).put("input", input)
            }
            "search" -> variables.put("term", args.optString("query", "").trim())
            else -> {
            }
        }
        val payload = JSONObject().put("query", query)
        if (variables.length() > 0) payload.put("variables", variables)
        val reply = send("POST", endpoint, headers, payload.toString())
        problem(reply, "Linear")?.let { return it }
        val body = HttpJson.objectOf(reply.body)
            ?: return ToolExecResult("Linear returned something that is not JSON.", success = false)
        val errors = body.optJSONArray("errors")
        if (errors != null && errors.length() > 0) {
            val messages = (0 until errors.length()).mapNotNull { index ->
                errors.optJSONObject(index)?.optString("message", "")
            }.filter { it.isNotBlank() }
            return ToolExecResult("Linear refused that query: ${messages.joinToString("; ")}", success = false)
        }
        val data = body.optJSONObject("data")
            ?: return ToolExecResult("Linear answered without any data:\n${HttpJson.pretty(reply.body)}", success = false)
        return ToolExecResult(linearText(action, data))
    }

    private fun linearText(action: String, data: JSONObject): String {
        return when (action) {
            "issues" -> HttpJson.rows(data.optJSONObject("issues")?.optJSONArray("nodes"), 25) { node ->
                "${node.optString("identifier", "")} | " +
                    "${node.optJSONObject("state")?.optString("name", "").orEmpty()} | " +
                    "${HttpJson.oneLine(node.optString("title", ""), 110)} | ${node.optString("url", "")}"
            }
            "issue_get" -> {
                val issue = data.optJSONObject("issue")
                if (issue == null) {
                    "Linear has no issue with that id."
                } else {
                    val sb = StringBuilder()
                    sb.append(issue.optString("identifier", "")).append(" — ")
                    sb.append(issue.optString("title", "")).append('\n')
                    sb.append("state: ").append(issue.optJSONObject("state")?.optString("name", "").orEmpty())
                    val assignee = issue.optJSONObject("assignee")?.optString("name", "").orEmpty()
                    sb.append(" | assignee: ").append(assignee.ifBlank { "none" })
                    sb.append('\n').append(issue.optString("url", "")).append('\n')
                    sb.append(HttpJson.cut(issue.optString("description", ""), 2500))
                    HttpJson.cut(sb.toString().trimEnd())
                }
            }
            "issue_create", "issue_update" -> {
                val key = if (action == "issue_create") "issueCreate" else "issueUpdate"
                val result = data.optJSONObject(key)
                val issue = result?.optJSONObject("issue")
                if (result == null || !result.optBoolean("success", false) || issue == null) {
                    "Linear reported that the change did not go through."
                } else {
                    "Saved ${issue.optString("identifier", "")} ${issue.optString("title", "")} " +
                        issue.optString("url", "")
                }
            }
            "teams" -> HttpJson.rows(data.optJSONObject("teams")?.optJSONArray("nodes"), 50) { node ->
                "${node.optString("id", "")} | ${node.optString("key", "")} | ${node.optString("name", "")}"
            }
            else -> HttpJson.rows(data.optJSONObject("searchIssues")?.optJSONArray("nodes"), 25) { node ->
                "${node.optString("identifier", "")} | ${HttpJson.oneLine(node.optString("title", ""), 110)} | " +
                    node.optString("url", "")
            }
        }
    }

    private suspend fun webdav(args: JSONObject): ToolExecResult {
        val config = lookup(WEBDAV_IDS) ?: return absent("WebDAV")
        val root = config.baseUrl.trim().trimEnd('/')
        if (root.isEmpty()) {
            return ToolExecResult(
                "The WebDAV connector has no server URL. Add it in Settings → Agent → Connectors.",
                success = false
            )
        }
        if (config.token.isBlank()) return tokenless("WebDAV")
        val raw = args.optString("action", "")
        val action = HttpJson.action(raw, WEBDAV_ACTIONS)
        if (action.isEmpty()) return unknown("WebDAV", raw, WEBDAV_ACTIONS)
        val headers = mutableMapOf("Authorization" to "Basic ${basic(config.account.trim(), config.token.trim())}")
        val path = args.optString("path", "").trim().trim('/')
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
                val content = args.optString("content", "")
                headers["Content-Type"] = "text/plain; charset=utf-8"
                val reply = send("PUT", url, headers, content, 120)
                problem(reply, "WebDAV")?.let { return it }
                ToolExecResult("Wrote ${content.toByteArray(StandardCharsets.UTF_8).size} bytes to $path.")
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
                val destination = args.optString("destination", "").trim()
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

    private suspend fun httpRequest(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val method = args.optString("method", "GET").trim().uppercase().ifBlank { "GET" }
        val url = args.optString("url", "").trim()
        HttpJson.urlProblem(url)?.let { return ToolExecResult(it, success = false) }
        val headers = headerMap(args)
        val body = if (args.has("body")) args.optString("body", "") else null
        val saveTo = args.optString("save_to", "").trim()
        if (saveTo.isNotEmpty()) {
            val file = try {
                Workspace.forWrite(ctx, saveTo)
            } catch (e: HarnessError) {
                return ToolExecResult(e.message ?: "That path cannot be written.", success = false)
            }
            val reply = HttpJson.requestBytes(method, url, headers, body, 120, true)
            if (reply.code == 0) return ToolExecResult("Request failed: ${reply.error}", success = false)
            if (!reply.ok) {
                return ToolExecResult(
                    "The request failed with ${HttpJson.describe(reply.code)}, so nothing was saved:\n" +
                        HttpJson.cut(String(reply.bytes, StandardCharsets.UTF_8), 1200),
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

    private fun headerMap(args: JSONObject): Map<String, String> {
        val obj = args.optJSONObject("headers") ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.isBlank()) continue
            out[key] = HttpJson.text(obj.opt(key))
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
            sb.append(entry.first).append(": ")
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
