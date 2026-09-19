package com.lucent.app.data

import android.content.Context
import com.lucent.app.AppScope
import com.lucent.app.i18n.S
import com.lucent.app.ui.LucentToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A user-initiated write that runs off the UI thread and is allowed to fail without the app dying.
 *
 * Every write in this app goes through [AppScope] so that navigating away cannot cancel it. The
 * scope now has an exception handler, so a failure there no longer ends the process — but a silent
 * failure is its own bug: the user taps "Unpin", the pin stays, and nothing explains why. This
 * helper is the other half. It runs the write, and on failure it records the technical detail in
 * the on-device event log (never the item's content) and tells the user, in their language, that
 * the change did not apply.
 *
 * The recorded line is deliberately specific enough to diagnose from an exported log alone —
 * "note pin toggle failed — SQLiteDiskIOException: disk I/O error" names both the operation and the
 * cause, which is exactly what a report like "it crashes when I pin things" cannot say.
 *
 * @param what short English name of the operation, e.g. "note pin toggle". Goes in the log only.
 */
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
