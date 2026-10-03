package com.lucent.app.ui

import androidx.compose.ui.graphics.ImageBitmap

expect fun diffuseImageBitmap(pixels: IntArray, edge: Int): ImageBitmap
