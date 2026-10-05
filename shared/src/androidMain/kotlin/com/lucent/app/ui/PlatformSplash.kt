package com.lucent.app.ui

import android.graphics.Typeface
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Typeface as ComposeTypeface
import java.io.File

actual fun Modifier.splashTopInset(): Modifier = statusBarsPadding()

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
        else {
            val tmp = File.createTempFile("splashScript", ".ttf")
            tmp.writeBytes(bytes)
            tmp.deleteOnExit()
            val tf = Typeface.createFromFile(tmp)
            FontFamily(ComposeTypeface(tf))
        }
    } catch (t: Throwable) {
        null
    }
}
