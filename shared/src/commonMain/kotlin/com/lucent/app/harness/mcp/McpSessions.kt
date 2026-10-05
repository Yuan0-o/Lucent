package com.lucent.app.harness.mcp

import com.lucent.app.harness.McpServer

expect object McpSessions {
    fun cachedTools(serverId: String): List<McpTool>
    fun lastError(serverId: String): String
    fun transportName(server: McpServer): String
    fun endpoint(server: McpServer): String
    suspend fun discovery(server: McpServer, force: Boolean = false): McpDiscovery
    suspend fun call(server: McpServer, tool: String, argumentsJson: String): McpResult
    suspend fun readResource(server: McpServer, uri: String): McpText
    suspend fun prompts(server: McpServer): McpPrompts
    suspend fun ping(server: McpServer): McpStatus
    suspend fun close()
}
