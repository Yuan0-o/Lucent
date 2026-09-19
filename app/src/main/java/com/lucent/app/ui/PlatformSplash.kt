package com.lucent.app.ui

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

fun Modifier.splashTopInset(): Modifier = statusBarsPadding()

@Composable
fun SplashBackground(palette: List<Color>, backdropColor: Color, modifier: Modifier, animated: Boolean) {
    FluidGlassBackground(palette = palette, backdropColor = backdropColor, modifier = modifier, animated = animated)
}
