package com.lucent.app.network

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
        streaming: Boolean
    ): JSONObject

    fun parseReply(bodyStr: String): RawModelReply

    fun parseStreamEvent(json: JSONObject, acc: StreamAccumulator, onDelta: (String) -> Unit)
}

class StreamAccumulator {
    val fullText = StringBuilder()
    val openAiToolAcc = LinkedHashMap<Int, ToolAcc>()
    val anthropicToolAcc = LinkedHashMap<Int, ToolAcc>()
    var returnedImageMime: String? = null
    var returnedImageData: String? = null

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

internal const val TEMPERATURE = 0.6
internal const val TOP_P = 0.9
internal const val MAX_TOKENS = 2048

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
        streaming: Boolean
    ): JSONObject {
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
                else -> messages.put(JSONObject().put("role", turn.role).put("content", openAiContent(turn)))
            }
        }

        val root = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", TEMPERATURE)
            .put("top_p", TOP_P)
            .put("max_tokens", MAX_TOKENS)
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
        return RawModelReply(text, toolCalls, image?.first, image?.second)
    }

    override fun parseStreamEvent(json: JSONObject, acc: StreamAccumulator, onDelta: (String) -> Unit) {
        val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
        val piece = delta?.let { if (it.isNull("content")) "" else it.optString("content", "") } ?: ""
        if (piece.isNotEmpty()) { acc.fullText.append(piece); onDelta(piece) }
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
        streaming: Boolean
    ): JSONObject {
        val messages = JSONArray()
        for (turn in history) {
            when {
                turn.toolCalls.isNotEmpty() -> {
                    val content = JSONArray()
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

        val root = JSONObject()
            .put("model", model)
            .put("max_tokens", MAX_TOKENS)
            .put("temperature", TEMPERATURE)
            .put("top_p", TOP_P)
            .put("system", systemPrompt)
            .put("messages", messages)
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
            root.put("tools", toolsArray)
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
        return RawModelReply(text, toolCalls)
    }

    override fun parseStreamEvent(json: JSONObject, acc: StreamAccumulator, onDelta: (String) -> Unit) {
        when (json.optString("type")) {
            "content_block_start" -> {
                val block = json.optJSONObject("content_block")
                if (block?.optString("type") == "tool_use") {
                    val idx = json.optInt("index", 0)
                    acc.anthropicToolAcc[idx] = ToolAcc(id = block.optString("id"), name = block.optString("name"))
                }
            }
            "content_block_delta" -> {
                val delta = json.optJSONObject("delta")
                when (delta?.optString("type")) {
                    "text_delta" -> {
                        val piece = if (delta.isNull("text")) "" else delta.optString("text", "")
                        if (piece.isNotEmpty()) { acc.fullText.append(piece); onDelta(piece) }
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
        streaming: Boolean
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
        root.put(
            "generationConfig",
            JSONObject()
                .put("temperature", TEMPERATURE)
                .put("topP", TOP_P)
                .put("maxOutputTokens", MAX_TOKENS)
        )
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
        return RawModelReply(text, toolCalls, imageMime, imageData)
    }

    override fun parseStreamEvent(json: JSONObject, acc: StreamAccumulator, onDelta: (String) -> Unit) {
        val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val piece = if (part.isNull("text")) "" else part.optString("text", "")
                if (piece.isNotEmpty()) { acc.fullText.append(piece); onDelta(piece) }
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
