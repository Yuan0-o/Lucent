package com.lucent.app.ui

import com.lucent.app.platform.PlatformContext
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
