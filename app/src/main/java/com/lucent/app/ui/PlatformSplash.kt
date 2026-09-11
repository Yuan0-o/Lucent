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

/**
 * The splash's animated backdrop. This stays [IsolatedBlobBackground] — Android's off-UI-thread,
 * half-resolution renderer — rather than the shared [FluidGlassBackground] the desktop twin uses:
 * the splash runs exactly when the JIT is coldest, which is the one place on this screen where
 * that isolation earns its cost. Losing it here was a silent regression in the first merge of this
 * file, caught by diffing against both platform originals before the duplicate files were deleted.
 */
@Composable
fun SplashBackground(palette: List<Color>, backdropColor: Color, modifier: Modifier, animated: Boolean) {
    IsolatedBlobBackground(palette = palette, backdropColor = backdropColor, modifier = modifier, animated = animated)
}
