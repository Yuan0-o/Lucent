package com.lucent.app.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File

actual object PlatformFontCompat {
    actual fun fontFamily(fontKey: String, path: String): FontFamily? = try {
        FontFamily(Font(File(path)))
    } catch (_: Throwable) { null }
}
