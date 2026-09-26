package com.lucent.app.harness

import android.content.Context
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.StartupLog
import com.lucent.app.network.ToolDefinition
import com.lucent.app.network.ToolExecResult
import org.json.JSONObject
import java.io.File

data class HarnessCtx(
    val context: Context,
    val db: AppDatabase,
    val config: HarnessConfig,
    val capabilities: Set<String>,
    val android: Boolean,
    val workspace: File
) {
    fun cap(name: String): Boolean = capabilities.contains(name)

    fun shellReady(): Boolean = cap(HarnessRuntime.CAP_SHELL)

    fun limit(text: String): String {
        val cap = config.maxOutputChars.coerceIn(2000, 200000)
        return if (text.length <= cap) text else text.take(cap) + "\n… output truncated at $cap characters."
    }
}

interface HarnessGroupTools {
    val group: HarnessGroup
    val tools: List<HarnessTool>
    suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult?
    fun canHandle(name: String): Boolean = tools.any { it.name == name }
}

object HarnessGate {

    @Volatile var dynamicTools: List<HarnessTool> = emptyList()

    private val groups: List<HarnessGroupTools> by lazy {
        listOf(
            FileTools,
            TerminalTools,
            OfficeDocTools,
            OfficeSheetTools,
            OfficeDeckTools,
            OfficeConvertTools,
            PdfTools,
            BrowserTools,
            DatabaseTools,
            GitTools,
            GitHubTools,
            SandboxTools,
            MemoryTools,
            PlanTools,
            AgentTools,
            SkillTools,
            McpTools,
            PluginTools,
            ConnectorTools,
            DeviceTools
        )
    }

    fun groupModules(): List<HarnessGroupTools> = groups

    fun allTools(): List<HarnessTool> = groups.flatMap { it.tools } + dynamicTools

    fun find(name: String): HarnessTool? = allTools().firstOrNull { it.name == name }

    fun isHarnessTool(name: String): Boolean = find(name) != null

    fun permissionOf(name: String): HarnessPermission? = find(name)?.permission

    fun groupOf(name: String): HarnessGroup? = find(name)?.group

    fun enabledTools(android: Boolean, capabilities: Set<String> = HarnessRuntime.capabilities()): List<HarnessTool> {
        val config = HarnessRuntime.config()
        if (!config.enabled) return emptyList()
        return allTools().filter { tool ->
            (config.groupEnabled(tool.group) || (tool.group == HarnessGroup.DEVICE && config.deviceEnabled)) &&
                (tool.group != HarnessGroup.DEVICE || config.deviceEnabled) &&
                tool.available(android, capabilities) &&
                supports(tool, capabilities)
        }
    }

    private fun supports(tool: HarnessTool, capabilities: Set<String>): Boolean {
        val platform = if (HarnessRuntime.android) "android" else "desktop"
        val declared = tool.requires ?: return true
        if (declared == platform) return true
        if (declared == HarnessRuntime.CAP_SHELL) return capabilities.contains(HarnessRuntime.CAP_SHELL)
        if (declared == HarnessRuntime.CAP_DEVICE) return capabilities.contains(HarnessRuntime.CAP_DEVICE)
        if (declared == HarnessRuntime.CAP_PLUGINS) return capabilities.contains(HarnessRuntime.CAP_PLUGINS)
        return capabilities.contains(declared)
    }

    fun definitions(android: Boolean, capabilities: Set<String> = HarnessRuntime.capabilities()): List<ToolDefinition> =
        enabledTools(android, capabilities).map { it.definition() }

    fun needsConfirmation(name: String): Boolean {
        val tool = find(name) ?: return true
        return HarnessRuntime.config().approvalFor(tool.permission) == Approval.CONFIRM
    }

    fun describe(name: String, argsJson: String): String = HarnessDescribe.describe(name, argsJson)

    suspend fun execute(
        context: Context,
        db: AppDatabase,
        name: String,
        argumentsJson: String
    ): ToolExecResult {
        val tool = find(name) ?: return ToolExecResult("Unknown tool: $name", success = false)
        val config = HarnessRuntime.config()
        val capabilities = HarnessRuntime.capabilities()
        val android = HarnessRuntime.android
        if (!config.enabled) return ToolExecResult("The agent workspace is switched off in Settings.", success = false)
        if (!config.groupEnabled(tool.group)) {
            return ToolExecResult("The ${tool.group.title} tools are switched off in Settings.", success = false)
        }
        if (tool.group == HarnessGroup.DEVICE && !config.deviceEnabled) {
            return ToolExecResult("Device control is switched off in Settings.", success = false)
        }
        if (!tool.available(android, capabilities) || !supports(tool, capabilities)) {
            return ToolExecResult("${tool.name} needs ${tool.requires ?: "a plugin"} first. Use plugin_status or install_plugin.", success = false)
        }
        val approval = config.approvalFor(tool.permission)
        if (approval == Approval.DENY) {
            return ToolExecResult(
                "${tool.permission.title} access is blocked by the permission policy, so ${tool.name} was not run.",
                success = false
            )
        }
        val args = try { JSONObject(argumentsJson) } catch (e: Exception) { JSONObject() }
        val started = System.currentTimeMillis()
        val ctx = HarnessCtx(context.applicationContext, db, config, capabilities, android, HarnessRuntime.workspace())
        if (config.auditEnabled && approval == Approval.CONFIRM) {
            AuditTrail.record(
                context.applicationContext,
                AuditEntry(
                    at = started,
                    tool = name,
                    group = tool.group.key,
                    permission = tool.permission.key,
                    approval = "asked",
                    arguments = HarnessDescribe.digest(argumentsJson),
                    outcome = "asked",
                    detail = "waiting for the user to allow ${tool.name}",
                    millis = 0L,
                    files = HarnessDescribe.files(argumentsJson)
                )
            )
        }
        val result = try {
            val module = groups.firstOrNull { module -> module.canHandle(name) }
            module?.execute(ctx, name, args)
                ?: ToolExecResult("Nothing here can run ${tool.name}.", success = false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            ToolExecResult("${tool.name} failed: ${t.message ?: t::class.simpleName}", success = false)
        }
        val elapsed = System.currentTimeMillis() - started
        val summary = ctx.limit(result.summary)
        if (config.auditEnabled) {
            AuditTrail.record(
                context.applicationContext,
                AuditEntry(
                    at = started,
                    tool = name,
                    group = tool.group.key,
                    permission = tool.permission.key,
                    approval = approval.key,
                    arguments = HarnessDescribe.digest(argumentsJson),
                    outcome = if (result.success) "ok" else "failed",
                    detail = summary.take(400),
                    millis = elapsed,
                    files = HarnessDescribe.files(argumentsJson)
                )
            )
        }
        StartupLog.event(
            context.applicationContext,
            "agent tool $name (${tool.group.key}) ${if (result.success) "ok" else "failed"} in ${elapsed}ms"
        )
        return if (summary == result.summary) result else result.copy(summary = summary)
    }
}
