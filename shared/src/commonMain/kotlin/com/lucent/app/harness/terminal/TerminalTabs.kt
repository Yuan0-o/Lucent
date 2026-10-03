package com.lucent.app.harness.terminal

class TerminalTab<T>(val id: Long, val number: Int, val value: T, val label: String? = null) {
    @Volatile var closing: Boolean = false
        internal set
}

class TerminalTabs<T> {

    private val tabs = mutableListOf<TerminalTab<T>>()
    private var nextId = 1L
    private var selectedId = 0L
    private var initialized = false

    @Synchronized
    fun add(value: T, label: String? = null): TerminalTab<T> {
        val occupied = HashSet<Int>()
        for (tab in tabs) occupied.add(tab.number)
        var number = 1
        while (occupied.contains(number)) number++
        val tab = TerminalTab(nextId++, number, value, label)
        tabs.add(tab)
        selectedId = tab.id
        initialized = true
        return tab
    }

    @Synchronized
    fun snapshot(): List<TerminalTab<T>> = ArrayList(tabs)

    @Synchronized
    fun current(): TerminalTab<T>? = findLocked(selectedId)

    @Synchronized
    fun find(id: Long): TerminalTab<T>? = findLocked(id)

    @Synchronized
    fun select(id: Long): Boolean {
        if (findLocked(id) == null) return false
        selectedId = id
        return true
    }

    @Synchronized
    fun beginClose(id: Long): Boolean {
        val tab = findLocked(id) ?: return false
        if (tab.closing) return false
        tab.closing = true
        return true
    }

    @Synchronized
    fun closeFailed(id: Long) {
        val tab = findLocked(id) ?: return
        tab.closing = false
    }

    @Synchronized
    fun remove(id: Long) {
        val tab = findLocked(id) ?: return
        val index = tabs.indexOf(tab)
        tabs.remove(tab)
        if (selectedId == id) {
            selectedId = if (tabs.isEmpty()) 0L else tabs[minOf(index, tabs.size - 1)].id
        }
    }

    @Synchronized
    fun clear() {
        tabs.clear()
        selectedId = 0L
    }

    @Synchronized
    fun wasInitialized(): Boolean = initialized

    @Synchronized
    fun size(): Int = tabs.size

    private fun findLocked(id: Long): TerminalTab<T>? {
        for (tab in tabs) if (tab.id == id) return tab
        return null
    }
}
