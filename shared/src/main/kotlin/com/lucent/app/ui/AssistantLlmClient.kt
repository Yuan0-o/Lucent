package com.lucent.app.ui

import com.lucent.app.network.ApiSpec
import com.lucent.app.network.ChatTurn
import com.lucent.app.network.LlmClient
import com.lucent.app.network.RawModelReply
import com.lucent.app.network.ToolDefinition

interface AssistantLlmClient {
    suspend fun streamChat(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        model: String,
        history: List<ChatTurn>,
        systemPrompt: String,
        tools: List<ToolDefinition>,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit = {},
        onRetry: (Int) -> Unit = {}
    ): Result<RawModelReply>
}

object RealAssistantLlmClient : AssistantLlmClient {
    override suspend fun streamChat(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        model: String,
        history: List<ChatTurn>,
        systemPrompt: String,
        tools: List<ToolDefinition>,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit,
        onRetry: (Int) -> Unit
    ): Result<RawModelReply> =
        LlmClient.streamChat(
            baseUrl, spec, apiKey, model, history, systemPrompt, tools,
            onDelta, onReasoning, onRetry
        )
}
