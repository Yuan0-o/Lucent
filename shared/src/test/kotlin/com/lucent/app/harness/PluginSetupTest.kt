package com.lucent.app.harness

import com.lucent.app.harness.plugins.PluginSetup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class PluginSetupTest {
    @Test
    fun testComputeSteps() = runBlocking {
        val steps = PluginSetup.computeSteps()
        assertNotNull(steps)
    }
}
