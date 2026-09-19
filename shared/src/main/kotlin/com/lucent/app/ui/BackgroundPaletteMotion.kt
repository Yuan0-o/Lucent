package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalInspectionMode

private const val CYCLE_STEPS_PER_PALETTE = 128

@Composable
fun rememberCyclingPaletteColors(
    palettes: List<List<Color>>,
    secondsPerPalette: Int = 12,
    animated: Boolean = true,
    environment: BackgroundEnvironment = LocalBackgroundEnvironment.current
): List<Color> {
    if (palettes.isEmpty()) return listOf(Color.White, Color.White, Color.White)
    if (palettes.size == 1) return palettes.first()

    val inspection = LocalInspectionMode.current
    val running = animated && environment.active && environment.motionEnabled && !inspection
    if (!running) return palettes.first()

    val n = palettes.size
    val totalMs = (n.toLong() * secondsPerPalette * 1000L).toFloat()

    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(n, secondsPerPalette) {
        var startNanos = -1L
        var lastEmitted = -1
        while (true) {
            androidx.compose.animation.core.withInfiniteAnimationFrameNanos { frameNanos ->
                if (startNanos < 0L) startNanos = frameNanos
                val elapsed = (frameNanos - startNanos) / 1_000_000f
                val raw = (elapsed / totalMs * n) % n
                val step = (raw * CYCLE_STEPS_PER_PALETTE).toInt()
                if (step != lastEmitted) {
                    lastEmitted = step
                    phase = step / CYCLE_STEPS_PER_PALETTE.toFloat()
                }
            }
        }
    }

    val index = phase.toInt().coerceIn(0, n - 1)
    val frac = (phase - index).coerceIn(0f, 1f)
    val current = palettes[index]
    val next = palettes[(index + 1) % n]
    return current.indices.map { i -> lerp(current[i], next[i % next.size], frac) }
}
