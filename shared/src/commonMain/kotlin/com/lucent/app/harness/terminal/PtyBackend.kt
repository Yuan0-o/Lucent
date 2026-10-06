package com.lucent.app.harness.terminal

import okio.Path
import okio.Sink
import okio.Source

class TerminalBackendUnavailable(message: String) : IllegalStateException(message)

data class PtyStartRequest(
    val cols: Int = 80,
    val rows: Int = 24,
    val workdir: Path? = null,
    val env: Map<String, String> = emptyMap()
)

interface PtyProcess {
    val input: Sink
    val output: Source
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
