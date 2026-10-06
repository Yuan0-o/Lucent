package com.lucent.app.network

import kotlinx.serialization.json.*

import com.lucent.app.data.ReasoningEffort
import com.lucent.app.data.ReasoningEfforts
import okhttp3.Request

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
    ): JsonObject

    fun parseReply(bodyStr: String): RawModelReply

    fun parseStreamEvent(
        json: JsonObject,
        acc: StreamAccumulator,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit = {}
    )
}

class StreamAccumulator {
    val fullText = StringBuilder()
    val fullReasoning = StringBuilder()
    val anthropicThinking = mutableListOf<MutableMap<String, String>>()
    var usage: TokenUsage = TokenUsage.NONE
    private val anthropicThinkingIndex = HashMap<Int, MutableMap<String, String>>()
    private val anthropicThinkingSignature = HashMap<Int, String>()

    fun beginThinkingBlock(index: Int, type: String, data: String?) {
        val block = mutableMapOf("type" to type)
        if (type == "thinking") block["thinking"] = "" else if (data != null) block["data"] = data
        anthropicThinkingIndex[index] = block
        anthropicThinking.add(block)
    }

    fun appendThinkingText(index: Int, text: String) {
        val block = anthropicThinkingIndex[index] ?: return
        block["thinking"] = block.getOrElse("thinking") { "" } + text
    }

    fun appendThinkingSignature(index: Int, signature: String) {
        val block = anthropicThinkingIndex[index] ?: return
        anthropicThinkingSignature[index] = (anthropicThinkingSignature[index] ?: "") + signature
        block["signature"] = anthropicThinkingSignature[index]!!
    }

    fun thinkingBlocksJson(): String {
        if (anthropicThinking.isEmpty()) return ""
        val kept = buildJsonArray {
            for (block in anthropicThinking) {
                when (block["type"]) {
                    "thinking" -> if (!block["thinking"].isNullOrEmpty() && !block["signature"].isNullOrEmpty()) {
                        add(buildJsonObject { block.forEach { (k, v) -> put(k, v) } })
                    }
                    "redacted_thinking" -> if (!block["data"].isNullOrEmpty()) {
                        add(buildJsonObject { block.forEach { (k, v) -> put(k, v) } })
                    }
                }
            }
        }
        return if (kept.isEmpty()) "" else Json.encodeToString(JsonElement.serializer(), kept)
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

fun adapterFor(spec: ApiSpec): ProviderAdapter = when (spec) {
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

private fun reasoningChannel(delta: JsonObject?): String {
    if (delta == null) return ""
    for (key in arrayOf("reasoning_content", "reasoning")) {
        if (!delta.containsKey(key)) continue
        val value = delta[key]?.jsonPrimitive?.content ?: ""
        if (value.isNotEmpty()) return value
    }
    val details = delta["reasoning_details"]?.jsonArray ?: return ""
    val sb = StringBuilder()
    for (i in 0 until details.size) {
        val part = details[i].jsonObject
        if (!part.containsKey("text")) continue
        sb.append(part["text"]?.jsonPrimitive?.content ?: "")
    }
    return sb.toString()
}

internal const val TEMPERATURE = 0.6
internal const val TOP_P = 0.9
internal const val MAX_TOKENS = 2048

internal fun openAiUsage(usage: JsonObject?): TokenUsage {
    if (usage == null || usage.isEmpty()) return TokenUsage.NONE
    val prompt = usage["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: usage["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
    val details = usage["prompt_tokens_details"]?.jsonObject?.get("cached_tokens")?.jsonPrimitive?.intOrNull
        ?: usage["input_tokens_details"]?.jsonObject?.get("cached_tokens")?.jsonPrimitive?.intOrNull
        ?: 0
    val cached = maxOf(details, usage["prompt_cache_hit_tokens"]?.jsonPrimitive?.intOrNull ?: 0, usage["cached_tokens"]?.jsonPrimitive?.intOrNull ?: 0)
    val output = usage["completion_tokens"]?.jsonPrimitive?.intOrNull ?: usage["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
    return TokenUsage(prompt.coerceAtLeast(0), cached.coerceAtLeast(0), output.coerceAtLeast(0))
}

internal fun googleUsage(meta: JsonObject?): TokenUsage {
    if (meta == null) return TokenUsage.NONE
    val prompt = meta["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
    val cached = meta["cachedContentTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
    val output = (meta["candidatesTokenCount"]?.jsonPrimitive?.intOrNull ?: 0) + (meta["thoughtsTokenCount"]?.jsonPrimitive?.intOrNull ?: 0)
    return TokenUsage(prompt.coerceAtLeast(0), cached.coerceAtLeast(0), output.coerceAtLeast(0))
}

internal fun anthropicUsage(usage: JsonObject?): TokenUsage {
    if (usage == null || usage.isEmpty()) return TokenUsage.NONE
    val fresh = usage["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
    val created = usage["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
    val read = usage["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
    val output = usage["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
    return TokenUsage(
        promptTokens = (fresh + created + read).coerceAtLeast(0),
        cachedTokens = read.coerceAtLeast(0),
        outputTokens = output.coerceAtLeast(0)
    )
}

internal fun mergeUsage(current: TokenUsage, fresh: TokenUsage): TokenUsage = TokenUsage(
    promptTokens = if (fresh.promptTokens > 0) fresh.promptTokens else current.promptTokens,
    cachedTokens = if (fresh.promptTokens > 0) fresh.cachedTokens else current.cachedTokens,
    outputTokens = if (fresh.outputTokens > 0) fresh.outputTokens else current.outputTokens
)

private fun toolSchema(tools: List<ToolDefinition>): List<JsonObject> {
    return tools.map { t ->
        val props = buildJsonObject {
            for (p in t.params) {
                put(p.name, buildJsonObject {
                    put("type", p.type)
                    put("description", p.description)
                    if (p.type == "array") put("items", buildJsonObject { put("type", p.itemType) })
                })
            }
        }
        val required = buildJsonArray {
            for (p in t.params) {
                if (p.required) add(p.name)
            }
        }
        buildJsonObject {
            put("_name", t.name)
            put("_description", t.description)
            put("_schema", buildJsonObject { put("type", "object"); put("properties", props); put("required", required) })
        }
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

private fun imageFromOpenAiImages(images: JsonArray?): Pair<String, String>? {
    if (images == null) return null
    var found: Pair<String, String>? = null
    for (i in 0 until images.size) {
        val url = images[i].jsonObject["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""
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
    ): JsonObject {
        val echoReasoning = provider == com.lucent.app.data.ApiProviders.DEEPSEEK ||
            provider == com.lucent.app.data.ApiProviders.KIMI
        val messages = buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", systemPrompt) })
            for (turn in history) {
                when {
                    turn.toolCalls.isNotEmpty() -> {
                        add(buildJsonObject {
                            put("role", "assistant")
                            if (turn.content.isBlank()) put("content", JsonNull) else put("content", turn.content)
                            val calls = buildJsonArray {
                                for (c in turn.toolCalls) {
                                    add(buildJsonObject {
                                        put("id", c.id)
                                        put("type", "function")
                                        put("function", buildJsonObject {
                                            put("name", c.name)
                                            put("arguments", c.argumentsJson)
                                        })
                                    })
                                }
                            }
                            put("tool_calls", calls)
                            if (echoReasoning && turn.reasoningContent.isNotBlank()) {
                                put("reasoning_content", turn.reasoningContent)
                            }
                        })
                    }
                    turn.toolResults.isNotEmpty() -> {
                        for (r in turn.toolResults) {
                            add(buildJsonObject {
                                put("role", "tool")
                                put("tool_call_id", r.id)
                                put("content", r.content)
                            })
                        }
                        if (turn.attachmentData != null && turn.attachmentMime?.startsWith("image/") == true) {
                            add(buildJsonObject {
                                put("role", "user")
                                put("content", openAiContent(turn.copy(toolResults = emptyList())))
                            })
                        }
                    }
                    else -> {
                        add(buildJsonObject {
                            put("role", turn.role)
                            put("content", openAiContent(turn))
                            if (echoReasoning && turn.role == "assistant" && turn.reasoningContent.isNotBlank()) {
                                put("reasoning_content", turn.reasoningContent)
                            }
                        })
                    }
                }
            }
            if (context.isNotBlank()) {
                add(buildJsonObject { put("role", "system"); put("content", context) })
            }
        }

        val plan = ReasoningEfforts.planFor(provider, model, reasoning)
        return buildJsonObject {
            put("model", model)
            put("messages", messages)
            put("max_tokens", MAX_TOKENS)
            if (plan.empty) {
                put("temperature", TEMPERATURE)
                put("top_p", TOP_P)
            } else {
                plan.effort?.let { put("reasoning_effort", it) }
                if (plan.thinkingOff) put("thinking", buildJsonObject { put("type", "disabled") })
            }
            if (streaming && (provider == com.lucent.app.data.ApiProviders.CHATGPT ||
                    provider == com.lucent.app.data.ApiProviders.KIMI)
            ) {
                put("stream_options", buildJsonObject { put("include_usage", true) })
            }
            val cacheTtls = ReasoningEfforts.cacheOptionsFor(provider, model)
            if (cacheTtls.isNotEmpty()) {
                put("prompt_cache_options", buildJsonObject { put("mode", "implicit"); put("ttl", cacheTtls.first()) })
            }
            if (cacheKey.isNotBlank() &&
                (provider == com.lucent.app.data.ApiProviders.KIMI || provider == com.lucent.app.data.ApiProviders.CHATGPT)
            ) {
                put("prompt_cache_key", cacheKey)
            }
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (t in toolSchema(tools)) {
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", t["_name"]!!)
                                put("description", t["_description"]!!)
                                put("parameters", t["_schema"]!!)
                            })
                        })
                    }
                })
            }
        }
    }

    override fun parseReply(bodyStr: String): RawModelReply {
        val json = Json.parseToJsonElement(bodyStr).jsonObject
        val message = json["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject
        val text = message["content"]?.jsonPrimitive?.contentOrNull
        val toolCalls = mutableListOf<ToolCallRequest>()
        val toolCallsArray = message["tool_calls"]?.jsonArray
        if (toolCallsArray != null) {
            for (i in 0 until toolCallsArray.size) {
                val tc = toolCallsArray[i].jsonObject
                val fn = tc["function"]!!.jsonObject
                toolCalls.add(ToolCallRequest(tc["id"]?.jsonPrimitive?.content ?: "call_$i", fn["name"]!!.jsonPrimitive.content, fn["arguments"]?.jsonPrimitive?.content ?: "{}"))
            }
        }
        val image = imageFromOpenAiImages(message["images"]?.jsonArray)
        return RawModelReply(
            text,
            toolCalls,
            image?.first,
            image?.second,
            reasoningContent = message["reasoning_content"]?.jsonPrimitive?.content ?: "",
            usage = openAiUsage(json["usage"]?.jsonObject)
        )
    }

    override fun parseStreamEvent(
        json: JsonObject,
        acc: StreamAccumulator,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit
    ) {
        json["usage"]?.jsonObject?.let { reported ->
            if (reported.isNotEmpty()) acc.usage = openAiUsage(reported)
        }
        val delta = json["choices"]?.jsonArray?.let { if (it.isEmpty()) null else it[0].jsonObject["delta"]?.jsonObject }
        val reasoning = reasoningChannel(delta)
        if (reasoning.isNotEmpty()) {
            acc.fullReasoning.append(reasoning)
            onReasoning(reasoning)
        }
        val piece = delta?.get("content")?.jsonPrimitive?.contentOrNull ?: ""
        if (piece.isNotEmpty()) routeContent(piece, acc, onDelta, onReasoning)
        delta?.get("tool_calls")?.jsonArray?.let { tcArr ->
            for (i in 0 until tcArr.size) {
                val tc = tcArr[i].jsonObject
                val idx = tc["index"]?.jsonPrimitive?.intOrNull ?: 0
                val a = acc.openAiToolAcc.getOrPut(idx) { ToolAcc() }
                tc["id"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }?.let { a.id = it }
                tc["function"]?.jsonObject?.let { fn ->
                    fn["name"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }?.let { a.name = it }
                    a.args.append(fn["arguments"]?.jsonPrimitive?.contentOrNull ?: "")
                }
            }
        }
        imageFromOpenAiImages(delta?.get("images")?.jsonArray)?.let {
            acc.returnedImageMime = it.first; acc.returnedImageData = it.second
        }
    }

    private fun openAiContent(turn: ChatTurn): JsonElement {
        if (turn.attachmentData != null && turn.attachmentMime?.startsWith("image/") == true) {
            return buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", turn.content) })
                add(buildJsonObject {
                    put("type", "image_url")
                    put("image_url", buildJsonObject { put("url", "data:${turn.attachmentMime};base64,${turn.attachmentData}") })
                })
            }
        }
        return JsonPrimitive(turn.content)
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
    ): JsonObject {
        val messagesData = mutableListOf<JsonObject>()
        for (turn in history) {
            when {
                turn.toolCalls.isNotEmpty() -> {
                    val contentArr = buildJsonArray {
                        if (turn.thinkingBlocksJson.isNotBlank()) {
                            try {
                                val blocks = Json.parseToJsonElement(turn.thinkingBlocksJson).jsonArray
                                for (i in 0 until blocks.size) {
                                    add(blocks[i].jsonObject)
                                }
                            } catch (e: Exception) {
                            }
                        }
                        if (turn.content.isNotBlank()) {
                            add(buildJsonObject { put("type", "text"); put("text", turn.content) })
                        }
                        for (c in turn.toolCalls) {
                            val input = try { Json.parseToJsonElement(c.argumentsJson).jsonObject } catch (e: Exception) { buildJsonObject {} }
                            add(buildJsonObject {
                                put("type", "tool_use")
                                put("id", c.id)
                                put("name", c.name)
                                put("input", input)
                            })
                        }
                    }
                    messagesData.add(buildJsonObject { put("role", "assistant"); put("content", contentArr) })
                }
                turn.toolResults.isNotEmpty() -> {
                    val contentArr = buildJsonArray {
                        for (r in turn.toolResults) {
                            add(buildJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", r.id)
                                put("content", r.content)
                            })
                        }
                        if (turn.attachmentData != null && turn.attachmentMime?.startsWith("image/") == true) {
                            add(buildJsonObject {
                                put("type", "image")
                                put("source", buildJsonObject {
                                    put("type", "base64")
                                    put("media_type", turn.attachmentMime)
                                    put("data", turn.attachmentData)
                                })
                            })
                        }
                    }
                    messagesData.add(buildJsonObject { put("role", "user"); put("content", contentArr) })
                }
                else -> {
                    val role = if (turn.role == "assistant") "assistant" else "user"
                    messagesData.add(buildJsonObject { put("role", role); put("content", anthropicContent(turn)) })
                }
            }
        }

        if (messagesData.isNotEmpty()) {
            val lastIdx = messagesData.lastIndex
            val lastMsg = messagesData[lastIdx]
            val breakpoint = buildJsonObject { put("type", "ephemeral") }
            val newContent = when (val c = lastMsg["content"]!!) {
                is JsonArray -> {
                    val arr = c.toMutableList()
                    if (arr.isNotEmpty()) {
                        val lastBlock = arr.last().jsonObject
                        arr[arr.lastIndex] = buildJsonObject {
                            lastBlock.forEach { (k, v) -> put(k, v) }
                            put("cache_control", breakpoint)
                        }
                    }
                    JsonArray(arr)
                }
                is JsonPrimitive -> {
                    buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", c.content)
                            put("cache_control", breakpoint)
                        })
                    }
                }
                else -> c
            }
            messagesData[lastIdx] = buildJsonObject {
                lastMsg.forEach { (k, v) -> put(k, v) }
                put("content", newContent)
            }
        }

        val plan = ReasoningEfforts.planFor(provider, model, reasoning)
        val systemBlocks = buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", systemPrompt)
                put("cache_control", buildJsonObject { put("type", "ephemeral") })
            })
            if (context.isNotBlank()) {
                add(buildJsonObject { put("type", "text"); put("text", context) })
            }
        }
        return buildJsonObject {
            put("model", model)
            put("system", systemBlocks)
            put("messages", JsonArray(messagesData))
            val budgetTokens = plan.claudeBudgetTokens
            when {
                budgetTokens != null -> {
                    put("max_tokens", budgetTokens + 4096)
                    put("thinking", buildJsonObject { put("type", "enabled"); put("budget_tokens", budgetTokens) })
                }
                plan.claudeAdaptive -> {
                    put("max_tokens", MAX_TOKENS)
                    put("thinking", buildJsonObject { put("type", "adaptive") })
                    plan.claudeEffort?.let { put("output_config", buildJsonObject { put("effort", it) }) }
                }
                else -> {
                    put("max_tokens", MAX_TOKENS)
                    put("temperature", TEMPERATURE)
                    put("top_p", TOP_P)
                }
            }
            if (tools.isNotEmpty()) {
                val toolsArrayData = mutableListOf<JsonElement>()
                for (t in toolSchema(tools)) {
                    toolsArrayData.add(buildJsonObject {
                        put("name", t["_name"]!!)
                        put("description", t["_description"]!!)
                        put("input_schema", t["_schema"]!!)
                    })
                }
                if (toolsArrayData.isNotEmpty()) {
                    val lastIdx = toolsArrayData.lastIndex
                    val lastBlock = toolsArrayData[lastIdx].jsonObject
                    toolsArrayData[lastIdx] = buildJsonObject {
                        lastBlock.forEach { (k, v) -> put(k, v) }
                        put("cache_control", buildJsonObject { put("type", "ephemeral") })
                    }
                }
                put("tools", JsonArray(toolsArrayData))
            }
        }
    }

    override fun parseReply(bodyStr: String): RawModelReply {
        val json = Json.parseToJsonElement(bodyStr).jsonObject
        val contentArray = json["content"]?.jsonArray ?: buildJsonArray {}
        var text: String? = null
        val toolCalls = mutableListOf<ToolCallRequest>()
        for (i in 0 until contentArray.size) {
            val block = contentArray[i].jsonObject
            when (block["type"]?.jsonPrimitive?.content) {
                "text" -> text = (text ?: "") + (block["text"]?.jsonPrimitive?.content ?: "")
                "tool_use" -> {
                    val input = block["input"]?.jsonObject ?: buildJsonObject {}
                    toolCalls.add(ToolCallRequest(block["id"]?.jsonPrimitive?.content ?: "", block["name"]?.jsonPrimitive?.content ?: "", Json.encodeToString(JsonElement.serializer(), input)))
                }
            }
        }
        return RawModelReply(text, toolCalls, usage = anthropicUsage(json["usage"]?.jsonObject))
    }

    override fun parseStreamEvent(
        json: JsonObject,
        acc: StreamAccumulator,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit
    ) {
        when (json["type"]?.jsonPrimitive?.content) {
            "message_start" -> {
                json["message"]?.jsonObject?.get("usage")?.jsonObject?.let {
                    acc.usage = mergeUsage(acc.usage, anthropicUsage(it))
                }
            }
            "message_delta" -> {
                json["usage"]?.jsonObject?.let {
                    acc.usage = mergeUsage(acc.usage, anthropicUsage(it))
                }
            }
            "content_block_start" -> {
                val block = json["content_block"]?.jsonObject
                val idx = json["index"]?.jsonPrimitive?.intOrNull ?: 0
                when (block?.get("type")?.jsonPrimitive?.content) {
                    "tool_use" -> acc.anthropicToolAcc[idx] = ToolAcc(id = block["id"]?.jsonPrimitive?.content ?: "", name = block["name"]?.jsonPrimitive?.content ?: "")
                    "thinking" -> acc.beginThinkingBlock(idx, "thinking", null)
                    "redacted_thinking" -> acc.beginThinkingBlock(idx, "redacted_thinking", block["data"]?.jsonPrimitive?.content)
                }
            }
            "content_block_delta" -> {
                val delta = json["delta"]?.jsonObject
                when (delta?.get("type")?.jsonPrimitive?.content) {
                    "text_delta" -> {
                        val piece = delta["text"]?.jsonPrimitive?.contentOrNull ?: ""
                        if (piece.isNotEmpty()) routeContent(piece, acc, onDelta, onReasoning)
                    }
                    "thinking_delta" -> {
                        val piece = delta["thinking"]?.jsonPrimitive?.contentOrNull ?: ""
                        if (piece.isNotEmpty()) {
                            acc.fullReasoning.append(piece)
                            acc.appendThinkingText(json["index"]?.jsonPrimitive?.intOrNull ?: 0, piece)
                            onReasoning(piece)
                        }
                    }
                    "signature_delta" -> {
                        val signature = delta["signature"]?.jsonPrimitive?.contentOrNull ?: ""
                        if (signature.isNotEmpty()) acc.appendThinkingSignature(json["index"]?.jsonPrimitive?.intOrNull ?: 0, signature)
                    }
                    "input_json_delta" -> {
                        val idx = json["index"]?.jsonPrimitive?.intOrNull ?: 0
                        acc.anthropicToolAcc[idx]?.args?.append(delta["partial_json"]?.jsonPrimitive?.contentOrNull ?: "")
                    }
                }
            }
        }
    }

    private fun anthropicContent(turn: ChatTurn): JsonElement {
        if (turn.attachmentData != null && turn.attachmentMime?.startsWith("image/") == true) {
            return buildJsonArray {
                add(buildJsonObject {
                    put("type", "image")
                    put("source", buildJsonObject {
                        put("type", "base64")
                        put("media_type", turn.attachmentMime)
                        put("data", turn.attachmentData)
                    })
                })
                add(buildJsonObject { put("type", "text"); put("text", turn.content) })
            }
        }
        return JsonPrimitive(turn.content)
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
    ): JsonObject {
        val contentsData = mutableListOf<JsonObject>()
        for (turn in history) {
            when {
                turn.toolCalls.isNotEmpty() -> {
                    val parts = buildJsonArray {
                        if (turn.content.isNotBlank()) add(buildJsonObject { put("text", turn.content) })
                        for (c in turn.toolCalls) {
                            val args = try { Json.parseToJsonElement(c.argumentsJson).jsonObject } catch (e: Exception) { buildJsonObject {} }
                            val part = buildJsonObject {
                                put("functionCall", buildJsonObject {
                                    put("name", c.name)
                                    put("args", args)
                                })
                                c.thoughtSignature?.takeIf { it.isNotBlank() }?.let { put("thoughtSignature", it) }
                            }
                            add(part)
                        }
                    }
                    contentsData.add(buildJsonObject { put("role", "model"); put("parts", parts) })
                }
                turn.toolResults.isNotEmpty() -> {
                    val parts = buildJsonArray {
                        for (r in turn.toolResults) {
                            add(buildJsonObject {
                                put("functionResponse", buildJsonObject {
                                    put("name", r.name)
                                    put("response", buildJsonObject { put("result", r.content) })
                                })
                            })
                        }
                        if (turn.attachmentData != null && turn.attachmentMime?.startsWith("image/") == true) {
                            add(buildJsonObject {
                                put("inlineData", buildJsonObject {
                                    put("mimeType", turn.attachmentMime)
                                    put("data", turn.attachmentData)
                                })
                            })
                        }
                    }
                    contentsData.add(buildJsonObject { put("role", "user"); put("parts", parts) })
                }
                else -> {
                    val role = if (turn.role == "assistant") "model" else "user"
                    val parts = buildJsonArray {
                        add(buildJsonObject { put("text", turn.content) })
                        if (turn.attachmentMime?.startsWith("image/") == true && turn.attachmentData != null) {
                            add(buildJsonObject {
                                put("inlineData", buildJsonObject {
                                    put("mimeType", turn.attachmentMime)
                                    put("data", turn.attachmentData)
                                })
                            })
                        }
                    }
                    contentsData.add(buildJsonObject { put("role", role); put("parts", parts) })
                }
            }
        }

        if (context.isNotBlank()) {
            if (contentsData.isNotEmpty() && contentsData.last()["role"]?.jsonPrimitive?.content == "user") {
                val lastIdx = contentsData.lastIndex
                val lastMsg = contentsData[lastIdx]
                val parts = lastMsg["parts"]?.jsonArray?.toMutableList() ?: mutableListOf()
                parts.add(buildJsonObject { put("text", context) })
                contentsData[lastIdx] = buildJsonObject {
                    lastMsg.forEach { (k, v) -> put(k, v) }
                    put("parts", JsonArray(parts))
                }
            } else {
                contentsData.add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", context) }) })
                })
            }
        }

        val plan = ReasoningEfforts.planFor(provider, model, reasoning)
        val budget = plan.googleThinkingBudget
        val generation = buildJsonObject {
            put("temperature", TEMPERATURE)
            put("topP", TOP_P)
            put("maxOutputTokens", if (budget != null && budget > 0) budget + 2048 else MAX_TOKENS)
            when {
                plan.googleThinkingLevel != null ->
                    put("thinkingConfig", buildJsonObject { put("thinkingLevel", plan.googleThinkingLevel) })
                budget != null ->
                    put("thinkingConfig", buildJsonObject { put("thinkingBudget", budget) })
            }
        }

        return buildJsonObject {
            put("contents", JsonArray(contentsData))
            put("generationConfig", generation)
            if (systemPrompt.isNotBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", systemPrompt) }) })
                })
            }
            if (tools.isNotEmpty()) {
                val declarations = buildJsonArray {
                    for (t in toolSchema(tools)) {
                        add(buildJsonObject {
                            put("name", t["_name"]!!)
                            put("description", t["_description"]!!)
                            put("parameters", t["_schema"]!!)
                        })
                    }
                }
                put("tools", buildJsonArray { add(buildJsonObject { put("functionDeclarations", declarations) }) })
            }
        }
    }

    override fun parseReply(bodyStr: String): RawModelReply {
        val json = Json.parseToJsonElement(bodyStr).jsonObject
        val candidates = json["candidates"]?.jsonArray
        if (candidates == null || candidates.isEmpty()) return RawModelReply(null, emptyList())
        val content = candidates[0].jsonObject["content"]?.jsonObject ?: buildJsonObject {}
        val parts = content["parts"]?.jsonArray ?: buildJsonArray {}
        var text: String? = null
        val toolCalls = mutableListOf<ToolCallRequest>()
        var imageMime: String? = null
        var imageData: String? = null

        for (i in 0 until parts.size) {
            val part = parts[i].jsonObject
            if (part.containsKey("text")) text = (text ?: "") + (part["text"]?.jsonPrimitive?.content ?: "")
            part["functionCall"]?.jsonObject?.let { fc ->
                val args = fc["args"]?.jsonObject ?: buildJsonObject {}
                val sig = part["thoughtSignature"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                toolCalls.add(ToolCallRequest("call_$i", fc["name"]?.jsonPrimitive?.content ?: "", Json.encodeToString(JsonElement.serializer(), args), sig))
            }
            part["inlineData"]?.jsonObject?.let { inlineData ->
                imageMime = inlineData["mimeType"]?.jsonPrimitive?.content
                imageData = inlineData["data"]?.jsonPrimitive?.content
            }
        }
        return RawModelReply(text, toolCalls, imageMime, imageData, usage = googleUsage(json["usageMetadata"]?.jsonObject))
    }

    override fun parseStreamEvent(
        json: JsonObject,
        acc: StreamAccumulator,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit
    ) {
        json["usageMetadata"]?.jsonObject?.let { acc.usage = googleUsage(it) }
        val candidates = json["candidates"]?.jsonArray
        val parts = if (candidates != null && candidates.isNotEmpty()) candidates[0].jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray else null
        if (parts != null) {
            for (i in 0 until parts.size) {
                val part = parts[i].jsonObject
                val piece = part["text"]?.jsonPrimitive?.contentOrNull ?: ""
                if (piece.isNotEmpty()) {
                    if (part["thought"]?.jsonPrimitive?.booleanOrNull == true) {
                        acc.fullReasoning.append(piece)
                        onReasoning(piece)
                    } else {
                        routeContent(piece, acc, onDelta, onReasoning)
                    }
                }
                part["functionCall"]?.jsonObject?.let { fc ->
                    val args = fc["args"]?.jsonObject ?: buildJsonObject {}
                    val a = acc.openAiToolAcc.getOrPut(i) { ToolAcc() }
                    a.name = fc["name"]?.jsonPrimitive?.content ?: ""
                    a.args.append(Json.encodeToString(JsonElement.serializer(), args))
                    part["thoughtSignature"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }?.let { a.thoughtSignature = it }
                }
                part["inlineData"]?.jsonObject?.let { inlineData ->
                    acc.returnedImageMime = inlineData["mimeType"]?.jsonPrimitive?.content
                    acc.returnedImageData = inlineData["data"]?.jsonPrimitive?.content
                }
            }
        }
    }
}
