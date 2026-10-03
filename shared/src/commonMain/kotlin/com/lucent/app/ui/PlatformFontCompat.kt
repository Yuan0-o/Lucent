package com.lucent.app.ui

import androidx.compose.ui.text.font.FontFamily

expect object PlatformFontCompat {
    fun fontFamily(fontKey: String, path: String): FontFamily?
}
