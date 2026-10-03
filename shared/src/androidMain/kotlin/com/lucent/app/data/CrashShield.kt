package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import android.os.Handler
import android.os.Looper
import android.util.Log

actual object CrashShield {

    private const val TAG = "LucentCrashShield"

    @Volatile private var installed = false
    @Volatile private var appContext: PlatformContext? = null

    @Volatile actual var caughtCount: Int = 0

    @Volatile actual var lastCaught: String? = null

    actual fun install(context: PlatformContext) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            appContext = context.applicationContext
            installBackgroundHandler()
            installMainLoopWrapper()
            installed = true
            StartupLog.event(context, "crash shield: installed")
        }
    }

    actual fun isInstalled(): Boolean = installed

    private fun installBackgroundHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record("thread \"${thread.name}\"", throwable)
        }
    }

    private fun installMainLoopWrapper() {
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                    return@post
                } catch (t: Throwable) {
                    record("main thread", t)
                    if (t is java.lang.StackOverflowError || t is OutOfMemoryError) {
                        Log.e(TAG, "resource-level error swallowed on the main thread", t)
                    }
                }
            }
        }
    }

    private fun record(where: String, throwable: Throwable) {
        caughtCount += 1
        lastCaught = "${throwable.javaClass.simpleName}: ${throwable.message ?: "(no message)"}"
        Log.e(TAG, "swallowed on $where", throwable)
        val ctx = appContext ?: return
        val trace = Log.getStackTraceString(throwable)
        StartupLog.event(ctx, "crash shield: caught on $where — ${lastCaught}\n$trace")
    }
}
