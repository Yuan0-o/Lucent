@file:JvmName("PlatformSplashDesktopKt")
package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font

actual fun Modifier.splashTopInset(): Modifier = this

private object SplashFontAnchor

@Composable
actual fun splashScriptFont(): FontFamily? = remember {
    try {
        val loader = Thread.currentThread().contextClassLoader
            ?: SplashFontAnchor::class.java.classLoader
        val bytes = loader
            ?.getResourceAsStream("fonts/GreatVibes-Regular.ttf")
            ?.use { it.readBytes() }
        if (bytes == null || bytes.isEmpty()) null
        else FontFamily(Font(identity = "splashScript", data = bytes))
    } catch (t: Throwable) {
        null
    }
}
