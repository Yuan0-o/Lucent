package com.lucent.app.network

import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals("model-x", body.getString("model"))
        assertEquals("sys", body.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertEquals("user", body.getJSONArray("messages").getJSONObject(1).getString("role"))
        assertTrue(body.getDouble("temperature") > 0)
        val t = body.getJSONArray("tools").getJSONObject(0)
        assertEquals("function", t.getString("type"))
        assertEquals("create_task", t.getJSONObject("function").getString("name"))
    }

    @Test
    fun `anthropic body shape`() {
        val body = AnthropicAdapter.buildBody("claude-x", sampleHistory(), "sys", tools, streaming = false)
        assertEquals("claude-x", body.getString("model"))
        val system = body.getJSONArray("system")
        assertEquals("sys", system.getJSONObject(0).getString("text"))
        assertNotNull(system.getJSONObject(0).optJSONObject("cache_control"))
        val messages = body.getJSONArray("messages")
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        val t = body.getJSONArray("tools").getJSONObject(0)
        assertEquals("create_task", t.getString("name"))
        assertNotNull(t.optJSONObject("input_schema"))
    }

    @Test
    fun `google body shape`() {
        val body = GoogleAdapter.buildBody("gemini-x", sampleHistory(), "sys", tools, streaming = true)
        val contents = body.getJSONArray("contents")
        assertEquals("user", contents.getJSONObject(0).getString("role"))
        assertTrue(body.has("generationConfig"))
        val decls = body.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations")
        assertEquals("create_task", decls.getJSONObject(0).getString("name"))
        assertEquals("sys", body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
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
        val json = org.json.JSONObject("""{"choices":[{"delta":{"content":"Hel"}}]}""")
        val collected = StringBuilder()
        OpenAiAdapter.parseStreamEvent(json, acc, { collected.append(it) })
        assertEquals("Hel", acc.fullText.toString())
        assertEquals("Hel", collected.toString())
    }

    @Test
    fun `anthropic stream event accumulates text delta`() {
        val acc = StreamAccumulator()
        val json = org.json.JSONObject("""{"type":"content_block_delta","delta":{"type":"text_delta","text":"lo"}}""")
        val collected = StringBuilder()
        AnthropicAdapter.parseStreamEvent(json, acc, { collected.append(it) })
        assertEquals("lo", acc.fullText.toString())
    }

    @Test
    fun `google stream event accumulates text and function call`() {
        val acc = StreamAccumulator()
        val json = org.json.JSONObject(
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
        assertEquals("adaptive", body.getJSONObject("thinking").getString("type"))
        assertEquals("high", body.getJSONObject("output_config").getString("effort"))
        assertNotNull(body.getJSONArray("tools").getJSONObject(0).optJSONObject("cache_control"))
        val last = body.getJSONArray("messages").getJSONObject(1)
        assertNotNull(last.getJSONArray("content").getJSONObject(0).optJSONObject("cache_control"))
    }

    @Test
    fun `anthropic keeps the legacy budget for older models and sends no effort`() {
        val body = AnthropicAdapter.buildBody(
            "claude-sonnet-4-5", sampleHistory(), "sys", tools, streaming = true,
            reasoning = "high", provider = "claude"
        )
        assertEquals("enabled", body.getJSONObject("thinking").getString("type"))
        assertEquals(16384, body.getJSONObject("thinking").getInt("budget_tokens"))
        assertFalse(body.has("output_config"))
    }

    @Test
    fun `openai sends the effort, the cache key and a usage request only when streaming`() {
        val body = OpenAiAdapter.buildBody(
            "gpt-5.6-terra", sampleHistory(), "sys", tools, streaming = true,
            reasoning = "xhigh", provider = "chatgpt", cacheKey = "lucent-7"
        )
        assertEquals("xhigh", body.getString("reasoning_effort"))
        assertEquals("lucent-7", body.getString("prompt_cache_key"))
        assertEquals("30m", body.getJSONObject("prompt_cache_options").getString("ttl"))
        assertTrue(body.getJSONObject("stream_options").getBoolean("include_usage"))
        assertFalse(body.has("temperature"))
    }

    @Test
    fun `openai hides reasoning fields for models without the parameter`() {
        val body = OpenAiAdapter.buildBody(
            "gpt-4o", sampleHistory(), "sys", tools, streaming = false,
            reasoning = "high", provider = "chatgpt"
        )
        assertFalse(body.has("reasoning_effort"))
        assertTrue(body.getDouble("temperature") > 0)
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
        assertEquals("low", body.getString("reasoning_effort"))
        assertFalse(body.has("stream_options"))
        assertEquals("because", body.getJSONArray("messages").getJSONObject(1).getString("reasoning_content"))
    }

    @Test
    fun `google switches to thinking level for gemini 3 and budget for 2_5`() {
        val three = GoogleAdapter.buildBody(
            "gemini-3.5-flash", sampleHistory(), "sys", tools, streaming = true,
            reasoning = "low", provider = "gemini"
        )
        val config = three.getJSONObject("generationConfig").getJSONObject("thinkingConfig")
        assertEquals("low", config.getString("thinkingLevel"))
        assertFalse(config.has("thinkingBudget"))
        val two = GoogleAdapter.buildBody(
            "gemini-2.5-flash", sampleHistory(), "sys", tools, streaming = true,
            reasoning = "none", provider = "gemini"
        )
        val legacy = two.getJSONObject("generationConfig").getJSONObject("thinkingConfig")
        assertEquals(0, legacy.getInt("thinkingBudget"))
        assertFalse(legacy.has("thinkingLevel"))
    }

    @Test
    fun `context rides at the end of every request`() {
        val openai = OpenAiAdapter.buildBody(
            "gpt-5.6-terra", sampleHistory(), "sys", tools, streaming = false,
            provider = "chatgpt", context = "the time is now"
        )
        val messages = openai.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(messages.length() - 1).getString("role"))
        assertEquals("the time is now", messages.getJSONObject(messages.length() - 1).getString("content"))
        val claude = AnthropicAdapter.buildBody(
            "claude-opus-5.5", sampleHistory(), "sys", tools, streaming = false,
            provider = "claude", context = "the time is now"
        )
        assertEquals("the time is now", claude.getJSONArray("system").getJSONObject(1).getString("text"))
        val google = GoogleAdapter.buildBody(
            "gemini-3.5-flash", listOf(ChatTurn(role = "user", content = "Hello")), "sys", tools,
            streaming = true, provider = "gemini", context = "the time is now"
        )
        val contents = google.getJSONArray("contents")
        assertEquals(1, contents.length())
        val parts = contents.getJSONObject(0).getJSONArray("parts")
        assertEquals("Hello", parts.getJSONObject(0).getString("text"))
        assertEquals("the time is now", parts.getJSONObject(1).getString("text"))
    }

    @Test
    fun `openai usage reports the cached share`() {
        val acc = StreamAccumulator()
        OpenAiAdapter.parseStreamEvent(
            org.json.JSONObject(
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
            org.json.JSONObject("""{"choices":[],"usage":{"prompt_tokens":900,"prompt_cache_hit_tokens":450}}"""),
            acc,
            {}
        )
        assertEquals(450, acc.usage.cachedTokens)
    }

    @Test
    fun `anthropic streaming usage adds the cache read to the prompt`() {
        val acc = StreamAccumulator()
        AnthropicAdapter.parseStreamEvent(
            org.json.JSONObject(
                """{"type":"message_start","message":{"usage":{"input_tokens":100,
                    "cache_creation_input_tokens":0,"cache_read_input_tokens":900,"output_tokens":2}}}"""
            ),
            acc,
            {}
        )
        assertEquals(1000, acc.usage.promptTokens)
        assertEquals(900, acc.usage.cachedTokens)
        AnthropicAdapter.parseStreamEvent(
            org.json.JSONObject("""{"type":"message_delta","usage":{"output_tokens":120}}"""),
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
            org.json.JSONObject(
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
            OpenAiAdapter.parseStreamEvent(org.json.JSONObject(it), acc, { answer.append(it) }, { reasoning.append(it) })
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
            org.json.JSONObject("""{"choices":[{"delta":{"reasoning":"thinking"}}]}"""),
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
            OpenAiAdapter.parseStreamEvent(org.json.JSONObject(it), acc, { answer.append(it) }, { reasoning.append(it) })
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
            org.json.JSONObject("""{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"hmm"}}"""),
            acc,
            { answer.append(it) },
            { reasoning.append(it) }
        )
        AnthropicAdapter.parseStreamEvent(
            org.json.JSONObject("""{"type":"content_block_delta","delta":{"type":"text_delta","text":"Sure"}}"""),
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
            org.json.JSONObject("""{"candidates":[{"content":{"parts":[{"text":"planning","thought":true},{"text":"Result"}]}}]}"""),
            acc,
            { answer.append(it) },
            { reasoning.append(it) }
        )
        assertEquals("planning", reasoning.toString())
        assertEquals("Result", answer.toString())
        assertEquals("Result", acc.fullText.toString())
    }
}
