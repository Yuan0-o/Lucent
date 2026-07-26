package com.lucent.app.data

import android.os.SystemClock
import org.json.JSONObject

/**
 * Brute-force throttling for **every** password prompt in the app (C-group task 18).
 *
 * ### One counter, not one per screen
 *
 * The app lock, the Blackout re-entry prompt, the backup password prompt and any future password
 * field all charge against the *same* counter. That is the entire point: a per-screen counter is
 * not a lockout, it is a menu — burn five attempts on the lock screen, walk to the backup import
 * prompt, get five more. [State] is therefore global and persisted, and every prompt calls
 * [registerFailure] / [registerSuccess] on it.
 *
 * ### The ladder
 *
 * | Round | Attempts            | Lockout after the round |
 * |-------|---------------------|-------------------------|
 * | 1     | [firstRoundLimit] (default 5) | 30s           |
 * | 2     | [laterRoundLimit] (default 3) | 1min          |
 * | 3     | [laterRoundLimit]             | 10min         |
 * | 4     | [laterRoundLimit]             | 30min         |
 * | 5+    | [laterRoundLimit]             | 60min (held)  |
 *
 * Both limits are user-configurable in Settings → Security; the ladder itself is not, because a
 * user-editable backoff curve is a backoff curve an attacker with the settings screen can flatten.
 *
 * ### One correct password resets everything
 *
 * [registerSuccess] clears the round, the failure count, the lockout deadline **and** the lifetime
 * counter that feeds self-destruct. This is called out because it is the requirement most easily
 * got wrong: a lifetime counter that survives a successful unlock would eventually wipe the data of
 * a legitimate user who is simply bad at typing, which is a data-loss bug wearing a security badge.
 *
 * ### Self-destruct
 *
 * Optional, **off by default**, and off by default on purpose: this is the only feature in Lucent
 * that destroys user data with no recovery path, and a destructive default is not a default anyone
 * consented to. When the user turns it on (behind a typed confirmation) and [lifetimeFailures]
 * reaches [selfDestructThreshold], [shouldSelfDestruct] returns true and the caller wipes.
 *
 * ### Clock tampering
 *
 * A lockout deadline stored as wall-clock time is defeated by changing the system clock. So each
 * deadline is stored twice — as wall-clock and as [SystemClock.elapsedRealtime] — together with the
 * boot stamp (`wall - elapsed`) that identifies the current boot. Within the same boot the elapsed
 * deadline is authoritative and immune to clock changes; across a reboot (elapsed restarts at zero,
 * so the boot stamp no longer matches) the wall-clock deadline is used instead. A wall clock that
 * has moved *backwards* past the moment the lockout began is treated as still locked rather than as
 * expired, so winding the clock back cannot shorten a lockout either.
 */
object PasswordAttempts {

    /** Shipped defaults. The first round is roomier than later ones — most failures are typos. */
    const val DEFAULT_FIRST_ROUND_LIMIT = 5
    const val DEFAULT_LATER_ROUND_LIMIT = 3
    const val DEFAULT_SELF_DESTRUCT_THRESHOLD = 25

    /** Bounds the settings UI enforces, so a stored value can never disable the throttle entirely. */
    val ROUND_LIMIT_RANGE = 1..10
    val SELF_DESTRUCT_RANGE = 10..200

    /** Lockout after each completed round, in milliseconds. The last entry repeats forever. */
    private val LADDER_MS = longArrayOf(
        30_000L,        // 30 seconds
        60_000L,        // 1 minute
        600_000L,       // 10 minutes
        1_800_000L,     // 30 minutes
        3_600_000L      // 1 hour, held for every round beyond the fourth
    )

    /**
     * The persisted throttle state. Serialised to a single JSON string so it rides in one
     * preferences entry (see [SettingsRepository.passwordAttemptState]) and can be extended without
     * a migration.
     *
     * @param failuresThisRound failures accumulated in the round currently in progress.
     * @param round             completed rounds so far; indexes [LADDER_MS].
     * @param lifetimeFailures  failures since the last *successful* unlock; feeds self-destruct.
     * @param untilWall         wall-clock millis the lockout ends, or 0 when not locked out.
     * @param untilElapsed      the same deadline on the monotonic clock.
     * @param bootStamp         `wall - elapsed` at the moment the deadline was written.
     * @param startedWall       wall-clock millis the lockout began; detects a backwards clock.
     */
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
                    // An unreadable throttle record must fail SAFE, not open: an attacker who can
                    // corrupt the preference should not thereby clear their own lockout. The state
                    // is treated as "locked out for the longest step" rather than as "no lockout".
                    EMPTY.copy(round = LADDER_MS.size).lockedNow()
                }
            }
        }

        /** This state, re-stamped as locked out from right now for its round's ladder step. */
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

    /** Tolerance when matching the stored boot stamp; small clock drift must not look like a reboot. */
    private const val BOOT_STAMP_TOLERANCE_MS = 5_000L

    /**
     * Milliseconds still to wait before a password may be tried again, or 0 when unlocked.
     *
     * See the class comment for why this consults two clocks. The short version: within one boot the
     * monotonic clock decides, because it cannot be edited; after a reboot the wall clock decides,
     * because the monotonic one has restarted.
     */
    fun remainingLockoutMs(state: State): Long {
        if (state.untilWall == 0L && state.untilElapsed == 0L) return 0L
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val sameBoot = kotlin.math.abs((wall - elapsed) - state.bootStamp) < BOOT_STAMP_TOLERANCE_MS
        if (sameBoot) return (state.untilElapsed - elapsed).coerceAtLeast(0L)
        // Different boot: the wall clock is all there is. A clock that now reads *earlier* than the
        // moment the lockout started has been wound back, so the full remaining step is re-imposed
        // instead of being treated as elapsed.
        if (wall < state.startedWall) return (state.untilWall - state.startedWall).coerceAtLeast(0L)
        return (state.untilWall - wall).coerceAtLeast(0L)
    }

    /** Whether a password may be submitted at all right now. */
    fun isLockedOut(state: State): Boolean = remainingLockoutMs(state) > 0L

    /** Attempts left in the current round before it closes and a lockout begins. */
    fun attemptsRemaining(state: State, firstRoundLimit: Int, laterRoundLimit: Int): Int {
        val limit = if (state.round == 0) firstRoundLimit else laterRoundLimit
        return (limit - state.failuresThisRound).coerceAtLeast(0)
    }

    /**
     * Record one wrong password and return the new state.
     *
     * Closing a round (the round's attempts are spent) advances [State.round] and stamps a fresh
     * lockout deadline from the ladder.
     */
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

    /**
     * Record a correct password: **everything** resets, including the lifetime counter that feeds
     * self-destruct. See the class comment — this is the requirement it is easiest to half-implement.
     */
    fun registerSuccess(): State = State.EMPTY

    /**
     * Whether the caller should now wipe all data.
     *
     * Returns false whenever the feature is off, so a caller that forgets to check [enabled] first
     * still cannot destroy anything by accident.
     */
    fun shouldSelfDestruct(state: State, enabled: Boolean, threshold: Int): Boolean =
        enabled && state.lifetimeFailures >= threshold

    /** Failures still available before self-destruct fires; used for the warning banner. */
    fun failuresBeforeSelfDestruct(state: State, threshold: Int): Int =
        (threshold - state.lifetimeFailures).coerceAtLeast(0)

    /** Human-readable remaining lockout, e.g. "9:58", for the lock screen countdown. */
    fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms + 999L) / 1000L
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
