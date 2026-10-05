package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

expect fun Modifier.splashTopInset(): Modifier

@Composable
fun SplashBackground(palette: List<Color>, backdropColor: Color, modifier: Modifier, animated: Boolean) {
    FluidGlassBackground(palette = palette, backdropColor = backdropColor, modifier = modifier, animated = animated)
}

@Composable
expect fun splashScriptFont(): FontFamily?
