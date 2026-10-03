package com.lucent.app.data

object ChatSearch {

    data class Doc(val conversationId: Long, val title: String, val content: String)

    private val TOKEN = Regex("\"([^\"]*)\"|(\\S+)")

    private data class ParsedQuery(val terms: List<String>, val phrases: List<String>) {
        val isEmpty get() = terms.isEmpty() && phrases.isEmpty()
    }

    private fun parse(raw: String): ParsedQuery {
        val terms = mutableListOf<String>()
        val phrases = mutableListOf<String>()
        for (m in TOKEN.findAll(raw.trim())) {
            if (m.value.startsWith("\"")) {
                val p = m.groupValues[1].trim().lowercase()
                if (p.isNotEmpty()) phrases += p
            } else {
                val t = m.groupValues[2].trim().lowercase()
                if (t.isNotEmpty()) terms += t
            }
        }
        return ParsedQuery(terms, phrases)
    }

    fun score(rawQuery: String, doc: Doc): Int = score(parse(rawQuery), doc)

    private fun score(q: ParsedQuery, doc: Doc): Int {
        if (q.isEmpty) return 0
        val title = doc.title.lowercase()
        val content = doc.content.lowercase()

        var score = 0
        for (phrase in q.phrases) {
            val inTitle = title.contains(phrase)
            val inContent = content.contains(phrase)
            if (!inTitle && !inContent) return 0
            if (inTitle) score += 60
            if (inContent) score += 25
        }
        for (term in q.terms) {
            val inTitle = title.contains(term)
            val inContent = content.contains(term)
            if (!inTitle && !inContent) return 0
            if (inTitle) score += 30
            if (inContent) score += 10
            if (title == term) score += 40 else if (title.startsWith(term)) score += 15
        }
        return score
    }

    fun snippet(rawQuery: String, doc: Doc): String {
        val q = parse(rawQuery)
        val content = doc.content
        if (content.isBlank()) return ""
        val lower = content.lowercase()
        val needle = (q.phrases.firstOrNull { lower.contains(it) }
            ?: q.terms.firstOrNull { lower.contains(it) })
            ?: return content.take(SNIPPET_RADIUS * 2).trim().replace('\n', ' ')
        val at = lower.indexOf(needle)
        val start = (at - SNIPPET_RADIUS).coerceAtLeast(0)
        val end = (at + needle.length + SNIPPET_RADIUS).coerceAtMost(content.length)
        val core = content.substring(start, end).replace('\n', ' ').trim()
        return buildString {
            if (start > 0) append('…')
            append(core)
            if (end < content.length) append('…')
        }
    }

    private const val SNIPPET_RADIUS = 42

    fun rank(rawQuery: String, docs: List<Doc>): List<Long>? {
        if (rawQuery.isBlank()) return null
        val q = parse(rawQuery)
        return docs
            .mapNotNull { d -> score(q, d).takeIf { it > 0 }?.let { d.conversationId to it } }
            .sortedByDescending { it.second }
            .map { it.first }
    }


    data class MessageDoc(
        val conversationId: Long,
        val conversationTitle: String,
        val messageId: Long,
        val content: String,
        val timestamp: Long
    )

    data class MessageMatch(
        val conversationId: Long,
        val conversationTitle: String,
        val messageId: Long,
        val snippet: String,
        val hitInSnippetStart: Int,
        val hitInSnippetLength: Int,
        val matchStart: Int,
        val matchLength: Int
    )

    fun messageNeedle(rawQuery: String): String = rawQuery.trim().trim('"').trim()

    fun messageMatches(rawQuery: String, docs: List<MessageDoc>): List<MessageMatch> {
        val needle = messageNeedle(rawQuery).lowercase()
        if (needle.isEmpty()) return emptyList()
        val out = mutableListOf<MessageMatch>()
        for (doc in docs.sortedByDescending { it.timestamp }) {
            val content = doc.content
            if (content.isEmpty()) continue
            val lower = content.lowercase()
            var from = 0
            while (true) {
                val at = lower.indexOf(needle, from)
                if (at < 0) break
                val start = (at - SNIPPET_RADIUS).coerceAtLeast(0)
                val end = (at + needle.length + SNIPPET_RADIUS).coerceAtMost(content.length)
                val leadingEllipsis = start > 0
                val core = content.substring(start, end).replace('\n', ' ')
                val snippet = buildString {
                    if (leadingEllipsis) append('…')
                    append(core)
                    if (end < content.length) append('…')
                }
                out += MessageMatch(
                    conversationId = doc.conversationId,
                    conversationTitle = doc.conversationTitle,
                    messageId = doc.messageId,
                    snippet = snippet,
                    hitInSnippetStart = (if (leadingEllipsis) 1 else 0) + (at - start),
                    hitInSnippetLength = needle.length,
                    matchStart = at,
                    matchLength = needle.length
                )
                from = at + needle.length
            }
        }
        return out
    }
}
