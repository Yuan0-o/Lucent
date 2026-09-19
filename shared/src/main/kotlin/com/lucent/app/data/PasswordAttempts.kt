package com.lucent.app.data

import android.os.SystemClock
import org.json.JSONObject

object PasswordAttempts {

    const val DEFAULT_FIRST_ROUND_LIMIT = 5
    const val DEFAULT_LATER_ROUND_LIMIT = 3
    const val DEFAULT_SELF_DESTRUCT_THRESHOLD = 25

    val ROUND_LIMIT_RANGE = 1..10
    val SELF_DESTRUCT_RANGE = 10..200

    private val LADDER_MS = longArrayOf(
        30_000L,
        60_000L,
        600_000L,
        1_800_000L,
        3_600_000L
    )

    data class State(
        val failuresThisRound: Int = 0,
        val round: Int = 0,
        val lifetimeFailures: Int = 0,
        val untilWall: Long = 0L,
        val untilElapsed: Long = 0L,
        val bootStamp: Long = 0L,
        val startedWall: Long = 0L
    ) {
        fun toJson(): String = JSONObject()
            .put("failuresThisRound", failuresThisRound)
            .put("round", round)
            .put("lifetimeFailures", lifetimeFailures)
            .put("untilWall", untilWall)
            .put("untilElapsed", untilElapsed)
            .put("bootStamp", bootStamp)
            .put("startedWall", startedWall)
            .toString()

        companion object {
            val EMPTY = State()

            fun fromJson(json: String): State {
                if (json.isBlank()) return EMPTY
                return try {
                    val o = JSONObject(json)
                    State(
                        failuresThisRound = o.optInt("failuresThisRound", 0),
                        round = o.optInt("round", 0),
                        lifetimeFailures = o.optInt("lifetimeFailures", 0),
                        untilWall = o.optLong("untilWall", 0L),
                        untilElapsed = o.optLong("untilElapsed", 0L),
                        bootStamp = o.optLong("bootStamp", 0L),
                        startedWall = o.optLong("startedWall", 0L)
                    )
                } catch (_: Throwable) {
                    EMPTY.copy(round = LADDER_MS.size).lockedNow()
                }
            }
        }

        internal fun lockedNow(): State {
            val wall = System.currentTimeMillis()
            val elapsed = SystemClock.elapsedRealtime()
            val step = LADDER_MS[(round - 1).coerceIn(0, LADDER_MS.size - 1)]
            return copy(
                untilWall = wall + step,
                untilElapsed = elapsed + step,
                bootStamp = wall - elapsed,
                startedWall = wall
            )
        }
    }

    private const val BOOT_STAMP_TOLERANCE_MS = 5_000L

    fun remainingLockoutMs(state: State): Long {
        if (state.untilWall == 0L && state.untilElapsed == 0L) return 0L
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val sameBoot = kotlin.math.abs((wall - elapsed) - state.bootStamp) < BOOT_STAMP_TOLERANCE_MS
        if (sameBoot) return (state.untilElapsed - elapsed).coerceAtLeast(0L)
        if (wall < state.startedWall) return (state.untilWall - state.startedWall).coerceAtLeast(0L)
        return (state.untilWall - wall).coerceAtLeast(0L)
    }

    fun isLockedOut(state: State): Boolean = remainingLockoutMs(state) > 0L

    fun attemptsRemaining(state: State, firstRoundLimit: Int, laterRoundLimit: Int): Int {
        val limit = if (state.round == 0) firstRoundLimit else laterRoundLimit
        return (limit - state.failuresThisRound).coerceAtLeast(0)
    }

    fun registerFailure(state: State, firstRoundLimit: Int, laterRoundLimit: Int): State {
        val limit = if (state.round == 0) firstRoundLimit else laterRoundLimit
        val failures = state.failuresThisRound + 1
        val lifetime = state.lifetimeFailures + 1
        return if (failures >= limit) {
            state.copy(failuresThisRound = 0, round = state.round + 1, lifetimeFailures = lifetime)
                .lockedNow()
        } else {
            state.copy(failuresThisRound = failures, lifetimeFailures = lifetime)
        }
    }

    fun registerSuccess(): State = State.EMPTY

    fun shouldSelfDestruct(state: State, enabled: Boolean, threshold: Int): Boolean =
        enabled && state.lifetimeFailures >= threshold

    fun failuresBeforeSelfDestruct(state: State, threshold: Int): Int =
        (threshold - state.lifetimeFailures).coerceAtLeast(0)

    fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms + 999L) / 1000L
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
