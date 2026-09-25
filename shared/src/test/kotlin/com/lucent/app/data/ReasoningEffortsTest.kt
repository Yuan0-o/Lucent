package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReasoningEffortsTest {

    @Test
    fun providersWithoutAPublishedDialOfferNothing() {
        assertTrue(ReasoningEfforts.optionsFor(ApiProviders.GEMINI, "gemini-1.5-pro").size <= 1)
        assertTrue(ReasoningEfforts.optionsFor(ApiProviders.CHATGPT, "gpt-4o").size <= 1)
        assertTrue(ReasoningEfforts.optionsFor(ApiProviders.KIMI, "kimi-k2.7-code").size <= 1)
        assertTrue(ReasoningEfforts.optionsFor(ApiProviders.CUSTOM, "gpt-5.6-terra").size <= 1)
        assertTrue(ReasoningEfforts.optionsFor(ApiProviders.CHATGPT, "").size <= 1)
    }

    @Test
    fun openAiLevelsFollowTheModel() {
        assertTrue(ReasoningEffort.MINIMAL in ReasoningEfforts.optionsFor(ApiProviders.CHATGPT, "gpt-5"))
        assertFalse(ReasoningEffort.MAX in ReasoningEfforts.optionsFor(ApiProviders.CHATGPT, "gpt-5.5"))
        assertTrue(ReasoningEffort.MAX in ReasoningEfforts.optionsFor(ApiProviders.CHATGPT, "gpt-5.6-terra"))
        assertFalse(ReasoningEffort.NONE in ReasoningEfforts.optionsFor(ApiProviders.CHATGPT, "gpt-6-astra"))
        assertTrue(ReasoningEffort.XHIGH in ReasoningEfforts.optionsFor(ApiProviders.CHATGPT, "gpt-6-sol"))
    }

    @Test
    fun claudeSwitchesFromBudgetsToAdaptiveAtFortySeven() {
        val legacy = ReasoningEfforts.optionsFor(ApiProviders.CLAUDE, "claude-sonnet-4-5-20250929")
        assertTrue(ReasoningEffort.HIGH in legacy)
        assertFalse(ReasoningEffort.XHIGH in legacy)
        assertEquals(16384, ReasoningEfforts.planFor(ApiProviders.CLAUDE, "claude-sonnet-4-5", "high").claudeBudgetTokens)
        val adaptive = ReasoningEfforts.planFor(ApiProviders.CLAUDE, "claude-opus-5.5", "xhigh")
        assertTrue(adaptive.claudeAdaptive)
        assertEquals("xhigh", adaptive.claudeEffort)
        assertNull(adaptive.claudeBudgetTokens)
    }

    @Test
    fun geminiUsesLevelsForThreeAndBudgetsForTwoFive() {
        assertEquals("medium", ReasoningEfforts.planFor(ApiProviders.GEMINI, "gemini-3.5-flash", "medium").googleThinkingLevel)
        assertEquals(0, ReasoningEfforts.planFor(ApiProviders.GEMINI, "gemini-2.5-flash", "none").googleThinkingBudget)
        assertTrue(ReasoningEfforts.optionsFor(ApiProviders.GEMINI, "gemini-3.1-pro").size <= 4)
        assertFalse(
            ReasoningEffort.NONE in ReasoningEfforts.optionsFor(ApiProviders.GEMINI, "gemini-2.5-pro")
        )
    }

    @Test
    fun deepSeekAndKimiKeepTheirOwnVocabulary() {
        assertEquals("max", ReasoningEfforts.planFor(ApiProviders.DEEPSEEK, "deepseek-flash", "max").effort)
        assertEquals("none", ReasoningEfforts.planFor(ApiProviders.DEEPSEEK, "deepseek-v4-pro", "none").effort)
        assertEquals("low", ReasoningEfforts.planFor(ApiProviders.KIMI, "kimi-k3", "low").effort)
        assertTrue(ReasoningEfforts.planFor(ApiProviders.KIMI, "kimi-k2.6", "none").thinkingOff)
        assertTrue(ReasoningEfforts.planFor(ApiProviders.KIMI, "kimi-k3", "none").empty)
    }

    @Test
    fun aLevelTheModelDoesNotOfferFallsBackToTheProviderDefault() {
        val plan = ReasoningEfforts.planFor(ApiProviders.CHATGPT, "gpt-4o", "high")
        assertTrue(plan.empty)
        assertEquals(
            ReasoningEffort.PROVIDER_DEFAULT,
            ReasoningEfforts.settled(ApiProviders.GEMINI, "gemini-3.1-pro", "minimal")
        )
        assertEquals(
            ReasoningEffort.HIGH,
            ReasoningEfforts.settled(ApiProviders.GEMINI, "gemini-3.1-pro", "high")
        )
    }

    @Test
    fun onlyTheModelsThatDocumentCacheTuningGetIt() {
        assertEquals(listOf("30m"), ReasoningEfforts.cacheOptionsFor(ApiProviders.CHATGPT, "gpt-5.6-sol"))
        assertTrue(ReasoningEfforts.cacheOptionsFor(ApiProviders.CHATGPT, "gpt-5.5").isEmpty())
        assertEquals(listOf("1h"), ReasoningEfforts.cacheOptionsFor(ApiProviders.KIMI, "kimi-k3"))
        assertTrue(ReasoningEfforts.cacheOptionsFor(ApiProviders.KIMI, "kimi-k2.6").isEmpty())
        assertTrue(ReasoningEfforts.cacheOptionsFor(ApiProviders.DEEPSEEK, "deepseek-flash").isEmpty())
    }
}
