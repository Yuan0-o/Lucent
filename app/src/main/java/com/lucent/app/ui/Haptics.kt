package com.lucent.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext

/**
 * Tactile feedback for valid interactions — a short vibration when the user does something that
 * actually performs a function (copy, save, send, confirm, pick), not merely tapping empty space.
 *
 * We drive the vibrator motor directly (rather than View.performHapticFeedback) so the tick fires
 * consistently across devices and even where system haptics for a given feedback constant are
 * suppressed. A very short, low-amplitude one-shot keeps it subtle. Requires no permission:
 * VIBRATE is a normal permission but the platform doesn't prompt for it, and we degrade silently
 * if the device has no vibrator.
 */
object Haptics {

    // Duration/strength of the standard "something happened" tick.
    private const val TICK_MS = 18L
    private const val TICK_AMPLITUDE = 90 // out of 255; gentle

    // Typewriter feedback (issue 11). A barely-there tick as each character is revealed, and a
    // firm buzz when the whole reply lands.
    //
    // "Lowest intensity for every character" is honoured as literally as a phone motor usefully
    // allows: the per-character effect is the shortest, weakest one-shot the API permits. But a
    // vibrator physically cannot restart faster than a few milliseconds, and firing one every ~20ms
    // for a long reply would fuse into a single unpleasant buzz and drain the battery — so
    // [typingTick] throttles itself to at most one pulse per [TYPING_MIN_GAP_MS]. In practice the
    // typewriter's own cadence is close to that anyway, so you feel one faint tap per character or
    // two, not a drill.
    // Task 8 — every pulse now runs the motor at its ceiling.
    //
    // The per-character tick used to sit at amplitude 1, the documented floor, on the theory that a
    // typewriter should whisper. The instruction is the opposite: full power throughout, and a
    // distinctly harder hit the instant the last character lands.
    //
    // The throttle stays, and it is not a compromise on strength — a vibrator physically cannot
    // restart faster than a few milliseconds, so firing one per character on a fast reply would fuse
    // into one continuous drone and you would feel *less*, not more. One clean full-power hit per
    // ~24ms reads as a hard staccato; anything faster reads as a buzz.
    private const val TYPING_TICK_MS = 14L
    private const val TYPING_AMPLITUDE = 255      // MAX_AMPLITUDE — the ceiling the platform accepts
    private const val TYPING_MIN_GAP_MS = 24L

    // The strong pulse that marks "the reply is complete" (B-group task 1: the finish buzz was not
    // being felt at all).
    //
    // Three things were wrong and all three are fixed here rather than one of them:
    //
    //  1. AMPLITUDE. The old waveform opened at 180/255 and only reached full power in its second
    //     step. The brief is explicitly "run the motor at maximum power once", so both active steps
    //     now sit at MAX_AMPLITUDE (255) — the literal ceiling the platform accepts.
    //  2. DURATION. A 40ms pulse is near the floor of what an LRA can spin up and be *felt* as
    //     anything more than the per-character tick. The main pulse is now long enough (160ms) to
    //     read as a distinct "done", which is the whole point of it existing.
    //  3. RACE. The per-character typewriter tick and this buzz could be issued within a few ms of
    //     each other. Many OEM vibrators do not queue effects — a new one issued while another is
    //     still playing is dropped rather than replacing it — so the finish buzz was being eaten by
    //     the last typing tick. [SETTLE_MS] below is the guard: cancel, let the motor come to rest,
    //     then fire. AssistantController additionally stops the typewriter before calling this, so
    //     no tick can even be issued inside that window.
    //
    // The during-reply feedback (tick / typingTick) is deliberately untouched — the brief keeps it.
    private const val MAX_AMPLITUDE = 255
    // Task 8 — the closing hit has to be unmistakably harder than the characters that preceded it,
    // and now that those run at full power too, "louder" is no longer available: the only remaining
    // dimensions are LENGTH and SHAPE. So the finish is a long full-power slam, a brief silence, and
    // a second even longer one — a double thud no single typing tick can be confused with.
    private val FINISH_TIMINGS = longArrayOf(0L, 120L, 55L, 260L)         // wait · on · gap · on
    private val FINISH_AMPLITUDES = intArrayOf(0, MAX_AMPLITUDE, 0, MAX_AMPLITUDE)

    // How long to leave the motor idle between cancelling whatever was playing and issuing the
    // finish buzz. Short enough to read as instant, long enough for an LRA to actually stop.
    // "Immediately after the last character", so this is as short as an LRA can be trusted to come
    // to rest in. Any longer and the finish reads as a separate event rather than the end of the one
    // that was playing.
    private const val SETTLE_MS = 10L

    @Volatile private var lastTypingTickAt = 0L

    private fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun oneShot(context: Context, ms: Long, amplitude: Int) {
        val vib = vibrator(context.applicationContext) ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amp = if (vib.hasAmplitudeControl()) amplitude.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
                vib.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(ms)
            }
        } catch (_: Throwable) {
            // Some OEM vibrators throw under odd states; feedback is non-essential, so swallow it.
        }
    }

    /** A short confirmation tick. Safe to call from any thread; no-op if there's no vibrator. */
    fun tick(context: Context) = oneShot(context, TICK_MS, TICK_AMPLITUDE)

    /**
     * The faint per-character typewriter tick. Self-throttling (see [TYPING_MIN_GAP_MS]) so calling
     * it on every revealed glyph is safe and won't turn into a continuous buzz. Any-thread safe.
     */
    fun typingTick(context: Context) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTypingTickAt < TYPING_MIN_GAP_MS) return
        lastTypingTickAt = now
        oneShot(context, TYPING_TICK_MS, TYPING_AMPLITUDE)
    }

    /**
     * Play [effect] as a *notification-usage* vibration. Tagging the usage is what stops a number of
     * OEM skins (Huawei/EMUI among them) from quietly suppressing an app's vibrations: an untagged
     * one is treated as a low-priority touch tick and can be dropped, while a notification-usage one
     * is honoured. Uses [VibrationAttributes] on API 33+ and the older [AudioAttributes] overload
     * (available since API 26) below that.
     */
    private fun vibrateStrong(vib: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
                .build()
            vib.vibrate(effect, attrs)
        } else {
            @Suppress("DEPRECATION")
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            @Suppress("DEPRECATION")
            vib.vibrate(effect, attrs)
        }
    }

    /**
     * The single, full-power "reply finished" buzz (B-group task 1). Any-thread safe.
     *
     * Blocks its calling thread for [SETTLE_MS] only — see the constant for why that pause is what
     * makes the buzz land on OEM motors that drop overlapping effects. Callers run it off the main
     * thread (the generation coroutine), so the pause is never on a frame path.
     */
    fun finishBuzz(context: Context) {
        val vib = vibrator(context.applicationContext) ?: return
        if (!vib.hasVibrator()) return
        try {
            // Clear any still-running typewriter tick first, then let the motor come to rest: some
            // OEM vibrators drop a new effect that arrives while another is playing instead of
            // replacing it, which is how the finish buzz went missing entirely.
            vib.cancel()
            lastTypingTickAt = 0L
            try { Thread.sleep(SETTLE_MS) } catch (_: InterruptedException) { }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // createWaveform with amplitudes degrades to on/off on motors without amplitude
                // control (non-zero = on), so this one call is correct on every API-26+ device.
                val effect = VibrationEffect.createWaveform(FINISH_TIMINGS, FINISH_AMPLITUDES, -1)
                vibrateStrong(vib, effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(FINISH_TIMINGS, -1)
            }
        } catch (_: Throwable) {
            // Feedback is non-essential; never let a misbehaving OEM vibrator crash a reply.
        }
    }
}

/**
 * Like [Modifier.clickable] but fires a haptic tick before running [onClick]. Use this for taps
 * that perform a real action (buttons, cards that open something, list rows) so every meaningful
 * tap gives tactile feedback. Purely decorative taps should keep plain `clickable`.
 */
fun Modifier.hapticClickable(onClick: () -> Unit): Modifier = composed {
    val context = LocalContext.current
    clickable {
        Haptics.tick(context)
        onClick()
    }
}
