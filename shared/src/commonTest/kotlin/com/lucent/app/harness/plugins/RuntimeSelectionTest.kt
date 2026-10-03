package com.lucent.app.harness.plugins

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeSelectionTest {
    @Test
    fun testSelectBackend() {
        assertEquals(RuntimeBackend.BUILTIN, selectBackend(true))
        assertEquals(RuntimeBackend.NONE, selectBackend(false))
    }
}
