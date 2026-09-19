package com.lucent.desktop.platform

import android.content.Context
import com.lucent.app.data.StartupLog
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

object SingleInstance {

    private const val PORT_FILE = "instance.port"

    private const val FOCUS_COMMAND = "LUCENT_FOCUS"

    @Volatile private var server: ServerSocket? = null

    fun acquire(context: Context, onFocusRequested: () -> Unit): Boolean {
        val portFile = File(context.filesDir, PORT_FILE)

        val recorded = portFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        if (recorded != null && signalExisting(recorded)) {
            return false
        }

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
                    }
                }
            }
            true
        } catch (t: Throwable) {
            StartupLog.event(context, "single-instance: guard unavailable (${t.message}); starting unguarded")
            true
        }
    }

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

    fun release() {
        runCatching { server?.close() }
        server = null
    }
}
