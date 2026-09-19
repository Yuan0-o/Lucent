package com.lucent.app.data

import android.content.Context
import com.lucent.app.AppScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StartupLog {

    private const val FILE_NAME = "startup_log.txt"
    private const val MAX_BYTES = 256 * 1024

    private val lock = Any()
    @Volatile private var enabled = false

    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun setEnabled(value: Boolean) { enabled = value }
    fun isEnabled(): Boolean = enabled

    private fun logFile(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    fun event(context: Context, message: String) {
        if (!enabled) return
        val app = context.applicationContext
        val stamp = synchronized(lock) { format.format(Date()) }
        AppScope.io.launch {
            synchronized(lock) {
                try {
                    val f = logFile(app)
                    f.appendText("$stamp  $message\n")
                    if (f.length() > MAX_BYTES) {
                        val kept = f.readText().takeLast(MAX_BYTES / 2)
                        f.writeText(kept)
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun readAll(context: Context): String = synchronized(lock) {
        val f = logFile(context)
        if (!f.exists()) "" else try { f.readText() } catch (_: Throwable) { "" }
    }

    fun buildExport(context: Context): String {
        val events = readAll(context)
        return buildString {
            append("==== Lucent event log ====\n")
            append(if (events.isBlank()) "(no events recorded)\n" else events)
            append("\n==== logcat: this app only (includes the native model engine) ====\n")
            append(captureOwnLogcat())
        }
    }

    private fun captureOwnLogcat(): String =
        "(no logcat on the desktop — the event log above is the full record)\n"

    fun hasEntries(context: Context): Boolean = synchronized(lock) {
        val f = logFile(context)
        f.exists() && f.length() > 0
    }

    fun clear(context: Context) {
        synchronized(lock) {
            try { logFile(context).delete() } catch (_: Throwable) {}
        }
    }
}
