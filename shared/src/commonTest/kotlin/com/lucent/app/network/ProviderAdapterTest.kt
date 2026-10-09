package com.lucent.app.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.double
import kotlinx.serialization.json.boolean
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProviderAdapterTest {

    private val tools = listOf(
        ToolDefinition(
            name = "create_task",
            description = "Create a task",
            params = listOf(ToolParam("title", "string", "The title", required = true))
        )
    )

    private fun sampleHistory(): List<ChatTurn> = listOf(
        ChatTurn(role = "user", content = "Hello"),
        ChatTurn(role = "assistant", content = "Hi there")
    )

    private fun jsonObj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject


    @Test
    fun `openai chat url`() {
        assertEquals(
            "https://api.example.com/chat/completions",
            OpenAiAdapter.chatUrl("https://api.example.com/", "model-x", streaming = true)
        )
    }

    @Test
    fun `openai models url`() {
        assertEquals("https://api.example.com/models", OpenAiAdapter.modelsUrl("https://api.example.com"))
    }

    @Test
    fun `anthropic chat url`() {
        assertEquals(
            "https://api.example.com/messages",
            AnthropicAdapter.chatUrl("https://api.example.com", "claude-x", streaming = true)
        )
    }

    @Test
    fun `google chat url strips model prefix and uses streaming endpoint`() {
        assertEquals(
            "https://api.example.com/models/gemini-x:streamGenerateContent?alt=sse",
            GoogleAdapter.chatUrl("https://api.example.com", "models/gemini-x", streaming = true)
        )
    }


    @Test
    fun `openai auth uses bearer`() {
        val b = okhttp3.Request.Builder().url("https://x.example.com/")
        OpenAiAdapter.addAuthHeaders(b, "secret")
        assertEquals("Bearer secret", b.build().header("Authorization"))
        assertEquals("application/json", b.build().header("Content-Type"))
    }

    @Test
    fun `anthropic auth uses api key and version`() {
        val b = okhttp3.Request.Builder().url("https://x.example.com/")
        AnthropicAdapter.addAuthHeaders(b, "sk-ant")
        assertEquals("sk-ant", b.build().header("x-api-key"))
        assertEquals("2023-06-01", b.build().header("anthropic-version"))
    }

    @Test
    fun `google auth uses api key header`() {
        val b = okhttp3.Request.Builder().url("https://x.example.com/")
        GoogleAdapter.addAuthHeaders(b, "gkey")
        assertEquals("gkey", b.build().header("x-goog-api-key"))
    }


    @Test
    fun `openai body shape`() {
        val body = OpenAiAdapter.buildBody("model-x", sampleHistory(), "sys", tools, streaming = false)
        assertEquals("model-x", body["model"]!!.jsonPrimitive.content)
        assertEquals("sys", body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", body["messages"]!!.jsonArray[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertTrue(body["temperature"]!!.jsonPrimitive.double > 0)
        val t = body["tools"]!!.jsonArray[0].jsonObject
        assertEquals("function", t["type"]!!.jsonPrimitive.content)
        assertEquals("create_task", t["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `anthropic body shape`() {
        val body = AnthropicAdapter.buildBody("claude-x", sampleHistory(), "sys", tools, streaming = false)
        assertEquals("claude-x", body["model"]!!.jsonPrimitive.content)
        val system = body["system"]!!.jsonArray
        assertEquals("sys", system[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertNotNull(system[0].jsonObject["cache_control"] as? JsonObject)
        val messages = body["messages"]!!.jsonArray
        assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        val t = body["tools"]!!.jsonArray[0].jsonObject
        assertEquals("create_task", t["name"]!!.jsonPrimitive.content)
        assertNotNull(t["input_schema"] as? JsonObject)
    }

    @Test
    fun `google body shape`() {
        val body = GoogleAdapter.buildBody("gemini-x", sampleHistory(), "sys", tools, streaming = true)
        val contents = body["contents"]!!.jsonArray
        assertEquals("user", contents[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertTrue(body.containsKey("generationConfig"))
        val decls = body["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray
        assertEquals("create_task", decls[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("sys", body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
    }


    @Test
    fun `openai reply parses text and tool calls`() {
        val json = """{"choices":[{"message":{"content":"Hi","tool_calls":[
            {"id":"call_1","function":{"name":"create_task","arguments":"{\"title\":\"x\"}"}}
        ]}}]}"""
        val reply = OpenAiAdapter.parseReply(json)
        assertEquals("Hi", reply.text)
        assertEquals(1, reply.toolCalls.size)
        assertEquals("call_1", reply.toolCalls[0].id)
        assertEquals("create_task", reply.toolCalls[0].name)
    }

    @Test
    fun `openai reply tolerates null usage and null message fields`() {
        val json = """{"choices":[{"message":{"content":null,"tool_calls":null}}],"usage":null}"""
        val reply = OpenAiAdapter.parseReply(json)
        assertEquals(null, reply.text)
        assertTrue(reply.toolCalls.isEmpty())
    }

    @Test
    fun `openai reply tolerates null message object`() {
        val json = """{"choices":[{"message":null}]}"""
        try {
            OpenAiAdapter.parseReply(json)
            assertFalse(true, "expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("message") == true)
        }
    }

    @Test
    fun `openai reply surfaces provider error body`() {
        val json = """{"error":{"message":"No available channel","type":"mixroute_error"}}"""
        try {
            OpenAiAdapter.parseReply(json)
            assertFalse(true, "expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("No available channel") == true)
        }
    }

    @Test
    fun `openai reply rejects non object body`() {
        try {
            OpenAiAdapter.parseReply("null")
            assertFalse(true, "expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("非 JSON 对象") == true)
        }
    }

    @Test
    fun `openai reply accepts object arguments`() {
        val json = """{"choices":[{"message":{"content":"","tool_calls":[
            {"id":"c1","function":{"name":"create_task","arguments":{"title":"x"}}}
        ]}}]}"""
        val reply = OpenAiAdapter.parseReply(json)
        assertEquals(1, reply.toolCalls.size)
        assertTrue(reply.toolCalls[0].argumentsJson.contains("title"))
    }

    @Test
    fun `anthropic reply parses text and tool_use blocks`() {
        val json = """{"content":[
            {"type":"text","text":"Hello"},
            {"type":"tool_use","id":"tu_1","name":"create_task","input":{"title":"x"}}
        ]}"""
        val reply = AnthropicAdapter.parseReply(json)
        assertEquals("Hello", reply.text)
        assertEquals(1, reply.toolCalls.size)
        assertEquals("tu_1", reply.toolCalls[0].id)
        assertEquals("create_task", reply.toolCalls[0].name)
    }

    @Test
    fun `google reply parses text and function calls`() {
        val json = """{"candidates":[{"content":{"parts":[
            {"text":"Hi"},
            {"functionCall":{"name":"create_task","args":{"title":"x"}}}
        ]}}]}"""
        val reply = GoogleAdapter.parseReply(json)
        assertEquals("Hi", reply.text)
        assertEquals(1, reply.toolCalls.size)
        assertEquals("create_task", reply.toolCalls[0].name)
    }


    @Test
    fun `openai stream event accumulates text`() {
        val acc = StreamAccumulator()
        val json = jsonObj("""{"choices":[{"delta":{"content":"Hel"}}]}""")
        val collected = StringBuilder()
        OpenAiAdapter.parseStreamEvent(json, acc, { collected.append(it) })
        assertEquals("Hel", acc.fullText.toString())
        assertEquals("Hel", collected.toString())
    }

    @Test
    fun `anthropic stream event accumulates text delta`() {
        val acc = StreamAccumulator()
        val json = jsonObj("""{"type":"content_block_delta","delta":{"type":"text_delta","text":"lo"}}""")
        val collected = StringBuilder()
        AnthropicAdapter.parseStreamEvent(json, acc, { collected.append(it) })
        assertEquals("lo", acc.fullText.toString())
    }

    @Test
    fun `google stream event accumulates text and function call`() {
        val acc = StreamAccumulator()
        val json = jsonObj(
            """{"candidates":[{"content":{"parts":[{"text":"ok"},{"functionCall":{"name":"create_task","args":{"title":"x"}}}]}}]}"""
        )
        GoogleAdapter.parseStreamEvent(json, acc, {})
        assertEquals("ok", acc.fullText.toString())
        val calls = acc.toolCalls(useAnthropicAcc = false)
        assertEquals(1, calls.size)
        assertEquals("create_task", calls[0].name)
    }

    @Test
    fun `anthropic sends effort with adaptive thinking and caches tools and system`() {
        val body = AnthropicAdapter.buildBody(
            "claude-opus-5.5", sampleHistory(), "sys", tools, streaming = true,
            reasoning = "high", provider = "claude"
        )
        assertEquals("adaptive", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("high", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertNotNull(body["tools"]!!.jsonArray[0].jsonObject["cache_control"] as? JsonObject)
        val last = body["messages"]!!.jsonArray[1].jsonObject
        assertNotNull(last["content"]!!.jsonArray[0].jsonObject["cache_control"] as? JsonObject)
    }

    @Test
    fun `anthropic keeps the legacy budget for older models and sends no effort`() {
        val body = AnthropicAdapter.buildBody(
            "claude-sonnet-4-5", sampleHistory(), "sys", tools, streaming = true,
            reasoning = "high", provider = "claude"
        )
        assertEquals("enabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(16384, body["thinking"]!!.jsonObject["budget_tokens"]!!.jsonPrimitive.int)
        assertFalse(body.containsKey("output_config"))
    }

    @Test
    fun `openai sends the effort, the cache key and a usage request only when streaming`() {
        val body = OpenAiAdapter.buildBody(
            "gpt-5.6-terra", sampleHistory(), "sys", tools, streaming = true,
            reasoning = "xhigh", provider = "chatgpt", cacheKey = "lucent-7"
        )
        assertEquals("xhigh", body["reasoning_effort"]!!.jsonPrimitive.content)
        assertEquals("lucent-7", body["prompt_cache_key"]!!.jsonPrimitive.content)
        assertEquals("30m", body["prompt_cache_options"]!!.jsonObject["ttl"]!!.jsonPrimitive.content)
        assertTrue(body["stream_options"]!!.jsonObject["include_usage"]!!.jsonPrimitive.boolean)
        assertFalse(body.containsKey("temperature"))
    }

    @Test
    fun `openai hides reasoning fields for models without the parameter`() {
        val body = OpenAiAdapter.buildBody(
            "gpt-4o", sampleHistory(), "sys", tools, streaming = false,
            reasoning = "high", provider = "chatgpt"
        )
        assertFalse(body.containsKey("reasoning_effort"))
        assertTrue(body["temperature"]!!.jsonPrimitive.double > 0)
    }

    @Test
    fun `deepseek gets effort and echoes reasoning content`() {
        val history = listOf(
            ChatTurn(role = "assistant", content = "ok", reasoningContent = "because")
        )
        val body = OpenAiAdapter.buildBody(
            "deepseek-flash", history, "sys", tools, streaming = true,
            reasoning = "low", provider = "deepseek"
        )
        assertEquals("low", body["reasoning_effort"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("stream_options"))
        assertEquals("because", body["messages"]!!.jsonArray[1].jsonObject["reasoning_content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `google switches to thinking level for gemini 3 and budget for 2_5`() {
        val three = GoogleAdapter.buildBody(
            "gemini-3.5-flash", sampleHistory(), "sys", tools, streaming = true,
            reasoning = "low", provider = "gemini"
        )
        val config = three["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject
        assertEquals("low", config["thinkingLevel"]!!.jsonPrimitive.content)
        assertFalse(config.containsKey("thinkingBudget"))
        val two = GoogleAdapter.buildBody(
            "gemini-2.5-flash", sampleHistory(), "sys", tools, streaming = true,
            reasoning = "none", provider = "gemini"
        )
        val legacy = two["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject
        assertEquals(0, legacy["thinkingBudget"]!!.jsonPrimitive.int)
        assertFalse(legacy.containsKey("thinkingLevel"))
    }

    @Test
    fun `context rides at the end of every request`() {
        val openai = OpenAiAdapter.buildBody(
            "gpt-5.6-terra", sampleHistory(), "sys", tools, streaming = false,
            provider = "chatgpt", context = "the time is now"
        )
        val messages = openai["messages"]!!.jsonArray
        assertEquals("system", messages[messages.size - 1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("the time is now", messages[messages.size - 1].jsonObject["content"]!!.jsonPrimitive.content)
        val claude = AnthropicAdapter.buildBody(
            "claude-opus-5.5", sampleHistory(), "sys", tools, streaming = false,
            provider = "claude", context = "the time is now"
        )
        assertEquals("the time is now", claude["system"]!!.jsonArray[1].jsonObject["text"]!!.jsonPrimitive.content)
        val google = GoogleAdapter.buildBody(
            "gemini-3.5-flash", listOf(ChatTurn(role = "user", content = "Hello")), "sys", tools,
            streaming = true, provider = "gemini", context = "the time is now"
        )
        val contents = google["contents"]!!.jsonArray
        assertEquals(1, contents.size)
        val parts = contents[0].jsonObject["parts"]!!.jsonArray
        assertEquals("Hello", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("the time is now", parts[1].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `openai usage reports the cached share`() {
        val acc = StreamAccumulator()
        OpenAiAdapter.parseStreamEvent(
            jsonObj(
                """{"choices":[],"usage":{"prompt_tokens":1000,"completion_tokens":50,
                    "prompt_tokens_details":{"cached_tokens":896}}}"""
            ),
            acc,
            {}
        )
        assertEquals(1000, acc.usage.promptTokens)
        assertEquals(896, acc.usage.cachedTokens)
        assertEquals(89, acc.usage.hitPercent)
    }

    @Test
    fun `deepseek usage reports the hit tokens`() {
        val acc = StreamAccumulator()
        OpenAiAdapter.parseStreamEvent(
            jsonObj("""{"choices":[],"usage":{"prompt_tokens":900,"prompt_cache_hit_tokens":450}}"""),
            acc,
            {}
        )
        assertEquals(450, acc.usage.cachedTokens)
    }

    @Test
    fun `anthropic streaming usage adds the cache read to the prompt`() {
        val acc = StreamAccumulator()
        AnthropicAdapter.parseStreamEvent(
            jsonObj(
                """{"type":"message_start","message":{"usage":{"input_tokens":100,
                    "cache_creation_input_tokens":0,"cache_read_input_tokens":900,"output_tokens":2}}}"""
            ),
            acc,
            {}
        )
        assertEquals(1000, acc.usage.promptTokens)
        assertEquals(900, acc.usage.cachedTokens)
        AnthropicAdapter.parseStreamEvent(
            jsonObj("""{"type":"message_delta","usage":{"output_tokens":120}}"""),
            acc,
            {}
        )
        assertEquals(1000, acc.usage.promptTokens)
        assertEquals(120, acc.usage.outputTokens)
    }

    @Test
    fun `google streaming usage is read from the metadata`() {
        val acc = StreamAccumulator()
        GoogleAdapter.parseStreamEvent(
            jsonObj(
                """{"usageMetadata":{"promptTokenCount":500,"cachedContentTokenCount":400,
                    "candidatesTokenCount":20,"thoughtsTokenCount":5}}"""
            ),
            acc,
            {}
        )
        assertEquals(500, acc.usage.promptTokens)
        assertEquals(400, acc.usage.cachedTokens)
        assertEquals(25, acc.usage.outputTokens)
    }

    @Test
    fun `adapterFor resolves every spec`() {
        assertEquals(OpenAiAdapter, adapterFor(ApiSpec.OPENAI))
        assertEquals(AnthropicAdapter, adapterFor(ApiSpec.ANTHROPIC))
        assertEquals(GoogleAdapter, adapterFor(ApiSpec.GOOGLE))
    }

    @Test
    fun `openai reasoning content is kept out of the answer`() {
        val acc = StreamAccumulator()
        val reasoning = StringBuilder()
        val answer = StringBuilder()
        val deltas = listOf(
            """{"choices":[{"delta":{"reasoning_content":"weighing options"}}]}""",
            """{"choices":[{"delta":{"content":"Answer"}}]}"""
        )
        deltas.forEach {
            OpenAiAdapter.parseStreamEvent(jsonObj(it), acc, { answer.append(it) }, { reasoning.append(it) })
        }
        assertEquals("weighing options", reasoning.toString())
        assertEquals("Answer", answer.toString())
        assertEquals("weighing options", acc.fullReasoning.toString())
        assertEquals("Answer", acc.fullText.toString())
    }

    @Test
    fun `openai reasoning field is accepted as well`() {
        val acc = StreamAccumulator()
        val reasoning = StringBuilder()
        OpenAiAdapter.parseStreamEvent(
            jsonObj("""{"choices":[{"delta":{"reasoning":"thinking"}}]}"""),
            acc,
            {},
            { reasoning.append(it) }
        )
        assertEquals("thinking", reasoning.toString())
        assertEquals("", acc.fullText.toString())
    }

    @Test
    fun `think blocks split across deltas land in reasoning only`() {
        val acc = StreamAccumulator()
        val reasoning = StringBuilder()
        val answer = StringBuilder()
        val deltas = listOf(
            """{"choices":[{"delta":{"content":"Heads up <thi"}}]}""",
            """{"choices":[{"delta":{"content":"nk>secret plan</thi"}}]}""",
            """{"choices":[{"delta":{"content":"nk>Done"}}]}"""
        )
        deltas.forEach {
            OpenAiAdapter.parseStreamEvent(jsonObj(it), acc, { answer.append(it) }, { reasoning.append(it) })
        }
        assertEquals("Heads up Done", answer.toString())
        assertEquals("secret plan", reasoning.toString())
        assertEquals("Heads up Done", acc.fullText.toString())
    }

    @Test
    fun `anthropic thinking delta is reasoning not answer`() {
        val acc = StreamAccumulator()
        val reasoning = StringBuilder()
        val answer = StringBuilder()
        AnthropicAdapter.parseStreamEvent(
            jsonObj("""{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"hmm"}}"""),
            acc,
            { answer.append(it) },
            { reasoning.append(it) }
        )
        AnthropicAdapter.parseStreamEvent(
            jsonObj("""{"type":"content_block_delta","delta":{"type":"text_delta","text":"Sure"}}"""),
            acc,
            { answer.append(it) },
            { reasoning.append(it) }
        )
        assertEquals("hmm", reasoning.toString())
        assertEquals("Sure", answer.toString())
        assertEquals("Sure", acc.fullText.toString())
    }

    @Test
    fun `google thought parts are reasoning not answer`() {
        val acc = StreamAccumulator()
        val reasoning = StringBuilder()
        val answer = StringBuilder()
        GoogleAdapter.parseStreamEvent(
            jsonObj("""{"candidates":[{"content":{"parts":[{"text":"planning","thought":true},{"text":"Result"}]}}]}"""),
            acc,
            { answer.append(it) },
            { reasoning.append(it) }
        )
        assertEquals("planning", reasoning.toString())
        assertEquals("Result", answer.toString())
        assertEquals("Result", acc.fullText.toString())
    }
}
