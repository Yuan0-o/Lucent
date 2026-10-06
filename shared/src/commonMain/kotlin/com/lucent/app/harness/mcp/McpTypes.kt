package com.lucent.app.harness.mcp

import com.lucent.app.harness.McpServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class McpReply(
    val ok: Boolean = false,
    val result: JsonObject? = null,
    val error: String = "",
    val sessionId: String = "",
    val status: Int = 0
)

@Serializable
data class McpDiscovery(
    val tools: List<McpTool> = emptyList(),
    val error: String = ""
)

@Serializable
data class McpPrompts(
    val prompts: List<McpPromptInfo> = emptyList(),
    val error: String = ""
)

@Serializable
data class McpStatus(
    val ok: Boolean,
    val millis: Long,
    val detail: String = ""
)

@Serializable
data class McpText(
    val text: String = "",
    val error: String = ""
)

interface McpTransport {
    val id: String
    fun describe(server: McpServer): String
    suspend fun send(server: McpServer, method: String, params: JsonObject?, notification: Boolean): McpReply
    fun close(serverId: String)
}
