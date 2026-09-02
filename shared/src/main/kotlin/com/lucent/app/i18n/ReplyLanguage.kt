package com.lucent.app.i18n

/**
 * Works out which language a reply should be written in, from the user's own message.
 *
 * ### Why this exists (B-group task 8)
 *
 * The system prompts already carry a carefully-worded rule: "always reply in the same language the
 * user is writing in, and if they type it romanized — Pinyin, Romaji — answer in that language's
 * normal script". A large cloud model follows that reliably. A 0.5B-to-3B on-device model very
 * often does not: the instruction is a paragraph of English prose competing with several other
 * paragraphs of English prose, and the cheapest thing for a small model to do is answer in the
 * language its instructions are written in. So users typing Chinese — and especially users typing
 * *Pinyin* — got English back.
 *
 * The fix is to stop asking the model to perform the detection. Detection is a job ordinary code
 * does well and deterministically; the model only has to obey a short, concrete order naming one
 * language. [instructionFor] produces that order, and the callers put it at the very END of the
 * system prompt, where a small model weights it most heavily.
 *
 * ### What it deliberately does not do
 *
 * It never guesses. Text that shows no clear signal returns null, and a null instruction means the
 * existing prose rule stands unchanged — so this can only ever ADD certainty, never override a
 * correct choice the model would have made. It also covers exactly the four languages the app
 * ships (see [AppLanguage]); anything else is not this function's business.
 */
object ReplyLanguage {

    enum class Lang(val englishName: String, val nativeName: String) {
        ZH("Chinese", "\u4e2d\u6587"),
        JA("Japanese", "\u65e5\u672c\u8a9e"),
        KO("Korean", "\ud55c\uad6d\uc5b4"),
        EN("English", "English")
    }

    // ---- Romanized-input markers ------------------------------------------------------------
    //
    // Only high-signal tokens go in these sets. The risk to manage is a FALSE positive: forcing a
    // Chinese reply on someone writing English would be a worse bug than the one being fixed. So
    // each entry is a token that essentially does not occur as an ordinary English word, and a
    // single hit is not enough on its own — see [detect], which requires either a strong marker or
    // two weaker ones.

    /** Pinyin-only syllables and very common Pinyin words. "wo", "de", "he" etc. are excluded. */
    private val PINYIN_STRONG = setOf(
        "nihao", "ni hao", "xiexie", "xie xie", "zaijian", "qing wen", "qingwen",
        "duibuqi", "meiguanxi", "shenme", "zenme", "weishenme", "bangwo", "bang wo",
        "jintian", "mingtian", "zuotian", "xiaoxi", "biji", "renwu", "shanchu"
    )
    private val PINYIN_WEAK = setOf(
        "wo", "ni", "ta", "shi", "bu", "le", "ma", "zhe", "na", "hen", "yao", "you",
        "zhu", "shou", "gei", "kan", "xie", "dian", "hao", "zai", "dou", "jiu"
    )

    /** Romaji particles and stock words. Japanese romaji is distinctive once particles appear. */
    private val ROMAJI_STRONG = setOf(
        "konnichiwa", "arigatou", "arigato", "ohayou", "ohayo", "sumimasen", "onegai",
        "onegaishimasu", "kudasai", "watashi", "desu", "masu", "shimasu", "nihongo",
        "sayonara", "gomen", "gomennasai", "wakarimasen", "daijoubu"
    )
    private val ROMAJI_WEAK = setOf("wa", "ga", "wo", "ni", "no", "to", "ka", "mo", "kara", "made", "yo", "ne")

    /** Romanized Korean. The verb endings are the giveaway and are unmistakable. */
    private val ROMAJA_STRONG = setOf(
        "annyeong", "annyeonghaseyo", "kamsahamnida", "gamsahamnida", "juseyo",
        "hamnida", "imnida", "haeyo", "isseoyo", "eopseoyo", "hangugeo", "jwoyo"
    )
    private val ROMAJA_WEAK = setOf("neun", "eun", "reul", "eul", "ege", "hago", "haji", "seyo")

    private val TOKENS = Regex("[a-z]+")

    /**
     * The language [text] is written in, or null when there is no clear signal.
     *
     * Native script wins outright and is checked first: Hangul means Korean, kana means Japanese,
     * and Han characters with no kana anywhere mean Chinese. (Kana before Han matters — Japanese
     * text is full of Han characters, so testing Han first would call every Japanese sentence
     * Chinese.) Only if the text carries no CJK at all do the romanization heuristics run.
     */
    fun detect(text: String): Lang? {
        if (text.isBlank()) return null

        var hasHangul = false
        var hasKana = false
        var hasHan = false
        for (ch in text) {
            when (ch.code) {
                in 0xAC00..0xD7AF, in 0x1100..0x11FF -> hasHangul = true
                in 0x3040..0x30FF -> hasKana = true
                in 0x4E00..0x9FFF, in 0x3400..0x4DBF -> hasHan = true
            }
        }
        if (hasHangul) return Lang.KO
        if (hasKana) return Lang.JA
        if (hasHan) return Lang.ZH

        // ---- No CJK: is this a romanized form of one of them? ----
        val lower = text.lowercase()
        val tokens = TOKENS.findAll(lower).map { it.value }.toList()
        if (tokens.isEmpty()) return null

        fun score(strong: Set<String>, weak: Set<String>): Int {
            var s = 0
            for (phrase in strong) if (phrase.contains(' ')) { if (lower.contains(phrase)) s += 3 }
            for (t in tokens) {
                if (t in strong) s += 3
                else if (t in weak) s += 1
            }
            return s
        }

        val zh = score(PINYIN_STRONG, PINYIN_WEAK)
        val ja = score(ROMAJI_STRONG, ROMAJI_WEAK)
        val ko = score(ROMAJA_STRONG, ROMAJA_WEAK)
        val best = maxOf(zh, ja, ko)
        // A score of 3 is one strong marker or three weak ones. Below that the text is far more
        // likely to be ordinary English that happens to contain "wa" or "ni", so we stay silent
        // and let the prompt's prose rule handle it.
        if (best < 3) return null
        // A tie is genuine ambiguity ("wo ni" reads as both Pinyin and Romaji); say nothing.
        return when {
            zh == best && ja < best && ko < best -> Lang.ZH
            ja == best && zh < best && ko < best -> Lang.JA
            ko == best && zh < best && ja < best -> Lang.KO
            else -> null
        }
    }

    /**
     * A short, unambiguous order to put at the END of a system prompt, or null when [detect] found
     * no clear signal.
     *
     * Phrased as one imperative sentence rather than an explanation, because a small model follows
     * "Write your reply in Chinese." far more reliably than a paragraph reasoning about matching.
     * The native name is included so a model that tokenizes the English name oddly still has an
     * unmistakable anchor.
     */
    fun instructionFor(userText: String): String? {
        val lang = detect(userText) ?: return null
        if (lang == Lang.EN) return null
        return "CRITICAL OUTPUT LANGUAGE: the user is writing in ${lang.englishName}. " +
            "Write your entire reply to the user in ${lang.englishName} (${lang.nativeName}), " +
            "using that language's normal script. This applies even though these instructions are " +
            "in English, and even if the user typed in a romanized form. Do not reply in English."
    }
}
