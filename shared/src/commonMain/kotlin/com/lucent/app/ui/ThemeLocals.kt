package com.lucent.app.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalOnGradient = compositionLocalOf { Color.White }
val LocalOnGradientMuted = compositionLocalOf { Color.White.copy(alpha = 0.65f) }
