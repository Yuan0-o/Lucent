package com.lucent.app.data

import android.content.Context
import android.util.Log
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Toolkit

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
            installEventQueueWrapper()
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

    private fun installEventQueueWrapper() {
        val shielded = object : EventQueue() {
            override fun dispatchEvent(event: AWTEvent) {
                try {
                    super.dispatchEvent(event)
                } catch (t: Throwable) {
                    record("event dispatch thread", t)
                }
            }
        }
        Toolkit.getDefaultToolkit().systemEventQueue.push(shielded)
    }

    private fun record(where: String, throwable: Throwable) {
        caughtCount += 1
        lastCaught = "${throwable.javaClass.simpleName}: ${throwable.message ?: "(no message)"}"
        Log.e(TAG, "swallowed on $where", throwable)
        val ctx = appContext ?: return
        val trace = java.io.StringWriter().also { sw ->
            throwable.printStackTrace(java.io.PrintWriter(sw))
        }.toString()
        StartupLog.event(ctx, "crash shield: caught on $where — ${lastCaught}\n$trace")
    }
}
