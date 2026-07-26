package com.lucent.app.data

import android.content.Context
import android.util.Log
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Toolkit

/**
 * **Crash Shield**, desktop twin (C-group task 3).
 *
 * Same switch, same promise, same honesty about its limits as the Android file — only the mechanism
 * for keeping the UI thread alive differs, because the JVM has no `Looper`.
 *
 * ### How the UI thread is kept alive here
 *
 * Compose for Desktop renders on the AWT **Event Dispatch Thread**, whose work is pumped by
 * [EventQueue.dispatchEvent]. `EventQueue.push` installs a replacement queue that the toolkit uses
 * from that moment on, so overriding `dispatchEvent` and wrapping the `super` call in a try/catch
 * puts a catch block around every UI event this app will ever process — clicks, key presses, repaint
 * requests and Compose's own frame callbacks alike. A throwing event is recorded and dropped; the
 * next event dispatches normally.
 *
 * This is the supported public API for the job (it is what AWT's own modal-dialog implementation
 * uses), not a reflection trick, so it does not depend on a JDK internal that can be removed.
 *
 * ### What is still not covered
 *
 *  - **Native crashes** in the on-device model engine (`lucent_llama_vk.dll` / `lucent_llama.dll`).
 *    An access violation inside a DLL terminates the process from below the JVM. On Windows this is
 *    the single most likely way Lucent can still die, which is why the GPU offload is now capped
 *    rather than unconditional — see `local/LocalLlm.kt`.
 *  - **JVM-level aborts**: a `-XX:+CrashOnOutOfMemoryError`-class fatal error writes an hs_err file
 *    and exits without unwinding.
 *
 * Everything reachable as a Java/Kotlin `Throwable`, on the EDT or on any other thread, is caught.
 */
object CrashShield {

    private const val TAG = "LucentCrashShield"

    @Volatile private var installed = false
    @Volatile private var appContext: Context? = null

    /** How many exceptions this process has swallowed; surfaced in Settings → Security. */
    @Volatile var caughtCount: Int = 0
        private set

    /** The most recent swallowed exception's one-line summary, for the Settings readout. */
    @Volatile var lastCaught: String? = null
        private set

    /**
     * Install the shield. Idempotent. Called from `Main.kt` before the window is created, so the
     * shielded queue is in place before the first event is dispatched.
     *
     * Deliberately never uninstalled — see the Android twin for why. The setting takes effect on the
     * next launch, and the Settings copy says so.
     */
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

    /** Whether the shield is live in this process. */
    fun isInstalled(): Boolean = installed

    /**
     * Every non-EDT thread. Returning without rethrowing lets that one thread die while the process
     * lives — which on the JVM is already the default for a plain thread, but not for code that
     * would otherwise reach a handler installed by a library.
     */
    private fun installBackgroundHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record("thread \"${thread.name}\"", throwable)
        }
    }

    /** See the class comment: a pushed EventQueue wraps every UI event in a catch block. */
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
