package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily

expect fun Modifier.splashTopInset(): Modifier

@Composable
expect fun splashScriptFont(): FontFamily?
