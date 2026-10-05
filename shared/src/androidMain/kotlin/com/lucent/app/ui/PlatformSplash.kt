package com.lucent.app.ui

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

actual fun Modifier.splashTopInset(): Modifier = statusBarsPadding()

@Composable
actual fun splashScriptFont(): FontFamily? = remember {
    runCatching { FontFamily(Font(com.lucent.shared.R.font.great_vibes)) }.getOrNull()
}
