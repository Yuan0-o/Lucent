package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import com.lucent.app.AppScope
import com.lucent.app.i18n.S
import com.lucent.app.platform.PlatformLog
import com.lucent.app.ui.LucentToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun backgroundWrite(context: PlatformContext, what: String, block: suspend () -> Unit) {
    val app = context.applicationContext
    AppScope.io.launch {
        try {
            block()
        } catch (t: Throwable) {
            PlatformLog.e("LucentWrite", "$what failed", t)
            StartupLog.event(app, "$what failed — ${t::class.simpleName}: ${t.message}")
            withContext(Dispatchers.Main) {
                runCatching { LucentToast.show(app, S.writeFailedToast) }
            }
        }
    }
}
