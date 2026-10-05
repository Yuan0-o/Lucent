package com.lucent.app.harness.mcp

import com.lucent.app.harness.McpServer
import org.json.JSONObject

data class McpReply(
    val ok: Boolean = false,
    val result: JSONObject? = null,
    val error: String = "",
    val sessionId: String = "",
    val status: Int = 0
)

data class McpDiscovery(
    val tools: List<McpTool> = emptyList(),
    val error: String = ""
)

data class McpPrompts(
    val prompts: List<McpPromptInfo> = emptyList(),
    val error: String = ""
)

data class McpStatus(
    val ok: Boolean,
    val millis: Long,
    val detail: String = ""
)

data class McpText(
    val text: String = "",
    val error: String = ""
)

interface McpTransport {
    val id: String
    fun describe(server: McpServer): String
    suspend fun send(server: McpServer, method: String, params: JSONObject?, notification: Boolean): McpReply
    fun close(serverId: String)
}
