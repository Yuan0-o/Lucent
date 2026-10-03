package com.lucent.app.ui

import com.lucent.app.platform.PlatformContext
import android.widget.Toast

actual object LucentToast {

    private val lock = Any()
    private var current: Toast? = null

    actual fun show(context: PlatformContext, message: String, longDuration: Boolean) {
        val appContext = context.applicationContext
        synchronized(lock) {
            current?.cancel()
            val duration = if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            current = Toast.makeText(appContext, message, duration).also { it.show() }
        }
    }
}
