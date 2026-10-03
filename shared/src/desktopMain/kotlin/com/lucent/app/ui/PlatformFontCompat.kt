package com.lucent.app.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import java.io.File

actual object PlatformFontCompat {
    actual fun fontFamily(fontKey: String, path: String): FontFamily? = try {
        FontFamily(Font(identity = fontKey, data = File(path).readBytes()))
    } catch (_: Throwable) { null }
}
