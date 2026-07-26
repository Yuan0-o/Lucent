package com.lucent.desktop.platform

import android.content.Context
import com.lucent.app.data.StartupLog
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * PHASE 3 (codebase review C-4) — the single-instance guard the desktop build never had.
 *
 * ### The problem being solved
 *
 * Launching the exe twice used to yield two full processes on the same encrypted database. WAL
 * tolerates the second connection, but the app's own assumptions do not: two ReminderSchedulers
 * double-fire every reminder, two tray icons appear, and two resident local models would contest
 * RAM. With close-to-tray in the picture (C-3) it gets worse — a hidden first instance makes a
 * second launch the NORMAL way people try to "open Lucent again".
 *
 * ### The mechanism, and why a loopback socket
 *
 * A [ServerSocket] bound to 127.0.0.1 on an ephemeral port doubles as both the mutual-exclusion
 * lock (the OS guarantees one binder) and the signalling channel (the second launch connects and
 * says "focus"), which is exactly the pair of jobs needed. The port is recorded in a file under the
 * app's data dir so the second instance knows where to knock. A `FileLock` alone would give the
 * exclusion but no way to tell the first instance to surface its window — and surfacing is the
 * whole point: "opening Lucent" must mean "show me the one that's already keeping watch".
 *
 * ### Failure policy — same house rule as everything else here
 *
 * Every step degrades to "behave like before this feature existed". If the socket can't bind AND
 * the knock can't be delivered (a stale port file after a crash, a firewall oddity), the launch
 * proceeds as a normal first instance rather than refusing to start: a rare second process is the
 * old, known behaviour; a Lucent that won't open is a new and worse one.
 */
object SingleInstance {

    private const val PORT_FILE = "instance.port"

    /** The token the second instance sends; anything else on the socket is ignored. */
    private const val FOCUS_COMMAND = "LUCENT_FOCUS"

    @Volatile private var server: ServerSocket? = null

    /**
     * Try to become the one instance. Returns true when this process holds the lock (proceed with
     * startup); false when another instance is already running and has been asked to focus — the
     * caller should exit quietly.
     *
     * [onFocusRequested] is invoked (on a background thread) every time a later launch knocks;
     * the caller marshals it onto the UI thread and surfaces the window.
     */
    fun acquire(context: Context, onFocusRequested: () -> Unit): Boolean {
        val portFile = File(context.filesDir, PORT_FILE)

        // 1) Is someone already home? Knock on the recorded port first: if a live instance answers,
        //    hand over and leave. A stale file (crash leftover) simply fails to connect and we fall
        //    through to becoming the instance ourselves.
        val recorded = portFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        if (recorded != null && signalExisting(recorded)) {
            return false
        }

        // 2) Become the instance: bind, record the port, listen for knocks.
        return try {
            val socket = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
            server = socket
            portFile.writeText(socket.localPort.toString())
            thread(isDaemon = true, name = "lucent-single-instance") {
                while (!socket.isClosed) {
                    try {
                        socket.accept().use { client ->
                            client.soTimeout = 1000
                            val line = client.getInputStream().bufferedReader().readLine()
                            if (line == FOCUS_COMMAND) onFocusRequested()
                        }
                    } catch (_: Throwable) {
                        // A malformed knock or a closed socket; keep listening (or fall out of the
                        // loop naturally once the socket is closed at shutdown).
                    }
                }
            }
            true
        } catch (t: Throwable) {
            // Couldn't bind and couldn't reach a prior instance: run standalone, loudly.
            StartupLog.event(context, "single-instance: guard unavailable (${t.message}); starting unguarded")
            true
        }
    }

    /** Deliver the focus knock to a presumed-live instance; true only when it was accepted. */
    private fun signalExisting(port: Int): Boolean = try {
        Socket(InetAddress.getLoopbackAddress(), port).use { s ->
            s.soTimeout = 1000
            s.getOutputStream().write((FOCUS_COMMAND + "\n").toByteArray())
            s.getOutputStream().flush()
        }
        true
    } catch (_: Throwable) {
        false
    }

    /** Release the lock on the way out so the port file never outlives a clean shutdown. */
    fun release() {
        runCatching { server?.close() }
        server = null
    }
}
