package com.lucent.app.ui

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Android seam for the shared splash screen. The splash itself is platform-neutral; only the
 * system-bar inset and the background renderer belong to the host platform.
 */
fun Modifier.splashTopInset(): Modifier = statusBarsPadding()

/** The splash uses the same bounded, off-main-thread diffuse field as the app and desktop. */
@Composable
fun SplashBackground(palette: List<Color>, backdropColor: Color, modifier: Modifier, animated: Boolean) {
    FluidGlassBackground(palette = palette, backdropColor = backdropColor, modifier = modifier, animated = animated)
}
