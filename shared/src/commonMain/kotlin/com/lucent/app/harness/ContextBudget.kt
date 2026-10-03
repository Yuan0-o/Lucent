package com.lucent.app.harness

import com.lucent.app.data.TokenEstimator
import com.lucent.app.network.ChatTurn
import com.lucent.app.network.ToolResultTurn

data class ContextBudgetStats(
    val tokensBefore: Int = 0,
    val tokensAfter: Int = 0,
    val turnsDropped: Int = 0,
    val toolResultsTrimmed: Int = 0
) {
    val changed: Boolean get() = turnsDropped > 0 || toolResultsTrimmed > 0
}

data class ContextBudgetResult(
    val turns: List<ChatTurn>,
    val summary: String = "",
    val stats: ContextBudgetStats = ContextBudgetStats()
)

object ContextBudget {

    const val DEFAULT_BUDGET_TOKENS = 32000
    const val MIN_BUDGET_TOKENS = 2000
    const val MAX_BUDGET_TOKENS = 200000
    const val PROTECTED_RECENT_TURNS = 4
    const val TOOL_RESULT_MAX_CHARS = 400
    const val SUMMARY_ENTRY_MAX_CHARS = 160
    const val SUMMARY_MAX_CHARS = 1600
    const val SUMMARY_MAX_ENTRIES = 8
    const val IMAGE_TOKENS = 1024
    const val TRIMMED_MARKER = " \u2026[trimmed]"

    private val WHITESPACE = Regex("\\s+")

    fun apply(
        history: List<ChatTurn>,
        systemPrompt: String,
        budgetTokens: Int
    ): ContextBudgetResult {
        val budget = budgetTokens.coerceIn(MIN_BUDGET_TOKENS, MAX_BUDGET_TOKENS)
        val tokensBefore = tokensOf(systemPrompt, history)
        if (tokensBefore <= budget) {
            return ContextBudgetResult(
                turns = history,
                summary = "",
                stats = ContextBudgetStats(tokensBefore, tokensBefore, 0, 0)
            )
        }
        val edge = droppableEnd(history)
        val available = (budget - TokenEstimator.estimate(systemPrompt)).coerceAtLeast(0)
        val trimmed = BooleanArray(history.size)
        val work = history.toMutableList()
        trimOldToolResults(work, edge, trimmed)
        var tokensAfter = tokensOf("", work)
        var dropCount = 0
        while (tokensAfter > available && dropCount < edge) {
            tokensAfter -= turnTokens(work[dropCount])
            dropCount++
        }
        val kept = if (dropCount == 0) work else work.drop(dropCount)
        val summary = if (dropCount == 0) "" else compress(history.take(dropCount))
        val withSummary = if (summary.isBlank()) kept else insertSummary(kept, summary)
        val stats = ContextBudgetStats(
            tokensBefore = tokensBefore,
            tokensAfter = tokensOf(systemPrompt, withSummary),
            turnsDropped = dropCount,
            toolResultsTrimmed = (dropCount until history.size).count { trimmed[it] }
        )
        return ContextBudgetResult(turns = withSummary, summary = summary, stats = stats)
    }

    private fun trimOldToolResults(
        work: MutableList<ChatTurn>,
        edge: Int,
        trimmed: BooleanArray
    ) {
        for (index in 0 until minOf(edge, work.size)) {
            val turn = work[index]
            if (turn.role != "tool" || turn.toolResults.isEmpty()) continue
            val reduced = turn.toolResults.map { shorten(it) }
            if (reduced == turn.toolResults) continue
            trimmed[index] = true
            work[index] = turn.copy(toolResults = reduced)
        }
    }

    private fun shorten(result: ToolResultTurn): ToolResultTurn {
        if (result.content.length <= TOOL_RESULT_MAX_CHARS) return result
        return result.copy(content = result.content.take(TOOL_RESULT_MAX_CHARS).trimEnd() + TRIMMED_MARKER)
    }

    private fun droppableEnd(history: List<ChatTurn>): Int {
        val firstSystem = history.indexOfFirst { it.role == "system" }
        val edge = if (firstSystem < 0) history.size else firstSystem
        return alignedStart(history, minOf(edge, protectedStart(history)).coerceAtLeast(0))
    }

    private fun alignedStart(history: List<ChatTurn>, start: Int): Int {
        var at = start
        while (at > 0 && history[at].role == "tool") at--
        return at
    }

    private fun protectedStart(history: List<ChatTurn>): Int {
        val recent = (history.size - PROTECTED_RECENT_TURNS).coerceAtLeast(0)
        val lastUser = history.indexOfLast { it.role == "user" }
        return if (lastUser < 0) recent else minOf(recent, lastUser)
    }

    private fun tokensOf(systemPrompt: String, turns: List<ChatTurn>): Int {
        var total = TokenEstimator.estimate(systemPrompt)
        turns.forEach { total += turnTokens(it) }
        return total
    }

    private fun turnTokens(turn: ChatTurn): Int {
        var total = TokenEstimator.estimate(turn.content) + TokenEstimator.estimate(turn.reasoningContent)
        turn.toolCalls.forEach {
            total += TokenEstimator.estimate(it.name) + TokenEstimator.estimate(it.argumentsJson)
        }
        turn.toolResults.forEach {
            total += TokenEstimator.estimate(it.name) + TokenEstimator.estimate(it.content)
        }
        if (!turn.attachmentData.isNullOrBlank()) total += IMAGE_TOKENS
        return total
    }

    private fun compress(dropped: List<ChatTurn>): String {
        val entries = ArrayList<String>()
        for (turn in dropped) {
            if (entries.size >= SUMMARY_MAX_ENTRIES) break
            val body = compact(bodyOf(turn))
            if (body.isBlank()) continue
            entries.add(rolePrefix(turn.role) + body)
        }
        val head = "Earlier conversation was compressed to fit the context budget: " +
            dropped.size + " older turn(s) were dropped."
        val tail = " Treat this as settled background; do not ask the user to repeat it."
        if (entries.isEmpty()) return head + tail
        val body = head + " What they covered - " + entries.joinToString("; ") + "." + tail
        return body.take(SUMMARY_MAX_CHARS)
    }

    private fun bodyOf(turn: ChatTurn): String {
        val parts = ArrayList<String>(3)
        if (turn.content.isNotBlank()) parts.add(turn.content)
        turn.toolCalls.forEach { parts.add("called " + it.name) }
        turn.toolResults.forEach { parts.add(toolName(it) + ": " + it.content) }
        return parts.joinToString(" ")
    }

    private fun toolName(result: ToolResultTurn): String = result.name.ifBlank { "tool" }

    private fun rolePrefix(role: String): String = when (role) {
        "user" -> "user asked: "
        "assistant" -> "you replied: "
        "tool" -> "tool output: "
        "system" -> "system note: "
        else -> "context: "
    }

    private fun compact(text: String): String {
        val flat = WHITESPACE.replace(text, " ").trim()
        if (flat.length <= SUMMARY_ENTRY_MAX_CHARS) return flat
        return flat.take(SUMMARY_ENTRY_MAX_CHARS - 1).trimEnd() + "\u2026"
    }

    private fun insertSummary(turns: List<ChatTurn>, summary: String): List<ChatTurn> {
        var at = 0
        while (at < turns.size && turns[at].role == "system") at++
        val out = ArrayList<ChatTurn>(turns.size + 1)
        out.addAll(turns.subList(0, at))
        out.add(ChatTurn(role = "system", content = summary))
        out.addAll(turns.subList(at, turns.size))
        return out
    }
}
