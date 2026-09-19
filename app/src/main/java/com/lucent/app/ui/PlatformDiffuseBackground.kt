package com.lucent.app.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** createBitmap copies the reusable scratch pixels into a new immutable frame. */
fun diffuseImageBitmap(pixels: IntArray, edge: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, edge, edge, Bitmap.Config.ARGB_8888).asImageBitmap()

/** Share one lifecycle and system-motion snapshot with the renderer and palette clocks. */
@Composable
fun rememberBackgroundEnvironment(): BackgroundEnvironment {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resolver = LocalContext.current.applicationContext.contentResolver
    var active by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var motionEnabled by remember(resolver) {
        mutableStateOf(ValueAnimator.areAnimatorsEnabled())
    }

    DisposableEffect(lifecycle, resolver) {
        fun refreshMotionPreference() {
            // Read the notified setting directly: ValueAnimator's cached scale can be updated by
            // a different observer after this callback. The framework remains the safe fallback.
            val frameworkEnabled = ValueAnimator.areAnimatorsEnabled()
            motionEnabled = runCatching {
                Settings.Global.getFloat(
                    resolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    if (frameworkEnabled) 1f else 0f
                ) > 0f
            }.getOrDefault(frameworkEnabled)
        }

        val motionObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshMotionPreference()
            }
        }
        val observingMotion = runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                motionObserver
            )
        }.isSuccess
        val lifecycleObserver = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (active) refreshMotionPreference()
        }
        lifecycle.addObserver(lifecycleObserver)
        active = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        refreshMotionPreference()

        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
            if (observingMotion) runCatching { resolver.unregisterContentObserver(motionObserver) }
        }
    }

    return BackgroundEnvironment(active = active, motionEnabled = motionEnabled)
}
