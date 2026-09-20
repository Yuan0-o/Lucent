package com.lucent.app.network

import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals("sys", body.getString("system"))
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
