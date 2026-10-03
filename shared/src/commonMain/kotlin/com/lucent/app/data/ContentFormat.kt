package com.lucent.app.data

enum class ContentFormat(val key: String) {
    RICH_TEXT("rich"),
    MARKDOWN("markdown");

    companion object {
        fun fromKey(key: String?): ContentFormat? =
            entries.firstOrNull { it.key == key }
    }
}

object ContentFormats {

    const val MARKDOWN_KEY = "markdown"

    const val RICH_TEXT_KEY = "rich"

    fun resolve(
        markdownEnabled: Boolean,
        richTextEnabled: Boolean,
        override: String?,
        body: String
    ): ContentFormat {
        val forced = ContentFormat.fromKey(override)
        if (forced == ContentFormat.MARKDOWN && markdownEnabled) return ContentFormat.MARKDOWN
        if (forced == ContentFormat.RICH_TEXT && richTextEnabled) return ContentFormat.RICH_TEXT
        return when {
            markdownEnabled && richTextEnabled ->
                if (looksLikeMarkdown(body)) ContentFormat.MARKDOWN else ContentFormat.RICH_TEXT
            markdownEnabled -> ContentFormat.MARKDOWN
            else -> ContentFormat.RICH_TEXT
        }
    }

    fun looksLikeMarkdown(body: String): Boolean {
        if (body.isBlank()) return false
        if (FENCE.containsMatchIn(body)) return true
        var score = 0
        if (HEADING.containsMatchIn(body)) score += 2
        if (CHECKBOX.containsMatchIn(body)) score += 2
        if (BULLET.findAll(body).take(2).count() >= 2) score += 2
        if (NUMBERED.findAll(body).take(2).count() >= 2) score += 2
        if (LINK.containsMatchIn(body)) score += 2
        if (QUOTE.containsMatchIn(body)) score += 1
        if (RULE.containsMatchIn(body)) score += 1
        if (STRONG.containsMatchIn(body)) score += 1
        if (CODE.containsMatchIn(body)) score += 1
        return score >= 2
    }

    private val FENCE = Regex("```")
    private val HEADING = Regex("(?m)^#{1,6}\\s+\\S")
    private val CHECKBOX = Regex("(?m)^\\s*[-*+]\\s+\\[[ xX]]")
    private val BULLET = Regex("(?m)^\\s*[-*+]\\s+\\S")
    private val NUMBERED = Regex("(?m)^\\s*\\d+[.)]\\s+\\S")
    private val QUOTE = Regex("(?m)^\\s*>\\s?\\S")
    private val RULE = Regex("(?m)^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$")
    private val STRONG = Regex("\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__")
    private val CODE = Regex("`[^`\\n]+`")
    private val LINK = Regex("\\[[^\\]\\n]+\\]\\([^)\\n]+\\)|\\[\\[[^\\]\\n]+]]")
}
