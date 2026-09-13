package com.lucent.app.ui

import com.lucent.app.network.ApiSpec
import com.lucent.app.network.ChatTurn
import com.lucent.app.network.LlmClient
import com.lucent.app.network.RawModelReply
import com.lucent.app.network.ToolDefinition

/**
 * P1-2: the seam [AssistantController] talks to a model provider through.
 *
 * [AssistantController] used to call the [LlmClient] *object* directly, which is exactly what made
 * the cloud tool-call loop — send → model asks for a tool → user confirms/declines → result
 * persisted — impossible to exercise from a plain JVM test: there was nothing to substitute for a
 * real network call. This interface is the minimal cut that fixes that.
 *
 * It is deliberately narrow: [streamChat] is the *only* [LlmClient] member [AssistantController]
 * ever calls (`LlmClient.fetchModels` is used elsewhere — `QuickModelSwitcher` and both
 * `SettingsScreen` files — and stays exactly as it was; those callers are untouched by this
 * refactor). Widening this interface to cover [LlmClient] in full would mean either turning
 * `LlmClient` itself into an interface — which breaks every one of ITS OWN existing callers, since
 * an `object` can be referenced by name but an interface needs an instance — or duplicating members
 * this class never uses. Neither is warranted just to make one method fakeable.
 *
 * The one production implementation, [RealAssistantLlmClient], simply forwards to the real
 * [LlmClient] object.
 */
interface AssistantLlmClient {
    suspend fun streamChat(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        model: String,
        history: List<ChatTurn>,
        systemPrompt: String,
        tools: List<ToolDefinition>,
        onDelta: (String) -> Unit
    ): Result<RawModelReply>
}

/** Forwards verbatim to [LlmClient.streamChat] — the same network call [AssistantController] made directly before this refactor. */
object RealAssistantLlmClient : AssistantLlmClient {
    override suspend fun streamChat(
        baseUrl: String,
        spec: ApiSpec,
        apiKey: String,
        model: String,
        history: List<ChatTurn>,
        systemPrompt: String,
        tools: List<ToolDefinition>,
        onDelta: (String) -> Unit
    ): Result<RawModelReply> =
        LlmClient.streamChat(baseUrl, spec, apiKey, model, history, systemPrompt, tools, onDelta)
}
