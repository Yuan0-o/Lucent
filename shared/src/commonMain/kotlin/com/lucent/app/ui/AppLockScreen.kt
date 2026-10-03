package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
expect fun LockScreen(paletteColors: List<Color>, backdropColor: Color, backgroundAnimated: Boolean = true)
