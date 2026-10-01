package com.lucent.app.harness

import com.lucent.app.harness.plugins.PluginSetup
import com.lucent.app.harness.plugins.SetupStepState
import com.lucent.app.harness.plugins.WizardSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PluginSetupTest {

    @Test
    fun testComputeSteps() = runBlocking {
        val steps = PluginSetup.computeSteps()
        assertNotNull(steps)
    }

    @Test
    fun testAutoCapabilityNeedsSetupStepOrderAndCurrentMarking() {
        val snapshot = WizardSnapshot(
            android = true,
            runtimeMode = "auto",
            builtinState = "needs_setup",
            capabilities = setOf(HarnessRuntime.CAP_BUILTIN_RUNTIME),
            diskSpaceBytes = 1000L,
            diskSpaceNeeded = 100L,
            workspaceShared = false,
            toolsPassed = false
        )
        val steps = PluginSetup.evaluate(snapshot)
        val stepIds = steps.map { it.id }
        assertEquals(listOf("disk_space", "bundled_env", "base_tools", "workspace_shared"), stepIds)
        assertEquals(SetupStepState.DONE, steps[0].state)
        assertEquals(SetupStepState.CURRENT, steps[1].state)
        assertEquals(SetupStepState.WAITING, steps[2].state)
        assertEquals(SetupStepState.WAITING, steps[3].state)
    }

    @Test
    fun testReadyMarksBundledEnvDone() {
        val snapshot = WizardSnapshot(
            android = true,
            runtimeMode = "auto",
            builtinState = "ready",
            capabilities = setOf(HarnessRuntime.CAP_BUILTIN_RUNTIME),
            diskSpaceBytes = 1000L,
            diskSpaceNeeded = 100L,
            workspaceShared = true,
            toolsPassed = false
        )
        val steps = PluginSetup.evaluate(snapshot)
        val stepIds = steps.map { it.id }
        assertEquals(listOf("disk_space", "bundled_env", "base_tools"), stepIds)
        assertEquals(SetupStepState.DONE, steps[0].state)
        assertEquals(SetupStepState.DONE, steps[1].state)
        assertEquals(SetupStepState.CURRENT, steps[2].state)
    }

    @Test
    fun testTermuxModeUnchangedIds() {
        val snapshot = WizardSnapshot(
            android = true,
            runtimeMode = "termux",
            builtinState = "ready",
            capabilities = setOf(HarnessRuntime.CAP_BUILTIN_RUNTIME, HarnessRuntime.CAP_TERMUX),
            diskSpaceBytes = 1000L,
            diskSpaceNeeded = 100L,
            workspaceShared = false,
            toolsPassed = false
        )
        val steps = PluginSetup.evaluate(snapshot)
        val stepIds = steps.map { it.id }
        assertEquals(listOf("termux_installed", "termux_configured", "workspace_shared", "disk_space", "ubuntu_userland", "base_tools"), stepIds)
    }

    @Test
    fun testAutoWithoutCapabilityUsesTermuxSteps() {
        val snapshot = WizardSnapshot(
            android = true,
            runtimeMode = "auto",
            builtinState = "unavailable",
            capabilities = emptySet(),
            diskSpaceBytes = 1000L,
            diskSpaceNeeded = 100L,
            workspaceShared = false,
            toolsPassed = false
        )
        val steps = PluginSetup.evaluate(snapshot)
        val stepIds = steps.map { it.id }
        assertEquals(listOf("termux_installed", "termux_configured", "workspace_shared", "disk_space", "ubuntu_userland", "base_tools"), stepIds)
    }

    @Test
    fun testBuiltinEnvDiskBytesConstant() {
        assertEquals(200_000_000L, PluginSetup.BUILTIN_ENV_DISK_BYTES)
    }
}
