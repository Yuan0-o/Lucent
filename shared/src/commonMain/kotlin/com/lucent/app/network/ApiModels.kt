package com.lucent.app.network

enum class ApiSpec { OPENAI, ANTHROPIC, GOOGLE }

data class ChatTurn(
    val role: String,
    val content: String,
    val attachmentMime: String? = null,
    val attachmentData: String? = null,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val toolResults: List<ToolResultTurn> = emptyList(),
    val thinkingBlocksJson: String = "",
    val reasoningContent: String = ""
)

data class ToolResultTurn(val id: String, val name: String, val content: String)

data class ToolParam(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val itemType: String = "string"
)

data class ToolDefinition(val name: String, val description: String, val params: List<ToolParam>)

data class ToolCallRequest(
    val id: String,
    val name: String,
    val argumentsJson: String,
    val thoughtSignature: String? = null
)

data class ToolImage(val mime: String, val data: String, val name: String)

data class ToolExecResult(
    val summary: String,
    val images: List<ToolImage> = emptyList(),
    val success: Boolean = true,
    val openNoteId: Long? = null,
    val openTaskId: Long? = null
)

data class TokenUsage(
    val promptTokens: Int = 0,
    val cachedTokens: Int = 0,
    val outputTokens: Int = 0
) {
    val known: Boolean get() = promptTokens > 0 || cachedTokens > 0

    val hitPercent: Int
        get() = if (promptTokens <= 0) 0 else ((cachedTokens.toLong() * 100L) / promptTokens.toLong()).toInt()

    companion object {
        val NONE = TokenUsage()
    }
}

data class RawModelReply(
    val text: String?,
    val toolCalls: List<ToolCallRequest>,
    val imageMime: String? = null,
    val imageData: String? = null,
    val thinkingBlocksJson: String = "",
    val reasoningContent: String = "",
    val usage: TokenUsage = TokenUsage.NONE
)

class ApiHttpException(val code: Int, val bodyText: String) :
    Exception("HTTP $code: ${bodyText.take(500)}")

class ApiNetworkException(message: String, cause: Throwable?) :
    java.io.IOException(message, cause)
