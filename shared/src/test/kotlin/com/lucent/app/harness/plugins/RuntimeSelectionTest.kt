package com.lucent.app.harness.plugins

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeSelectionTest {

    @Test
    fun `termux mode returns TERMUX if termuxReady`() {
        assertEquals(RuntimeBackend.TERMUX, selectBackend("termux", true, true))
        assertEquals(RuntimeBackend.TERMUX, selectBackend("termux", true, false))
    }

    @Test
    fun `termux mode returns NONE if not termuxReady`() {
        assertEquals(RuntimeBackend.NONE, selectBackend("termux", false, true))
        assertEquals(RuntimeBackend.NONE, selectBackend("termux", false, false))
    }

    @Test
    fun `builtin mode returns BUILTIN if builtinAvailable`() {
        assertEquals(RuntimeBackend.BUILTIN, selectBackend("builtin", true, true))
        assertEquals(RuntimeBackend.BUILTIN, selectBackend("builtin", false, true))
    }

    @Test
    fun `builtin mode returns NONE if not builtinAvailable`() {
        assertEquals(RuntimeBackend.NONE, selectBackend("builtin", true, false))
        assertEquals(RuntimeBackend.NONE, selectBackend("builtin", false, false))
    }

    @Test
    fun `auto mode prioritizes BUILTIN then TERMUX then NONE`() {
        assertEquals(RuntimeBackend.BUILTIN, selectBackend("auto", true, true))
        assertEquals(RuntimeBackend.TERMUX, selectBackend("auto", true, false))
        assertEquals(RuntimeBackend.BUILTIN, selectBackend("auto", false, true))
        assertEquals(RuntimeBackend.NONE, selectBackend("auto", false, false))
    }
    
    @Test
    fun `unknown mode defaults to auto logic`() {
        assertEquals(RuntimeBackend.BUILTIN, selectBackend("unknown", true, true))
        assertEquals(RuntimeBackend.TERMUX, selectBackend("unknown", true, false))
        assertEquals(RuntimeBackend.BUILTIN, selectBackend("unknown", false, true))
        assertEquals(RuntimeBackend.NONE, selectBackend("unknown", false, false))
    }
}
