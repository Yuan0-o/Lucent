package com.lucent.app.assistant.text

import com.lucent.app.network.ToolExecResult

object ReplyPolish {

    fun isBareRefusal(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty() || t.length > 64) return false
        val latin = t.lowercase().replace(NON_LATIN, " ").replace(MULTI_WHITESPACE, " ").trim()
        if (REFUSAL_OPENER.containsMatchIn(latin)) return true
        return CJK_REFUSALS.any { t.contains(it) }
    }

    fun isTerseNonAnswer(s: String): Boolean {
        val t = s.trim().trimEnd('.', '!', ' ').lowercase()
        return t in setOf("done", "ok", "okay", "no reply", "none", "null", "n/a", "")
    }

    fun deRobotify(text: String): String {
        var s = text
        s = BOLD_SPAN.replace(s) { it.groupValues[1] }
        s = STAR_SPAN.replace(s) { it.groupValues[1] }
        s = DUNDER_SPAN.replace(s) { it.groupValues[1] }
        s = UNDERSCORE_SPAN.replace(s) { it.groupValues[1] }
        s = CODE_SPAN.replace(s) { it.groupValues[1] }
        s = s.lineSequence().joinToString("\n") { line ->
            var l = line
            l = HEADING_PREFIX.replace(l, "")
            l = BULLET_PREFIX.replace(l, "")
            l
        }
        return s
    }

    fun imageFileName(mime: String?): String = when {
        mime == null -> "image.png"
        mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) -> "image.jpg"
        mime.contains("webp", ignoreCase = true) -> "image.webp"
        mime.contains("gif", ignoreCase = true) -> "image.gif"
        else -> "image.png"
    }

    fun replyContent(
        text: String?,
        hasImage: Boolean,
        toolResults: List<ToolExecResult>,
        userText: String = ""
    ): String {
        val cleaned = text?.let { deRobotify(it).trim() }
        val deniesRealSuccess =
            !cleaned.isNullOrBlank() && toolResults.any { it.success } && isBareRefusal(cleaned)
        if (!cleaned.isNullOrBlank() && !isTerseNonAnswer(cleaned) && !deniesRealSuccess) return cleaned

        val failures = toolResults.filter { !it.success }
        if (failures.isNotEmpty()) return failures.joinToString(" ") { it.summary }
        val phrases = FallbackPhrases.forText(userText)
        if (hasImage) return phrases.image
        val successes = toolResults.filter { it.success }
        if (successes.isNotEmpty()) return successes.joinToString(" ") { it.summary }
        return phrases.retry
    }

    private data class FallbackPhrases(val image: String, val retry: String) {
        companion object {
            private val EN = FallbackPhrases("Here's the image you asked for.", "Sorry, I didn't quite catch that — could you say it another way?")
            private val ZH = FallbackPhrases("这是你要的图片。", "抱歉，我没太明白，可以换个说法再说一遍吗？")
            private val JA = FallbackPhrases("ご希望の画像です。", "ごめんなさい、うまく理解できませんでした。別の言い方でもう一度お願いできますか？")
            private val KO = FallbackPhrases("요청하신 이미지예요.", "죄송해요, 잘 이해하지 못했어요. 다른 방식으로 다시 말씀해 주시겠어요?")

            fun forText(text: String): FallbackPhrases {
                for (ch in text) {
                    val c = ch.code
                    if (c in 0xAC00..0xD7AF) return KO
                    if (c in 0x3040..0x30FF) return JA
                    if (c in 0x4E00..0x9FFF || c in 0x3400..0x4DBF) return ZH
                }
                return EN
            }
        }
    }

    private val NON_LATIN = Regex("[^a-z ]")
    private val MULTI_WHITESPACE = Regex("\\s+")
    private val REFUSAL_OPENER = Regex("^(sorry )?(but )?i (just )?(really )?(can ?no ?t|can ?t|cannot|am unable to|am not able to)\\b")
    private val CJK_REFUSALS = arrayOf("我不能", "我无法", "无法完成", "做不到", "帮不了", "できません", "できかねます", "私にはできません", "할 수 없", "못해요", "못합니다")

    private val BOLD_SPAN = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*")
    private val STAR_SPAN = Regex("(?<![\\w*])\\*(?=\\S)([^*\\n]+?)(?<=\\S)\\*(?![\\w*])")
    private val DUNDER_SPAN = Regex("__(?=\\S)(.+?)(?<=\\S)__")
    private val UNDERSCORE_SPAN = Regex("(?<![\\w_])_(?=\\S)([^_\\n]+?)(?<=\\S)_(?![\\w_])")
    private val CODE_SPAN = Regex("`([^`\\n]+?)`")
    private val HEADING_PREFIX = Regex("^\\s{0,3}#{1,6}\\s+")
    private val BULLET_PREFIX = Regex("^\\s{0,3}[-*•]\\s+")
}
