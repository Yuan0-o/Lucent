package com.lucent.app.i18n

object ReplyLanguage {

    enum class Lang(val englishName: String, val nativeName: String) {
        ZH("Chinese", "\u4e2d\u6587"),
        JA("Japanese", "\u65e5\u672c\u8a9e"),
        KO("Korean", "\ud55c\uad6d\uc5b4"),
        EN("English", "English")
    }


    private val PINYIN_STRONG = setOf(
        "nihao", "ni hao", "xiexie", "xie xie", "zaijian", "qing wen", "qingwen",
        "duibuqi", "meiguanxi", "shenme", "zenme", "weishenme", "bangwo", "bang wo",
        "jintian", "mingtian", "zuotian", "xiaoxi", "biji", "renwu", "shanchu"
    )
    private val PINYIN_WEAK = setOf(
        "wo", "ni", "ta", "shi", "bu", "le", "ma", "zhe", "na", "hen", "yao", "you",
        "zhu", "shou", "gei", "kan", "xie", "dian", "hao", "zai", "dou", "jiu"
    )

    private val ROMAJI_STRONG = setOf(
        "konnichiwa", "arigatou", "arigato", "ohayou", "ohayo", "sumimasen", "onegai",
        "onegaishimasu", "kudasai", "watashi", "desu", "masu", "shimasu", "nihongo",
        "sayonara", "gomen", "gomennasai", "wakarimasen", "daijoubu"
    )
    private val ROMAJI_WEAK = setOf("wa", "ga", "wo", "ni", "no", "to", "ka", "mo", "kara", "made", "yo", "ne")

    private val ROMAJA_STRONG = setOf(
        "annyeong", "annyeonghaseyo", "kamsahamnida", "gamsahamnida", "juseyo",
        "hamnida", "imnida", "haeyo", "isseoyo", "eopseoyo", "hangugeo", "jwoyo"
    )
    private val ROMAJA_WEAK = setOf("neun", "eun", "reul", "eul", "ege", "hago", "haji", "seyo")

    private val TOKENS = Regex("[a-z]+")

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
        if (best < 3) return null
        return when {
            zh == best && ja < best && ko < best -> Lang.ZH
            ja == best && zh < best && ko < best -> Lang.JA
            ko == best && zh < best && ja < best -> Lang.KO
            else -> null
        }
    }

    fun instructionFor(userText: String): String? {
        val lang = detect(userText) ?: return null
        if (lang == Lang.EN) return null
        return "CRITICAL OUTPUT LANGUAGE: the user is writing in ${lang.englishName}. " +
            "Write your entire reply to the user in ${lang.englishName} (${lang.nativeName}), " +
            "using that language's normal script. This applies even though these instructions are " +
            "in English, and even if the user typed in a romanized form. Do not reply in English."
    }
}
