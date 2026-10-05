package com.lucent.app.harness.terminal

import com.lucent.app.AppScope
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface PtySessionListener {
    fun onOutput(session: PtySession, chunk: String)
    fun onExit(session: PtySession, exitCode: Int)
}

class PtySession(
    val process: PtyProcess,
    cols: Int,
    rows: Int,
    private val scope: CoroutineScope = AppScope.io
) {

    @Volatile var cols: Int = cols
        private set
    @Volatile var rows: Int = rows
        private set
    @Volatile var state: PtySessionState = PtySessionState.RUNNING
        private set
    @Volatile var exitCode: Int? = null
        private set
    @Volatile var failure: String = ""
        private set
    @Volatile var listener: PtySessionListener? = null

    private val transcriptLock = Any()
    private val transcript = StringBuilder()
    private val writeLock = Any()
    private val closedByUser = AtomicBoolean(false)
    private val exitFired = AtomicBoolean(false)
    private val pumpJob: Job
    private val watcherJob: Job

    init {
        pumpJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(process.output, Charsets.UTF_8))
                val buffer = CharArray(4096)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    append(String(buffer, 0, read))
                }
            } catch (t: Throwable) {
                if (state == PtySessionState.RUNNING && !closedByUser.get()) {
                    failure = t.message ?: t.toString()
                }
            }
        }
        watcherJob = scope.launch {
            val code = try {
                withContext(Dispatchers.IO) { process.waitFor() }
            } catch (t: Throwable) {
                failure = t.message ?: t.toString()
                -1
            }
            exitCode = code
            if (!closedByUser.get()) {
                state = if (failure.isNotEmpty() && code == -1) PtySessionState.FAILED else PtySessionState.EXITED
            }
            fireExit(code)
        }
    }

    private fun append(chunk: String) {
        synchronized(transcriptLock) {
            transcript.append(chunk)
            if (transcript.length > MAX_TRANSCRIPT_CHARS) {
                transcript.delete(0, transcript.length - KEEP_TRANSCRIPT_CHARS)
            }
        }
        try {
            listener?.onOutput(this, chunk)
        } catch (t: Throwable) {
            failure = t.message ?: t.toString()
        }
    }

    private fun fireExit(code: Int) {
        if (!exitFired.compareAndSet(false, true)) return
        pumpJob.cancel()
        try {
            listener?.onExit(this, code)
        } catch (_: Throwable) {
        }
    }

    fun transcript(): String = synchronized(transcriptLock) { transcript.toString() }

    fun write(text: String): Boolean {
        if (state != PtySessionState.RUNNING || text.isEmpty()) return text.isEmpty()
        return try {
            synchronized(writeLock) {
                process.input.write(text.toByteArray(Charsets.UTF_8))
                process.input.flush()
            }
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun writeKey(sequence: String): Boolean = write(sequence)

    fun resize(newCols: Int, newRows: Int) {
        cols = newCols.coerceIn(4, 1000)
        rows = newRows.coerceIn(2, 1000)
        if (state != PtySessionState.RUNNING) return
        try {
            process.resize(cols, rows)
        } catch (_: Throwable) {
        }
    }

    fun close() {
        if (!closedByUser.compareAndSet(false, true)) return
        if (state == PtySessionState.RUNNING) state = PtySessionState.CLOSED
        try {
            process.input.close()
        } catch (_: Throwable) {
        }
        scope.launch {
            try {
                process.destroy()
                var waited = 0L
                while (process.isAlive() && waited < 1500L) {
                    delay(50L)
                    waited += 50L
                }
                if (process.isAlive()) process.destroyForcibly()
            } catch (_: Throwable) {
            }
        }
    }

    companion object {
        const val MAX_TRANSCRIPT_CHARS = 300_000
        const val KEEP_TRANSCRIPT_CHARS = 100_000
    }
}
