package com.lucent.app.harness

import com.lucent.app.i18n.S
import com.lucent.app.network.ToolDefinition
import com.lucent.app.network.ToolParam
import org.json.JSONObject

enum class HarnessGroup(val key: String) {
    FILES("files"),
    TERMINAL("terminal"),
    GIT("git"),
    GITHUB("github"),
    OFFICE("office"),
    PDF("pdf"),
    BROWSER("browser"),
    DATA("data"),
    SANDBOX("sandbox"),
    MEMORY("memory"),
    PLAN("plan"),
    AGENT("agent"),
    SKILLS("skills"),
    MCP("mcp"),
    PLUGINS("plugins"),
    DEVICE("device"),
    CONNECTORS("connectors");

    val title: String
        get() = when (this) {
            FILES -> S.harnessGroupFiles
            TERMINAL -> S.harnessGroupTerminal
            GIT -> S.harnessGroupGit
            GITHUB -> S.harnessGroupGithub
            OFFICE -> S.harnessGroupOffice
            PDF -> S.harnessGroupPdf
            BROWSER -> S.harnessGroupBrowser
            DATA -> S.harnessGroupData
            SANDBOX -> S.harnessGroupSandbox
            MEMORY -> S.harnessGroupMemory
            PLAN -> S.harnessGroupPlan
            AGENT -> S.harnessGroupAgent
            SKILLS -> S.harnessGroupSkills
            MCP -> S.harnessGroupMcp
            PLUGINS -> S.harnessGroupPlugins
            DEVICE -> S.harnessGroupDevice
            CONNECTORS -> S.harnessGroupConnectors
        }

    companion object {
        val DEFAULT_ON = setOf(FILES, TERMINAL, OFFICE, PLAN, MEMORY, PLUGINS)

        fun of(key: String): HarnessGroup? = entries.firstOrNull { it.key == key }
    }
}

enum class HarnessPermission(val key: String) {
    READ("read"),
    WRITE("write"),
    DELETE("delete"),
    EXECUTE("execute"),
    NETWORK("network"),
    GIT("git"),
    GITHUB("github"),
    BROWSER("browser"),
    SENSITIVE("sensitive"),
    DEVICE("device");

    val title: String
        get() = when (this) {
            READ -> S.harnessPermissionRead
            WRITE -> S.harnessPermissionWrite
            DELETE -> S.harnessPermissionDelete
            EXECUTE -> S.harnessPermissionExecute
            NETWORK -> S.harnessPermissionNetwork
            GIT -> S.harnessPermissionGit
            GITHUB -> S.harnessPermissionGithub
            BROWSER -> S.harnessPermissionBrowser
            SENSITIVE -> S.harnessPermissionSensitive
            DEVICE -> S.harnessPermissionDevice
        }

    val detail: String
        get() = when (this) {
            READ -> S.harnessPermissionReadSub
            WRITE -> S.harnessPermissionWriteSub
            DELETE -> S.harnessPermissionDeleteSub
            EXECUTE -> S.harnessPermissionExecuteSub
            NETWORK -> S.harnessPermissionNetworkSub
            GIT -> S.harnessPermissionGitSub
            GITHUB -> S.harnessPermissionGithubSub
            BROWSER -> S.harnessPermissionBrowserSub
            SENSITIVE -> S.harnessPermissionSensitiveSub
            DEVICE -> S.harnessPermissionDeviceSub
        }

    companion object {
        fun of(key: String): HarnessPermission? = entries.firstOrNull { it.key == key }
    }
}

enum class Approval(val key: String) {
    ALLOW("allow"),
    CONFIRM("confirm"),
    DENY("deny");

    val title: String
        get() = when (this) {
            ALLOW -> S.harnessPolicyAllow
            CONFIRM -> S.harnessPolicyConfirm
            DENY -> S.harnessPolicyDeny
        }

    companion object {
        fun of(key: String): Approval? = entries.firstOrNull { it.key == key }

        fun defaultFor(permission: HarnessPermission): Approval = when (permission) {
            HarnessPermission.READ -> ALLOW
            HarnessPermission.WRITE -> ALLOW
            HarnessPermission.DELETE -> CONFIRM
            HarnessPermission.EXECUTE -> CONFIRM
            HarnessPermission.NETWORK -> ALLOW
            HarnessPermission.GIT -> CONFIRM
            HarnessPermission.GITHUB -> CONFIRM
            HarnessPermission.BROWSER -> ALLOW
            HarnessPermission.SENSITIVE -> CONFIRM
            HarnessPermission.DEVICE -> CONFIRM
        }
    }
}

data class HarnessEscalation(val sandboxPermissions: String, val justification: String) {

    val fullAccess: Boolean get() = sandboxPermissions == FULL_ACCESS

    val usable: Boolean get() = MODES.contains(sandboxPermissions) && justification.isNotBlank()

    companion object {

        const val PARAM_PERMISSIONS = "sandbox_permissions"
        const val PARAM_JUSTIFICATION = "justification"
        const val WORKSPACE_WRITE = "workspace-write"
        const val FULL_ACCESS = "danger-full-access"

        val MODES = setOf(WORKSPACE_WRITE, FULL_ACCESS)

        fun of(args: JSONObject): HarnessEscalation? {
            val mode = args.optString(PARAM_PERMISSIONS, "").trim().lowercase()
            if (mode.isEmpty()) return null
            return HarnessEscalation(mode, args.optString(PARAM_JUSTIFICATION, "").trim())
        }
    }
}

data class HarnessTool(
    val name: String,
    val group: HarnessGroup,
    val permission: HarnessPermission,
    val description: String,
    val params: List<ToolParam> = emptyList(),
    val readOnly: Boolean = permission == HarnessPermission.READ,
    val requires: String? = null,
    val androidOnly: Boolean = false,
    val desktopOnly: Boolean = false
) {
    val escalatable: Boolean
        get() = !readOnly && params.any { it.name == HarnessEscalation.PARAM_PERMISSIONS }

    fun definition(): ToolDefinition = ToolDefinition(name, description, params)

    fun available(android: Boolean, capabilities: Set<String>): Boolean {
        if (androidOnly && !android) return false
        if (desktopOnly && android) return false
        if (requires != null && !capabilities.contains(requires)) return false
        return true
    }
}

object HarnessSchema {

    fun text(name: String, description: String, required: Boolean = true) =
        ToolParam(name, "string", description, required)

    fun number(name: String, description: String, required: Boolean = true) =
        ToolParam(name, "number", description, required)

    fun flag(name: String, description: String, required: Boolean = false) =
        ToolParam(name, "boolean", description, required)

    fun list(name: String, description: String, required: Boolean = true, itemType: String = "string") =
        ToolParam(name, "array", description, required, itemType)

    fun json(name: String, description: String, required: Boolean = true) =
        ToolParam(name, "object", description, required)

    fun escalation(): List<ToolParam> = listOf(
        ToolParam(
            HarnessEscalation.PARAM_PERMISSIONS,
            "string",
            "Ask for a wider sandbox for one retry after this tool was refused: ${HarnessEscalation.WORKSPACE_WRITE} " +
                "widens to the folders this call names, ${HarnessEscalation.FULL_ACCESS} to their filesystem roots. " +
                "Needs justification, is never granted twice in one conversation, and never overrides a denied " +
                "permission or a protected system path.",
            false
        ),
        ToolParam(
            HarnessEscalation.PARAM_JUSTIFICATION,
            "string",
            "One sentence saying why the retry needs the wider sandbox. Required when sandbox_permissions is set.",
            false
        )
    )
}
