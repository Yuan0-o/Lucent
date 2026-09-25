package com.lucent.app.network

import com.lucent.app.data.ReasoningEffort
import com.lucent.app.data.ReasoningEfforts
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

sealed interface ProviderAdapter {
    val spec: ApiSpec

    fun chatUrl(baseUrl: String, model: String, streaming: Boolean): String

    fun modelsUrl(baseUrl: String): String

    fun addAuthHeaders(builder: Request.Builder, apiKey: String)

    fun buildBody(
        model: String,
        history: List<ChatTurn>,
        systemPrompt: String,
        tools: List<ToolDefinition>,
        streaming: Boolean,
        reasoning: String = ReasoningEffort.DEFAULT.key,
        provider: String = "",
        cacheKey: String = "",
        context: String = ""
    ): JSONObject

    fun parseReply(bodyStr: String): RawModelReply

    fun parseStreamEvent(
        json: JSONObject,
        acc: StreamAccumulator,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit = {}
    )
}

class StreamAccumulator {
    val fullText = StringBuilder()
    val fullReasoning = StringBuilder()
    val anthropicThinking = JSONArray()
    var usage: TokenUsage = TokenUsage.NONE
    private val anthropicThinkingIndex = HashMap<Int, JSONObject>()
    private val anthropicThinkingSignature = HashMap<Int, String>()

    fun beginThinkingBlock(index: Int, type: String, data: String?) {
        val block = JSONObject().put("type", type)
        if (type == "thinking") block.put("thinking", "") else if (data != null) block.put("data", data)
        anthropicThinkingIndex[index] = block
        anthropicThinking.put(block)
    }

    fun appendThinkingText(index: Int, text: String) {
        val block = anthropicThinkingIndex[index] ?: return
        block.put("thinking", block.optString("thinking", "") + text)
    }

    fun appendThinkingSignature(index: Int, signature: String) {
        val block = anthropicThinkingIndex[index] ?: return
        anthropicThinkingSignature[index] = (anthropicThinkingSignature[index] ?: "") + signature
        block.put("signature", anthropicThinkingSignature[index])
    }

    fun thinkingBlocksJson(): String {
        if (anthropicThinking.length() == 0) return ""
        val kept = JSONArray()
        for (i in 0 until anthropicThinking.length()) {
            val block = anthropicThinking.optJSONObject(i) ?: continue
            when (block.optString("type")) {
                "thinking" -> if (block.optString("thinking").isNotEmpty() && block.optString("signature").isNotEmpty()) {
                    kept.put(block)
                }
                "redacted_thinking" -> if (block.optString("data").isNotEmpty()) kept.put(block)
            }
        }
        return if (kept.length() == 0) "" else kept.toString()
    }
    val openAiToolAcc = LinkedHashMap<Int, ToolAcc>()
    val anthropicToolAcc = LinkedHashMap<Int, ToolAcc>()
    var returnedImageMime: String? = null
    var returnedImageData: String? = null
    internal var inThinkBlock = false
    internal val thinkCarry = StringBuilder()

    fun toolCalls(useAnthropicAcc: Boolean): List<ToolCallRequest> {
        val source = if (useAnthropicAcc) anthropicToolAcc else openAiToolAcc
        val out = mutableListOf<ToolCallRequest>()
        for ((_, acc) in source) {
            if (acc.name.isNotBlank()) {
                out.add(
                    ToolCallRequest(
                        id = acc.id.ifBlank { "call" },
                        name = acc.name,
                        argumentsJson = acc.args.toString().ifBlank { "{}" },
                        thoughtSignature = acc.thoughtSignature
                    )
                )
            }
        }
        return out
    }
}

internal fun adapterFor(spec: ApiSpec): ProviderAdapter = when (spec) {
    ApiSpec.OPENAI -> OpenAiAdapter
    ApiSpec.ANTHROPIC -> AnthropicAdapter
    ApiSpec.GOOGLE -> GoogleAdapter
}

private const val THINK_OPEN = "<think>"
private const val THINK_CLOSE = "</think>"

private fun routeContent(
    piece: String,
    acc: StreamAccumulator,
    onDelta: (String) -> Unit,
    onReasoning: (String) -> Unit
) {
    var pending = acc.thinkCarry.append(piece).toString()
    acc.thinkCarry.setLength(0)
    while (pending.isNotEmpty()) {
        val tag = if (acc.inThinkBlock) THINK_CLOSE else THINK_OPEN
        val at = pending.indexOf(tag)
        if (at >= 0) {
            emitChunk(pending.substring(0, at), acc, onDelta, onReasoning)
            acc.inThinkBlock = !acc.inThinkBlock
            pending = pending.substring(at + tag.length)
            continue
        }
        val keep = thinkTailLength(pending)
        emitChunk(pending.substring(0, pending.length - keep), acc, onDelta, onReasoning)
        if (keep > 0) acc.thinkCarry.append(pending.substring(pending.length - keep))
        pending = ""
    }
}

internal fun flushContentRouting(
    acc: StreamAccumulator,
    onDelta: (String) -> Unit,
    onReasoning: (String) -> Unit
) {
    if (acc.thinkCarry.isEmpty()) return
    val pending = acc.thinkCarry.toString()
    acc.thinkCarry.setLength(0)
    emitChunk(pending, acc, onDelta, onReasoning)
}

private fun emitChunk(
    text: String,
    acc: StreamAccumulator,
    onDelta: (String) -> Unit,
    onReasoning: (String) -> Unit
) {
    if (text.isEmpty()) return
    if (acc.inThinkBlock) {
        acc.fullReasoning.append(text)
        onReasoning(text)
    } else {
        acc.fullText.append(text)
        onDelta(text)
    }
}

private fun thinkTailLength(text: String): Int {
    val max = minOf(text.length, maxOf(THINK_OPEN.length, THINK_CLOSE.length) - 1)
    for (k in max downTo 1) {
        val tail = text.substring(text.length - k)
        if (THINK_OPEN.startsWith(tail) || THINK_CLOSE.startsWith(tail)) return k
    }
    return 0
}

private fun reasoningChannel(delta: JSONObject?): String {
    if (delta == null) return ""
    for (key in arrayOf("reasoning_content", "reasoning")) {
        if (delta.isNull(key)) continue
        val value = delta.optString(key, "")
        if (value.isNotEmpty()) return value
    }
    val details = delta.optJSONArray("reasoning_details") ?: return ""
    val sb = StringBuilder()
    for (i in 0 until details.length()) {
        val part = details.optJSONObject(i) ?: continue
        if (part.isNull("text")) continue
        sb.append(part.optString("text", ""))
    }
    return sb.toString()
}

internal const val TEMPERATURE = 0.6
internal const val TOP_P = 0.9
internal const val MAX_TOKENS = 2048

internal fun openAiUsage(usage: JSONObject?): TokenUsage {
    if (usage == null || usage.length() == 0) return TokenUsage.NONE
    val prompt = usage.optInt("prompt_tokens", usage.optInt("input_tokens", 0))
    val details = usage.optJSONObject("prompt_tokens_details")?.optInt("cached_tokens", 0)
        ?: usage.optJSONObject("input_tokens_details")?.optInt("cached_tokens", 0)
        ?: 0
    val cached = maxOf(details, usage.optInt("prompt_cache_hit_tokens", 0), usage.optInt("cached_tokens", 0))
    val output = usage.optInt("completion_tokens", usage.optInt("output_tokens", 0))
    return TokenUsage(prompt.coerceAtLeast(0), cached.coerceAtLeast(0), output.coerceAtLeast(0))
}

internal fun googleUsage(meta: JSONObject?): TokenUsage {
    if (meta == null) return TokenUsage.NONE
    val prompt = meta.optInt("promptTokenCount", 0)
    val cached = meta.optInt("cachedContentTokenCount", 0)
    val output = meta.optInt("candidatesTokenCount", 0) + meta.optInt("thoughtsTokenCount", 0)
    return TokenUsage(prompt.coerceAtLeast(0), cached.coerceAtLeast(0), output.coerceAtLeast(0))
}

internal fun anthropicUsage(usage: JSONObject?): TokenUsage {
    if (usage == null || usage.length() == 0) return TokenUsage.NONE
    val fresh = usage.optInt("input_tokens", 0)
    val created = usage.optInt("cache_creation_input_tokens", 0)
    val read = usage.optInt("cache_read_input_tokens", 0)
    val output = usage.optInt("output_tokens", 0)
    return TokenUsage(
        promptTokens = (fresh + created + read).coerceAtLeast(0),
        cachedTokens = read.coerceAtLeast(0),
        outputTokens = output.coerceAtLeast(0)
    )
}

private fun markMessageBreakpoint(message: JSONObject?) {
    val target = message ?: return
    val breakpoint = JSONObject().put("type", "ephemeral")
    when (val content = target.opt("content")) {
        is JSONArray -> content.optJSONObject(content.length() - 1)?.put("cache_control", breakpoint)
        is String -> {
            target.put(
                "content",
                JSONArray().put(JSONObject().put("type", "text").put("text", content).put("cache_control", breakpoint))
            )
        }
    }
}

internal fun mergeUsage(current: TokenUsage, fresh: TokenUsage): TokenUsage = TokenUsage(
    promptTokens = if (fresh.promptTokens > 0) fresh.promptTokens else current.promptTokens,
    cachedTokens = if (fresh.promptTokens > 0) fresh.cachedTokens else current.cachedTokens,
    outputTokens = if (fresh.outputTokens > 0) fresh.outputTokens else current.outputTokens
)

private fun toolSchema(tools: List<ToolDefinition>): List<JSONObject> {
    return tools.map { t ->
        val props = JSONObject()
        val required = JSONArray()
        for (p in t.params) {
            props.put(p.name, JSONObject().put("type", p.type).put("description", p.description))
            if (p.required) required.put(p.name)
        }
        JSONObject()
            .put("_name", t.name)
            .put("_description", t.description)
            .put("_schema", JSONObject().put("type", "object").put("properties", props).put("required", required))
    }
}

private fun parseDataUrl(url: String): Pair<String, String>? {
    if (!url.startsWith("data:")) return null
    val comma = url.indexOf(',')
    if (comma < 0) return null
    val data = url.substring(comma + 1)
    if (data.isBlank()) return null
    val meta = url.substring(5, comma)
    val mime = meta.substringBefore(';').ifBlank { "image/png" }
    return mime to data
}

private fun imageFromOpenAiImages(images: JSONArray?): Pair<String, String>? {
    if (images == null) return null
    var found: Pair<String, String>? = null
    for (i in 0 until images.length()) {
        val url = images.optJSONObject(i)?.optJSONObject("image_url")?.optString("url", "") ?: ""
        parseDataUrl(url)?.let { found = it }
    }
    return found
}

object OpenAiAdapter : ProviderAdapter {
    override val spec = ApiSpec.OPENAI

    override fun chatUrl(baseUrl: String, model: String, streaming: Boolean): String =
        baseUrl.trimEnd('/') + "/chat/completions"

    override fun modelsUrl(baseUrl: String): String = baseUrl.trimEnd('/') + "/models"

    override fun addAuthHeaders(builder: Request.Builder, apiKey: String) {
        builder.addHeader("Authorization", "Bearer $apiKey")
        builder.addHeader("Content-Type", "application/json")
    }

    override fun buildBody(
        model: String,
        history: List<ChatTurn>,
        systemPrompt: String,
        tools: List<ToolDefinition>,
        streaming: Boolean,
        reasoning: String,
        provider: String,
        cacheKey: String,
        context: String
    ): JSONObject {
        val echoReasoning = provider == com.lucent.app.data.ApiProviders.DEEPSEEK ||
            provider == com.lucent.app.data.ApiProviders.KIMI
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        for (turn in history) {
            when {
                turn.toolCalls.isNotEmpty() -> {
                    val msg = JSONObject().put("role", "assistant")
                    msg.put("content", if (turn.content.isBlank()) JSONObject.NULL else turn.content)
                    val calls = JSONArray()
                    for (c in turn.toolCalls) {
                        calls.put(
                            JSONObject().put("id", c.id).put("type", "function").put(
                                "function",
                                JSONObject().put("name", c.name).put("arguments", c.argumentsJson)
                            )
                        )
                    }
                    msg.put("tool_calls", calls)
                    if (echoReasoning && turn.reasoningContent.isNotBlank()) {
                        msg.put("reasoning_content", turn.reasoningContent)
                    }
                    messages.put(msg)
                }
                turn.toolResults.isNotEmpty() -> {
                    for (r in turn.toolResults) {
                        messages.put(
                            JSONObject().put("role", "tool").put("tool_call_id", r.id).put("content", r.content)
                        )
                    }
                    if (turn.attachmentData != null && turn.attachmentMime?.startsWith("image/") == true) {
                        messages.put(JSONObject().put("role", "user").put("content", openAiContent(turn.copy(toolResults = emptyList()))))
                    }
                }
                else -> {
                    val msg = JSONObject().put("role", turn.role).put("content", openAiContent(turn))
                    if (echoReasoning && turn.role == "assistant" && turn.reasoningContent.isNotBlank()) {
                        msg.put("reasoning_content", turn.reasoningContent)
                    }
                    messages.put(msg)
                }
            }
        }

        val plan = ReasoningEfforts.planFor(provider, model, reasoning)
        val root = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("max_tokens", MAX_TOKENS)
        if (context.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", context))
        }
        if (plan.empty) {
            root.put("temperature", TEMPERATURE).put("top_p", TOP_P)
        } else {
            plan.effort?.let { root.put("reasoning_effort", it) }
            if (plan.thinkingOff) root.put("thinking", JSONObject().put("type", "disabled"))
        }
        if (streaming && (provider == com.lucent.app.data.ApiProviders.CHATGPT ||
                provider == com.lucent.app.data.ApiProviders.KIMI)
        ) {
            root.put("stream_options", JSONObject().put("include_usage", true))
        }
        val cacheTtls = ReasoningEfforts.cacheOptionsFor(provider, model)
        if (cacheTtls.isNotEmpty()) {
            root.put("prompt_cache_options", JSONObject().put("mode", "implicit").put("ttl", cacheTtls.first()))
        }
        if (cacheKey.isNotBlank() &&
            (provider == com.lucent.app.data.ApiProviders.KIMI ||
                provider == com.lucent.app.data.ApiProviders.CHATGPT)
        ) {
            root.put("prompt_cache_key", cacheKey)
        }
        if (tools.isNotEmpty()) {
            val toolsArray = JSONArray()
            for (t in toolSchema(tools)) {
                toolsArray.put(
                    JSONObject().put("type", "function").put(
                        "function",
                        JSONObject()
                            .put("name", t.getString("_name"))
                            .put("description", t.getString("_description"))
                            .put("parameters", t.getJSONObject("_schema"))
                    )
                )
            }
            root.put("tools", toolsArray)
        }
        return root
    }

    override fun parseReply(bodyStr: String): RawModelReply {
        val json = JSONObject(bodyStr)
        val message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val text = if (message.isNull("content")) null else message.optString("content")
        val toolCalls = mutableListOf<ToolCallRequest>()
        val toolCallsArray = message.optJSONArray("tool_calls")
        if (toolCallsArray != null) {
            for (i in 0 until toolCallsArray.length()) {
                val tc = toolCallsArray.getJSONObject(i)
                val fn = tc.getJSONObject("function")
                toolCalls.add(ToolCallRequest(tc.optString("id", "call_$i"), fn.getString("name"), fn.optString("arguments", "{}")))
            }
        }
        val image = imageFromOpenAiImages(message.optJSONArray("images"))
        return RawModelReply(
            text,
            toolCalls,
            image?.first,
            image?.second,
            reasoningContent = message.optString("reasoning_content", ""),
            usage = openAiUsage(json.optJSONObject("usage"))
        )
    }

    override fun parseStreamEvent(
        json: JSONObject,
        acc: StreamAccumulator,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit
    ) {
        json.optJSONObject("usage")?.let { reported ->
            if (reported.length() > 0) acc.usage = openAiUsage(reported)
        }
        val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
        val reasoning = reasoningChannel(delta)
        if (reasoning.isNotEmpty()) {
            acc.fullReasoning.append(reasoning)
            onReasoning(reasoning)
        }
        val piece = delta?.let { if (it.isNull("content")) "" else it.optString("content", "") } ?: ""
        if (piece.isNotEmpty()) routeContent(piece, acc, onDelta, onReasoning)
        delta?.optJSONArray("tool_calls")?.let { tcArr ->
            for (i in 0 until tcArr.length()) {
                val tc = tcArr.getJSONObject(i)
                val idx = tc.optInt("index", 0)
                val a = acc.openAiToolAcc.getOrPut(idx) { ToolAcc() }
                tc.optString("id", "").takeIf { it.isNotEmpty() }?.let { a.id = it }
                tc.optJSONObject("function")?.let { fn ->
                    fn.optString("name", "").takeIf { it.isNotEmpty() }?.let { a.name = it }
                    a.args.append(if (fn.isNull("arguments")) "" else fn.optString("arguments", ""))
                }
            }
        }
        imageFromOpenAiImages(delta?.optJSONArray("images"))?.let {
            acc.returnedImageMime = it.first; acc.returnedImageData = it.second
        }
    }

    private fun openAiContent(turn: ChatTurn): Any {
        if (turn.attachmentData != null && turn.attachmentMime?.startsWith("image/") == true) {
            val arr = JSONArray()
            arr.put(JSONObject().put("type", "text").put("text", turn.content))
            arr.put(
                JSONObject().put("type", "image_url").put(
                    "image_url",
                    JSONObject().put("url", "data:${turn.attachmentMime};base64,${turn.attachmentData}")
                )
            )
            return arr
        }
        return turn.content
    }
}

object AnthropicAdapter : ProviderAdapter {
    override val spec = ApiSpec.ANTHROPIC

    override fun chatUrl(baseUrl: String, model: String, streaming: Boolean): String =
        baseUrl.trimEnd('/') + "/messages"

    override fun modelsUrl(baseUrl: String): String = baseUrl.trimEnd('/') + "/models"

    override fun addAuthHeaders(builder: Request.Builder, apiKey: String) {
        builder.addHeader("x-api-key", apiKey)
        builder.addHeader("anthropic-version", "2023-06-01")
        builder.addHeader("Content-Type", "application/json")
    }

    override fun buildBody(
        model: String,
        history: List<ChatTurn>,
        systemPrompt: String,
        tools: List<ToolDefinition>,
        streaming: Boolean,
        reasoning: String,
        provider: String,
        cacheKey: String,
        context: String
    ): JSONObject {
        val messages = JSONArray()
        for (turn in history) {
            when {
                turn.toolCalls.isNotEmpty() -> {
                    val content = JSONArray()
                    if (turn.thinkingBlocksJson.isNotBlank()) {
                        try {
                            val blocks = JSONArray(turn.thinkingBlocksJson)
                            for (i in 0 until blocks.length()) {
                                blocks.optJSONObject(i)?.let { content.put(it) }
                            }
                        } catch (e: Exception) {
                        }
                    }
                    if (turn.content.isNotBlank()) {
                        content.put(JSONObject().put("type", "text").put("text", turn.content))
                    }
                    for (c in turn.toolCalls) {
                        val input = try { JSONObject(c.argumentsJson) } catch (e: Exception) { JSONObject() }
                        content.put(
                            JSONObject().put("type", "tool_use").put("id", c.id).put("name", c.name).put("input", input)
                        )
                    }
                    messages.put(JSONObject().put("role", "assistant").put("content", content))
                }
                turn.toolResults.isNotEmpty() -> {
                    val content = JSONArray()
                    for (r in turn.toolResults) {
                        content.put(
                            JSONObject().put("type", "tool_result").put("tool_use_id", r.id).put("content", r.content)
                        )
                    }
                    if (turn.attachmentData != null && turn.attachmentMime?.startsWith("image/") == true) {
                        content.put(
                            JSONObject().put("type", "image").put(
                                "source",
                                JSONObject().put("type", "base64").put("media_type", turn.attachmentMime).put("data", turn.attachmentData)
                            )
                        )
                    }
                    messages.put(JSONObject().put("role", "user").put("content", content))
                }
                else -> {
                    val role = if (turn.role == "assistant") "assistant" else "user"
                    messages.put(JSONObject().put("role", role).put("content", anthropicContent(turn)))
                }
            }
        }

        val plan = ReasoningEfforts.planFor(provider, model, reasoning)
        val systemBlocks = JSONArray()
        systemBlocks.put(
            JSONObject()
                .put("type", "text")
                .put("text", systemPrompt)
                .put("cache_control", JSONObject().put("type", "ephemeral"))
        )
        if (context.isNotBlank()) {
            systemBlocks.put(JSONObject().put("type", "text").put("text", context))
        }
        val root = JSONObject()
            .put("model", model)
            .put("system", systemBlocks)
            .put("messages", messages)
        when {
            plan.claudeBudgetTokens != null -> {
                root.put("max_tokens", plan.claudeBudgetTokens + 4096)
                root.put("thinking", JSONObject().put("type", "enabled").put("budget_tokens", plan.claudeBudgetTokens))
            }
            plan.claudeAdaptive -> {
                root.put("max_tokens", MAX_TOKENS)
                root.put("thinking", JSONObject().put("type", "adaptive"))
                plan.claudeEffort?.let { root.put("output_config", JSONObject().put("effort", it)) }
            }
            else -> {
                root.put("max_tokens", MAX_TOKENS).put("temperature", TEMPERATURE).put("top_p", TOP_P)
            }
        }
        if (tools.isNotEmpty()) {
            val toolsArray = JSONArray()
            for (t in toolSchema(tools)) {
                toolsArray.put(
                    JSONObject()
                        .put("name", t.getString("_name"))
                        .put("description", t.getString("_description"))
                        .put("input_schema", t.getJSONObject("_schema"))
                )
            }
            toolsArray.optJSONObject(toolsArray.length() - 1)
                ?.put("cache_control", JSONObject().put("type", "ephemeral"))
            root.put("tools", toolsArray)
        }
        if (messages.length() > 0) {
            markMessageBreakpoint(messages.optJSONObject(messages.length() - 1))
        }
        return root
    }

    override fun parseReply(bodyStr: String): RawModelReply {
        val json = JSONObject(bodyStr)
        val contentArray = json.getJSONArray("content")
        var text: String? = null
        val toolCalls = mutableListOf<ToolCallRequest>()
        for (i in 0 until contentArray.length()) {
            val block = contentArray.getJSONObject(i)
            when (block.optString("type")) {
                "text" -> text = (text ?: "") + block.optString("text")
                "tool_use" -> {
                    val input = block.optJSONObject("input") ?: JSONObject()
                    toolCalls.add(ToolCallRequest(block.optString("id"), block.optString("name"), input.toString()))
                }
            }
        }
        return RawModelReply(text, toolCalls, usage = anthropicUsage(json.optJSONObject("usage")))
    }

    override fun parseStreamEvent(
        json: JSONObject,
        acc: StreamAccumulator,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit
    ) {
        when (json.optString("type")) {
            "message_start" -> {
                json.optJSONObject("message")?.optJSONObject("usage")?.let {
                    acc.usage = mergeUsage(acc.usage, anthropicUsage(it))
                }
            }
            "message_delta" -> {
                json.optJSONObject("usage")?.let {
                    acc.usage = mergeUsage(acc.usage, anthropicUsage(it))
                }
            }
            "content_block_start" -> {
                val block = json.optJSONObject("content_block")
                val idx = json.optInt("index", 0)
                when (block?.optString("type")) {
                    "tool_use" -> acc.anthropicToolAcc[idx] = ToolAcc(id = block.optString("id"), name = block.optString("name"))
                    "thinking" -> acc.beginThinkingBlock(idx, "thinking", null)
                    "redacted_thinking" -> acc.beginThinkingBlock(idx, "redacted_thinking", block.optString("data"))
                }
            }
            "content_block_delta" -> {
                val delta = json.optJSONObject("delta")
                when (delta?.optString("type")) {
                    "text_delta" -> {
                        val piece = if (delta.isNull("text")) "" else delta.optString("text", "")
                        if (piece.isNotEmpty()) routeContent(piece, acc, onDelta, onReasoning)
                    }
                    "thinking_delta" -> {
                        val piece = if (delta.isNull("thinking")) "" else delta.optString("thinking", "")
                        if (piece.isNotEmpty()) {
                            acc.fullReasoning.append(piece)
                            acc.appendThinkingText(json.optInt("index", 0), piece)
                            onReasoning(piece)
                        }
                    }
                    "signature_delta" -> {
                        val signature = if (delta.isNull("signature")) "" else delta.optString("signature", "")
                        if (signature.isNotEmpty()) acc.appendThinkingSignature(json.optInt("index", 0), signature)
                    }
                    "input_json_delta" -> {
                        val idx = json.optInt("index", 0)
                        acc.anthropicToolAcc[idx]?.args?.append(if (delta.isNull("partial_json")) "" else delta.optString("partial_json", ""))
                    }
                }
            }
        }
    }

    private fun anthropicContent(turn: ChatTurn): Any {
        if (turn.attachmentData != null && turn.attachmentMime?.startsWith("image/") == true) {
            val arr = JSONArray()
            arr.put(
                JSONObject().put("type", "image").put(
                    "source",
                    JSONObject().put("type", "base64").put("media_type", turn.attachmentMime).put("data", turn.attachmentData)
                )
            )
            arr.put(JSONObject().put("type", "text").put("text", turn.content))
            return arr
        }
        return turn.content
    }
}

object GoogleAdapter : ProviderAdapter {
    override val spec = ApiSpec.GOOGLE

    override fun chatUrl(baseUrl: String, model: String, streaming: Boolean): String {
        return baseUrl.trimEnd('/') + "/models/${model.removePrefix("models/")}:streamGenerateContent?alt=sse"
    }

    override fun modelsUrl(baseUrl: String): String = baseUrl.trimEnd('/') + "/models"

    override fun addAuthHeaders(builder: Request.Builder, apiKey: String) {
        builder.addHeader("x-goog-api-key", apiKey)
        builder.addHeader("Content-Type", "application/json")
    }

    override fun buildBody(
        model: String,
        history: List<ChatTurn>,
        systemPrompt: String,
        tools: List<ToolDefinition>,
        streaming: Boolean,
        reasoning: String,
        provider: String,
        cacheKey: String,
        context: String
    ): JSONObject {
        val contents = JSONArray()
        for (turn in history) {
            when {
                turn.toolCalls.isNotEmpty() -> {
                    val parts = JSONArray()
                    if (turn.content.isNotBlank()) parts.put(JSONObject().put("text", turn.content))
                    for (c in turn.toolCalls) {
                        val args = try { JSONObject(c.argumentsJson) } catch (e: Exception) { JSONObject() }
                        val part = JSONObject().put("functionCall", JSONObject().put("name", c.name).put("args", args))
                        c.thoughtSignature?.takeIf { it.isNotBlank() }?.let { part.put("thoughtSignature", it) }
                        parts.put(part)
                    }
                    contents.put(JSONObject().put("role", "model").put("parts", parts))
                }
                turn.toolResults.isNotEmpty() -> {
                    val parts = JSONArray()
                    for (r in turn.toolResults) {
                        parts.put(
                            JSONObject().put(
                                "functionResponse",
                                JSONObject().put("name", r.name).put("response", JSONObject().put("result", r.content))
                            )
                        )
                    }
                    if (turn.attachmentData != null && turn.attachmentMime?.startsWith("image/") == true) {
                        parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", turn.attachmentMime).put("data", turn.attachmentData)))
                    }
                    contents.put(JSONObject().put("role", "user").put("parts", parts))
                }
                else -> {
                    val role = if (turn.role == "assistant") "model" else "user"
                    val parts = JSONArray()
                    parts.put(JSONObject().put("text", turn.content))
                    if (turn.attachmentMime?.startsWith("image/") == true && turn.attachmentData != null) {
                        parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", turn.attachmentMime).put("data", turn.attachmentData)))
                    }
                    contents.put(JSONObject().put("role", role).put("parts", parts))
                }
            }
        }

        val root = JSONObject().put("contents", contents)
        val plan = ReasoningEfforts.planFor(provider, model, reasoning)
        val budget = plan.googleThinkingBudget
        val generation = JSONObject()
            .put("temperature", TEMPERATURE)
            .put("topP", TOP_P)
            .put("maxOutputTokens", if (budget != null && budget > 0) budget + 2048 else MAX_TOKENS)
        when {
            plan.googleThinkingLevel != null ->
                generation.put("thinkingConfig", JSONObject().put("thinkingLevel", plan.googleThinkingLevel))
            budget != null ->
                generation.put("thinkingConfig", JSONObject().put("thinkingBudget", budget))
        }
        root.put("generationConfig", generation)
        if (context.isNotBlank()) {
            val last = contents.optJSONObject(contents.length() - 1)
            val parts = last?.optJSONArray("parts")
            if (last != null && parts != null && last.optString("role") == "user") {
                parts.put(JSONObject().put("text", context))
            } else {
                contents.put(
                    JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", context)))
                )
            }
        }
        if (systemPrompt.isNotBlank()) {
            root.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
        }
        if (tools.isNotEmpty()) {
            val declarations = JSONArray()
            for (t in toolSchema(tools)) {
                declarations.put(
                    JSONObject()
                        .put("name", t.getString("_name"))
                        .put("description", t.getString("_description"))
                        .put("parameters", t.getJSONObject("_schema"))
                )
            }
            root.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", declarations)))
        }
        return root
    }

    override fun parseReply(bodyStr: String): RawModelReply {
        val json = JSONObject(bodyStr)
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) return RawModelReply(null, emptyList())
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: JSONObject()
        val parts = content.optJSONArray("parts") ?: JSONArray()
        var text: String? = null
        val toolCalls = mutableListOf<ToolCallRequest>()
        var imageMime: String? = null
        var imageData: String? = null

        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("text")) text = (text ?: "") + part.optString("text")
            part.optJSONObject("functionCall")?.let { fc ->
                val args = fc.optJSONObject("args") ?: JSONObject()
                val sig = part.optString("thoughtSignature").takeIf { it.isNotEmpty() }
                toolCalls.add(ToolCallRequest("call_$i", fc.optString("name"), args.toString(), sig))
            }
            part.optJSONObject("inlineData")?.let { inlineData ->
                imageMime = inlineData.optString("mimeType")
                imageData = inlineData.optString("data")
            }
        }
        return RawModelReply(text, toolCalls, imageMime, imageData, usage = googleUsage(json.optJSONObject("usageMetadata")))
    }

    override fun parseStreamEvent(
        json: JSONObject,
        acc: StreamAccumulator,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit
    ) {
        json.optJSONObject("usageMetadata")?.let { acc.usage = googleUsage(it) }
        val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val piece = if (part.isNull("text")) "" else part.optString("text", "")
                if (piece.isNotEmpty()) {
                    if (part.optBoolean("thought", false)) {
                        acc.fullReasoning.append(piece)
                        onReasoning(piece)
                    } else {
                        routeContent(piece, acc, onDelta, onReasoning)
                    }
                }
                part.optJSONObject("functionCall")?.let { fc ->
                    val args = fc.optJSONObject("args") ?: JSONObject()
                    val a = acc.openAiToolAcc.getOrPut(i) { ToolAcc() }
                    a.name = fc.optString("name")
                    a.args.append(args.toString())
                    part.optString("thoughtSignature").takeIf { it.isNotEmpty() }?.let { a.thoughtSignature = it }
                }
                part.optJSONObject("inlineData")?.let { inlineData ->
                    acc.returnedImageMime = inlineData.optString("mimeType")
                    acc.returnedImageData = inlineData.optString("data")
                }
            }
        }
    }
}
