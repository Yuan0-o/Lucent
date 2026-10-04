package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private class GoalHost(private val root: File) : HarnessHost {
    override val android: Boolean = false
    override fun defaultWorkspace(): File = File(root, "workspace").apply { mkdirs() }
    override fun filesDir(): File = File(root, "files").apply { mkdirs() }
    override fun cacheDir(): File = File(root, "cache").apply { mkdirs() }
}

class GoalToolsTest {

    private fun sandbox(block: (HarnessCtx) -> Unit) {
        val root = java.nio.file.Files.createTempDirectory("lucent-goals").toFile()
        val previousHost = HarnessRuntime.host
        val previousConfig = HarnessRuntime.config()
        val previousConversation = HarnessRuntime.conversationId
        HarnessRuntime.host = GoalHost(root)
        HarnessRuntime.android = false
        HarnessRuntime.conversationId = 42L
        HarnessRuntime.update(HarnessConfig(enabled = true))
        GoalStore.clear()
        try {
            HarnessRuntime.workspace().mkdirs()
            val ctx = HarnessCtx(
                harnessTestContext(),
                null,
                HarnessRuntime.config(),
                emptySet(),
                false,
                HarnessRuntime.workspace()
            )
            block(ctx)
        } finally {
            GoalStore.clear()
            HarnessRuntime.conversationId = previousConversation
            HarnessRuntime.host = previousHost
            HarnessRuntime.update(previousConfig)
            root.deleteRecursively()
        }
    }

    private fun create(ctx: HarnessCtx, objective: String, limit: Int? = null): GoalState {
        val args = JSONObject().put("objective", objective)
        if (limit != null) args.put("max_goal_rounds", limit)
        val result = runBlocking { assertNotNull(GoalTools.execute(ctx, "create_goal", args)) as ToolExecResult }
        assertTrue(result.success, result.summary)
        return assertNotNull(GoalStore.current())
    }

    private fun createResult(ctx: HarnessCtx, objective: String): ToolExecResult {
        val args = JSONObject().put("objective", objective)
        return runBlocking { assertNotNull(GoalTools.execute(ctx, "create_goal", args)) as ToolExecResult }
    }

    private fun update(
        ctx: HarnessCtx,
        goal: GoalState,
        action: String,
        revision: Int = goal.revision,
        reason: String? = null
    ): ToolExecResult {
        val args = JSONObject().put("goal_id", goal.id).put("revision", revision).put("action", action)
        if (reason != null) args.put("blocked_reason", reason)
        return runBlocking { assertNotNull(GoalTools.execute(ctx, "update_goal", args)) as ToolExecResult }
    }

    private fun status(ctx: HarnessCtx): ToolExecResult =
        runBlocking { assertNotNull(GoalTools.execute(ctx, "get_goal", JSONObject())) as ToolExecResult }

    private fun beginRound(ctx: HarnessCtx): GoalState =
        assertNotNull(GoalStore.beginRound(ctx.context, 42L, automatic = true))

    @Test
    fun theGoalToolsAreNamedAfterTheHarnessOnes() {
        assertEquals(listOf("create_goal", "get_goal", "update_goal"), GoalTools.tools.map { it.name })
        assertEquals(HarnessGroup.PLAN, GoalTools.group)
        assertTrue(GoalTools.tools.all { it.description.length > 30 })
    }

    @Test
    fun creatingAGoalRefusesASecondWhileTheFirstIsStillOpen() = sandbox { ctx ->
        val first = create(ctx, "get the release notes out")
        assertEquals(GoalPhase.ACTIVE, first.phase)
        assertEquals(1, first.roundsStarted)
        assertEquals(GoalStore.DEFAULT_ROUNDS, first.maxRounds)

        val refused = createResult(ctx, "start something else entirely")
        assertFalse(refused.success, refused.summary)
        assertEquals(first.id, assertNotNull(GoalStore.current()).id)

        assertTrue(update(ctx, first, "complete").success)
        val replacement = create(ctx, "start something else entirely")
        assertTrue(replacement.id != first.id)
        assertEquals(GoalPhase.ACTIVE, assertNotNull(GoalStore.current()).phase)
    }

    @Test
    fun pauseAndResumeFlipThePhaseAndTheRoundGate() = sandbox { ctx ->
        val goal = create(ctx, "finish the migration")
        val paused = update(ctx, goal, "pause")
        assertTrue(paused.success, paused.summary)
        val held = assertNotNull(GoalStore.current())
        assertEquals(GoalPhase.PAUSED, held.phase)
        assertFalse(held.armed)
        assertNull(GoalStore.beginRound(ctx.context, 42L, automatic = true))

        val resumed = update(ctx, held, "resume")
        assertTrue(resumed.success, resumed.summary)
        val open = assertNotNull(GoalStore.current())
        assertEquals(GoalPhase.ACTIVE, open.phase)
        assertTrue(open.armed)
        assertEquals(2, beginRound(ctx).roundsStarted)
        assertFalse(update(ctx, assertNotNull(GoalStore.current()), "resume").success)
    }

    @Test
    fun completingAGoalStopsEveryFurtherRound() = sandbox { ctx ->
        val goal = create(ctx, "write the report")
        assertTrue(update(ctx, goal, "complete").success)
        val done = assertNotNull(GoalStore.current())
        assertEquals(GoalPhase.COMPLETE, done.phase)
        assertFalse(done.armed)
        assertNull(GoalStore.beginRound(ctx.context, 42L, automatic = true))
        assertFalse(update(ctx, done, "complete").success)
        assertFalse(update(ctx, done, "resume").success)
    }

    @Test
    fun aStaleRevisionOrAForeignIdIsRefused() = sandbox { ctx ->
        val goal = create(ctx, "tidy the workspace")
        assertTrue(update(ctx, goal, "pause").success)
        val stale = update(ctx, goal, "resume")
        assertFalse(stale.success, stale.summary)
        assertTrue(stale.summary.contains("stale"), stale.summary)
        assertEquals(GoalPhase.PAUSED, assertNotNull(GoalStore.current()).phase)

        val foreign = runBlocking {
            assertNotNull(
                GoalTools.execute(
                    ctx,
                    "update_goal",
                    JSONObject().put("goal_id", "goal-not-this-one").put("revision", 2).put("action", "resume")
                )
            ) as ToolExecResult
        }
        assertFalse(foreign.success, foreign.summary)
        assertEquals(GoalPhase.PAUSED, assertNotNull(GoalStore.current()).phase)
    }

    @Test
    fun blockedWaitsForThreeConsecutiveRoundsWithTheSameBlocker() = sandbox { ctx ->
        val goal = create(ctx, "ship the release")
        val early = update(ctx, goal, "blocked", reason = "the signing key is missing")
        assertFalse(early.success, early.summary)
        assertEquals(GoalPhase.ACTIVE, assertNotNull(GoalStore.current()).phase)

        beginRound(ctx)
        val first = update(ctx, goal, "blocked", reason = "the signing key is missing")
        assertFalse(first.success, first.summary)
        assertTrue(first.summary.contains("1 of 3"), first.summary)

        beginRound(ctx)
        val second = update(ctx, goal, "blocked", reason = "the signing key is missing")
        assertFalse(second.success, second.summary)
        assertTrue(second.summary.contains("2 of 3"), second.summary)

        beginRound(ctx)
        val accepted = update(ctx, goal, "blocked", reason = "the signing key is missing")
        assertTrue(accepted.success, accepted.summary)
        val blocked = assertNotNull(GoalStore.current())
        assertEquals(GoalPhase.BLOCKED, blocked.phase)
        assertEquals("the signing key is missing", blocked.blockerReason)
        assertFalse(blocked.armed)
        assertNull(GoalStore.beginRound(ctx.context, 42L, automatic = true))
    }

    @Test
    fun aRoundWithoutTheBlockerBreaksTheStreak() = sandbox { ctx ->
        val goal = create(ctx, "fix the pipeline", limit = 10)
        beginRound(ctx)
        assertTrue(!update(ctx, goal, "blocked", reason = "flaky runner").success)
        beginRound(ctx)
        beginRound(ctx)
        val afterGap = update(ctx, goal, "blocked", reason = "flaky runner")
        assertFalse(afterGap.success, afterGap.summary)
        assertTrue(afterGap.summary.contains("1 of 3"), afterGap.summary)
    }

    @Test
    fun theRoundLimitStopsContinuationAndShowsInGetGoal() = sandbox { ctx ->
        val goal = create(ctx, "run the full sweep", limit = 2)
        assertEquals(2, goal.maxRounds)
        assertEquals(2, beginRound(ctx).roundsStarted)
        assertNull(GoalStore.beginRound(ctx.context, 42L, automatic = true))
        assertFalse(assertNotNull(GoalStore.current()).armed)

        val reported = status(ctx)
        assertTrue(reported.summary.contains("round limit: 2"), reported.summary)
        assertTrue(reported.summary.contains("continuation armed: no"), reported.summary)
        assertTrue(reported.summary.contains("phase: active"), reported.summary)
        assertTrue(reported.summary.contains(goal.id), reported.summary)
    }

    @Test
    fun theSummaryCarriesTheObjectiveAndTheRoundCount() = sandbox { ctx ->
        val goal = create(ctx, "keep the workspace readable", limit = 6)
        beginRound(ctx)
        val summary = GoalStore.summary()
        assertTrue(summary.contains(goal.id), summary)
        assertTrue(summary.contains("round 2 of 6"), summary)
        assertTrue(summary.contains("keep the workspace readable"), summary)
        assertTrue(HarnessPrompt.goalLine()?.contains("Working towards") == true)
    }
}
