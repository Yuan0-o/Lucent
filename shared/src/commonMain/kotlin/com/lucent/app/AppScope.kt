package com.lucent.app

import com.lucent.app.platform.PlatformContext
import kotlin.concurrent.Volatile
import com.lucent.app.data.StartupLog
import com.lucent.app.platform.PlatformLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppScope {
    val io = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            PlatformLog.e(TAG, "background write failed", throwable)
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
    var appContext: PlatformContext? = null

    private const val TAG = "LucentAppScope"
}
