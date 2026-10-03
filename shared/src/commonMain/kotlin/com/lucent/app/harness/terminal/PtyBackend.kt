package com.lucent.app.harness.terminal

import java.io.File
import java.io.InputStream
import java.io.OutputStream

class TerminalBackendUnavailable(message: String) : IllegalStateException(message)

data class PtyStartRequest(
    val cols: Int = 80,
    val rows: Int = 24,
    val workdir: File? = null,
    val env: Map<String, String> = emptyMap()
)

interface PtyProcess {
    val input: OutputStream
    val output: InputStream
    fun isAlive(): Boolean
    fun waitFor(): Int
    fun destroy()
    fun destroyForcibly()
    fun resize(cols: Int, rows: Int)
}

interface PtyBackend {
    fun isReady(): Boolean
    fun describe(): String
    fun start(request: PtyStartRequest): PtyProcess
}
