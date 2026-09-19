package com.lucent.app

import android.content.Context
import com.lucent.app.data.StartupLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A process-lifetime coroutine scope for writes that must not be cancelled when the composable
 * that triggered them leaves composition.
 *
 * The screens use `rememberCoroutineScope()` for most work, which is exactly right for UI-scoped
 * jobs. But a few writes are triggered *as the user navigates away* — most importantly the "Save"
 * button in the unsaved-changes dialog, which saves and then switches screens in the same click.
 * Switching screens disposes the old screen and cancels its remembered scope, which could cancel
 * the save before it commits and silently lose the note/task/settings. Running those specific
 * writes here keeps them alive until they finish, independent of the UI.
 *
 * ### Why a failing write no longer takes the process with it
 *
 * A `SupervisorJob` keeps one child's failure from cancelling its siblings, but it does nothing
 * about the exception itself: with no handler installed, an exception thrown inside
 * `io.launch { … }` reaches the thread's default uncaught-exception handler, and on Android that
 * ends the process. So a single failed database write — an encrypted store that cannot be written,
 * a malformed row, a full disk — killed the app instantly, with no message and nothing in the log,
 * and the user saw only "it crashes when I touch that item".
 *
 * That is the opposite of what this app promises (see [com.lucent.app.data.CrashShield]: "the app
 * must not crash, whatever happens"). The handler below keeps that promise for background writes:
 * the failure is recorded — to the on-device event log, which is what the Export logs button
 * carries — and the process survives so the user can retry or take a backup. A crash is not a
 * safer outcome than a failed write; it is the same failure with the evidence destroyed.
 *
 * Writes that a user is waiting on should still catch their own errors and say so
 * (see [com.lucent.app.data.backgroundWrite]); this handler is the net underneath those, not a
 * substitute for them.
 */
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

    /**
     * The application context, set once at startup by each platform, so that a handler which runs
     * with no screen attached can still write to the on-device event log. An application context is
     * a process singleton, so holding it here leaks nothing.
     */
    @Volatile
    var appContext: Context? = null

    private const val TAG = "LucentAppScope"
}
