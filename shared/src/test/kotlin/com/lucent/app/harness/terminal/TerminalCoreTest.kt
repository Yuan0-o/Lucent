package com.lucent.app.harness.terminal

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakePtyProcess : PtyProcess {
    private val sessionOutput = PipedInputStream(64 * 1024)
    private val processOutput = PipedOutputStream(sessionOutput)
    private val capturedInput = ByteArrayOutputStream()
    private val exitLatch = CountDownLatch(1)
    @Volatile private var code = 0
    @Volatile var destroyed = false
    @Volatile var forciblyDestroyed = false
    @Volatile var lastResize: Pair<Int, Int>? = null

    override val input: OutputStream = object : OutputStream() {
        override fun write(b: Int) {
            capturedInput.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            capturedInput.write(b, off, len)
        }
    }
    override val output: InputStream = sessionOutput

    fun feed(text: String) {
        processOutput.write(text.toByteArray(Charsets.UTF_8))
        processOutput.flush()
    }

    fun writtenText(): String = String(capturedInput.toByteArray(), Charsets.UTF_8)

    fun exit(exitCode: Int) {
        terminate(exitCode)
    }

    private fun terminate(exitCode: Int) {
        code = exitCode
        runCatching { processOutput.close() }
        exitLatch.countDown()
    }

    override fun isAlive(): Boolean = exitLatch.count > 0
    override fun waitFor(): Int {
        exitLatch.await()
        return code
    }
    override fun destroy() {
        destroyed = true
        if (isAlive()) terminate(143)
    }
    override fun destroyForcibly() {
        forciblyDestroyed = true
        if (isAlive()) terminate(137)
    }
    override fun resize(cols: Int, rows: Int) {
        lastResize = cols to rows
    }
}

private class FakePtyBackend : PtyBackend {
    val processes = CopyOnWriteArrayList<FakePtyProcess>()
    override fun isReady(): Boolean = true
    override fun describe(): String = "fake backend"
    override fun start(request: PtyStartRequest): PtyProcess {
        val process = FakePtyProcess()
        processes.add(process)
        return process
    }
}

private class RecordingSessionListener : PtySessionListener {
    val chunks = CopyOnWriteArrayList<String>()
    val exits = CopyOnWriteArrayList<Int>()
    override fun onOutput(session: PtySession, chunk: String) {
        chunks.add(chunk)
    }
    override fun onExit(session: PtySession, exitCode: Int) {
        exits.add(exitCode)
    }
}

private fun awaitUntil(timeoutMs: Long = 4000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(10)
    }
    assertTrue(condition(), "condition not met within ${timeoutMs}ms")
}

class TerminalCoreTest {

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Test
    fun testPtyKeysSequences() {
        assertEquals("\u001B", PtyKeys.ESCAPE)
        assertEquals("\u001B[A", PtyKeys.ARROW_UP)
        assertEquals("\u001B[B", PtyKeys.ARROW_DOWN)
        assertEquals("\u001B[C", PtyKeys.ARROW_RIGHT)
        assertEquals("\u001B[D", PtyKeys.ARROW_LEFT)
        assertEquals("\u007F", PtyKeys.BACKSPACE)
        assertEquals("\r", PtyKeys.ENTER)
        assertEquals("\t", PtyKeys.TAB)
        assertEquals("\u0003", PtyKeys.ctrl('c'))
        assertEquals("\u0003", PtyKeys.ctrl('C'))
        assertEquals("\u0001", PtyKeys.ctrl('a'))
        assertEquals("\u001A", PtyKeys.ctrl('z'))
        assertEquals("", PtyKeys.ctrl('1'))
        assertEquals("\u001Bx", PtyKeys.alt("x"))
    }

    @Test
    fun testTerminalTabsNumberingAndSelection() {
        val tabs = TerminalTabs<String>()
        assertFalse(tabs.wasInitialized())
        val first = tabs.add("a")
        val second = tabs.add("b")
        val third = tabs.add("c")
        assertTrue(tabs.wasInitialized())
        assertEquals(1, first.number)
        assertEquals(2, second.number)
        assertEquals(3, third.number)
        assertEquals(third.id, tabs.current()?.id)

        assertTrue(tabs.beginClose(second.id))
        assertTrue(second.closing)
        val fourth = tabs.add("d")
        assertEquals(4, fourth.number)
        tabs.remove(second.id)
        val fifth = tabs.add("e")
        assertEquals(2, fifth.number)

        assertTrue(tabs.select(first.id))
        tabs.remove(first.id)
        assertEquals(fifth.id, tabs.current()?.id)
        assertFalse(tabs.select(999999L))
        assertNull(tabs.find(424242L))
    }

    @Test
    fun testTerminalTabsCloseFailedClearsFlag() {
        val tabs = TerminalTabs<String>()
        val tab = tabs.add("only")
        assertTrue(tabs.beginClose(tab.id))
        assertFalse(tabs.beginClose(tab.id))
        tabs.closeFailed(tab.id)
        assertFalse(tab.closing)
        assertTrue(tabs.beginClose(tab.id))
    }

    @Test
    fun testPtySessionOutputTranscriptAndExit() {
        val process = FakePtyProcess()
        val session = PtySession(process, 80, 24, testScope)
        val listener = RecordingSessionListener()
        session.listener = listener
        assertEquals(PtySessionState.RUNNING, session.state)

        process.feed("hello ")
        process.feed("world\n")
        awaitUntil { session.transcript() == "hello world\n" }
        assertEquals("hello world\n", listener.chunks.joinToString(""))

        assertTrue(session.write("ls\n"))
        assertEquals("ls\n", process.writtenText())

        process.exit(7)
        awaitUntil { session.state == PtySessionState.EXITED }
        assertEquals(7, session.exitCode)
        awaitUntil { listener.exits.isNotEmpty() }
        assertEquals(listOf(7), listener.exits.toList())
        assertFalse(session.write("after exit"))
        assertEquals("hello world\n", session.transcript())
        session.close()
    }

    @Test
    fun testPtySessionCloseIsIdempotentAndKeepsTranscript() {
        val process = FakePtyProcess()
        val session = PtySession(process, 80, 24, testScope)
        val listener = RecordingSessionListener()
        session.listener = listener
        process.feed("before close")
        awaitUntil { session.transcript() == "before close" }

        session.close()
        session.close()
        assertEquals(PtySessionState.CLOSED, session.state)
        awaitUntil { process.destroyed }
        awaitUntil { listener.exits.isNotEmpty() }
        assertEquals(1, listener.exits.size)
        assertEquals("before close", session.transcript())
        assertFalse(session.write("nope"))
    }

    @Test
    fun testPtySessionResizeClampsAndForwards() {
        val process = FakePtyProcess()
        val session = PtySession(process, 80, 24, testScope)
        session.resize(120, 40)
        assertEquals(120, session.cols)
        assertEquals(40, session.rows)
        assertEquals(120 to 40, process.lastResize)
        session.resize(1, 1)
        assertEquals(4, session.cols)
        assertEquals(2, session.rows)
        session.close()
    }

    @Test
    fun testPtySessionTranscriptIsBounded() {
        val process = FakePtyProcess()
        val session = PtySession(process, 80, 24, testScope)
        val chunk = "a".repeat(4096)
        repeat(80) { process.feed(chunk) }
        process.feed("TAILMARKER")
        awaitUntil { session.transcript().endsWith("TAILMARKER") }
        assertTrue(session.transcript().length <= 300_000)
        assertTrue(session.transcript().length >= 100_000)
        assertTrue(session.transcript().all { it == 'a' || "TAILMARKER".contains(it) })
        session.close()
    }

    @Test
    fun testManagerCreatesSelectsAndClosesSessions() {
        val backend = FakePtyBackend()
        val manager = TerminalSessionManager { backend }
        assertTrue(manager.isAvailable())

        val tabEvents = CopyOnWriteArrayList<Boolean>()
        val outputEvents = CopyOnWriteArrayList<Pair<Long, String>>()
        manager.addListener(object : TerminalSessionListener {
            override fun onTabsChanged() {
                tabEvents.add(true)
            }
            override fun onSessionOutput(tab: TerminalTab<PtySession>, chunk: String) {
                outputEvents.add(tab.id to chunk)
            }
            override fun onSessionExit(tab: TerminalTab<PtySession>, exitCode: Int) {
            }
        })

        val first = manager.createSession()
        val second = manager.createSession(PtyStartRequest(cols = 100, rows = 30))
        assertEquals(1, first.number)
        assertEquals(2, second.number)
        assertEquals(second.id, manager.current()?.id)
        assertEquals(100, second.value.cols)

        backend.processes[0].feed("from first")
        awaitUntil { outputEvents.any { it.first == first.id && it.second.contains("from first") } }

        assertTrue(manager.write(first.id, "echo hi\n"))
        assertEquals("echo hi\n", backend.processes[0].writtenText())

        assertTrue(manager.select(first.id))
        assertEquals(first.id, manager.current()?.id)
        assertTrue(manager.closeSession(second.id))
        assertEquals(1, manager.snapshot().size)
        assertFalse(manager.closeSession(second.id))
        assertFalse(manager.write(987654L, "x"))
        assertTrue(tabEvents.isNotEmpty())
        manager.closeAll()
        assertTrue(manager.snapshot().isEmpty())
    }

    @Test
    fun testManagerThrowsWhenBackendMissing() {
        val manager = TerminalSessionManager { null }
        assertFalse(manager.isAvailable())
        assertFailsWith<TerminalBackendUnavailable> { manager.createSession() }
    }

    @Test
    fun testManagerExitNotification() {
        val backend = FakePtyBackend()
        val manager = TerminalSessionManager { backend }
        val exits = CopyOnWriteArrayList<Pair<Long, Int>>()
        manager.addListener(object : TerminalSessionListener {
            override fun onTabsChanged() {
            }
            override fun onSessionOutput(tab: TerminalTab<PtySession>, chunk: String) {
            }
            override fun onSessionExit(tab: TerminalTab<PtySession>, exitCode: Int) {
                exits.add(tab.id to exitCode)
            }
        })
        val tab = manager.createSession()
        backend.processes[0].exit(3)
        awaitUntil { exits.isNotEmpty() }
        assertEquals(listOf(tab.id to 3), exits.toList())
        awaitUntil { tab.value.state == PtySessionState.EXITED }
        manager.closeAll()
    }
}
