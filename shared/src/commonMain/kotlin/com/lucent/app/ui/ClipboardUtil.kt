package com.lucent.app.ui

import androidx.compose.ui.Modifier
import com.lucent.app.platform.PlatformContext

expect fun copyToClipboard(context: PlatformContext, text: String)

expect fun Modifier.longPressCopy(context: PlatformContext, text: String): Modifier
