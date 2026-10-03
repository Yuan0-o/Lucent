package com.lucent.app.network

import com.lucent.app.data.WebSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object WebSearchClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(16, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private const val INSTANT_ENDPOINT = "https://api.duckduckgo.com/"
    private const val WIKI_ENDPOINT = "https://en.wikipedia.org/w/api.php"
    private const val WIKI_ZH_ENDPOINT = "https://zh.wikipedia.org/w/api.php"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private const val MAX_RESULTS = 6
    private const val MIN_USEFUL_RESULTS = 2

    private data class SearchResult(val title: String, val url: String, val snippet: String)

    private data class EngineAttempt(val engine: WebSearchEngine, val results: List<SearchResult>)

    suspend fun search(query: String, engineKey: String = WebSearchEngine.AUTO.key): Result<String> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext Result.success("No search query was given.")

            val requested = WebSearchEngine.fromKey(engineKey)
            var networkFailure: Exception? = null
            fun note(e: Exception) {
                if (networkFailure == null) networkFailure = e
            }

            val chain = if (requested == WebSearchEngine.AUTO) autoChain(trimmed) else listOf(requested)
            var used: EngineAttempt? = null
            for (engine in chain) {
                val hits = try {
                    fetchFrom(engine, trimmed)
                } catch (e: Exception) {
                    note(e)
                    emptyList()
                }
                if (hits.isNotEmpty()) {
                    used = EngineAttempt(engine, hits)
                    if (hits.size >= MIN_USEFUL_RESULTS) break
                }
            }

            val instant: String? = if (requested == WebSearchEngine.AUTO || used == null) {
                try {
                    fetchInstantAnswer(trimmed)
                } catch (e: Exception) {
                    note(e)
                    null
                }
            } else null

            if (used == null) {
                val wiki = try {
                    fetchWikipedia(trimmed)
                } catch (e: Exception) {
                    note(e)
                    null
                }
                if (wiki != null) used = EngineAttempt(WebSearchEngine.WIKIPEDIA, wiki)
            }

            val results = used?.results.orEmpty()
            val hasInstant = !instant.isNullOrBlank()
            if (!hasInstant && results.isEmpty()) {
                val failure = networkFailure
                return@withContext if (failure != null) {
                    Result.failure(failure)
                } else {
                    Result.success(
                        "Web search for \"$trimmed\" returned no clear results. Tell the user you " +
                            "couldn't find anything solid and, if useful, suggest a more specific wording."
                    )
                }
            }

            val sb = StringBuilder()
            sb.append("Web search results for \"").append(trimmed).append("\"")
            used?.let { if (it.results.isNotEmpty()) sb.append(" (via ").append(it.engine.label).append(")") }
            sb.append(":\n")
            if (hasInstant) sb.append("\nSummary: ").append(instant?.trim()).append("\n")
            if (results.isNotEmpty()) {
                sb.append("\nTop results:\n")
                results.take(MAX_RESULTS).forEachIndexed { i, r ->
                    sb.append(i + 1).append(". ").append(r.title)
                    if (r.snippet.isNotBlank()) sb.append(" — ").append(r.snippet)
                    if (r.url.isNotBlank()) sb.append(" (").append(r.url).append(")")
                    sb.append("\n")
                }
            }
            sb.append(
                "\nUse these results to answer the user's question in their own language. Cite a source " +
                    "link when it helps, and say plainly if the results don't actually settle the question."
            )
            Result.success(sb.toString().trim())
        }

    private fun autoChain(query: String): List<WebSearchEngine> {
        val cjk = query.any { it.code > 0x2E80 }
        return if (cjk) {
            listOf(
                WebSearchEngine.BING, WebSearchEngine.BAIDU, WebSearchEngine.SOGOU, WebSearchEngine.SO360,
                WebSearchEngine.DUCKDUCKGO, WebSearchEngine.GOOGLE, WebSearchEngine.BRAVE,
                WebSearchEngine.MOJEEK, WebSearchEngine.YANDEX, WebSearchEngine.WIKIPEDIA
            )
        } else {
            listOf(
                WebSearchEngine.BING, WebSearchEngine.DUCKDUCKGO, WebSearchEngine.GOOGLE,
                WebSearchEngine.BRAVE, WebSearchEngine.MOJEEK, WebSearchEngine.YANDEX,
                WebSearchEngine.BAIDU, WebSearchEngine.SOGOU, WebSearchEngine.SO360,
                WebSearchEngine.WIKIPEDIA
            )
        }
    }

    private fun fetchFrom(engine: WebSearchEngine, query: String): List<SearchResult> = when (engine) {
        WebSearchEngine.AUTO -> emptyList()
        WebSearchEngine.WIKIPEDIA -> fetchWikipedia(query).orEmpty()
        WebSearchEngine.DUCKDUCKGO -> {
            val lite = fetchDuckDuckGoLite(query)
            if (lite.isNotEmpty()) lite else fetchDuckDuckGoHtml(query)
        }
        WebSearchEngine.GOOGLE -> fetchGoogle(query)
        WebSearchEngine.BING -> fetchBing(query)
        WebSearchEngine.BRAVE -> fetchBrave(query)
        WebSearchEngine.YANDEX -> fetchYandex(query)
        WebSearchEngine.MOJEEK -> fetchMojeek(query)
        WebSearchEngine.BAIDU -> fetchBaidu(query)
        WebSearchEngine.SOGOU -> fetchSogou(query)
        WebSearchEngine.SO360 -> fetchSo360(query)
    }

    private fun get(url: String, acceptLanguage: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", acceptLanguage)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            return body.ifBlank { null }
        }
    }

    private fun formPost(url: String, fields: Map<String, String>, acceptLanguage: String): String? {
        val builder = okhttp3.FormBody.Builder()
        fields.forEach { (k, v) -> builder.add(k, v) }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", acceptLanguage)
            .post(builder.build())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()?.ifBlank { null }
        }
    }

    private fun q(value: String): String = URLEncoder.encode(value, "UTF-8")

    private val BLOCKED = listOf(
        "anomaly-modal", "captcha", "unusual traffic", "are you a robot",
        "verify you are human", "enable javascript and cookies to continue"
    )

    private fun looksBlocked(html: String): Boolean =
        BLOCKED.any { html.contains(it, ignoreCase = true) }

    private fun fetchDuckDuckGoLite(query: String): List<SearchResult> {
        val html = get("https://lite.duckduckgo.com/lite/?q=${q(query)}&kl=wt-wt", "en-US,en;q=0.9")
            ?: return emptyList()
        if (looksBlocked(html)) return emptyList()
        return parsePairs(
            html,
            Regex(
                """<a\b([^>]*\bclass=['"][^'"]*result-link[^'"]*['"][^>]*)>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            Regex(
                """<td[^>]*\bclass=['"][^'"]*result-snippet[^'"]*['"][^>]*>(.*?)</td>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            )
        )
    }

    private fun fetchDuckDuckGoHtml(query: String): List<SearchResult> {
        val html = formPost(
            "https://html.duckduckgo.com/html/",
            mapOf("q" to query, "kl" to "wt-wt"),
            "en-US,en;q=0.9"
        ) ?: return emptyList()
        if (looksBlocked(html)) return emptyList()
        return parsePairs(
            html,
            Regex(
                """<a\b([^>]*\bclass=['"][^'"]*result__a[^'"]*['"][^>]*)>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            Regex(
                """<a[^>]*\bclass=['"][^'"]*result__snippet[^'"]*['"][^>]*>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            )
        )
    }

    private fun fetchGoogle(query: String): List<SearchResult> {
        val html = get("https://www.google.com/search?q=${q(query)}&num=10&hl=en", "en-US,en;q=0.9")
            ?: return emptyList()
        if (looksBlocked(html)) return emptyList()
        val primary = parsePairs(
            html,
            Regex(
                """<a[^>]*\bhref="(https?://[^"]+)"[^>]*>\s*<h3[^>]*>(.*?)</h3>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            Regex(
                """<div[^>]*\bclass=['"][^'"]*(?:VwiC3b|IsZvec|aCOpRe)[^'"]*['"][^>]*>(.*?)</div>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            hrefGroup = 1,
            titleGroup = 2
        )
        if (primary.isNotEmpty()) return primary
        return parsePairs(
            html,
            Regex(
                """<a[^>]*\bhref="/url\?q=([^"&]+)[^"]*"[^>]*>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            Regex(
                """<span[^>]*\bclass=['"][^'"]*aCOpRe[^'"]*['"][^>]*>(.*?)</span>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            )
        )
    }

    private fun fetchBing(query: String): List<SearchResult> {
        val html = get("https://www.bing.com/search?q=${q(query)}&count=10&setlang=en", "en-US,en;q=0.9")
            ?: return emptyList()
        if (looksBlocked(html)) return emptyList()
        return parsePairs(
            html,
            Regex(
                """<h2[^>]*>\s*<a[^>]*\bhref="([^"]+)"[^>]*>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            Regex(
                """<p[^>]*\bclass=['"][^'"]*b_lineclamp[^'"]*['"][^>]*>(.*?)</p>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            hrefGroup = 1,
            titleGroup = 2
        )
    }

    private fun fetchBrave(query: String): List<SearchResult> {
        val html = get("https://search.brave.com/search?q=${q(query)}", "en-US,en;q=0.9")
            ?: return emptyList()
        if (looksBlocked(html)) return emptyList()
        return parsePairs(
            html,
            Regex(
                """<a[^>]*\bhref="(https?://[^"]+)"[^>]*\bclass=['"][^'"]*result-header[^'"]*['"][^>]*>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            Regex(
                """<div[^>]*\bclass=['"][^'"]*snippet-description[^'"]*['"][^>]*>(.*?)</div>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            hrefGroup = 1,
            titleGroup = 2
        )
    }

    private fun fetchYandex(query: String): List<SearchResult> {
        val html = get("https://yandex.com/search/?text=${q(query)}&lr=87", "en-US,en;q=0.9")
            ?: return emptyList()
        if (looksBlocked(html)) return emptyList()
        return parsePairs(
            html,
            Regex(
                """<a[^>]*\bhref="(https?://[^"]+)"[^>]*\bclass=['"][^'"]*(?:OrganicTitle-Link|organic__url)[^'"]*['"][^>]*>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            Regex(
                """<span[^>]*\bclass=['"][^'"]*(?:OrganicTextContentSpan|organic__text)[^'"]*['"][^>]*>(.*?)</span>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            hrefGroup = 1,
            titleGroup = 2
        )
    }

    private fun fetchMojeek(query: String): List<SearchResult> {
        val html = get("https://www.mojeek.com/search?q=${q(query)}", "en-US,en;q=0.9")
            ?: return emptyList()
        if (looksBlocked(html)) return emptyList()
        return parsePairs(
            html,
            Regex(
                """<a[^>]*\bhref="(https?://[^"]+)"[^>]*\bclass=['"][^'"]*title[^'"]*['"][^>]*>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            Regex(
                """<p[^>]*\bclass=['"][^'"]*s[^'"]*['"][^>]*>(.*?)</p>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            hrefGroup = 1,
            titleGroup = 2
        )
    }

    private fun fetchBaidu(query: String): List<SearchResult> {
        val html = get("https://www.baidu.com/s?wd=${q(query)}&rn=10", "zh-CN,zh;q=0.9,en;q=0.8")
            ?: return emptyList()
        if (looksBlocked(html)) return emptyList()
        val primary = parsePairs(
            html,
            Regex(
                """<h3[^>]*\bclass=['"][^'"]*t[^'"]*['"][^>]*>\s*<a[^>]*\bhref="([^"]+)"[^>]*>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            Regex(
                """<div[^>]*\bclass=['"][^'"]*(?:c-abstract|c-span-last)[^'"]*['"][^>]*>(.*?)</div>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            hrefGroup = 1,
            titleGroup = 2
        )
        if (primary.isNotEmpty()) return resolveBaiduLinks(primary)
        val fallback = parsePairs(
            html,
            Regex(
                """<a[^>]*\bhref="(http://www\.baidu\.com/link\?url=[^"]+|https?://[^"]+)"[^>]*\btarget="_blank"[^>]*>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            Regex(
                """<span[^>]*\bclass=['"][^'"]*content-right[^'"]*['"][^>]*>(.*?)</span>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            hrefGroup = 1,
            titleGroup = 2
        )
        return resolveBaiduLinks(fallback)
    }

    private fun fetchSogou(query: String): List<SearchResult> {
        val html = get("https://www.sogou.com/web?query=${q(query)}", "zh-CN,zh;q=0.9,en;q=0.8")
            ?: return emptyList()
        if (looksBlocked(html)) return emptyList()
        return parsePairs(
            html,
            Regex(
                """<h3[^>]*\bclass=['"][^'"]*(?:vr-title|vrTitle)[^'"]*['"][^>]*>\s*<a[^>]*\bhref="([^"]+)"[^>]*>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            Regex(
                """<div[^>]*\bclass=['"][^'"]*(?:text-layout|fz-mid|space-txt)[^'"]*['"][^>]*>(.*?)</div>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            hrefGroup = 1,
            titleGroup = 2
        )
    }

    private fun fetchSo360(query: String): List<SearchResult> {
        val html = get("https://www.so.com/s?q=${q(query)}", "zh-CN,zh;q=0.9,en;q=0.8")
            ?: return emptyList()
        if (looksBlocked(html)) return emptyList()
        return parsePairs(
            html,
            Regex(
                """<h3[^>]*\bclass=['"][^'"]*res-title[^'"]*['"][^>]*>\s*<a[^>]*\bhref="([^"]+)"[^>]*>(.*?)</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            Regex(
                """<p[^>]*\bclass=['"][^'"]*res-desc[^'"]*['"][^>]*>(.*?)</p>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ),
            hrefGroup = 1,
            titleGroup = 2
        )
    }

    private fun resolveBaiduLinks(results: List<SearchResult>): List<SearchResult> =
        results.map { r ->
            val direct = baiduRealUrl(r.url)
            if (direct.isBlank()) r else r.copy(url = direct)
        }

    private fun baiduRealUrl(href: String): String {
        if (!href.contains("baidu.com/link?url=")) return ""
        return try {
            val request = Request.Builder().url(href).header("User-Agent", USER_AGENT).get().build()
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (finalUrl.contains("baidu.com/link?url=")) "" else finalUrl
            }
        } catch (e: Exception) {
            ""
        }
    }

    private val HTML_TAG = Regex("<[^>]+>")
    private val WS_RUN = Regex("\\s+")

    private fun parsePairs(
        html: String,
        linkRe: Regex,
        snippetRe: Regex,
        hrefGroup: Int = 1,
        titleGroup: Int = 0
    ): List<SearchResult> {
        val links = linkRe.findAll(html).toList()
        val snippets = snippetRe.findAll(html).map { cleanText(it.groupValues[1]) }.toList()
        val out = mutableListOf<SearchResult>()
        val seen = HashSet<String>()
        links.forEachIndexed { i, m ->
            val title = if (titleGroup == 0) {
                cleanText(m.groupValues.getOrElse(1) { "" })
            } else {
                cleanText(m.groupValues.getOrElse(titleGroup) { "" })
            }
            if (title.length < 3) return@forEachIndexed
            val rawHref = m.groupValues.getOrElse(hrefGroup) { "" }
            val url = resolveRedirect(rawHref)
            if (url.isBlank() || url.startsWith("javascript")) return@forEachIndexed
            if (!seen.add(url)) return@forEachIndexed
            val snippet = snippets.getOrNull(i).orEmpty()
            out += SearchResult(title, url, snippet)
            if (out.size >= MAX_RESULTS) return out
        }
        return out
    }

    private fun resolveRedirect(href: String): String {
        val h = href.replace("&amp;", "&").trim()
        val marker = "uddg="
        val idx = h.indexOf(marker)
        if (idx >= 0) {
            val start = idx + marker.length
            val end = h.indexOf('&', start).let { if (it < 0) h.length else it }
            val enc = h.substring(start, end)
            return try { URLDecoder.decode(enc, "UTF-8") } catch (e: Exception) { enc }
        }
        return when {
            h.startsWith("http") -> h
            h.startsWith("//") -> "https:$h"
            h.startsWith("/url?q=") -> {
                val rest = h.removePrefix("/url?q=")
                val end = rest.indexOf('&').let { if (it < 0) rest.length else it }
                try {
                    URLDecoder.decode(rest.substring(0, end), "UTF-8")
                } catch (e: Exception) {
                    rest.substring(0, end)
                }
            }
            else -> h
        }
    }

    private fun fetchInstantAnswer(query: String): String? {
        val url = INSTANT_ENDPOINT + "?q=" + q(query) + "&format=json&no_html=1&skip_disambig=1&t=lucent"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val json = try { JSONObject(body) } catch (e: Exception) { return null }
            val sb = StringBuilder()
            val heading = json.optString("Heading", "")
            val abstract = json.optString("AbstractText", "").ifBlank { json.optString("Abstract", "") }
            val answer = json.optString("Answer", "")
            val definition = json.optString("Definition", "")
            val abstractUrl = json.optString("AbstractURL", "")
            if (answer.isNotBlank()) sb.append(answer).append(" ")
            if (abstract.isNotBlank()) {
                if (heading.isNotBlank()) sb.append(heading).append(": ")
                sb.append(abstract)
                if (abstractUrl.isNotBlank()) sb.append(" (source: ").append(abstractUrl).append(")")
                sb.append(" ")
            }
            if (definition.isNotBlank()) sb.append(definition)
            return sb.toString().trim().ifBlank { null }
        }
    }

    private fun fetchWikipedia(query: String): List<SearchResult>? {
        val cjk = query.any { it.code > 0x2E80 }
        val endpoint = if (cjk) WIKI_ZH_ENDPOINT else WIKI_ENDPOINT
        val host = if (cjk) "zh.wikipedia.org" else "en.wikipedia.org"
        val url = endpoint + "?action=query&list=search&format=json&srlimit=" + MAX_RESULTS +
            "&srsearch=" + q(query)
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val arr = try {
                JSONObject(body).optJSONObject("query")?.optJSONArray("search")
            } catch (e: Exception) {
                null
            } ?: return null
            val out = mutableListOf<SearchResult>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.optString("title", "")
                if (title.isBlank()) continue
                val snippet = cleanText(o.optString("snippet", ""))
                val slug = title.replace(' ', '_')
                val encoded = try { q(slug).replace("+", "%20") } catch (e: Exception) { slug }
                out += SearchResult("$title (Wikipedia)", "https://$host/wiki/$encoded", snippet)
            }
            return out.ifEmpty { null }
        }
    }

    private fun cleanText(raw: String): String {
        val noTags = raw.replace(HTML_TAG, "")
        val decoded = noTags
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&#x2F;", "/")
            .replace("&nbsp;", " ")
        return decoded.replace(WS_RUN, " ").trim()
    }
}
