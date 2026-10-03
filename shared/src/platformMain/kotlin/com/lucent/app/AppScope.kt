package com.lucent.app

import android.content.Context
import com.lucent.app.data.StartupLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppScope {
    val io = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e(TAG, "background write failed", throwable)
            val context = appContext
            if (context != null) {
                StartupLog.event(
                    context,
                    "background write failed — ${throwable::class.simpleName}: ${throwable.message}"
                )
            }
        }
    )

    @Volatile
    var appContext: Context? = null

    private const val TAG = "LucentAppScope"
}
