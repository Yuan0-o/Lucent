package com.lucent.app.assistant.prompts

import com.lucent.app.data.MemoryTier
import com.lucent.app.network.ToolParam
import com.lucent.app.network.ToolDefinition
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemPromptsTest {

    private val tools = listOf(
        ToolDefinition(
            name = "create_task",
            description = "Create a new task. Pass the title, an optional due date and a priority.",
            params = listOf(ToolParam("title", "string", "The task title", required = true))
        )
    )

    @Test
    fun localPromptNamesToolsAndJsonShape() {
        val p = SystemPrompts.local(tools, userText = "hello", compact = false)
        assertTrue(p.contains("create_task"))
        assertTrue(p.contains("\"tool\""))
        assertTrue(p.contains("Result of <tool>:"))
        assertTrue(p.contains("Lucent"))
    }

    @Test
    fun localCompactPromptTrimsDescriptions() {
        val p = SystemPrompts.local(tools, userText = "hello", compact = true)
        assertFalse(p.contains("Create a new task. Pass the title, an optional due date"))
        assertTrue(p.contains("Create a new task"))
    }

    @Test
    fun localPromptOrdersReplyInUserLanguage() {
        val en = SystemPrompts.local(tools, userText = "hello", compact = false)
        val zh = SystemPrompts.local(tools, userText = "你好", compact = false)
        assertTrue(zh != en)
    }

    @Test
    fun fullPromptCarriesCoreRules() {
        val p = SystemPrompts.full(
            name = "Lucent", style = "", tier = MemoryTier.MEDIUM,
            webSearchEnabled = false, crossMemory = "", userText = "hello"
        )
        assertTrue(p.contains("LANGUAGE RULE"))
        assertTrue(p.contains("plain, natural, everyday language"))
        assertTrue(p.contains("Never use asterisks"))
        assertTrue(p.contains("NOTES are pieces of information"))
        assertTrue(p.contains("TASKS are actionable to-do items"))
        assertTrue(p.contains("You do NOT have web access"))
    }

    @Test
    fun fullPromptEnablesWebSearchWhenConfigured() {
        val on = SystemPrompts.full(
            name = "Lucent", style = "", tier = MemoryTier.MEDIUM,
            webSearchEnabled = true, crossMemory = "", userText = "hello"
        )
        val off = SystemPrompts.full(
            name = "Lucent", style = "", tier = MemoryTier.LOW,
            webSearchEnabled = false, crossMemory = "", userText = "hello"
        )
        assertTrue(on.contains("call the web_search tool"))
        assertFalse(off.contains("call the web_search tool"))
        assertTrue(on.contains("web_search"))
    }

    @Test
    fun fullPromptLaysOutMemoryTierRules() {
        val low = SystemPrompts.full(
            name = "Lucent", style = "", tier = MemoryTier.LOW,
            webSearchEnabled = false, crossMemory = "", userText = "hello"
        )
        assertTrue(low.contains("single-turn"))
        val high = SystemPrompts.full(
            name = "Lucent", style = "", tier = MemoryTier.HIGH,
            webSearchEnabled = false, crossMemory = "digest", userText = "hello"
        )
        assertTrue(high.contains("cross-conversation memory"))
        assertTrue(high.contains("digest"))
    }

    @Test
    fun compactPromptIsSubstantiallyShorterAndKeyed() {
        val full = SystemPrompts.full(
            name = "Lucent", style = "", tier = MemoryTier.LOW,
            webSearchEnabled = false, crossMemory = "", userText = "hello"
        )
        val compact = SystemPrompts.compact(
            name = "Lucent", style = "", tier = MemoryTier.LOW,
            webSearchEnabled = false, userText = "hello", crossMemory = ""
        )
        assertTrue(compact.length < full.length)
        assertTrue(compact.contains("No markdown"))
        assertTrue(compact.contains("NOTES are things to remember"))
    }

    @Test
    fun promptsEmbedCurrentDate() {
        val p = SystemPrompts.full(
            name = "Lucent", style = "", tier = MemoryTier.MEDIUM,
            webSearchEnabled = false, crossMemory = "", userText = "hello"
        )
        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}").containsMatchIn(p))
    }
}
