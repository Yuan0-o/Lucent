package com.lucent.app.data

import java.util.WeakHashMap

object TokenEstimator {

    private const val CHARS_PER_TOKEN = 4

    private val cache = WeakHashMap<String, Int>()

    private const val CACHE_MAX = 500

    fun estimate(text: String?): Int {
        if (text.isNullOrEmpty()) return 0
        val cached = cache[text]
        if (cached != null) return cached
        var cjk = 0
        var other = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (isWideScript(cp)) cjk++ else if (!Character.isWhitespace(cp)) other++
        }
        val est = cjk + (other + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN
        val result = if (est <= 0 && text.isNotBlank()) 1 else est
        if (cache.size < CACHE_MAX) cache[text] = result
        return result
    }

    fun estimateAll(texts: Iterable<String?>): Int = texts.sumOf { estimate(it) }

    fun label(tokens: Int): String {
        if (tokens <= 0) return "~0 tokens"
        return if (tokens >= 1000) {
            val k = tokens / 1000.0
            "~${String.format("%.1f", k)}k tokens"
        } else {
            "~$tokens tokens"
        }
    }

    fun labelFromUsage(prompt: Int, completion: Int): String {
        val total = prompt + completion
        if (total <= 0) return "unknown"
        val promptStr = if (prompt >= 1000) String.format("%.1fk", prompt / 1000.0) else "$prompt"
        val compStr = if (completion >= 1000) String.format("%.1fk", completion / 1000.0) else "$completion"
        return "~${promptStr}+${compStr} tokens"
    }

    fun invalidate(text: String) { cache.remove(text) }

    fun clearCache() { cache.clear() }

    private fun isWideScript(cp: Int): Boolean {
        return (cp in 0x3040..0x30FF) ||
            (cp in 0x3400..0x4DBF) ||
            (cp in 0x4E00..0x9FFF) ||
            (cp in 0xAC00..0xD7AF) ||
            (cp in 0xF900..0xFAFF) ||
            (cp in 0x3000..0x303F) ||
            (cp in 0xFF00..0xFFEF) ||
            (cp in 0x20000..0x2FA1F)
    }
}