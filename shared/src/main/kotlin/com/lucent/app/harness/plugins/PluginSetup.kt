package com.lucent.app.harness.plugins

import com.lucent.app.harness.HarnessRuntime
import com.lucent.app.harness.Workspace
import com.lucent.app.i18n.S
import java.io.File
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

object PluginSetup {

    suspend fun computeSteps(): List<SetupStep> {
        val steps = mutableListOf<SetupStep>()
        val android = HarnessRuntime.android
        val host = HarnessRuntime.host
        val config = HarnessRuntime.config()
        
        if (android) {
            val termuxRaw = HarnessRuntime.capabilities().contains(HarnessRuntime.CAP_TERMUX) || 
                                  HarnessRuntime.shell?.capabilityNames()?.contains("termux") == true ||
                                  (host?.capabilities()?.contains("termux") ?: false)
            val builtinAvailable = HarnessRuntime.capabilities().contains(HarnessRuntime.CAP_BUILTIN_RUNTIME)
            val useBuiltin = builtinAvailable && config.runtimeMode != "termux"
            val termuxInstalled = termuxRaw || useBuiltin

            steps.add(
                SetupStep(
                    id = "termux_installed",
                    title = S.setupTermuxTitle,
                    body = S.setupTermuxBody,
                    action = S.setupTermuxAction,
                    passed = termuxInstalled,
                    blocks = true
                )
            )

            val configured = if (termuxInstalled) host?.probeShell()?.ok == true else false
            steps.add(
                SetupStep(
                    id = "termux_configured",
                    title = S.setupTermuxConfigTitle,
                    body = S.setupTermuxConfigBody,
                    action = S.setupTermuxConfigAction,
                    passed = configured,
                    blocks = true
                )
            )

            val dir = HarnessRuntime.workspace()
            if (!PluginPreflight.sharedStorage(dir)) {
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
        }

        val largestDownload = PluginCatalog.effective().maxOfOrNull { it.bytes } ?: 0L
        if (largestDownload > 0) {
            val space = HarnessRuntime.downloadsDir().usableSpace
            val needed = largestDownload * 2L
            steps.add(
                SetupStep(
                    id = "disk_space",
                    title = S.setupDiskSpaceTitle,
                    body = S.setupDiskSpaceBody(Workspace.humanSize(space), Workspace.humanSize(needed)),
                    action = S.setupDiskSpaceAction,
                    passed = space >= needed,
                    blocks = true
                )
            )
        }

        if (android) {
            val ubuntuInstalled = config.pluginInstalled("ubuntu")
            steps.add(
                SetupStep(
                    id = "ubuntu_userland",
                    title = S.setupUbuntuTitle,
                    body = S.setupUbuntuBody,
                    action = S.setupUbuntuAction,
                    passed = ubuntuInstalled,
                    blocks = true
                )
            )
        }

        val toolsPassed = coroutineScope {
            val tools = PluginCatalog.forPlatformEffective(android).filter { it.id != "ubuntu" && it.id != "playwright" }
            val checks = tools.map { plugin ->
                async {
                    if (config.pluginInstalled(plugin.id)) true
                    else HarnessRuntime.pluginHost?.detect(plugin) == true
                }
            }
            checks.awaitAll().all { it }
        }
        
        steps.add(
            SetupStep(
                id = "base_tools",
                title = S.setupToolsTitle,
                body = S.setupToolsBody,
                action = S.setupToolsAction,
                passed = toolsPassed,
                blocks = true
            )
        )

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
