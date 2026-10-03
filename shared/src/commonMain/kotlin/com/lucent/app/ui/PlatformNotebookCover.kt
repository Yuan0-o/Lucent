package com.lucent.app.ui

import androidx.compose.ui.graphics.ImageBitmap

expect fun decodeCoverBitmap(bytes: ByteArray): ImageBitmap?
