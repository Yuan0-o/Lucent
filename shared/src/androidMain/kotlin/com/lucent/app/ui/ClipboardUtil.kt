package com.lucent.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import com.lucent.app.platform.PlatformContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

actual fun copyToClipboard(context: PlatformContext, text: String) {
    if (text.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Lucent", text))
    Haptics.tick(context)
    LucentToast.show(context, com.lucent.app.i18n.S.copiedToast)
}

actual fun Modifier.longPressCopy(context: PlatformContext, text: String): Modifier =
    this.pointerInput(text) {
        detectTapGestures(onLongPress = { copyToClipboard(context, text) })
    }
