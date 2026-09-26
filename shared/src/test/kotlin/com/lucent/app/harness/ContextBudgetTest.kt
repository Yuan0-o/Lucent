package com.lucent.app.harness

import com.lucent.app.network.ChatTurn
import com.lucent.app.network.ToolCallRequest
import com.lucent.app.network.ToolResultTurn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextBudgetTest {

    private fun filler(prefix: String, count: Int): String = prefix + "x".repeat(count)

    @Test
    fun aSmallHistoryPassesThroughUntouched() {
        val history = listOf(
            ChatTurn("user", "hello there"),
            ChatTurn("assistant", "hi, how can I help?"),
            ChatTurn("user", "what is on my list today?")
        )
        val result = ContextBudget.apply(history, "You are a helpful assistant.", 32000)
        assertEquals(history, result.turns)
        assertEquals("", result.summary)
        assertEquals(0, result.stats.turnsDropped)
        assertEquals(0, result.stats.toolResultsTrimmed)
        assertFalse(result.stats.changed)
        assertEquals(result.stats.tokensBefore, result.stats.tokensAfter)
    }

    @Test
    fun anOversizedHistoryKeepsTheNewestExchangeAndTheSystemPrompt() {
        val old = (1..30).flatMap { index ->
            listOf(
                ChatTurn("user", filler("old question $index ", 3000)),
                ChatTurn("assistant", filler("old answer $index ", 3000))
            )
        }
        val newest = listOf(
            ChatTurn("user", "the newest question"),
            ChatTurn("assistant", "the newest answer")
        )
        val history = old + newest
        val result = ContextBudget.apply(history, "system", 2000)
        assertEquals(58, result.stats.turnsDropped)
        assertEquals(newest, result.turns.takeLast(2))
        assertEquals("the newest question", result.turns[result.turns.size - 2].content)
        assertEquals("the newest answer", result.turns.last().content)
        assertEquals("system", result.turns.first().role)
        assertTrue(result.stats.tokensAfter < result.stats.tokensBefore)
    }

    @Test
    fun aHugeNewestExchangeIsKeptEvenWhenItBreaksTheBudgetOnItsOwn() {
        val big = "n".repeat(20000)
        val history = (1..8).map { ChatTurn("user", filler("old $it ", 2000)) } +
            listOf(ChatTurn("user", big))
        val result = ContextBudget.apply(history, "system", 2000)
        assertEquals(5, result.stats.turnsDropped)
        assertEquals(big, result.turns.last().content)
        assertTrue(result.stats.tokensAfter > 2000)
    }

    @Test
    fun aDroppedToolCallNeverLeavesAnOrphanedToolResult() {
        val history = listOf(
            ChatTurn("user", "x".repeat(20000)),
            ChatTurn("assistant", "tc1", toolCalls = listOf(ToolCallRequest("1", "list_notes", "{}"))),
            ChatTurn("tool", "", toolResults = listOf(ToolResultTurn("1", "list_notes", "ok"))),
            ChatTurn("user", "q1"),
            ChatTurn("assistant", "y".repeat(10000), toolCalls = listOf(ToolCallRequest("2", "read_note", "{}"))),
            ChatTurn("tool", "", toolResults = listOf(ToolResultTurn("2", "read_note", "ok"))),
            ChatTurn("user", "q2"),
            ChatTurn("assistant", "a2"),
            ChatTurn("assistant", "a3")
        )
        val result = ContextBudget.apply(history, "system", 2000)
        assertEquals(4, result.stats.turnsDropped)
        val body = result.turns.filter { it.role != "system" }
        assertEquals("assistant", body.first().role)
        assertEquals(listOf("2"), body.first().toolCalls.map { it.id })
    }

    @Test
    fun toolResultsAreTrimmedBeforeWholeTurnsAreDropped() {
        val toolBody = "y".repeat(6000)
        val history = listOf(
            ChatTurn("user", filler("q0 ", 1000)),
            ChatTurn("tool", "", toolResults = listOf(ToolResultTurn("1", "list_notes", toolBody))),
            ChatTurn("assistant", filler("a0 ", 1000)),
            ChatTurn("user", filler("q1 ", 1000)),
            ChatTurn("assistant", filler("a1 ", 1000)),
            ChatTurn("user", filler("q2 ", 1000)),
            ChatTurn("user", "the newest question"),
            ChatTurn("assistant", "the newest answer")
        )
        val result = ContextBudget.apply(history, "system", 2000)
        assertEquals(0, result.stats.turnsDropped)
        assertEquals(1, result.stats.toolResultsTrimmed)
        assertEquals(history.size, result.turns.size)
        assertEquals("q0 " + "x".repeat(1000), result.turns[0].content)
        val trimmed = result.turns[1].toolResults.single().content
        assertTrue(trimmed.endsWith("[trimmed]"), trimmed.takeLast(40))
        assertTrue(trimmed.length < toolBody.length)
        assertEquals("the newest answer", result.turns.last().content)
    }

    @Test
    fun theSummarySaysWhatWentAndWhatWasDecided() {
        val old = (1..30).flatMap { index ->
            listOf(
                ChatTurn("user", filler("old question $index ", 3000)),
                ChatTurn("assistant", filler("old answer $index ", 3000))
            )
        }
        val history = old + listOf(ChatTurn("user", "the newest question"))
        val result = ContextBudget.apply(history, "system", 2000)
        assertTrue(result.stats.turnsDropped > 0)
        assertTrue(result.summary.contains(result.stats.turnsDropped.toString()), result.summary)
        assertTrue(result.summary.contains("old question 1"), result.summary)
        assertTrue(result.summary.contains("old answer 1"), result.summary)
        assertTrue(result.summary.contains("user asked:"), result.summary)
        assertTrue(result.summary.contains("you replied:"), result.summary)
        assertTrue(result.summary.length <= ContextBudget.SUMMARY_MAX_CHARS)
        assertEquals(result.summary, result.turns.first().content)
        assertEquals("system", result.turns.first().role)
    }

    @Test
    fun theResultIsDeterministic() {
        val history = (1..12).flatMap { index ->
            listOf(
                ChatTurn("user", filler("ask $index ", 2000)),
                ChatTurn("tool", "", toolResults = listOf(ToolResultTurn("t$index", "read_note", "z".repeat(2000)))),
                ChatTurn("assistant", filler("say $index ", 2000))
            )
        } + listOf(ChatTurn("user", "final question"), ChatTurn("assistant", "final answer"))
        val first = ContextBudget.apply(history, "system prompt", 3000)
        val second = ContextBudget.apply(history.toList(), "system prompt", 3000)
        assertEquals(first, second)
        assertEquals(first.stats, second.stats)
        assertEquals(first.summary, second.summary)
        assertTrue(first.stats.changed)
    }
}
