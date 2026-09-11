package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Desktop seam for the shared splash screen. Desktop windows have no Android status-bar inset.
 */
fun Modifier.splashTopInset(): Modifier = this

/** The splash's animated backdrop: the same [FluidGlassBackground] the rest of the desktop app uses. */
@Composable
fun SplashBackground(palette: List<Color>, backdropColor: Color, modifier: Modifier, animated: Boolean) {
    FluidGlassBackground(palette = palette, backdropColor = backdropColor, modifier = modifier, animated = animated)
}
