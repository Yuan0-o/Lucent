package com.lucent.app.ui

import android.content.Context

/**
 * Desktop twin of the Android Haptics helper. Desktops have no vibrator, so every cue is a no-op;
 * the API survives so AssistantController, TaskStyling, and NoteColors compile verbatim, and the
 * "typing haptics" setting remains a stored (if inert) preference that still round-trips backups.
 *
 * B-group task 1 (full-power buzz when a reply finishes) is therefore Android-only by nature. The
 * shared caller — AssistantController.Turn.completionBuzz — stops the typewriter before calling
 * [finishBuzz] on BOTH platforms, so the call sites stay identical and only the motor is missing
 * here. If a desktop cue is ever wanted (a taskbar flash, a soft chime), this is the hook for it.
 */
object Haptics {
    fun tick(context: Context) { /* no vibrator on desktop */ }
    fun typingTick(context: Context) { /* no vibrator on desktop */ }
    fun finishBuzz(context: Context) { /* no vibrator on desktop */ }
}
