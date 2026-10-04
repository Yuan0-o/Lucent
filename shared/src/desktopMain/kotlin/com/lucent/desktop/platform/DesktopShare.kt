
package com.lucent.desktop.platform

import com.lucent.app.platform.PlatformContext
import com.lucent.app.ui.copyToClipboard

object DesktopShare {

    fun shareText(context: PlatformContext, subject: String? = null, text: String) {
        val payload = when {
            subject.isNullOrBlank() -> text
            text.isBlank() -> subject
            else -> "$subject\n\n$text"
        }
        copyToClipboard(context, payload)
    }
}
