package com.lucent.app.harness

import org.json.JSONArray
import org.json.JSONObject

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
    val mirrors: Map<String, String> = emptyMap(),
    val fastMirror: Boolean = true
) {

    fun approvalFor(permission: HarnessPermission): Approval =
        Approval.of(approvals[permission.key] ?: "") ?: Approval.defaultFor(permission)

    fun groupEnabled(group: HarnessGroup): Boolean = groups.contains(group.key)

    fun plugin(id: String): PluginState? = plugins.firstOrNull { it.id == id }

    fun pluginInstalled(id: String): Boolean = plugin(id)?.installed == true

    fun installedPlugins(): Set<String> = plugins.filter { it.installed }.map { it.id }.toSet()

    fun withPlugin(state: PluginState): HarnessConfig =
        copy(plugins = plugins.filterNot { it.id == state.id } + state)

    fun withMirror(pluginId: String, sourceId: String): HarnessConfig =
        copy(mirrors = mirrors + (pluginId to sourceId))

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

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("workspace", workspace)
        put("writeRoots", JSONArray(writeRoots))
        put("readOnlyRoots", JSONArray(readOnlyRoots))
        put("groups", JSONArray(groups.toList().sorted()))
        put("approvals", JSONObject(approvals as Map<*, *>))
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
        put("skillDirs", JSONArray(skillDirs))
        put("githubToken", githubToken)
        put("githubTokens", JSONArray().apply {
            githubTokens.forEach { entry ->
                put(JSONObject().apply {
                    put("id", entry.id)
                    put("name", entry.name)
                    put("token", entry.token)
                })
            }
        })
        put("githubActiveId", githubActiveId)
        put("githubApi", githubApi)
        put("shizukuForAssistant", shizukuForAssistant)
        put("mcpServers", JSONArray().apply {
            mcpServers.forEach { server ->
                put(JSONObject().apply {
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
        put("connectors", JSONArray().apply {
            connectors.forEach { connector ->
                put(JSONObject().apply {
                    put("id", connector.id)
                    put("token", connector.token)
                    put("baseUrl", connector.baseUrl)
                    put("account", connector.account)
                })
            }
        })
        put("plugins", JSONArray().apply {
            plugins.forEach { plugin ->
                put(JSONObject().apply {
                    put("id", plugin.id)
                    put("installed", plugin.installed)
                    put("source", plugin.source)
                    put("version", plugin.version)
                    put("sizeBytes", plugin.sizeBytes)
                    put("installedAt", plugin.installedAt)
                })
            }
        })
        put("mirrors", JSONObject(mirrors as Map<*, *>))
        put("fastMirror", fastMirror)
    }

    companion object {

        const val MAX_GITHUB_TOKENS = 5

        val DEFAULT = HarnessConfig()

        fun parse(raw: String): HarnessConfig {
            if (raw.isBlank()) return DEFAULT
            val o = try { JSONObject(raw) } catch (e: Exception) { return DEFAULT }
            val defaults = DEFAULT
            return HarnessConfig(
                enabled = o.optBoolean("enabled", defaults.enabled),
                workspace = o.optString("workspace", defaults.workspace),
                writeRoots = strings(o.optJSONArray("writeRoots")),
                readOnlyRoots = strings(o.optJSONArray("readOnlyRoots")),
                groups = strings(o.optJSONArray("groups")).toSet().ifEmpty { defaults.groups },
                approvals = map(o.optJSONObject("approvals")),
                shellEnabled = o.optBoolean("shellEnabled", defaults.shellEnabled),
                sandboxMode = o.optString("sandboxMode", defaults.sandboxMode),
                timeoutSeconds = o.optInt("timeoutSeconds", defaults.timeoutSeconds),
                maxOutputChars = o.optInt("maxOutputChars", defaults.maxOutputChars),
                snapshots = o.optBoolean("snapshots", defaults.snapshots),
                snapshotLimit = o.optInt("snapshotLimit", defaults.snapshotLimit),
                auditEnabled = o.optBoolean("auditEnabled", defaults.auditEnabled),
                deviceEnabled = o.optBoolean("deviceEnabled", defaults.deviceEnabled),
                subAgents = o.optBoolean("subAgents", defaults.subAgents),
                maxSubAgents = o.optInt("maxSubAgents", defaults.maxSubAgents),
                skillDirs = strings(o.optJSONArray("skillDirs")),
                githubToken = o.optString("githubToken", ""),
                githubTokens = githubTokens(o.optJSONArray("githubTokens"), o.optString("githubToken", "")),
                githubActiveId = o.optString("githubActiveId", ""),
                githubApi = o.optString("githubApi", defaults.githubApi).ifBlank { defaults.githubApi },
                shizukuForAssistant = o.optBoolean("shizukuForAssistant", defaults.shizukuForAssistant),
                mcpServers = servers(o.optJSONArray("mcpServers")),
                connectors = connectors(o.optJSONArray("connectors")),
                plugins = plugins(o.optJSONArray("plugins")),
                mirrors = map(o.optJSONObject("mirrors")),
                fastMirror = o.optBoolean("fastMirror", defaults.fastMirror)
            )
        }

        private fun strings(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val value = array.optString(i, "")
                if (value.isNotBlank()) out.add(value)
            }
            return out
        }

        private fun map(obj: JSONObject?): Map<String, String> {
            if (obj == null) return emptyMap()
            val out = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                out[key] = obj.optString(key, "")
            }
            return out
        }

        private fun githubTokens(array: JSONArray?, legacy: String): List<GithubToken> {
            val out = mutableListOf<GithubToken>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val token = o.optString("token", "")
                    if (token.isBlank()) continue
                    out.add(
                        GithubToken(
                            id = o.optString("id", "").ifBlank { "gh-${out.size + 1}" },
                            name = o.optString("name", ""),
                            token = token
                        )
                    )
                }
            }
            if (out.isEmpty() && legacy.isNotBlank()) out.add(GithubToken(id = "gh-1", token = legacy))
            return out.take(MAX_GITHUB_TOKENS)
        }

        private fun servers(array: JSONArray?): List<McpServer> {
            if (array == null) return emptyList()
            val out = mutableListOf<McpServer>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id", "")
                if (id.isBlank()) continue
                out.add(
                    McpServer(
                        id = id,
                        name = o.optString("name", id),
                        url = o.optString("url", ""),
                        command = o.optString("command", ""),
                        arguments = o.optString("arguments", ""),
                        token = o.optString("token", ""),
                        enabled = o.optBoolean("enabled", true)
                    )
                )
            }
            return out
        }

        private fun connectors(array: JSONArray?): List<ConnectorConfig> {
            if (array == null) return emptyList()
            val out = mutableListOf<ConnectorConfig>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id", "")
                if (id.isBlank()) continue
                out.add(
                    ConnectorConfig(
                        id = id,
                        token = o.optString("token", ""),
                        baseUrl = o.optString("baseUrl", ""),
                        account = o.optString("account", "")
                    )
                )
            }
            return out
        }

        private fun plugins(array: JSONArray?): List<PluginState> {
            if (array == null) return emptyList()
            val out = mutableListOf<PluginState>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id", "")
                if (id.isBlank()) continue
                out.add(
                    PluginState(
                        id = id,
                        installed = o.optBoolean("installed", false),
                        source = o.optString("source", ""),
                        version = o.optString("version", ""),
                        sizeBytes = o.optLong("sizeBytes", 0L),
                        installedAt = o.optLong("installedAt", 0L)
                    )
                )
            }
            return out
        }
    }
}
