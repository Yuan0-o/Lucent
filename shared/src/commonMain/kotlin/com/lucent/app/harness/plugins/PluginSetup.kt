package com.lucent.app.harness.plugins

import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.harness.Workspace
import com.lucent.app.i18n.S
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.math.max
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

enum class SetupStepState { DONE, CURRENT, WAITING }

data class SetupStep(
    val id: String,
    val title: String,
    val body: String,
    val action: String,
    val passed: Boolean,
    val blocks: Boolean,
    val state: SetupStepState = SetupStepState.WAITING
)

data class WizardSnapshot(
    val android: Boolean = false,
    val runtimeMode: String = "auto",
    val builtinState: String = "unavailable",
    val capabilities: Set<String> = emptySet(),
    val workspaceShared: Boolean = false,
    val diskSpaceBytes: Long = 0L,
    val diskSpaceNeeded: Long = 0L,
    val toolsPassed: Boolean = false
)

object PluginSetup {

    const val BUILTIN_ENV_DISK_BYTES = 200_000_000L

    suspend fun snapshot(): WizardSnapshot {
        val android = HarnessRuntime.android
        val host = HarnessRuntime.host
        val config = HarnessRuntime.config()
        val runtimeMode = config.runtimeMode
        val builtinState = host?.builtinRuntimeState() ?: "unavailable"
        val capabilities = HarnessRuntime.capabilities()
        val dir = HarnessRuntime.workspacePath().toPath()
        val workspaceShared = PluginPreflight.sharedStorage(dir)
        val largestDownload = PluginCatalog.effective().maxOfOrNull { it.bytes } ?: 0L
        val diskSpaceBytes = Long.MAX_VALUE
        var diskSpaceNeeded = largestDownload * 2L
        if (android) {
            diskSpaceNeeded = max(diskSpaceNeeded, BUILTIN_ENV_DISK_BYTES)
        }
        val toolsPassed = coroutineScope {
            val tools = PluginCatalog.forPlatformEffective(android).filter { it.id != "playwright" }
            val checks = tools.map { plugin ->
                async {
                    if (config.pluginInstalled(plugin.id)) true
                    else HarnessRuntime.pluginHost?.detect(plugin) == true
                }
            }
            checks.awaitAll().all { it }
        }
        return WizardSnapshot(
            android = android,
            runtimeMode = runtimeMode,
            builtinState = builtinState,
            capabilities = capabilities,
            workspaceShared = workspaceShared,
            diskSpaceBytes = diskSpaceBytes,
            diskSpaceNeeded = diskSpaceNeeded,
            toolsPassed = toolsPassed
        )
    }

    suspend fun computeSteps(): List<SetupStep> = evaluate(snapshot())

    fun evaluate(snapshot: WizardSnapshot): List<SetupStep> {
        val steps = mutableListOf<SetupStep>()
        if (snapshot.android) {
            val diskPassed = snapshot.diskSpaceNeeded <= 0L || snapshot.diskSpaceBytes >= snapshot.diskSpaceNeeded
            steps.add(
                SetupStep(
                    id = "disk_space",
                    title = S.setupDiskSpaceTitle,
                    body = S.setupDiskSpaceBody(Workspace.humanSize(snapshot.diskSpaceBytes), Workspace.humanSize(snapshot.diskSpaceNeeded)),
                    action = S.setupDiskSpaceAction,
                    passed = diskPassed,
                    blocks = true
                )
            )

            val unavailable = snapshot.builtinState == "unavailable"
            steps.add(
                SetupStep(
                    id = "bundled_env",
                    title = S.setupBundledTitle,
                    body = if (unavailable) S.setupBundledUnavailable else S.setupBundledBody,
                    action = if (unavailable) "" else S.setupBundledAction,
                    passed = snapshot.builtinState == "ready",
                    blocks = true
                )
            )

            steps.add(
                SetupStep(
                    id = "base_tools",
                    title = S.setupToolsTitle,
                    body = S.setupToolsBody,
                    action = S.setupToolsAction,
                    passed = snapshot.toolsPassed,
                    blocks = true
                )
            )

            if (!snapshot.workspaceShared) {
                steps.add(
                    SetupStep(
                        id = "workspace_shared",
                        title = S.setupWorkspaceTitle,
                        body = S.setupWorkspaceBody,
                        action = S.setupWorkspaceAction,
                        passed = false,
                        blocks = true
                    )
                )
            }
        } else {
            if (snapshot.diskSpaceNeeded > 0) {
                steps.add(
                    SetupStep(
                        id = "disk_space",
                        title = S.setupDiskSpaceTitle,
                        body = S.setupDiskSpaceBody(Workspace.humanSize(snapshot.diskSpaceBytes), Workspace.humanSize(snapshot.diskSpaceNeeded)),
                        action = S.setupDiskSpaceAction,
                        passed = snapshot.diskSpaceBytes >= snapshot.diskSpaceNeeded,
                        blocks = true
                    )
                )
            }

            steps.add(
                SetupStep(
                    id = "base_tools",
                    title = S.setupToolsTitle,
                    body = S.setupToolsBody,
                    action = S.setupToolsAction,
                    passed = snapshot.toolsPassed,
                    blocks = true
                )
            )
        }

        var foundCurrent = false
        return steps.map { step ->
            val state = if (step.passed) {
                SetupStepState.DONE
            } else if (!foundCurrent) {
                foundCurrent = true
                SetupStepState.CURRENT
            } else {
                SetupStepState.WAITING
            }
            step.copy(state = state)
        }
    }

    fun fullSetupPasses(steps: List<SetupStep>): Boolean = steps.none { it.blocks && !it.passed }
}
