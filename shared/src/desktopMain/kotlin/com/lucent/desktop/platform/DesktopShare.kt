
package com.lucent.desktop.platform

import android.content.Context
import com.lucent.app.ui.copyToClipboard

object DesktopShare {

    fun shareText(context: Context, subject: String? = null, text: String) {
        val payload = when {
            subject.isNullOrBlank() -> text
            text.isBlank() -> subject
            else -> "$subject\n\n$text"
        }
        copyToClipboard(context, payload)
    }
}
