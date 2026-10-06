package com.lucent.app.data
import com.lucent.app.platform.applicationContext
import com.lucent.app.platform.filesDir

import com.lucent.app.platform.PlatformContext
import com.lucent.app.AppScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual object StartupLog {

    private const val FILE_NAME = "startup_log.txt"
    private const val MAX_BYTES = 256 * 1024

    private val lock = Any()
    @Volatile private var enabled = false

    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    actual fun setEnabled(value: Boolean) { enabled = value }
    actual fun isEnabled(): Boolean = enabled

    private fun logFile(context: PlatformContext) = File(context.applicationContext.filesDir, FILE_NAME)

    actual fun event(context: PlatformContext, message: String) {
        if (!enabled) return
        val app = context.applicationContext
        val stamp = synchronized(lock) { format.format(Date()) }
        AppScope.io.launch {
            synchronized(lock) {
                try {
                    val f = logFile(app)
                    f.appendText("$stamp  $message\n")
                    if ((okio.FileSystem.SYSTEM.metadataOrNull(f)?.size ?: 0L) > MAX_BYTES) {
                        val kept = f.readText().takeLast(MAX_BYTES / 2)
                        f.writeText(kept)
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    actual fun readAll(context: PlatformContext): String = synchronized(lock) {
        val f = logFile(context)
        if (!okio.FileSystem.SYSTEM.exists(f)) "" else try { f.readText() } catch (_: Throwable) { "" }
    }

    actual fun buildExport(context: PlatformContext): String {
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

    actual fun hasEntries(context: PlatformContext): Boolean = synchronized(lock) {
        val f = logFile(context)
        okio.FileSystem.SYSTEM.exists(f) && (okio.FileSystem.SYSTEM.metadataOrNull(f)?.size ?: 0L) > 0
    }

    actual fun clear(context: PlatformContext) {
        synchronized(lock) {
            try { logFile(context).delete() } catch (_: Throwable) {}
        }
    }
}
