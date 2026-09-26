package com.lucent.app.harness

import android.content.Context
import com.lucent.app.network.ToolExecResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class GoalPhase(val key: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    COMPLETE("complete"),
    BLOCKED("blocked");

    companion object {
        fun of(key: String): GoalPhase = entries.firstOrNull { it.key == key } ?: ACTIVE
    }
}

data class GoalState(
    val id: String,
    val revision: Int = 1,
    val objective: String,
    val phase: GoalPhase = GoalPhase.ACTIVE,
    val roundsStarted: Int = 1,
    val maxRounds: Int = GoalStore.DEFAULT_ROUNDS,
    val blockerReason: String = "",
    val blockerStreak: Int = 0,
    val blockerRound: Int = 0,
    val roundLog: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val active: Boolean get() = phase == GoalPhase.ACTIVE

    val finished: Boolean get() = phase == GoalPhase.COMPLETE || phase == GoalPhase.BLOCKED

    val armed: Boolean get() = active && roundsStarted < maxRounds
}

object GoalTools : HarnessGroupTools {

    override val group = HarnessGroup.PLAN

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "create_goal",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Set the one durable goal for this conversation, exactly like the DeepSeek Harness " +
                "create_goal: an objective plus an optional max_goal_rounds. Once the goal exists the app starts " +
                "another round on its own whenever a turn finishes, until the goal is complete, paused or blocked, " +
                "so write an objective that can be worked towards without the person prompting again. Creating a goal " +
                "while another one is still active or paused is refused.",
            params = listOf(
                HarnessSchema.text("objective", "What this goal is working towards"),
                HarnessSchema.number("max_goal_rounds", "Most rounds the goal may run, default 8", false)
            )
        ),
        HarnessTool(
            name = "get_goal",
            group = group,
            permission = HarnessPermission.READ,
            description = "Read the goal for this conversation: its id, revision, objective, phase (active, paused, " +
                "complete or blocked), rounds started, round limit, blocker reason and whether another continuation " +
                "round is armed. Call it at the start of a continuation round to see what is still owed."
        ),
        HarnessTool(
            name = "update_goal",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Change the goal for this conversation. Pass the goal_id and revision from get_goal; a stale " +
                "revision is refused. action is edit (only when the person has directly asked for the change, with a " +
                "new objective or max_goal_rounds), pause, resume, complete, or blocked with a concrete " +
                "blocked_reason. blocked is only accepted during an automatic continuation round and only once the " +
                "same blocker has been reported in three consecutive rounds.",
            params = listOf(
                HarnessSchema.text("goal_id", "Goal id from get_goal"),
                HarnessSchema.number("revision", "Revision from get_goal"),
                HarnessSchema.text("action", "edit, pause, resume, complete or blocked"),
                HarnessSchema.text("objective", "New objective for action edit", false),
                HarnessSchema.number("max_goal_rounds", "New round limit for action edit", false),
                HarnessSchema.text("blocked_reason", "The concrete blocker for action blocked", false)
            )
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = when (name) {
        "create_goal" -> create(ctx, args)
        "get_goal" -> get(ctx, args)
        "update_goal" -> update(ctx, args)
        else -> null
    }

    private fun create(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val objective = args.optString("objective", "").trim()
        if (objective.isEmpty()) return ToolExecResult("Give me the objective for the goal.", success = false)
        val conversation = conversationOf(args)
        GoalStore.load(ctx.context, conversation)
        val existing = GoalStore.current()
        if (existing != null && !existing.finished) {
            return ToolExecResult(
                "There is already a goal in this conversation (${existing.id}, ${existing.phase.key}). Finish it, " +
                    "pause it, or mark it complete or blocked before creating another one.",
                success = false
            )
        }
        val limit = GoalStore.roundLimit(goalInt(args, "max_goal_rounds", GoalStore.DEFAULT_ROUNDS))
        val goal = GoalStore.create(ctx.context, conversation, objective, limit)
        return ToolExecResult(
            "Goal ${goal.id} created at revision ${goal.revision}, round 1 of ${goal.maxRounds}. Automatic rounds " +
                "will keep it moving until it is complete, paused or blocked.\n" + GoalStore.render(goal)
        )
    }

    private fun get(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val conversation = conversationOf(args)
        GoalStore.load(ctx.context, conversation)
        val goal = GoalStore.current() ?: return ToolExecResult("No goal is set for this conversation yet.")
        return ToolExecResult(GoalStore.render(goal))
    }

    private fun update(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val conversation = conversationOf(args)
        GoalStore.load(ctx.context, conversation)
        val goal = GoalStore.current() ?: return ToolExecResult("There is no goal to update.", success = false)
        val stated = args.optString("goal_id", "").trim()
        if (stated.isEmpty()) return ToolExecResult("Pass the goal_id from get_goal.", success = false)
        if (stated != goal.id) {
            return ToolExecResult("$stated is not the goal for this conversation (${goal.id}).", success = false)
        }
        val revision = goalInt(args, "revision", -1)
        if (revision != goal.revision) {
            return ToolExecResult(
                "Revision $revision is stale: the goal is at revision ${goal.revision}. Call get_goal and retry.",
                success = false
            )
        }
        return when (args.optString("action", "").trim().lowercase()) {
            "edit" -> edit(ctx, conversation, goal, args)
            "pause" -> pause(ctx, conversation, goal)
            "resume" -> resume(ctx, conversation, goal)
            "complete" -> complete(ctx, conversation, goal)
            "blocked" -> blocked(ctx, conversation, goal, args)
            else -> ToolExecResult("action must be edit, pause, resume, complete or blocked.", success = false)
        }
    }

    private fun edit(
        ctx: HarnessCtx,
        conversation: Long,
        goal: GoalState,
        args: JSONObject
    ): ToolExecResult {
        if (GoalStore.roundIsAutomatic(conversation)) {
            return ToolExecResult(
                "edit needs a direct request from the person, and this is an automatic continuation round. Ask " +
                    "the person to confirm the new objective before changing it.",
                success = false
            )
        }
        val objective = args.optString("objective", "").trim()
        val requested = goalInt(args, "max_goal_rounds", 0)
        if (objective.isEmpty() && requested <= 0) {
            return ToolExecResult("Give me a new objective or a new max_goal_rounds.", success = false)
        }
        val next = goal.copy(
            objective = objective.ifEmpty { goal.objective },
            maxRounds = if (requested > 0) GoalStore.roundLimit(requested) else goal.maxRounds,
            revision = goal.revision + 1,
            updatedAt = System.currentTimeMillis()
        )
        GoalStore.commit(ctx.context, conversation, next)
        return ToolExecResult("Goal ${next.id} updated to revision ${next.revision}.\n" + GoalStore.render(next))
    }

    private fun pause(ctx: HarnessCtx, conversation: Long, goal: GoalState): ToolExecResult {
        if (!goal.active) {
            return ToolExecResult("The goal is ${goal.phase.key}, so there is nothing to pause.", success = false)
        }
        val next = goal.copy(
            phase = GoalPhase.PAUSED,
            revision = goal.revision + 1,
            updatedAt = System.currentTimeMillis()
        )
        GoalStore.endRound()
        GoalStore.commit(ctx.context, conversation, next)
        return ToolExecResult(
            "Goal ${next.id} paused after round ${next.roundsStarted}; no further round starts until it is resumed."
        )
    }

    private fun resume(ctx: HarnessCtx, conversation: Long, goal: GoalState): ToolExecResult {
        if (goal.active) return ToolExecResult("The goal is already active.", success = false)
        if (goal.phase == GoalPhase.COMPLETE) {
            return ToolExecResult("A completed goal cannot be resumed; create a new goal instead.", success = false)
        }
        val next = goal.copy(
            phase = GoalPhase.ACTIVE,
            blockerStreak = 0,
            blockerRound = 0,
            revision = goal.revision + 1,
            updatedAt = System.currentTimeMillis()
        )
        GoalStore.commit(ctx.context, conversation, next)
        return ToolExecResult(
            "Goal ${next.id} resumed at round ${next.roundsStarted} of ${next.maxRounds}."
        )
    }

    private fun complete(ctx: HarnessCtx, conversation: Long, goal: GoalState): ToolExecResult {
        if (goal.phase == GoalPhase.COMPLETE) {
            return ToolExecResult("The goal is already complete.", success = false)
        }
        val next = goal.copy(
            phase = GoalPhase.COMPLETE,
            revision = goal.revision + 1,
            updatedAt = System.currentTimeMillis()
        )
        GoalStore.endRound()
        GoalStore.commit(ctx.context, conversation, next)
        return ToolExecResult(
            "Goal ${next.id} complete after ${next.roundsStarted} round(s). No further round will start."
        )
    }

    private fun blocked(
        ctx: HarnessCtx,
        conversation: Long,
        goal: GoalState,
        args: JSONObject
    ): ToolExecResult {
        val reason = args.optString("blocked_reason", "").trim()
        if (reason.isEmpty()) {
            return ToolExecResult("blocked needs a concrete blocked_reason.", success = false)
        }
        if (!goal.active) {
            return ToolExecResult("The goal is ${goal.phase.key}, so it cannot be blocked.", success = false)
        }
        if (!GoalStore.roundIsAutomatic(conversation)) {
            return ToolExecResult(
                "A goal can only be marked blocked during an automatic continuation round; this round came from " +
                    "the person.",
                success = false
            )
        }
        val report = GoalStore.reportBlocker(ctx.context, conversation, reason)
        if (!report.accepted) return ToolExecResult(report.message, success = false)
        return ToolExecResult(report.message)
    }

    private fun conversationOf(args: JSONObject): Long {
        val stated = args.optLong("conversation_id", 0L)
        if (stated > 0L) return stated
        val current = HarnessRuntime.conversationId
        return if (current > 0L) current else 1L
    }
}

object GoalStore {

    const val DEFAULT_ROUNDS = 8
    const val MIN_ROUNDS = 1
    const val MAX_ROUNDS = 50
    const val MIN_BLOCKER_ROUNDS = 3

    private const val LOG_LINES = 10
    private const val LOG_CHARS = 240
    private const val SUMMARY_CHARS = 220

    private val changeFlow = MutableStateFlow(0L)

    @Volatile private var goal: GoalState? = null

    @Volatile private var loadedConversation: Long = -1L

    @Volatile private var automaticRound: Boolean = false

    @Volatile private var automaticFor: Long = -1L

    @Volatile private var roundNote: String? = null

    val changes: StateFlow<Long> = changeFlow.asStateFlow()

    fun current(): GoalState? = goal

    fun loadedFor(): Long = loadedConversation

    fun roundLimit(requested: Int): Int = requested.coerceIn(MIN_ROUNDS, MAX_ROUNDS)

    fun load(context: Context, conversationId: Long) {
        val id = conversationId.coerceAtLeast(1L)
        if (loadedConversation == id) return
        goal = GoalFiles.read(context, id)
        loadedConversation = id
        publish()
    }

    fun clear() {
        goal = null
        loadedConversation = -1L
        clearAutomatic()
        roundNote = null
        publish()
    }

    fun create(context: Context, conversationId: Long, objective: String, maxRounds: Int): GoalState {
        val now = System.currentTimeMillis()
        val fresh = GoalState(
            id = "goal-" + java.util.UUID.randomUUID().toString().replace("-", "").take(10),
            revision = 1,
            objective = objective,
            phase = GoalPhase.ACTIVE,
            roundsStarted = 1,
            maxRounds = roundLimit(maxRounds),
            createdAt = now,
            updatedAt = now
        )
        clearAutomatic()
        commit(context, conversationId, fresh)
        return fresh
    }

    fun commit(context: Context, conversationId: Long, state: GoalState) {
        goal = state
        loadedConversation = conversationId.coerceAtLeast(1L)
        GoalFiles.write(context, conversationId, state)
        publish()
    }

    fun beginRound(context: Context, conversationId: Long, automatic: Boolean): GoalState? {
        val current = goal ?: return null
        if (loadedConversation != conversationId.coerceAtLeast(1L)) return null
        if (!current.active) return null
        if (current.roundsStarted >= current.maxRounds) return null
        val stale = current.blockerStreak > 0 && current.blockerRound < current.roundsStarted
        val base = if (stale) current.copy(blockerReason = "", blockerStreak = 0, blockerRound = 0) else current
        val next = base.copy(roundsStarted = base.roundsStarted + 1, updatedAt = System.currentTimeMillis())
        automaticRound = automatic
        automaticFor = if (automatic) conversationId.coerceAtLeast(1L) else -1L
        commit(context, conversationId, next)
        return next
    }

    fun endRound() {
        clearAutomatic()
    }

    fun roundIsAutomatic(conversationId: Long): Boolean =
        automaticRound && automaticFor == conversationId.coerceAtLeast(1L)

    private fun clearAutomatic() {
        automaticRound = false
        automaticFor = -1L
    }

    fun setRoundNote(text: String) {
        roundNote = text
    }

    fun takeRoundNote(): String? {
        val note = roundNote
        roundNote = null
        return note
    }

    fun logRound(context: Context, conversationId: Long, line: String) {
        val current = goal ?: return
        if (loadedConversation != conversationId.coerceAtLeast(1L)) return
        val entry = line.replace(Regex("\\s+"), " ").trim().take(LOG_CHARS)
        if (entry.isEmpty()) return
        commit(context, conversationId, current.copy(roundLog = (current.roundLog + entry).takeLast(LOG_LINES)))
    }

    data class BlockerReport(val accepted: Boolean, val streak: Int, val message: String)

    fun reportBlocker(context: Context, conversationId: Long, reason: String): BlockerReport {
        val current = goal ?: return BlockerReport(false, 0, "There is no goal to block.")
        val clean = reason.trim()
        if (clean.isEmpty()) return BlockerReport(false, 0, "blocked needs a concrete blocked_reason.")
        val same = clean.equals(current.blockerReason, ignoreCase = true)
        if (same && current.blockerRound == current.roundsStarted) {
            return BlockerReport(
                false,
                current.blockerStreak,
                "That blocker is already recorded for round ${current.roundsStarted}. Report it again in the next " +
                    "round if it still stops you."
            )
        }
        val streak = if (same) current.blockerStreak + 1 else 1
        val accepted = streak >= MIN_BLOCKER_ROUNDS
        val next = current.copy(
            phase = if (accepted) GoalPhase.BLOCKED else current.phase,
            blockerReason = clean,
            blockerStreak = streak,
            blockerRound = current.roundsStarted,
            revision = if (accepted) current.revision + 1 else current.revision,
            updatedAt = System.currentTimeMillis()
        )
        commit(context, conversationId, next)
        val message = if (accepted) {
            "Goal ${next.id} blocked after $streak consecutive rounds with the same blocker: $clean"
        } else {
            "Not blocked yet: the same blocker has now been reported for $streak of $MIN_BLOCKER_ROUNDS consecutive " +
                "rounds. Keep working, or report it again next round if it still stops you."
        }
        return BlockerReport(accepted, streak, message)
    }

    fun pause(context: Context, conversationId: Long): GoalState? {
        val current = goal ?: return null
        if (!current.active) return current
        clearAutomatic()
        val next = current.copy(
            phase = GoalPhase.PAUSED,
            revision = current.revision + 1,
            updatedAt = System.currentTimeMillis()
        )
        commit(context, conversationId, next)
        return next
    }

    fun resume(context: Context, conversationId: Long): GoalState? {
        val current = goal ?: return null
        if (current.active || current.phase == GoalPhase.COMPLETE) return current
        val next = current.copy(
            phase = GoalPhase.ACTIVE,
            blockerStreak = 0,
            blockerRound = 0,
            revision = current.revision + 1,
            updatedAt = System.currentTimeMillis()
        )
        commit(context, conversationId, next)
        return next
    }

    fun complete(context: Context, conversationId: Long): GoalState? {
        val current = goal ?: return null
        if (current.phase == GoalPhase.COMPLETE) return current
        clearAutomatic()
        val next = current.copy(
            phase = GoalPhase.COMPLETE,
            revision = current.revision + 1,
            updatedAt = System.currentTimeMillis()
        )
        commit(context, conversationId, next)
        return next
    }

    fun render(state: GoalState): String = buildString {
        append("goal_id: ").append(state.id).append('\n')
        append("revision: ").append(state.revision).append('\n')
        append("phase: ").append(state.phase.key).append('\n')
        append("objective: ").append(state.objective).append('\n')
        append("rounds started: ").append(state.roundsStarted).append('\n')
        append("round limit: ").append(state.maxRounds).append('\n')
        append("blocker reason: ").append(state.blockerReason.ifBlank { "none" }).append('\n')
        append("rounds with the same blocker: ").append(state.blockerStreak).append('\n')
        append("continuation armed: ").append(if (state.armed) "yes" else "no")
    }

    fun summary(): String {
        val state = goal ?: return ""
        val armed = if (state.armed) "armed" else "not armed"
        return "${state.id} (${state.phase.key}, round ${state.roundsStarted} of ${state.maxRounds}, " +
            "revision ${state.revision}, $armed): " + state.objective.take(SUMMARY_CHARS)
    }

    fun recentRounds(count: Int): List<String> {
        val state = goal ?: return emptyList()
        return state.roundLog.takeLast(count.coerceAtLeast(0))
    }

    private fun publish() {
        changeFlow.value = changeFlow.value + 1L
    }
}

object GoalFiles {

    fun fileFor(conversationId: Long): File =
        File(HarnessRuntime.subDir("goals"), "conv-" + conversationId.coerceAtLeast(1L) + ".json")

    fun read(context: Context, conversationId: Long): GoalState? {
        val text = HarnessVault.read(context, fileFor(conversationId))
        if (text.isBlank()) return null
        return try {
            parse(JSONObject(text))
        } catch (t: Throwable) {
            null
        }
    }

    fun write(context: Context, conversationId: Long, state: GoalState) {
        HarnessVault.write(context, fileFor(conversationId), toJson(state).toString())
    }

    private fun toJson(state: GoalState): JSONObject = JSONObject().apply {
        put("id", state.id)
        put("revision", state.revision)
        put("objective", state.objective)
        put("phase", state.phase.key)
        put("roundsStarted", state.roundsStarted)
        put("maxRounds", state.maxRounds)
        put("blockerReason", state.blockerReason)
        put("blockerStreak", state.blockerStreak)
        put("blockerRound", state.blockerRound)
        put("roundLog", JSONArray(state.roundLog))
        put("createdAt", state.createdAt)
        put("updatedAt", state.updatedAt)
    }

    private fun parse(json: JSONObject): GoalState? {
        val id = json.optString("id", "").trim()
        val objective = json.optString("objective", "").trim()
        if (id.isEmpty() || objective.isEmpty()) return null
        val log = mutableListOf<String>()
        json.optJSONArray("roundLog")?.let { array ->
            for (i in 0 until array.length()) {
                val line = array.optString(i, "")
                if (line.isNotBlank()) log.add(line)
            }
        }
        return GoalState(
            id = id,
            revision = json.optInt("revision", 1),
            objective = objective,
            phase = GoalPhase.of(json.optString("phase", GoalPhase.ACTIVE.key)),
            roundsStarted = json.optInt("roundsStarted", 1),
            maxRounds = GoalStore.roundLimit(json.optInt("maxRounds", GoalStore.DEFAULT_ROUNDS)),
            blockerReason = json.optString("blockerReason", ""),
            blockerStreak = json.optInt("blockerStreak", 0),
            blockerRound = json.optInt("blockerRound", 0),
            roundLog = log,
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L)
        )
    }
}

internal fun goalInt(args: JSONObject, key: String, fallback: Int): Int {
    val raw = args.opt(key) ?: return fallback
    return when (raw) {
        is Number -> raw.toInt()
        is String -> raw.trim().toIntOrNull() ?: fallback
        else -> fallback
    }
}
