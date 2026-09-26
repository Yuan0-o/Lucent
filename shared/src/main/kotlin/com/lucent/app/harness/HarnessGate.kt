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
    val db: AppDatabase?,
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
            TodoTools,
            GoalTools,
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

    private val escalations = LinkedHashSet<String>()
    private var escalationConversation = Long.MIN_VALUE

    fun escalatedInThisConversation(name: String): Boolean = synchronized(escalations) {
        val conversation = HarnessRuntime.conversationId
        if (conversation != escalationConversation) {
            escalations.clear()
            escalationConversation = conversation
        }
        escalations.contains(name)
    }

    fun clearEscalations() {
        synchronized(escalations) { escalations.clear() }
    }

    fun escalationProblem(tool: HarnessTool, escalation: HarnessEscalation): String? = when {
        !tool.escalatable -> "${tool.name} is not a tool that can ask for a wider sandbox."
        !HarnessEscalation.MODES.contains(escalation.sandboxPermissions) ->
            "sandbox_permissions must be ${HarnessEscalation.MODES.joinToString(" or ")}."
        escalation.justification.isBlank() -> "A retry with a wider sandbox needs a justification."
        escalatedInThisConversation(tool.name) ->
            "${tool.name} has already been retried once with a wider sandbox in this conversation."
        else -> null
    }

    fun widenedConfig(config: HarnessConfig, argsJson: String, escalation: HarnessEscalation): HarnessConfig {
        val args = try { JSONObject(argsJson) } catch (e: Exception) { JSONObject() }
        val roots = mutableListOf<String>()
        HarnessDescribe.files(args.toString()).forEach { raw ->
            val file = runCatching { Workspace.resolve(raw) }.getOrNull() ?: return@forEach
            val root = if (escalation.fullAccess) file.toPath().root?.toFile() ?: file else file.parentFile ?: file
            if (!Workspace.blocked(root.path)) roots.add(root.path)
        }
        val extra = roots.distinct()
        if (extra.isEmpty()) return config
        return config.copy(writeRoots = (config.writeRoots + extra).distinct())
    }

    private suspend fun run(name: String, ctx: HarnessCtx, args: JSONObject): Attempt = try {
        val module = groups.firstOrNull { module -> module.canHandle(name) }
        val result = module?.execute(ctx, name, args)
            ?: ToolExecResult("Nothing here can run $name.", success = false)
        Attempt(result, false)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: HarnessError) {
        Attempt(ToolExecResult(e.message ?: "${name} was refused", success = false), e.blocked)
    } catch (t: Throwable) {
        Attempt(ToolExecResult("$name failed: ${t.message ?: t::class.simpleName}", success = false), false)
    }

    private class Attempt(val result: ToolExecResult, val blocked: Boolean)

    private fun audit(
        tool: HarnessTool,
        argumentsJson: String,
        at: Long,
        approval: String,
        outcome: String,
        detail: String,
        millis: Long = 0L
    ): AuditEntry = AuditEntry(
        at = at,
        tool = tool.name,
        group = tool.group.key,
        permission = tool.permission.key,
        approval = approval,
        arguments = HarnessDescribe.digest(argumentsJson),
        outcome = outcome,
        detail = detail,
        millis = millis,
        files = HarnessDescribe.files(argumentsJson)
    )

    suspend fun execute(
        context: Context,
        db: AppDatabase?,
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
        val escalation = HarnessEscalation.of(args)
        val started = System.currentTimeMillis()
        if (escalation != null) {
            val problem = escalationProblem(tool, escalation)
            if (problem != null) {
                if (config.auditEnabled) {
                    AuditTrail.record(
                        context,
                        audit(tool, argumentsJson, started, "escalation-refused", "escalation-refused", problem)
                    )
                }
                return ToolExecResult(problem, success = false)
            }
        }
        if (config.auditEnabled && approval == Approval.CONFIRM) {
            AuditTrail.record(
                context,
                audit(tool, argumentsJson, started, "asked", "asked", "waiting for the user to allow ${tool.name}")
            )
        }
        var ctx = HarnessCtx(context, db, config, capabilities, android, HarnessRuntime.workspace())
        var attempt = run(name, ctx, args)
        var escalated = false
        if (attempt.blocked && escalation != null && !escalatedInThisConversation(name)) {
            val wider = widenedConfig(config, argumentsJson, escalation)
            if (wider != config) {
                escalated = true
                synchronized(escalations) { escalations.add(name) }
                if (config.auditEnabled) {
                    AuditTrail.record(
                        context,
                        audit(
                            tool,
                            argumentsJson,
                            System.currentTimeMillis(),
                            "escalated",
                            "escalated",
                            "retrying ${tool.name} once with ${escalation.sandboxPermissions}: " +
                                escalation.justification.take(200)
                        )
                    )
                }
                ctx = ctx.copy(config = wider)
                attempt = run(name, ctx, args)
            }
        }
        val result = attempt.result
        val elapsed = System.currentTimeMillis() - started
        val summary = ctx.limit(result.summary)
        if (config.auditEnabled) {
            AuditTrail.record(
                context,
                audit(
                    tool,
                    argumentsJson,
                    started,
                    if (escalated) "escalated" else approval.key,
                    if (result.success) "ok" else "failed",
                    summary.take(400),
                    elapsed
                )
            )
        }
        runCatching {
            StartupLog.event(
                context,
                "agent tool $name (${tool.group.key}) ${if (result.success) "ok" else "failed"} in ${elapsed}ms"
            )
        }
        return if (summary == result.summary) result else result.copy(summary = summary)
    }
}
