package com.lucent.app.ui

import com.lucent.app.platform.PlatformContext
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lucent.app.platform.LocalPlatformContext
import androidx.compose.ui.text.font.FontFamily
import com.lucent.app.data.FontStore

const val SYSTEM_FONT_KEY = "system"

object LucentFontResolver {

    private class Holder(val family: FontFamily?)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Holder>()

    fun resolve(context: PlatformContext, fontKey: String?): FontFamily? {
        if (fontKey.isNullOrBlank() || fontKey == SYSTEM_FONT_KEY) return null
        return cache.getOrPut(fontKey) {
            try {
                val file = FontStore.fontFile(context.applicationContext, fontKey)
                    ?: return@getOrPut Holder(null)
                Holder(PlatformFontCompat.fontFamily(fontKey, file.absolutePath))
            } catch (_: Throwable) {
                Holder(null)
            }
        }.family
    }

    fun evict(fontKey: String) {
        cache.remove(fontKey)
    }

    fun evictAll() {
        cache.clear()
    }
}

@Composable
fun lucentTypography(fontKey: String): Typography {
    val context = LocalPlatformContext.current
    val base = Typography()
    val family = remember(fontKey) { LucentFontResolver.resolve(context, fontKey) } ?: return base
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family)
    )
}
