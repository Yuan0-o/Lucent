package com.lucent.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * **Crash Shield** (C-group task 3) — keep the app on screen when something throws.
 *
 * ### What it actually promises, stated honestly
 *
 * The requirement is "the app must not crash, whatever happens". No process on Android can promise
 * that literally, and a switch whose label over-promises is worse than no switch: the user stops
 * saving their work because they believe a guarantee that does not exist. So the switch is built to
 * cover everything that *can* be covered, and the Settings copy states the three cases that cannot:
 *
 *  - **Native crashes.** A SIGSEGV inside the on-device model engine (`lucent_llama.so`) kills the
 *    process from below the JVM. Nothing running in Kotlin can intercept a signal.
 *  - **Out-of-memory kills.** The kernel's low-memory killer does not throw; it terminates.
 *  - **ANRs.** A blocked main thread is killed by the system watchdog, not by an exception.
 *
 * Everything else — every Java/Kotlin exception on the main thread and on every background thread —
 * is caught and swallowed here, and the app keeps running.
 *
 * ### How the main thread is kept alive
 *
 * A `Thread.UncaughtExceptionHandler` alone does **not** save the UI thread: Android's default
 * handler kills the process after ours returns. What keeps the main thread alive is re-entering its
 * message loop. `Looper.loop()` propagates an exception thrown by any message out to its caller, so
 * running it inside `while (true) { try { Looper.loop() } catch (…) {} }` means a throwing message
 * unwinds to us, is recorded, and the loop is immediately re-entered — the next message is
 * dispatched as if nothing happened. The install is posted to the main queue so this wrapper becomes
 * the outermost frame of the loop.
 *
 * ### The honest caveat about state
 *
 * A swallowed exception means the work that threw did **not** finish. A composable that threw
 * mid-layout leaves that frame incomplete; a save that threw did not save. The app stays usable —
 * which is the point, since a live app can still export a backup and a dead one cannot — but the
 * shield is a way to *survive* a bug, not a way to be *unaffected* by one. Every catch therefore
 * writes a full stack trace to [StartupLog], and that is exactly why the switch force-enables
 * logging: a shield that hides failures without recording them would turn one visible crash into a
 * silent, permanent misbehaviour nobody can diagnose.
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
     * Install the shield. Idempotent, and safe to call before anything else in `Application`/
     * `MainActivity.onCreate` — earlier is strictly better, since exceptions thrown before the
     * install are not covered.
     *
     * Deliberately never uninstalled. Turning the setting off stops the *next* launch from
     * installing it, but tearing the loop wrapper out of a running process would mean unwinding the
     * very frame the main thread is currently sitting in. The Settings copy says the change applies
     * on next launch rather than pretending otherwise.
     */
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

    /** Whether the shield is live in this process. */
    fun isInstalled(): Boolean = installed

    /**
     * Background and worker threads. Returning from this handler without rethrowing is enough on a
     * non-main thread: only that thread dies, and the process continues. The default handler is
     * NOT chained — chaining it would call the very code that kills the process.
     */
    private fun installBackgroundHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record("thread \"${thread.name}\"", throwable)
        }
    }

    /** See the class comment: re-entering `Looper.loop()` is what keeps the UI thread alive. */
    private fun installMainLoopWrapper() {
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                    // A clean return means the looper was quit deliberately (the process is going
                    // away). Breaking out lets that happen instead of spinning forever.
                    return@post
                } catch (t: Throwable) {
                    record("main thread", t)
                    // The dropped message may have been a Compose frame callback; asking for a new
                    // frame keeps the UI advancing rather than freezing on the broken one.
                    if (t is java.lang.StackOverflowError || t is OutOfMemoryError) {
                        // These two say the process is already in trouble. Recording and continuing
                        // is still better than dying, but they are surfaced distinctly in the log so
                        // a bug report shows the difference between "a bug threw" and "we ran out".
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
        // The full stack trace, not just the summary: the whole justification for swallowing an
        // exception is that it is written down somewhere a bug report can reach.
        val trace = Log.getStackTraceString(throwable)
        StartupLog.event(ctx, "crash shield: caught on $where — ${lastCaught}\n$trace")
    }
}
