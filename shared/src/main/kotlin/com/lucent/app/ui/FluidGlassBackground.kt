package com.lucent.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.lucent.app.data.StartupLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.ceil

data class BackgroundEnvironment(val active: Boolean = true, val motionEnabled: Boolean = true)

val LocalBackgroundEnvironment = staticCompositionLocalOf { BackgroundEnvironment() }

object BackgroundMotion {

    var resting by mutableStateOf(false)
        private set

    fun hold(value: Boolean) {
        resting = value
    }
}

private const val STILL_COLOUR_TICK_MS = 1_000L

private class PaletteArgbs {
    private var values = IntArray(0)

    fun of(palette: List<Color>): IntArray {
        if (!matches(palette)) values = IntArray(palette.size) { palette[it].toArgb() }
        return values
    }

    private fun matches(palette: List<Color>): Boolean {
        if (palette.size != values.size) return false
        for (index in palette.indices) {
            if (palette[index].toArgb() != values[index]) return false
        }
        return true
    }
}

@Composable
fun FluidGlassBackground(
    palette: List<Color>,
    backdropColor: Color,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val context = LocalContext.current
    val environment = LocalBackgroundEnvironment.current
    val inspection = LocalInspectionMode.current
    val resting = BackgroundMotion.resting
    val moving = animated && environment.motionEnabled && !resting && !inspection
    LaunchedEffect(animated, environment.active, moving) {
        val mode = when {
            !animated -> "flat"
            !environment.active -> "paused"
            resting -> "held while scrolling"
            !moving -> "still waves, slow colour drift"
            else -> "animated gradient"
        }
        StartupLog.event(context, "Background: $mode")
    }
    if (!animated) {
        Box(modifier.fillMaxSize().background(backdropColor))
        return
    }

    val currentPalette by rememberUpdatedState(palette)
    val currentBackdrop by rememberUpdatedState(backdropColor)
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val timeline = remember { BackgroundTimeline() }
    val policy = remember { BackgroundFramePolicy() }
    val argbPalette = remember { PaletteArgbs() }

    LaunchedEffect(environment.active, moving) {
        if (!environment.active) return@LaunchedEffect
        policy.resume()
        var field = DiffuseGradientField(policy.edge)
        suspend fun paint(spatialSeconds: Double, colourSeconds: Double): ImageBitmap {
            val colors = argbPalette.of(currentPalette)
            val backdrop = currentBackdrop
            return withContext(Dispatchers.Default) {
                if (field.edge != policy.edge) field = DiffuseGradientField(policy.edge)
                val pixels = field.render(
                    spatialSeconds = spatialSeconds,
                    colourSeconds = colourSeconds,
                    palette = colors,
                    backdrop = backdrop.toArgb(),
                    dark = backdrop.luminance() < 0.5f
                )
                diffuseImageBitmap(pixels, field.edge)
            }
        }
        try {
            when {
                inspection -> frame = paint(0.0, 0.0)

                !moving -> {
                    var colourSeconds = 0.0
                    while (isActive) {
                        frame = paint(spatialSeconds = 0.0, colourSeconds = colourSeconds)
                        delay(STILL_COLOUR_TICK_MS)
                        colourSeconds += STILL_COLOUR_TICK_MS / 1000.0
                    }
                }

                else -> while (isActive) {
                    val frameNanos = withFrameNanos { it }
                    if (!policy.isDue(frameNanos)) continue
                    val seconds = timeline.advance(frameNanos)
                    val started = System.nanoTime()
                    val next = paint(seconds, seconds)
                    val renderNanos = System.nanoTime() - started
                    frame = next
                    if (policy.record(frameNanos, renderNanos)) {
                        StartupLog.event(context, "Background: quality tier ${policy.tier}, texture ${policy.edge}px")
                    }
                }
            }
        } finally {
            timeline.pause()
        }
    }

    Canvas(modifier.fillMaxSize().background(backdropColor)) {
        frame?.let { image ->
            if (size.width >= 1f && size.height >= 1f) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(ceil(size.width).toInt(), ceil(size.height).toInt()),
                    filterQuality = FilterQuality.Low
                )
            }
        }
    }
}
