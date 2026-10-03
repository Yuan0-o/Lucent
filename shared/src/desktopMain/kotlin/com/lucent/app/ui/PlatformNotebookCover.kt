package com.lucent.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

actual fun decodeCoverBitmap(bytes: ByteArray): ImageBitmap? = try {
    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (_: Exception) { null }
