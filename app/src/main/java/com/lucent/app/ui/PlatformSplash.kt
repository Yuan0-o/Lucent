package com.lucent.app.ui

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

fun Modifier.splashTopInset(): Modifier = statusBarsPadding()

@Composable
fun SplashBackground(palette: List<Color>, backdropColor: Color, modifier: Modifier, animated: Boolean) {
    FluidGlassBackground(palette = palette, backdropColor = backdropColor, modifier = modifier, animated = animated)
}

@Composable
fun splashScriptFont(): FontFamily? = remember {
    runCatching { FontFamily(Font(com.lucent.app.R.font.great_vibes)) }.getOrNull()
}
