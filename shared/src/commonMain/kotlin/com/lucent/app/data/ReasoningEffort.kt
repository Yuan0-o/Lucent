package com.lucent.app.data

enum class ReasoningEffort(val key: String) {
    PROVIDER_DEFAULT("default"),
    NONE("none"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max");

    val label: String
        get() = when (this) {
            PROVIDER_DEFAULT -> com.lucent.app.i18n.S.reasoningDefault
            NONE -> com.lucent.app.i18n.S.reasoningNone
            MINIMAL -> com.lucent.app.i18n.S.reasoningMinimal
            LOW -> com.lucent.app.i18n.S.reasoningLow
            MEDIUM -> com.lucent.app.i18n.S.reasoningMedium
            HIGH -> com.lucent.app.i18n.S.reasoningHigh
            XHIGH -> com.lucent.app.i18n.S.reasoningXhigh
            MAX -> com.lucent.app.i18n.S.reasoningMax
        }

    val detail: String
        get() = when (this) {
            PROVIDER_DEFAULT -> com.lucent.app.i18n.S.reasoningDefaultSub
            NONE -> com.lucent.app.i18n.S.reasoningNoneSub
            MINIMAL -> com.lucent.app.i18n.S.reasoningMinimalSub
            LOW -> com.lucent.app.i18n.S.reasoningLowSub
            MEDIUM -> com.lucent.app.i18n.S.reasoningMediumSub
            HIGH -> com.lucent.app.i18n.S.reasoningHighSub
            XHIGH -> com.lucent.app.i18n.S.reasoningXhighSub
            MAX -> com.lucent.app.i18n.S.reasoningMaxSub
        }

    companion object {
        val DEFAULT = PROVIDER_DEFAULT

        fun fromKey(key: String?): ReasoningEffort =
            entries.firstOrNull { it.key == (key?.trim()?.lowercase() ?: "") } ?: DEFAULT
    }
}

data class ReasoningPlan(
    val effort: String? = null,
    val thinkingOff: Boolean = false,
    val claudeAdaptive: Boolean = false,
    val claudeEffort: String? = null,
    val claudeBudgetTokens: Int? = null,
    val googleThinkingLevel: String? = null,
    val googleThinkingBudget: Int? = null
) {
    val empty: Boolean
        get() = effort == null && !thinkingOff && !claudeAdaptive && claudeBudgetTokens == null &&
            googleThinkingLevel == null && googleThinkingBudget == null

    companion object {
        val NONE = ReasoningPlan()
    }
}

object ReasoningEfforts {

    private fun modelKey(model: String): String =
        model.trim().lowercase().removePrefix("models/").substringAfterLast('/')

    private fun versionOf(model: String, after: String): Double? {
        val at = model.indexOf(after)
        if (at < 0) return null
        val tail = model.substring(at + after.length)
        val match = Regex("(\\d+)(?:[.-](\\d{1,2})(?!\\d))?").find(tail) ?: return null
        val major = match.groupValues[1].toDoubleOrNull() ?: return null
        val minor = match.groupValues[2].toDoubleOrNull() ?: 0.0
        return major + minor / 10.0
    }

    private fun levels(vararg values: ReasoningEffort): List<ReasoningEffort> =
        listOf(ReasoningEffort.PROVIDER_DEFAULT) + values

    private val OPENAI_WIDE = levels(
        ReasoningEffort.NONE,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH,
        ReasoningEffort.XHIGH,
        ReasoningEffort.MAX
    )

    private val OPENAI_MEDIUM = levels(
        ReasoningEffort.NONE,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH,
        ReasoningEffort.XHIGH
    )

    private val OPENAI_SMALL = levels(
        ReasoningEffort.NONE,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val OPENAI_MINIMAL = levels(
        ReasoningEffort.MINIMAL,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val OPENAI_ASTRA = levels(
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH,
        ReasoningEffort.XHIGH,
        ReasoningEffort.MAX
    )

    private val CLAUDE_ADAPTIVE = levels(
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH,
        ReasoningEffort.MAX
    )

    private val CLAUDE_ADAPTIVE_WIDE = levels(
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH,
        ReasoningEffort.XHIGH,
        ReasoningEffort.MAX
    )

    private val LOW_MEDIUM_HIGH = levels(
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val GEMINI_LEVELS = levels(
        ReasoningEffort.MINIMAL,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val GEMINI_NO_MINIMAL = levels(
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val GEMINI_BUDGETS = levels(
        ReasoningEffort.NONE,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val GEMINI_BUDGETS_NO_OFF = levels(
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val DEEPSEEK_LEVELS = listOf(
        ReasoningEffort.PROVIDER_DEFAULT,
        ReasoningEffort.NONE,
        ReasoningEffort.LOW,
        ReasoningEffort.HIGH,
        ReasoningEffort.MAX
    )

    private val KIMI_K3_LEVELS = listOf(
        ReasoningEffort.PROVIDER_DEFAULT,
        ReasoningEffort.LOW,
        ReasoningEffort.HIGH,
        ReasoningEffort.MAX
    )

    private val ON_OFF = listOf(ReasoningEffort.PROVIDER_DEFAULT, ReasoningEffort.NONE)

    private val NOTHING = listOf(ReasoningEffort.PROVIDER_DEFAULT)

    fun optionsFor(providerId: String, model: String = ""): List<ReasoningEffort> {
        val name = modelKey(model)
        if (name.isBlank()) return NOTHING
        return when (providerId) {
            ApiProviders.CHATGPT -> when {
                name.contains("astra") -> OPENAI_ASTRA
                name.contains("gpt-6") || name.contains("gpt6") -> OPENAI_WIDE
                name.contains("gpt-5.6") || name.contains("gpt5.6") -> OPENAI_WIDE
                name.contains("gpt-5.5-pro") -> listOf(
                    ReasoningEffort.PROVIDER_DEFAULT,
                    ReasoningEffort.MEDIUM,
                    ReasoningEffort.HIGH,
                    ReasoningEffort.XHIGH
                )
                name.contains("pro") -> listOf(
                    ReasoningEffort.PROVIDER_DEFAULT,
                    ReasoningEffort.MEDIUM,
                    ReasoningEffort.HIGH,
                    ReasoningEffort.XHIGH
                )
                name.contains("gpt-5.5") || name.contains("gpt-5.4") || name.contains("gpt-5.2") ->
                    OPENAI_MEDIUM
                name.contains("gpt-5.1") -> OPENAI_SMALL
                name.contains("gpt-5") || name.contains("gpt5") -> OPENAI_MINIMAL
                name.startsWith("o1") || name.startsWith("o3") || name.startsWith("o4") -> LOW_MEDIUM_HIGH
                else -> NOTHING
            }
            ApiProviders.CLAUDE -> {
                val version = versionOf(name, "claude-")
                when {
                    version == null -> NOTHING
                    version >= 4.7 -> if (name.contains("haiku")) CLAUDE_ADAPTIVE else CLAUDE_ADAPTIVE_WIDE
                    version >= 4.0 -> LOW_MEDIUM_HIGH
                    else -> NOTHING
                }
            }
            ApiProviders.GEMINI -> when {
                name.contains("gemini-3.8") || name.contains("gemini-3.7") ||
                    name.contains("gemini3.8") || name.contains("gemini3.7") -> GEMINI_NO_MINIMAL
                name.contains("flash-lite-image") -> levels(ReasoningEffort.MINIMAL, ReasoningEffort.HIGH)
                (name.contains("gemini-3") || name.contains("gemini3")) && name.contains("pro") ->
                    GEMINI_NO_MINIMAL
                name.contains("gemini-3") || name.contains("gemini3") -> GEMINI_LEVELS
                name.contains("gemini-2.5") || name.contains("gemini2.5") ->
                    if (name.contains("pro")) GEMINI_BUDGETS_NO_OFF else GEMINI_BUDGETS
                else -> NOTHING
            }
            ApiProviders.DEEPSEEK -> if (name.contains("deepseek")) DEEPSEEK_LEVELS else NOTHING
            ApiProviders.KIMI -> when {
                !name.contains("kimi") && !name.contains("moonshot") -> NOTHING
                name.contains("k2.5") -> NOTHING
                name.contains("k3") -> KIMI_K3_LEVELS
                name.contains("k2.6") || name.contains("k2-6") -> ON_OFF
                name.contains("k2.7") || name.contains("k2-7") -> NOTHING
                else -> NOTHING
            }
            else -> NOTHING
        }
    }

    fun cacheOptionsFor(providerId: String, model: String): List<String> {
        val name = model.trim().lowercase()
        return when (providerId) {
            ApiProviders.KIMI -> if (name.contains("k3")) listOf("1h") else emptyList()
            ApiProviders.CHATGPT -> if (name.contains("gpt-5.6") || name.contains("gpt-6")) listOf("30m") else emptyList()
            else -> emptyList()
        }
    }

    fun offersChoice(providerId: String, model: String = ""): Boolean =
        optionsFor(providerId, model).size > 1

    fun settled(providerId: String, model: String, current: String): ReasoningEffort {
        val wanted = ReasoningEffort.fromKey(current)
        return if (wanted in optionsFor(providerId, model)) wanted else ReasoningEffort.PROVIDER_DEFAULT
    }

    fun planFor(providerId: String, model: String, current: String): ReasoningPlan {
        val options = optionsFor(providerId, model)
        val effort = settled(providerId, model, current)
        if (options.size <= 1 || effort == ReasoningEffort.PROVIDER_DEFAULT) return ReasoningPlan.NONE
        return when (providerId) {
            ApiProviders.CHATGPT -> ReasoningPlan(effort = effort.key)
            ApiProviders.DEEPSEEK -> ReasoningPlan(effort = effort.key)
            ApiProviders.KIMI ->
                if (effort == ReasoningEffort.NONE) ReasoningPlan(thinkingOff = true)
                else ReasoningPlan(effort = effort.key)
            ApiProviders.CLAUDE -> {
                val version = versionOf(modelKey(model), "claude-") ?: 0.0
                if (version >= 4.7) {
                    ReasoningPlan(claudeAdaptive = true, claudeEffort = effort.key)
                } else {
                    ReasoningPlan(claudeBudgetTokens = anthropicBudgetTokens(effort))
                }
            }
            ApiProviders.GEMINI -> when {
                modelKey(model).contains("gemini-2.5") ->
                    ReasoningPlan(googleThinkingBudget = googleThinkingBudget(effort))
                else -> ReasoningPlan(googleThinkingLevel = effort.key)
            }
            else -> ReasoningPlan.NONE
        }
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
