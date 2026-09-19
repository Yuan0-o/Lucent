package com.lucent.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

object CrashShield {

    private const val TAG = "LucentCrashShield"

    @Volatile private var installed = false
    @Volatile private var appContext: Context? = null

    @Volatile var caughtCount: Int = 0
        private set

    @Volatile var lastCaught: String? = null
        private set

    fun install(context: Context) {
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

    fun isInstalled(): Boolean = installed

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
