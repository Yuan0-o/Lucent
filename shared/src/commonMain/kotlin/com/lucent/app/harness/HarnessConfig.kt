package com.lucent.app.harness

import kotlinx.serialization.json.*

data class McpServer(
    val id: String,
    val name: String,
    val url: String = "",
    val command: String = "",
    val arguments: String = "",
    val token: String = "",
    val enabled: Boolean = true
)

data class ConnectorConfig(
    val id: String,
    val token: String = "",
    val baseUrl: String = "",
    val account: String = ""
)

data class PluginState(
    val id: String,
    val installed: Boolean = false,
    val source: String = "",
    val version: String = "",
    val sizeBytes: Long = 0L,
    val installedAt: Long = 0L
)

data class GithubToken(
    val id: String,
    val name: String = "",
    val token: String = ""
)

data class HarnessConfig(
    val enabled: Boolean = false,
    val workspace: String = "",
    val writeRoots: List<String> = emptyList(),
    val readOnlyRoots: List<String> = emptyList(),
    val groups: Set<String> = HarnessGroup.DEFAULT_ON.map { it.key }.toSet(),
    val approvals: Map<String, String> = emptyMap(),
    val shellEnabled: Boolean = true,
    val sandboxMode: String = "auto",
    val timeoutSeconds: Int = 300,
    val maxOutputChars: Int = 24000,
    val snapshots: Boolean = true,
    val snapshotLimit: Int = 120,
    val auditEnabled: Boolean = true,
    val deviceEnabled: Boolean = false,
    val subAgents: Boolean = true,
    val maxSubAgents: Int = 3,
    val skillDirs: List<String> = emptyList(),
    val githubToken: String = "",
    val githubTokens: List<GithubToken> = emptyList(),
    val githubActiveId: String = "",
    val githubApi: String = "https://api.github.com",
    val shizukuForAssistant: Boolean = false,
    val mcpServers: List<McpServer> = emptyList(),
    val connectors: List<ConnectorConfig> = emptyList(),
    val plugins: List<PluginState> = emptyList(),
    val fastMirror: Boolean = true,
    val contextBudgetTokens: Int = ContextBudget.DEFAULT_BUDGET_TOKENS,
    val pluginMirrorRegion: String = "auto",
    val runtimeMode: String = "auto",
    val builtinRootfsVersion: Int = 0,
    val setupComplete: Boolean = false,
    val setupCompletedAt: Long = 0L,
    val pluginBackupScope: String = "state",
    val pluginCatalogUrl: String = "",
    val pluginCatalogCacheEpoch: Long = 0L,
    val pluginPendingReinstall: List<String> = emptyList()
) {

    fun approvalFor(permission: HarnessPermission): Approval =
        Approval.of(approvals[permission.key] ?: "") ?: Approval.defaultFor(permission)

    fun groupEnabled(group: HarnessGroup): Boolean = groups.contains(group.key)

    fun plugin(id: String): PluginState? = plugins.firstOrNull { it.id == id }

    fun pluginInstalled(id: String): Boolean = plugin(id)?.installed == true

    fun installedPlugins(): Set<String> = plugins.filter { it.installed }.map { it.id }.toSet()

    fun withPlugin(state: PluginState): HarnessConfig =
        copy(plugins = plugins.filterNot { it.id == state.id } + state)

    fun withApproval(permission: HarnessPermission, approval: Approval): HarnessConfig =
        copy(approvals = approvals + (permission.key to approval.key))

    fun withGroup(group: HarnessGroup, on: Boolean): HarnessConfig =
        copy(groups = if (on) groups + group.key else groups - group.key)

    fun withMcp(server: McpServer): HarnessConfig =
        copy(mcpServers = mcpServers.filterNot { it.id == server.id } + server)

    fun withoutMcp(id: String): HarnessConfig = copy(mcpServers = mcpServers.filterNot { it.id == id })

    fun withConnector(connector: ConnectorConfig): HarnessConfig =
        copy(connectors = connectors.filterNot { it.id == connector.id } + connector)

    fun connector(id: String): ConnectorConfig? = connectors.firstOrNull { it.id == id }

    fun withGithubTokens(tokens: List<GithubToken>, activeId: String = githubActiveId): HarnessConfig {
        val kept = tokens.filter { it.token.isNotBlank() }.take(MAX_GITHUB_TOKENS)
        val active = kept.firstOrNull { it.id == activeId } ?: kept.firstOrNull()
        return copy(
            githubTokens = kept,
            githubActiveId = active?.id.orEmpty(),
            githubToken = active?.token.orEmpty()
        )
    }

    fun toJson(): String = toJsonObject().toString()

    fun toJsonObject(): JsonObject = buildJsonObject {
        put("enabled", enabled)
        put("workspace", workspace)
        put("writeRoots", JsonArray(writeRoots.map { JsonPrimitive(it) }))
        put("readOnlyRoots", JsonArray(readOnlyRoots.map { JsonPrimitive(it) }))
        put("groups", JsonArray(groups.toList().sorted().map { JsonPrimitive(it) }))
        put("approvals", JsonObject(approvals.mapValues { JsonPrimitive(it.value) }))
        put("shellEnabled", shellEnabled)
        put("sandboxMode", sandboxMode)
        put("timeoutSeconds", timeoutSeconds)
        put("maxOutputChars", maxOutputChars)
        put("snapshots", snapshots)
        put("snapshotLimit", snapshotLimit)
        put("auditEnabled", auditEnabled)
        put("deviceEnabled", deviceEnabled)
        put("subAgents", subAgents)
        put("maxSubAgents", maxSubAgents)
        put("skillDirs", JsonArray(skillDirs.map { JsonPrimitive(it) }))
        put("githubToken", githubToken)
        put("githubTokens", buildJsonArray {
            githubTokens.forEach { entry ->
                add(buildJsonObject {
                    put("id", entry.id)
                    put("name", entry.name)
                    put("token", entry.token)
                })
            }
        })
        put("githubActiveId", githubActiveId)
        put("githubApi", githubApi)
        put("shizukuForAssistant", shizukuForAssistant)
        put("mcpServers", buildJsonArray {
            mcpServers.forEach { server ->
                add(buildJsonObject {
                    put("id", server.id)
                    put("name", server.name)
                    put("url", server.url)
                    put("command", server.command)
                    put("arguments", server.arguments)
                    put("token", server.token)
                    put("enabled", server.enabled)
                })
            }
        })
        put("connectors", buildJsonArray {
            connectors.forEach { connector ->
                add(buildJsonObject {
                    put("id", connector.id)
                    put("token", connector.token)
                    put("baseUrl", connector.baseUrl)
                    put("account", connector.account)
                })
            }
        })
        put("plugins", buildJsonArray {
            plugins.forEach { plugin ->
                add(buildJsonObject {
                    put("id", plugin.id)
                    put("installed", plugin.installed)
                    put("source", plugin.source)
                    put("version", plugin.version)
                    put("sizeBytes", plugin.sizeBytes)
                    put("installedAt", plugin.installedAt)
                })
            }
        })
        put("fastMirror", fastMirror)
        put("contextBudgetTokens", contextBudgetTokens)
        put("pluginMirrorRegion", pluginMirrorRegion)
        put("runtimeMode", runtimeMode)
        put("builtinRootfsVersion", builtinRootfsVersion)
        put("setupComplete", setupComplete)
        put("setupCompletedAt", setupCompletedAt)
        put("pluginBackupScope", pluginBackupScope)
        put("pluginCatalogUrl", pluginCatalogUrl)
        put("pluginCatalogCacheEpoch", pluginCatalogCacheEpoch)
        put("pluginPendingReinstall", JsonArray(pluginPendingReinstall.map { JsonPrimitive(it) }))
    }

    companion object {

        const val MAX_GITHUB_TOKENS = 5

        val DEFAULT = HarnessConfig()

        fun parse(raw: String): HarnessConfig {
            if (raw.isBlank()) return DEFAULT
            val o = try { Json.parseToJsonElement(raw).jsonObject } catch (e: Exception) { return DEFAULT }
            val defaults = DEFAULT
            return HarnessConfig(
                enabled = o["enabled"]?.jsonPrimitive?.booleanOrNull ?: defaults.enabled,
                workspace = o["workspace"]?.jsonPrimitive?.content ?: defaults.workspace,
                writeRoots = strings(o["writeRoots"]?.jsonArray),
                readOnlyRoots = strings(o["readOnlyRoots"]?.jsonArray),
                groups = strings(o["groups"]?.jsonArray).toSet().ifEmpty { defaults.groups },
                approvals = map(o["approvals"]?.jsonObject),
                shellEnabled = o["shellEnabled"]?.jsonPrimitive?.booleanOrNull ?: defaults.shellEnabled,
                sandboxMode = o["sandboxMode"]?.jsonPrimitive?.content ?: defaults.sandboxMode,
                timeoutSeconds = o["timeoutSeconds"]?.jsonPrimitive?.intOrNull ?: defaults.timeoutSeconds,
                maxOutputChars = o["maxOutputChars"]?.jsonPrimitive?.intOrNull ?: defaults.maxOutputChars,
                snapshots = o["snapshots"]?.jsonPrimitive?.booleanOrNull ?: defaults.snapshots,
                snapshotLimit = o["snapshotLimit"]?.jsonPrimitive?.intOrNull ?: defaults.snapshotLimit,
                auditEnabled = o["auditEnabled"]?.jsonPrimitive?.booleanOrNull ?: defaults.auditEnabled,
                deviceEnabled = o["deviceEnabled"]?.jsonPrimitive?.booleanOrNull ?: defaults.deviceEnabled,
                subAgents = o["subAgents"]?.jsonPrimitive?.booleanOrNull ?: defaults.subAgents,
                maxSubAgents = o["maxSubAgents"]?.jsonPrimitive?.intOrNull ?: defaults.maxSubAgents,
                skillDirs = strings(o["skillDirs"]?.jsonArray),
                githubToken = o["githubToken"]?.jsonPrimitive?.content ?: "",
                githubTokens = githubTokens(o["githubTokens"]?.jsonArray, o["githubToken"]?.jsonPrimitive?.content ?: ""),
                githubActiveId = o["githubActiveId"]?.jsonPrimitive?.content ?: "",
                githubApi = (o["githubApi"]?.jsonPrimitive?.content ?: defaults.githubApi).ifBlank { defaults.githubApi },
                shizukuForAssistant = o["shizukuForAssistant"]?.jsonPrimitive?.booleanOrNull ?: defaults.shizukuForAssistant,
                mcpServers = servers(o["mcpServers"]?.jsonArray),
                connectors = connectors(o["connectors"]?.jsonArray),
                plugins = plugins(o["plugins"]?.jsonArray),
                fastMirror = o["fastMirror"]?.jsonPrimitive?.booleanOrNull ?: defaults.fastMirror,
                contextBudgetTokens = o["contextBudgetTokens"]?.jsonPrimitive?.intOrNull ?: defaults.contextBudgetTokens,
                pluginMirrorRegion = o["pluginMirrorRegion"]?.jsonPrimitive?.content ?: defaults.pluginMirrorRegion,
                runtimeMode = o["runtimeMode"]?.jsonPrimitive?.content ?: defaults.runtimeMode,
                builtinRootfsVersion = o["builtinRootfsVersion"]?.jsonPrimitive?.intOrNull ?: defaults.builtinRootfsVersion,
                setupComplete = o["setupComplete"]?.jsonPrimitive?.booleanOrNull ?: defaults.setupComplete,
                setupCompletedAt = o["setupCompletedAt"]?.jsonPrimitive?.longOrNull ?: defaults.setupCompletedAt,
                pluginBackupScope = o["pluginBackupScope"]?.jsonPrimitive?.content ?: defaults.pluginBackupScope,
                pluginCatalogUrl = o["pluginCatalogUrl"]?.jsonPrimitive?.content ?: defaults.pluginCatalogUrl,
                pluginCatalogCacheEpoch = o["pluginCatalogCacheEpoch"]?.jsonPrimitive?.longOrNull ?: defaults.pluginCatalogCacheEpoch,
                pluginPendingReinstall = strings(o["pluginPendingReinstall"]?.jsonArray)
            )
        }

        private fun strings(array: JsonArray?): List<String> {
            if (array == null) return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until array.size) {
                val value = array[i].jsonPrimitive.content
                if (value.isNotBlank()) out.add(value)
            }
            return out
        }

        private fun map(obj: JsonObject?): Map<String, String> {
            if (obj == null) return emptyMap()
            val out = mutableMapOf<String, String>()
            for ((key, value) in obj) {
                out[key] = value.jsonPrimitive.content
            }
            return out
        }

        private fun githubTokens(array: JsonArray?, legacy: String): List<GithubToken> {
            val out = mutableListOf<GithubToken>()
            if (array != null) {
                for (i in 0 until array.size) {
                    val o = runCatching { array[i].jsonObject }.getOrNull() ?: continue
                    val token = o["token"]?.jsonPrimitive?.content ?: ""
                    if (token.isBlank()) continue
                    out.add(
                        GithubToken(
                            id = (o["id"]?.jsonPrimitive?.content ?: "").ifBlank { "gh-${out.size + 1}" },
                            name = o["name"]?.jsonPrimitive?.content ?: "",
                            token = token
                        )
                    )
                }
            }
            if (out.isEmpty() && legacy.isNotBlank()) out.add(GithubToken(id = "gh-1", token = legacy))
            return out.take(MAX_GITHUB_TOKENS)
        }

        private fun servers(array: JsonArray?): List<McpServer> {
            if (array == null) return emptyList()
            val out = mutableListOf<McpServer>()
            for (i in 0 until array.size) {
                val o = runCatching { array[i].jsonObject }.getOrNull() ?: continue
                val id = o["id"]?.jsonPrimitive?.content ?: ""
                if (id.isBlank()) continue
                out.add(
                    McpServer(
                        id = id,
                        name = o["name"]?.jsonPrimitive?.content ?: id,
                        url = o["url"]?.jsonPrimitive?.content ?: "",
                        command = o["command"]?.jsonPrimitive?.content ?: "",
                        arguments = o["arguments"]?.jsonPrimitive?.content ?: "",
                        token = o["token"]?.jsonPrimitive?.content ?: "",
                        enabled = o["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
                    )
                )
            }
            return out
        }

        private fun connectors(array: JsonArray?): List<ConnectorConfig> {
            if (array == null) return emptyList()
            val out = mutableListOf<ConnectorConfig>()
            for (i in 0 until array.size) {
                val o = runCatching { array[i].jsonObject }.getOrNull() ?: continue
                val id = o["id"]?.jsonPrimitive?.content ?: ""
                if (id.isBlank()) continue
                out.add(
                    ConnectorConfig(
                        id = id,
                        token = o["token"]?.jsonPrimitive?.content ?: "",
                        baseUrl = o["baseUrl"]?.jsonPrimitive?.content ?: "",
                        account = o["account"]?.jsonPrimitive?.content ?: ""
                    )
                )
            }
            return out
        }

        private fun plugins(array: JsonArray?): List<PluginState> {
            if (array == null) return emptyList()
            val out = mutableListOf<PluginState>()
            for (i in 0 until array.size) {
                val o = runCatching { array[i].jsonObject }.getOrNull() ?: continue
                val id = o["id"]?.jsonPrimitive?.content ?: ""
                if (id.isBlank()) continue
                out.add(
                    PluginState(
                        id = id,
                        installed = o["installed"]?.jsonPrimitive?.booleanOrNull ?: false,
                        source = o["source"]?.jsonPrimitive?.content ?: "",
                        version = o["version"]?.jsonPrimitive?.content ?: "",
                        sizeBytes = o["sizeBytes"]?.jsonPrimitive?.longOrNull ?: 0L,
                        installedAt = o["installedAt"]?.jsonPrimitive?.longOrNull ?: 0L
                    )
                )
            }
            return out
        }
    }
}
