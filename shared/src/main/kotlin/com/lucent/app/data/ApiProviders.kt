package com.lucent.app.data

object ApiProviders {

    const val CHATGPT = "chatgpt"
    const val GEMINI = "gemini"
    const val CLAUDE = "claude"
    const val DEEPSEEK = "deepseek"
    const val KIMI = "kimi"
    const val CUSTOM = "custom"

    data class Preset(val id: String, val spec: String, val baseUrl: String)

    val PRESETS = listOf(
        Preset(CHATGPT, "openai", "https://api.openai.com/v1"),
        Preset(GEMINI, "google", "https://generativelanguage.googleapis.com/v1beta"),
        Preset(CLAUDE, "anthropic", "https://api.anthropic.com/v1"),
        Preset(DEEPSEEK, "openai", "https://api.deepseek.com"),
        Preset(KIMI, "openai", "https://api.moonshot.cn/v1")
    )

    val ALL = PRESETS.map { it.id } + CUSTOM

    fun preset(id: String): Preset? = PRESETS.firstOrNull { it.id == id }

    fun isPreset(id: String): Boolean = preset(id) != null

    fun match(spec: String, baseUrl: String): String {
        val url = normalize(baseUrl)
        if (url.isEmpty()) return CUSTOM
        return PRESETS.firstOrNull { it.spec == spec && normalize(it.baseUrl) == url }?.id ?: CUSTOM
    }

    fun resolve(provider: String, spec: String, baseUrl: String): String =
        if (provider in ALL) provider else match(spec, baseUrl)

    private fun normalize(url: String): String = url.trim().trimEnd('/').lowercase()
}
