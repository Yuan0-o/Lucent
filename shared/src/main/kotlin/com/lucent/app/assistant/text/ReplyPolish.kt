package com.lucent.app.assistant.text

import com.lucent.app.network.ToolExecResult

/**
 * Post-processing for assistant replies, extracted verbatim from AssistantController (v2.7.6).
 *
 * [deRobotify] scrubs markdown artifacts a model was told never to produce, [isBareRefusal] and
 * [isTerseNonAnswer] recognise "I can't"-style non-answers, and [replyContent] decides between the
 * model's own words, an honest tool summary and the scripted fallback lines. All logic is pure and
 * shared by the cloud and local generation paths; keeping it here lets it be unit-tested on the JVM.
 */
object ReplyPolish {

    /**
     * A short reply whose whole content is "I can't" in any of the app's four languages. Kept
     * deliberately narrow — anything over 64 characters, or with substance beyond the refusal,
     * passes through untouched, because second-guessing real prose would be worse than the
     * occasional confused line this exists to catch. Only consulted when a tool actually
     * succeeded this turn (see [replyContent]), so it can never suppress a legitimate "I can't"
     * about something the assistant truly cannot do.
     */
    fun isBareRefusal(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty() || t.length > 64) return false
        val latin = t.lowercase().replace(NON_LATIN, " ").replace(MULTI_WHITESPACE, " ").trim()
        if (REFUSAL_OPENER.containsMatchIn(latin)) return true
        return CJK_REFUSALS.any { t.contains(it) }
    }

    /** A reply that's technically present but says nothing — the exact shapes issue 10 shows. */
    fun isTerseNonAnswer(s: String): Boolean {
        val t = s.trim().trimEnd('.', '!', ' ').lowercase()
        return t in setOf("done", "ok", "okay", "no reply", "none", "null", "n/a", "")
    }

    /**
     * Belt-and-suspenders cleanup of markdown artifacts the model was told never to produce. The
     * system prompt forbids them, but if one slips through it would render as literal punctuation
     * in the app's plain-text bubbles and instantly look robotic. This strips only unambiguous
     * markdown so it can't damage ordinary prose:
     *  - bold/italic/inline-code wrappers around a span: **x**, *x*, __x__, _x_, `x`
     *  - stage-direction asterisks around a whole clause: *smiles*, *laughs*
     *  - leading heading hashes (# , ## ) and leading list bullets (-, *, •) at the start of a line
     * It deliberately leaves apostrophes, hyphens between words, arithmetic, and lone symbols alone.
     */
    fun deRobotify(text: String): String {
        var s = text
        // Bold/italic/code spans: keep the inner text, drop the markers. Non-greedy, must have
        // non-space content, so it won't eat across unrelated asterisks.
        s = BOLD_SPAN.replace(s) { it.groupValues[1] }
        s = STAR_SPAN.replace(s) { it.groupValues[1] }
        s = DUNDER_SPAN.replace(s) { it.groupValues[1] }
        s = UNDERSCORE_SPAN.replace(s) { it.groupValues[1] }
        s = CODE_SPAN.replace(s) { it.groupValues[1] }
        // Line-leading markdown: heading hashes and list bullets.
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

    /**
     * The assistant's final words: its own text when that is real content; otherwise an honest
     * summary of what the tools actually did, or a friendly scripted line in the user's language.
     * A small model sometimes answers a SUCCESSFUL tool run with "i cant" — the transcript
     * confused it, the task exists, and the words flatly contradict what just happened (the
     * reported "created the task, replied i can't" bug). When a short bare refusal denies an
     * action the results prove, the honest tool summary below wins over the model's words.
     */
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

    /**
     * A tiny per-script set of fallback lines. Not a translation system — just enough that the two
     * things the assistant may have to say when it produced no words itself land in the language the
     * user is actually writing, instead of always English, in step with the dynamic-language rule.
     *
     * The app ships exactly four locales — English, Chinese, Japanese, Korean — so these cover every
     * language the product supports and nothing else. Text in any other script falls through to
     * English, which is the correct behaviour for an unsupported language.
     */
    private data class FallbackPhrases(val image: String, val retry: String) {
        companion object {
            private val EN = FallbackPhrases("Here's the image you asked for.", "Sorry, I didn't quite catch that — could you say it another way?")
            private val ZH = FallbackPhrases("这是你要的图片。", "抱歉，我没太明白，可以换个说法再说一遍吗？")
            private val JA = FallbackPhrases("ご希望の画像です。", "ごめんなさい、うまく理解できませんでした。別の言い方でもう一度お願いできますか？")
            private val KO = FallbackPhrases("요청하신 이미지예요.", "죄송해요, 잘 이해하지 못했어요. 다른 방식으로 다시 말씀해 주시겠어요?")

            fun forText(text: String): FallbackPhrases {
                for (ch in text) {
                    val c = ch.code
                    if (c in 0xAC00..0xD7AF) return KO                        // Hangul
                    if (c in 0x3040..0x30FF) return JA                        // Kana
                    if (c in 0x4E00..0x9FFF || c in 0x3400..0x4DBF) return ZH // CJK ideographs
                }
                return EN
            }
        }
    }

    // Compiled once — isBareRefusal is consulted after every successful tool turn.
    private val NON_LATIN = Regex("[^a-z ]")
    private val MULTI_WHITESPACE = Regex("\\s+")
    private val REFUSAL_OPENER = Regex("^(sorry )?(but )?i (just )?(really )?(can ?no ?t|can ?t|cannot|am unable to|am not able to)\\b")
    private val CJK_REFUSALS = arrayOf("我不能", "我无法", "无法完成", "做不到", "帮不了", "できません", "できかねます", "私にはできません", "할 수 없", "못해요", "못합니다")

    // Compiled once — deRobotify runs on every finished reply (and again on the stop/cancel
    // paths). The two line-leading patterns used to be re-compiled for every LINE of every
    // reply, which is the kind of thing a profiler notices on a long, chatty conversation.
    private val BOLD_SPAN = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*")
    private val STAR_SPAN = Regex("(?<![\\w*])\\*(?=\\S)([^*\\n]+?)(?<=\\S)\\*(?![\\w*])")
    private val DUNDER_SPAN = Regex("__(?=\\S)(.+?)(?<=\\S)__")
    private val UNDERSCORE_SPAN = Regex("(?<![\\w_])_(?=\\S)([^_\\n]+?)(?<=\\S)_(?![\\w_])")
    private val CODE_SPAN = Regex("`([^`\\n]+?)`")
    private val HEADING_PREFIX = Regex("^\\s{0,3}#{1,6}\\s+")
    private val BULLET_PREFIX = Regex("^\\s{0,3}[-*•]\\s+")
}
