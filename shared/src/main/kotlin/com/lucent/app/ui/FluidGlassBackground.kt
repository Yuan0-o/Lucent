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
import androidx.compose.ui.unit.IntSize
import com.lucent.app.data.StartupLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * How the background is allowed to behave *here and now*: whether the screen is on show at all, and
 * whether the platform's own motion preference allows movement.
 *
 * Each platform supplies one of these from its real lifecycle (an activity that is stopped, a
 * Windows window that is hidden or minimised) and its real system setting, and the renderer, the
 * palette clocks and the "Random"/"Cycle" walkers all read the same value. One source of truth is
 * what stops the animation from costing anything while nobody is looking at it.
 */
data class BackgroundEnvironment(val active: Boolean = true, val motionEnabled: Boolean = true)

val LocalBackgroundEnvironment = staticCompositionLocalOf { BackgroundEnvironment() }

/** While motion is reduced the colours still crossfade, just slowly — see the still loop below. */
private const val STILL_COLOUR_TICK_MS = 1_000L

/**
 * The full-screen diffuse gradient.
 *
 * A continuous colour field, rasterized off the UI thread into a small texture and drawn as one
 * cheap image, so its cost depends on the texture rather than on the size of the display. The field
 * changes in two ways at once — its waves travel, and each of its three colours walks slowly through
 * the palette — so the picture is never the same twice. See [DiffuseGradientField].
 */
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
    val moving = animated && environment.motionEnabled && !inspection
    LaunchedEffect(animated, environment.active, moving) {
        val mode = when {
            !animated -> "flat"
            !environment.active -> "paused"
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

    LaunchedEffect(environment.active, moving) {
        if (!environment.active) return@LaunchedEffect
        policy.resume()
        var field = DiffuseGradientField(policy.edge)
        suspend fun paint(spatialSeconds: Double, colourSeconds: Double): ImageBitmap {
            val colors = currentPalette.map { it.toArgb() }.toIntArray()
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
                // Each platform copies the scratch pixels; a published texture is never overwritten.
                diffuseImageBitmap(pixels, field.edge)
            }
        }
        try {
            when {
                // A preview has no runtime to drive a clock, so it gets one frame.
                inspection -> frame = paint(0.0, 0.0)

                // Motion is reduced: the travelling waves are frozen in place, but the palette
                // keeps walking. A colour that drifts over half a minute is not motion, and it is
                // the difference between a living gradient and a printed wallpaper — which is the
                // one thing this background must never be.
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
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Low
                )
            }
        }
    }
}
