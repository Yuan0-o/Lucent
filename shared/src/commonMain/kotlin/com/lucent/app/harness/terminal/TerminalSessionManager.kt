package com.lucent.app.harness.terminal

import com.lucent.app.harness.HarnessRuntime

interface TerminalSessionListener {
    fun onTabsChanged()
    fun onSessionOutput(tab: TerminalTab<PtySession>, chunk: String)
    fun onSessionExit(tab: TerminalTab<PtySession>, exitCode: Int)
}

class TerminalSessionManager(
    private val backendProvider: () -> PtyBackend?
) {

    private val tabs = TerminalTabs<PtySession>()
    private val listenersLock = Any()
    private val listeners = mutableListOf<TerminalSessionListener>()

    private val sessionForwarder = object : PtySessionListener {
        override fun onOutput(session: PtySession, chunk: String) {
            val tab = tabOf(session) ?: return
            for (listener in listenerSnapshot()) {
                try {
                    listener.onSessionOutput(tab, chunk)
                } catch (_: Throwable) {
                }
            }
        }

        override fun onExit(session: PtySession, exitCode: Int) {
            val tab = tabOf(session) ?: return
            HarnessRuntime.note("terminal: session ${tab.number} exited with code $exitCode")
            for (listener in listenerSnapshot()) {
                try {
                    listener.onSessionExit(tab, exitCode)
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun addListener(listener: TerminalSessionListener) {
        synchronized(listenersLock) { listeners.add(listener) }
    }

    fun removeListener(listener: TerminalSessionListener) {
        synchronized(listenersLock) { listeners.remove(listener) }
    }

    fun snapshot(): List<TerminalTab<PtySession>> = tabs.snapshot()

    fun current(): TerminalTab<PtySession>? = tabs.current()

    fun find(id: Long): TerminalTab<PtySession>? = tabs.find(id)

    fun wasInitialized(): Boolean = tabs.wasInitialized()

    fun isAvailable(): Boolean {
        val backend = backendProvider() ?: return false
        return backend.isReady()
    }

    fun describe(): String {
        val backend = backendProvider() ?: return "no terminal backend is registered on this platform"
        return backend.describe()
    }

    fun createSession(request: PtyStartRequest = PtyStartRequest(), label: String? = null): TerminalTab<PtySession> {
        val backend = backendProvider()
            ?: throw TerminalBackendUnavailable("No terminal backend is registered on this platform")
        if (!backend.isReady()) throw TerminalBackendUnavailable(backend.describe())
        val process = try {
            backend.start(request)
        } catch (t: TerminalBackendUnavailable) {
            throw t
        } catch (t: Throwable) {
            throw TerminalBackendUnavailable(t.message ?: "The terminal session could not be started")
        }
        val session = PtySession(process, request.cols, request.rows)
        session.listener = sessionForwarder
        val tab = tabs.add(session, label)
        HarnessRuntime.note("terminal: session ${tab.number} opened")
        notifyTabsChanged()
        return tab
    }

    fun select(id: Long): Boolean {
        val selected = tabs.select(id)
        if (selected) notifyTabsChanged()
        return selected
    }

    fun closeSession(id: Long): Boolean {
        val tab = tabs.find(id) ?: return false
        if (!tabs.beginClose(id)) return false
        tab.value.close()
        tabs.remove(id)
        HarnessRuntime.note("terminal: session ${tab.number} closed")
        notifyTabsChanged()
        return true
    }

    fun write(id: Long, text: String): Boolean {
        val tab = tabs.find(id) ?: return false
        return tab.value.write(text)
    }

    fun writeToCurrent(text: String): Boolean {
        val tab = tabs.current() ?: return false
        return tab.value.write(text)
    }

    fun resize(id: Long, cols: Int, rows: Int): Boolean {
        val tab = tabs.find(id) ?: return false
        tab.value.resize(cols, rows)
        return true
    }

    fun resizeCurrent(cols: Int, rows: Int): Boolean {
        val tab = tabs.current() ?: return false
        tab.value.resize(cols, rows)
        return true
    }

    fun closeAll() {
        val all = tabs.snapshot()
        if (all.isEmpty()) return
        for (tab in all) {
            tabs.beginClose(tab.id)
            tab.value.close()
        }
        tabs.clear()
        HarnessRuntime.note("terminal: all sessions closed")
        notifyTabsChanged()
    }

    private fun tabOf(session: PtySession): TerminalTab<PtySession>? {
        for (tab in tabs.snapshot()) {
            if (tab.value === session) return tab
        }
        return null
    }

    private fun listenerSnapshot(): List<TerminalSessionListener> =
        synchronized(listenersLock) { ArrayList(listeners) }

    private fun notifyTabsChanged() {
        for (listener in listenerSnapshot()) {
            try {
                listener.onTabsChanged()
            } catch (_: Throwable) {
            }
        }
    }
}

object TerminalSessions {
    val manager: TerminalSessionManager by lazy {
        TerminalSessionManager { HarnessRuntime.terminalBackend }
    }
}
