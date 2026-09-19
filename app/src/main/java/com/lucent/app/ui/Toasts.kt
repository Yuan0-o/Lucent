package com.lucent.app.ui

import android.content.Context
import android.widget.Toast

object LucentToast {

    private val lock = Any()
    private var current: Toast? = null

    fun show(context: Context, message: String, longDuration: Boolean = false) {
        val appContext = context.applicationContext
        synchronized(lock) {
            current?.cancel()
            val duration = if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            current = Toast.makeText(appContext, message, duration).also { it.show() }
        }
    }
}
