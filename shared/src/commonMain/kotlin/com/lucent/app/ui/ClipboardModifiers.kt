package com.lucent.app.ui

import com.lucent.app.platform.PlatformContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.longPressCopy(context: PlatformContext, text: String): Modifier =
    pointerInput(text) {
        detectTapGestures(onLongPress = { copyToClipboard(context, text) })
    }.onSecondaryClick {
        copyToClipboard(context, text)
    }
