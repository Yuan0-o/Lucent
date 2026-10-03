package com.lucent.app.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.lucent.app.platform.PlatformContext

const val SYSTEM_FONT_KEY = "system"

expect object LucentFontResolver {
    fun resolve(context: PlatformContext, fontKey: String?): FontFamily?
    fun evict(fontKey: String)
    fun evictAll()
}

@Composable
expect fun lucentTypography(fontKey: String): Typography
