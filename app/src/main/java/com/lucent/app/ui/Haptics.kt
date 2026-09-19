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

object Haptics {

    private const val TICK_MS = 18L
    private const val TICK_AMPLITUDE = 90

    private const val TYPING_TICK_MS = 14L
    private const val TYPING_AMPLITUDE = 255
    private const val TYPING_MIN_GAP_MS = 24L

    private const val MAX_AMPLITUDE = 255
    private val FINISH_TIMINGS = longArrayOf(0L, 120L, 55L, 260L)
    private val FINISH_AMPLITUDES = intArrayOf(0, MAX_AMPLITUDE, 0, MAX_AMPLITUDE)

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
        }
    }

    fun tick(context: Context) = oneShot(context, TICK_MS, TICK_AMPLITUDE)

    fun typingTick(context: Context) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTypingTickAt < TYPING_MIN_GAP_MS) return
        lastTypingTickAt = now
        oneShot(context, TYPING_TICK_MS, TYPING_AMPLITUDE)
    }

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

    fun finishBuzz(context: Context) {
        val vib = vibrator(context.applicationContext) ?: return
        if (!vib.hasVibrator()) return
        try {
            vib.cancel()
            lastTypingTickAt = 0L
            try { Thread.sleep(SETTLE_MS) } catch (_: InterruptedException) { }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(FINISH_TIMINGS, FINISH_AMPLITUDES, -1)
                vibrateStrong(vib, effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(FINISH_TIMINGS, -1)
            }
        } catch (_: Throwable) {
        }
    }
}

fun Modifier.hapticClickable(onClick: () -> Unit): Modifier = composed {
    val context = LocalContext.current
    clickable {
        Haptics.tick(context)
        onClick()
    }
}
