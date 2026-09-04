package com.lucent.app.data

import android.os.SystemClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasswordAttemptsTest {

    @Test
    fun registerFailureClosesRoundAndLocks() {
        val limit = PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT
        var state = PasswordAttempts.State.EMPTY
        repeat(limit) { state = PasswordAttempts.registerFailure(state, limit, 3) }
        assertEquals(1, state.round)
        assertEquals(0, state.failuresThisRound)
        assertTrue(state.untilElapsed > 0L)
        assertTrue(PasswordAttempts.remainingLockoutMs(state) > 0L)
    }

    @Test
    fun successResetsEverything() {
        var state = PasswordAttempts.State.EMPTY
        repeat(8) { state = PasswordAttempts.registerFailure(state, 3, 3) }
        state = PasswordAttempts.registerSuccess()
        assertEquals(PasswordAttempts.State.EMPTY, state)
        assertEquals(0L, PasswordAttempts.remainingLockoutMs(state))
    }

    @Test
    fun remainingLockoutUsesElapsedClock() {
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val state = PasswordAttempts.State(
            untilWall = wall + 60_000L,
            untilElapsed = elapsed + 60_000L,
            bootStamp = wall - elapsed
        )
        val remaining = PasswordAttempts.remainingLockoutMs(state)
        assertTrue(remaining in 55_000L..60_000L, "remaining=$remaining")
    }

    @Test
    fun corruptStateFailsSafe() {
        val state = PasswordAttempts.State.fromJson("{ broken")
        assertTrue(state.round > 0)
        assertTrue(PasswordAttempts.remainingLockoutMs(state) > 0L)
    }

    @Test
    fun selfDestructRespectsEnabledFlag() {
        val hot = PasswordAttempts.State(lifetimeFailures = 10)
        assertTrue(PasswordAttempts.shouldSelfDestruct(hot, enabled = true, threshold = 10))
        assertTrue(!PasswordAttempts.shouldSelfDestruct(hot, enabled = false, threshold = 10))
        assertTrue(!PasswordAttempts.shouldSelfDestruct(hot, enabled = true, threshold = 11))
    }
}
