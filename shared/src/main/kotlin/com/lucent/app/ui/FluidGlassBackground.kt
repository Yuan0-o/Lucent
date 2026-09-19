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
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class BackgroundEnvironment(val active: Boolean = true, val motionEnabled: Boolean = true)

val LocalBackgroundEnvironment = staticCompositionLocalOf { BackgroundEnvironment() }

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
            !moving -> "still gradient"
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
        suspend fun render(seconds: Double): ImageBitmap {
            val colors = currentPalette.map { it.toArgb() }.toIntArray()
            val backdrop = currentBackdrop
            return withContext(Dispatchers.Default) {
                if (field.edge != policy.edge) field = DiffuseGradientField(policy.edge)
                val pixels = field.render(seconds, colors, backdrop.toArgb(), backdrop.luminance() < 0.5f)
                // Each platform copies the scratch pixels; a published texture is never overwritten.
                diffuseImageBitmap(pixels, field.edge)
            }
        }
        try {
            if (!moving) {
                snapshotFlow { currentPalette to currentBackdrop }.collectLatest {
                    frame = render(timeline.seconds)
                }
            } else {
                while (isActive) {
                    val frameNanos = withFrameNanos { it }
                    if (!policy.isDue(frameNanos)) continue
                    val seconds = timeline.advance(frameNanos)
                    val started = System.nanoTime()
                    val next = render(seconds)
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
