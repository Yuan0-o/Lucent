package com.lucent.app.data

enum class ReasoningEffort(val key: String) {
    PROVIDER_DEFAULT("default"),
    NONE("none"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    val label: String
        get() = when (this) {
            PROVIDER_DEFAULT -> com.lucent.app.i18n.S.reasoningDefault
            NONE -> com.lucent.app.i18n.S.reasoningNone
            MINIMAL -> com.lucent.app.i18n.S.reasoningMinimal
            LOW -> com.lucent.app.i18n.S.reasoningLow
            MEDIUM -> com.lucent.app.i18n.S.reasoningMedium
            HIGH -> com.lucent.app.i18n.S.reasoningHigh
        }

    val detail: String
        get() = when (this) {
            PROVIDER_DEFAULT -> com.lucent.app.i18n.S.reasoningDefaultSub
            NONE -> com.lucent.app.i18n.S.reasoningNoneSub
            MINIMAL -> com.lucent.app.i18n.S.reasoningMinimalSub
            LOW -> com.lucent.app.i18n.S.reasoningLowSub
            MEDIUM -> com.lucent.app.i18n.S.reasoningMediumSub
            HIGH -> com.lucent.app.i18n.S.reasoningHighSub
        }

    companion object {
        val DEFAULT = PROVIDER_DEFAULT

        fun fromKey(key: String?): ReasoningEffort =
            entries.firstOrNull { it.key == (key?.trim()?.lowercase() ?: "") } ?: DEFAULT
    }
}

object ReasoningEfforts {

    private val OPENAI_STYLE = listOf(
        ReasoningEffort.PROVIDER_DEFAULT,
        ReasoningEffort.NONE,
        ReasoningEffort.MINIMAL,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val ANTHROPIC = listOf(
        ReasoningEffort.PROVIDER_DEFAULT,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val GEMINI = listOf(
        ReasoningEffort.PROVIDER_DEFAULT,
        ReasoningEffort.NONE,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val NONE_OFFERED = listOf(ReasoningEffort.PROVIDER_DEFAULT)

    fun optionsFor(providerId: String): List<ReasoningEffort> = when (providerId) {
        ApiProviders.CHATGPT -> OPENAI_STYLE
        ApiProviders.CLAUDE -> ANTHROPIC
        ApiProviders.GEMINI -> GEMINI
        ApiProviders.DEEPSEEK, ApiProviders.KIMI -> NONE_OFFERED
        else -> OPENAI_STYLE
    }

    fun offersChoice(providerId: String): Boolean = optionsFor(providerId).size > 1

    fun openAiEffort(effort: ReasoningEffort): String? = when (effort) {
        ReasoningEffort.NONE -> "none"
        ReasoningEffort.MINIMAL -> "minimal"
        ReasoningEffort.LOW -> "low"
        ReasoningEffort.MEDIUM -> "medium"
        ReasoningEffort.HIGH -> "high"
        ReasoningEffort.PROVIDER_DEFAULT -> null
    }

    fun anthropicBudgetTokens(effort: ReasoningEffort): Int? = when (effort) {
        ReasoningEffort.LOW -> 2048
        ReasoningEffort.MEDIUM -> 8192
        ReasoningEffort.HIGH -> 16384
        else -> null
    }

    fun googleThinkingBudget(effort: ReasoningEffort): Int? = when (effort) {
        ReasoningEffort.NONE -> 0
        ReasoningEffort.LOW -> 1024
        ReasoningEffort.MEDIUM -> 8192
        ReasoningEffort.HIGH -> 24576
        else -> null
    }
}
