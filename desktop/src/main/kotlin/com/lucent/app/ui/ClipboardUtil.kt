package com.lucent.app.ui

import android.content.Context
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

fun copyToClipboard(context: Context, text: String) {
    if (text.isBlank()) return
    try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    } catch (t: Throwable) {
        return
    }
    Haptics.tick(context)
    LucentToast.show(context, com.lucent.app.i18n.S.copiedToast)
}

fun Modifier.longPressCopy(context: Context, text: String): Modifier =
    pointerInput(text) {
        detectTapGestures(onLongPress = { copyToClipboard(context, text) })
    }
