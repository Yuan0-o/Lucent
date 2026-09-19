package com.lucent.app.data

import android.content.Context
import com.lucent.app.AppScope
import com.lucent.app.i18n.S
import com.lucent.app.ui.LucentToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun backgroundWrite(context: Context, what: String, block: suspend () -> Unit) {
    val app = context.applicationContext
    AppScope.io.launch {
        try {
            block()
        } catch (t: Throwable) {
            android.util.Log.e("LucentWrite", "$what failed", t)
            StartupLog.event(app, "$what failed — ${t::class.simpleName}: ${t.message}")
            withContext(Dispatchers.Main) {
                runCatching { LucentToast.show(app, S.writeFailedToast) }
            }
        }
    }
}
