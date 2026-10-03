package com.lucent.app.data

enum class WebSearchEngine(val key: String, val host: String) {
    AUTO("auto", ""),
    GOOGLE("google", "google.com"),
    BING("bing", "bing.com"),
    DUCKDUCKGO("duckduckgo", "duckduckgo.com"),
    BRAVE("brave", "search.brave.com"),
    YANDEX("yandex", "yandex.com"),
    MOJEEK("mojeek", "mojeek.com"),
    BAIDU("baidu", "baidu.com"),
    SOGOU("sogou", "sogou.com"),
    SO360("so360", "so.com"),
    WIKIPEDIA("wikipedia", "wikipedia.org");

    val label: String
        get() = when (this) {
            AUTO -> com.lucent.app.i18n.S.searchEngineAuto
            GOOGLE -> "Google"
            BING -> "Bing"
            DUCKDUCKGO -> "DuckDuckGo"
            BRAVE -> "Brave Search"
            YANDEX -> "Yandex"
            MOJEEK -> "Mojeek"
            BAIDU -> com.lucent.app.i18n.S.searchEngineBaidu
            SOGOU -> com.lucent.app.i18n.S.searchEngineSogou
            SO360 -> com.lucent.app.i18n.S.searchEngine360
            WIKIPEDIA -> "Wikipedia"
        }

    val usesCjk: Boolean
        get() = this == BAIDU || this == SOGOU || this == SO360 || this == BING

    companion object {
        val DEFAULT = AUTO

        fun fromKey(key: String?): WebSearchEngine =
            entries.firstOrNull { it.key == (key?.trim()?.lowercase() ?: "") } ?: DEFAULT

        val PICKER: List<WebSearchEngine> = listOf(
            AUTO, GOOGLE, BING, DUCKDUCKGO, BRAVE, YANDEX, MOJEEK, BAIDU, SOGOU, SO360, WIKIPEDIA
        )
    }
}
