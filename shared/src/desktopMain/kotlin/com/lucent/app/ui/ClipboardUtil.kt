package com.lucent.app.ui

import com.lucent.app.platform.PlatformContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual fun copyToClipboard(context: PlatformContext, text: String) {
    if (text.isBlank()) return
    try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    } catch (t: Throwable) {
        return
    }
    Haptics.tick(context)
    LucentToast.show(context, com.lucent.app.i18n.S.copiedToast)
}

actual fun Modifier.longPressCopy(context: PlatformContext, text: String): Modifier =
    pointerInput(text) {
        detectTapGestures(onLongPress = { copyToClipboard(context, text) })
    }.onSecondaryClick {
        copyToClipboard(context, text)
    }
