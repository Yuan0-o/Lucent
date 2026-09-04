package com.lucent.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * v2.7.4 — the Android background, FULLY SEPARATED from the app's UI thread.
 *
 * The old path (shared [FluidGlassBackground]) drew six analytic radial gradients inside the UI
 * thread's Compose frame. When the UI thread was busy — a page transition, a heavy list — the
 * background's frame competed for the same budget and, on a device that cannot spare it, the
 * motion turned uneven: the exact "Mate 50 stutters, Pixel 7 doesn't" report.
 *
 * This composable separates the two completely:
 *
 *  - The expensive work — six gradient rasterizations per blob frame, plus the backdrop — happens
 *    on [Dispatchers.Default] in its own loop, into a small offscreen [Bitmap] at half screen
 *    resolution. The UI thread never draws a gradient; it never computes an oscillator.
 *  - The UI thread only composites the finished frame with one cheap textured draw
 *    ([Image]/[ContentScale.Crop]). Even under a page transition, that draw is a fraction of the
 *    cost of the old path, and the background keeps advancing on its own clock between frames,
 *    so a slower UI never makes the blobs hitch.
 *  - The clock is wall time, not frame time, and it has the same adaptive tiers as the shared
 *    path (30 -> 24 -> 20 fps) plus an explicit pause while the activity is stopped, so the
 *    renderer is never a battery drain in the background.
 *
 * The maths are the shared [computeBlobFrameParams] formulas — identical motion, same morph, same
 * squash and spin; only the rasterizer differs (software radial gradients at half resolution, then
 * GPU upscale — the blobs are soft fields, so the upscale is invisible).
 */
@Composable
fun IsolatedBlobBackground(
    palette: List<Color>,
    backdropColor: Color,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    if (!animated) {
        Box(modifier = modifier.fillMaxSize().background(backdropColor))
        return
    }

    val currentPalette by rememberUpdatedState(palette)
    val currentBackdrop by rememberUpdatedState(backdropColor)

    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var uiActive by remember { mutableStateOf(true) }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> uiActive = true
                Lifecycle.Event.ON_STOP -> uiActive = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val buffer = FloatArray(6 * 6)
        val startNs = System.nanoTime()
        var lastMs = 0f
        var tier = 0
        val tiers = floatArrayOf(33f, 41.7f, 50f)
        var slowStreak = 0
        var fastStreak = 0
        // Double buffering: the renderer alternates between two bitmaps, so the bitmap the UI is
        // compositing is never the one being rasterized underneath it (tearing would flicker).
        var caches = arrayOfNulls<android.graphics.Bitmap>(2)
        var cacheIndex = 0
        var cacheW = 0
        var cacheH = 0
        val rect = RectF(-1f, -1f, 1f, 1f)
        val paint = Paint()
        val radius = FloatArray(6)

        while (true) {
            if (!uiActive) {
                delay(250)
                lastMs = (System.nanoTime() - startNs) / 1_000_000f
                continue
            }
            val w = sizePx.width
            val h = sizePx.height
            if (w > 0 && h > 0) {
                val t = (System.nanoTime() - startNs) / 1_000_000f
                // Compute on a background thread; only the final bitmap crosses to the UI.
                val rendered = withContext(Dispatchers.Default) {
                    computeBlobFrameParams(t, w.toFloat(), h.toFloat(), buffer)
                    val sw = w / 2
                    val sh = h / 2
                    if (caches[cacheIndex] == null || cacheW != sw || cacheH != sh) {
                        caches[cacheIndex] = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
                        cacheW = sw
                        cacheH = sh
                    }
                    val bmp = caches[cacheIndex]!!
                    val canvas = Canvas(bmp)
                    canvas.drawColor(currentBackdrop.toArgb())
                    val scale = 0.5f
                    canvas.scale(scale, scale)
                    val colors = currentPalette
                    for (i in 0 until 6) {
                        val o = i * 6
                        val c = colors[i % colors.size]
                        val alpha = (0.55f * 255f).toInt()
                        val argb = (alpha shl 24) or
                            (((c.red * 255f).toInt()) shl 16) or
                            (((c.green * 255f).toInt()) shl 8) or
                            ((c.blue * 255f).toInt())
                        paint.shader = RadialGradient(
                            0f, 0f, 1f,
                            intArrayOf(argb, argb and 0x00FFFFFF),
                            floatArrayOf(0f, 1f),
                            Shader.TileMode.CLAMP
                        )
                        canvas.save()
                        canvas.translate(buffer[o], buffer[o + 1])
                        canvas.rotate(buffer[o + 5])
                        val r = buffer[o + 2]
                        canvas.scale(r * buffer[o + 4], r / buffer[o + 4])
                        val corner = buffer[o + 3]
                        canvas.drawRoundRect(rect, corner, corner, paint)
                        canvas.restore()
                    }
                    canvas.scale(1f / scale, 1f / scale)
                    bmp
                }
                frame = rendered.asImageBitmap()
                cacheIndex = 1 - cacheIndex
                lastMs = (System.nanoTime() - startNs) / 1_000_000f
            }
            val budget = tiers[tier]
            delay((budget - ((System.nanoTime() - startNs) / 1_000_000f - lastMs)).coerceAtLeast(0f).toLong())
            val elapsedGap = (System.nanoTime() - startNs) / 1_000_000f - lastMs
            if (elapsedGap > budget + 8f) {
                slowStreak++
                fastStreak = 0
                if (slowStreak >= 6 && tier < tiers.size - 1) { tier++; slowStreak = 0 }
            } else {
                fastStreak++
                slowStreak = 0
                if (fastStreak >= 18 && tier > 0) { tier--; fastStreak = 0 }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backdropColor)
            .onSizeChanged { sizePx = it }
    ) {
        frame?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** The immutable snapshot the renderer publishes: the bitmap is replaced, never mutated. */
private fun Color.toArgb(): Int {
    val r = (red * 255f + 0.5f).toInt().coerceIn(0, 255)
    val g = (green * 255f + 0.5f).toInt().coerceIn(0, 255)
    val b = (blue * 255f + 0.5f).toInt().coerceIn(0, 255)
    val a = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
