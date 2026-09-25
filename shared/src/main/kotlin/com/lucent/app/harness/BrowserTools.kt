package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object HtmlText {

    private val SCRIPT_BLOCKS = listOf("script", "style", "noscript", "svg", "head", "iframe", "template")
    private val BLOCK_TAGS = listOf(
        "p", "div", "br", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6", "section", "article",
        "header", "footer", "table", "ul", "ol", "blockquote", "pre"
    )

    fun title(html: String): String =
        Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(html)?.groupValues?.get(1)?.let { clean(it) }?.trim().orEmpty()

    fun description(html: String): String =
        Regex("<meta[^>]+name=[\"']description[\"'][^>]*content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let { clean(it) }?.trim().orEmpty()

    fun text(html: String): String {
        var body = html
        SCRIPT_BLOCKS.forEach { tag ->
            body = body.replace(
                Regex("<$tag\\b.*?</$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
                " "
            )
            body = body.replace(Regex("<$tag\\b[^>]*/?>", RegexOption.IGNORE_CASE), " ")
        }
        BLOCK_TAGS.forEach { tag ->
            body = body.replace(Regex("</?$tag\\b[^>]*>", RegexOption.IGNORE_CASE), "\n")
        }
        body = body.replace(Regex("<[^>]+>"), " ")
        val decoded = clean(body)
        return decoded.lines()
            .map { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    fun links(html: String, baseUrl: String, limit: Int = 60): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val pattern = Regex("<a\\b[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        pattern.findAll(html).forEach { match ->
            if (out.size >= limit) return@forEach
            val href = match.groupValues[1].trim()
            val label = clean(match.groupValues[2].replace(Regex("<[^>]+>"), " ")).trim()
            if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript:")) return@forEach
            out.add(absolute(baseUrl, href) to label.take(120))
        }
        return out
    }

    fun absolute(baseUrl: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        return try {
            val base = baseUrl.toHttpUrlOrNull() ?: return href
            base.resolve(href)?.toString() ?: href
        } catch (t: Throwable) {
            href
        }
    }

    fun clean(value: String): String {
        var out = value
        val named = mapOf(
            "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"",
            "&#39;" to "'", "&apos;" to "'", "&mdash;" to "—", "&ndash;" to "–", "&hellip;" to "…",
            "&rsquo;" to "'", "&lsquo;" to "'", "&ldquo;" to "\"", "&rdquo;" to "\"", "&middot;" to "·"
        )
        named.forEach { (entity, replacement) -> out = out.replace(entity, replacement) }
        out = out.replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            match.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: ""
        }
        out = out.replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: ""
        }
        return out
    }
}

object BrowserTools : HarnessGroupTools {

    override val group = HarnessGroup.BROWSER

    private const val AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36 Lucent/3.0"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "fetch_url",
            group = group,
            permission = HarnessPermission.BROWSER,
            description = "Fetch a web page and return it as readable text, with the title and final url. Use it to read " +
                "documentation or an article. Set raw true for the original body. Pages that need JavaScript come back " +
                "nearly empty; say so rather than guessing.",
            params = listOf(
                HarnessSchema.text("url", "Page to fetch"),
                HarnessSchema.number("max_chars", "How much text to return", false),
                HarnessSchema.flag("raw", "Return the raw body instead of text", false)
            )
        ),
        HarnessTool(
            name = "browse_page",
            group = group,
            permission = HarnessPermission.BROWSER,
            description = "Fetch a page and list its links as well, so you can choose where to go next.",
            params = listOf(
                HarnessSchema.text("url", "Page to fetch"),
                HarnessSchema.number("max_chars", "How much text to return", false)
            )
        ),
        HarnessTool(
            name = "download_file",
            group = group,
            permission = HarnessPermission.BROWSER,
            description = "Download a file from the web into the workspace. Arguments: url, path (optional), overwrite.",
            params = listOf(
                HarnessSchema.text("url", "File to download"),
                HarnessSchema.text("path", "Where to save it", false),
                HarnessSchema.flag("overwrite", "Replace an existing file", false)
            )
        ),
        HarnessTool(
            name = "web_search",
            group = group,
            permission = HarnessPermission.NETWORK,
            description = "Search the public web and get a short digest of results with links.",
            params = listOf(HarnessSchema.text("query", "What to look up"))
        ),
        HarnessTool(
            name = "open_url",
            group = group,
            permission = HarnessPermission.BROWSER,
            description = "Open a link on the device so the user can see it themselves.",
            params = listOf(HarnessSchema.text("url", "Link to open"))
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = when (name) {
        "fetch_url" -> fetch(ctx, args, withLinks = false)
        "browse_page" -> fetch(ctx, args, withLinks = true)
        "download_file" -> download(ctx, args)
        "web_search" -> search(ctx, args)
        "open_url" -> openUrl(ctx, args)
        else -> null
    }

    private fun valid(url: String): Boolean = url.startsWith("http://") || url.startsWith("https://")

    private fun fetch(ctx: HarnessCtx, args: JSONObject, withLinks: Boolean): ToolExecResult {
        val url = args.optString("url", "").trim()
        if (!valid(url)) return ToolExecResult("Give me an http or https url.", success = false)
        val max = args.optInt("max_chars", 12000).coerceIn(500, 120000)
        return try {
            val request = Request.Builder().url(url).header("User-Agent", AGENT).build()
            client.newCall(request).execute().use { response ->
                val fetched = response.body?.string()
                val body = (fetched ?: "").take(2 * 1024 * 1024)
                val type = response.header("Content-Type", "").lowercase()
                val textLike = type.contains("text") || type.contains("json") || type.contains("xml") || type.contains("html")
                if (!textLike) {
                    return ToolExecResult(
                        "That url returns ${type.ifBlank { "binary data" }} (${body.length} bytes), so there is no text " +
                            "to read. Use download_file if you want to keep it.",
                        success = false
                    )
                }
                val raw = args.optBoolean("raw", false)
                val sb = StringBuilder()
                sb.append(response.request.url).append(" — HTTP ").append(response.code).append('\n')
                val title = HtmlText.title(body)
                if (title.isNotBlank()) sb.append("Title: ").append(title).append('\n')
                val description = HtmlText.description(body)
                if (description.isNotBlank()) sb.append("Summary: ").append(description).append('\n')
                sb.append('\n')
                val content = if (raw) body else HtmlText.text(body)
                sb.append(content.take(max))
                if (content.length > max) sb.append("\n… truncated at $max characters")
                if (withLinks) {
                    val links = HtmlText.links(body, response.request.url.toString())
                    if (links.isNotEmpty()) {
                        sb.append("\n\nLinks:\n")
                        links.forEachIndexed { index, pair ->
                            sb.append(index + 1).append(". ").append(pair.second.take(80))
                                .append(" — ").append(pair.first).append('\n')
                        }
                    }
                }
                if (content.isBlank()) {
                    sb.append("\n(That page came back empty. It probably needs JavaScript; the browser plugin can " +
                        "handle it if it is installed.)")
                }
                ToolExecResult(ctx.limit(sb.toString()))
            }
        } catch (t: Throwable) {
            ToolExecResult("Could not fetch $url: ${t.message ?: t::class.simpleName}", success = false)
        }
    }

    private fun download(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val url = args.optString("url", "").trim()
        if (!valid(url)) return ToolExecResult("Give me an http or https url.", success = false)
        val target = if (args.optString("path", "").isBlank()) {
            val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "download.bin" }
            Workspace.forWrite(ctx, name)
        } else {
            try {
                Workspace.forWrite(ctx, args.optString("path", ""))
            } catch (e: HarnessError) {
                return ToolExecResult(e.message ?: "That path cannot be written", success = false)
            }
        }
        if (target.exists() && !args.optBoolean("overwrite", false)) {
            return ToolExecResult("${Workspace.display(ctx, target)} already exists.", success = false)
        }
        return try {
            val request = Request.Builder().url(url).header("User-Agent", AGENT).build()
            client.newCall(request).execute().use { response ->
                val body = response.body ?: return ToolExecResult("The server sent nothing back.", success = false)
                val length = body.contentLength()
                if (length > 512L * 1024 * 1024) {
                    return ToolExecResult("That file is larger than 512 MiB; not downloading it.", success = false)
                }
                target.parentFile?.mkdirs()
                body.byteStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                ToolExecResult(
                    "Downloaded ${Workspace.display(ctx, target)} (${Workspace.humanSize(target.length())}, " +
                        "${response.header("Content-Type", "unknown type")})."
                )
            }
        } catch (t: Throwable) {
            ToolExecResult("Download failed: ${t.message ?: t::class.simpleName}", success = false)
        }
    }

    private suspend fun search(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val query = args.optString("query", "").trim()
        if (query.isEmpty()) return ToolExecResult("What should I look up?", success = false)
        val engine = com.lucent.app.data.WebSearchEngine.AUTO.key
        return com.lucent.app.network.WebSearchClient.search(query, engine).fold(
            onSuccess = { ToolExecResult(ctx.limit(it)) },
            onFailure = { ToolExecResult("Search failed: ${it.message ?: "network error"}", success = false) }
        )
    }

    private fun openUrl(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val url = args.optString("url", "").trim()
        if (!valid(url)) return ToolExecResult("Give me an http or https url.", success = false)
        val host = HarnessRuntime.host ?: return ToolExecResult("This build cannot open links.", success = false)
        return if (host.openUrl(url)) ToolExecResult("Opened $url.")
        else ToolExecResult("The link could not be opened.", success = false)
    }
}
