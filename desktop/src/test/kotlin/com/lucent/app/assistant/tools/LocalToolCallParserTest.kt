package com.lucent.app.assistant.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalToolCallParserTest {

    private val valid = setOf(
        "create_task", "list_tasks", "complete_task", "read_note",
        "create_note", "update_note", "attach_upload_to_note", "web_search"
    )

    @Test
    fun parsesBareJsonObject() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """{"tool": "create_task", "arguments": {"title": "Buy milk"}}""",
            valid
        )
        assertNotNull(call)
        assertEquals("create_task", call.name)
        assertTrue(call.argsJson.contains("Buy milk"))
    }

    @Test
    fun parsesCodeFencedJson() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """Here you go:
```json
{"tool": "complete_task", "arguments": {"id": 7}}
```""",
            valid
        )
        assertNotNull(call)
        assertEquals("complete_task", call.name)
    }

    @Test
    fun parsesToolCallTag() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """<tool_call>{"tool": "list_tasks", "arguments": {}}</tool_call>""",
            valid
        )
        assertNotNull(call)
        assertEquals("list_tasks", call.name)
    }

    @Test
    fun unwrapsNestedWrapperObject() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """{"function_call": {"name": "read_note", "arguments": {"title": "Trip"}}}""",
            valid
        )
        assertNotNull(call)
        assertEquals("read_note", call.name)
        assertTrue(call.argsJson.contains("Trip"))
    }

    @Test
    fun acceptsAlternateArgumentKeys() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """{"tool": "create_note", "params": {"title": "A note"}}""",
            valid
        )
        assertNotNull(call)
        assertEquals("create_note", call.name)
        assertTrue(call.argsJson.contains("A note"))
    }

    @Test
    fun parsesDoubleEncodedArguments() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """{"tool": "update_note", "arguments": "{\"title\": \"New\"}"}""",
            valid
        )
        assertNotNull(call)
        assertEquals("update_note", call.name)
        assertTrue(call.argsJson.contains("New"))
    }

    @Test
    fun mapsSynonymVerbsToRealTools() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """{"tool": "add_task", "arguments": {"title": "x"}}""",
            valid
        )
        assertNotNull(call)
        assertEquals("create_task", call.name)
    }

    @Test
    fun mapsSingularPluralNouns() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """{"tool": "list_task", "arguments": {}}""",
            valid
        )
        assertNotNull(call)
        assertEquals("list_tasks", call.name)
    }

    @Test
    fun rejectsUnknownTools() {
        assertNull(
            LocalToolCallParser.parseLocalToolCall(
                """{"tool": "delete_everything", "arguments": {}}""",
                valid
            )
        )
    }

    @Test
    fun plainProseIsNotACall() {
        assertNull(LocalToolCallParser.parseLocalToolCall("Just a friendly chat reply.", valid))
        assertNull(LocalToolCallParser.parseLocalToolCall("", valid))
        assertNull(LocalToolCallParser.parseLocalToolCall("   ", valid))
        assertNull(
            LocalToolCallParser.parseLocalToolCall("""Try {"tool": "nonsense"} later.""", valid)
        )
    }

    @Test
    fun attemptedCallNamesShapeOnly() {
        assertEquals("web_search", LocalToolCallParser.attemptedToolCallName("""{"tool": "web_search"}"""))
        assertEquals("web_search", LocalToolCallParser.attemptedToolCallName("""{"tool": "web_search", "arguments": {}}"""))
        assertEquals("(unnamed)", LocalToolCallParser.attemptedToolCallName("""{"arguments": {"q": "x"}}"""))
        assertNull(LocalToolCallParser.attemptedToolCallName(""))
        assertNull(LocalToolCallParser.attemptedToolCallName("I would love to help with that."))
    }

    @Test
    fun renderRoundTripsAValidCall() {
        val call = LocalToolCallParser.LocalToolCall(
            "create_task",
            """{"title":"Buy milk","priority":"high"}"""
        )
        val rendered = LocalToolCallParser.renderLocalToolCall(call)
        val reparsed = LocalToolCallParser.parseLocalToolCall(rendered, valid)
        assertNotNull(reparsed)
        assertEquals("create_task", reparsed.name)
        assertTrue(reparsed.argsJson.contains("Buy milk"))
        assertTrue(reparsed.argsJson.contains("high"))
    }

    @Test
    fun stripsBlankArguments() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """{"tool": "create_task", "arguments": {"title": "Real", "due": "", "priority": null, "repeat": "  "}}""",
            valid
        )
        assertNotNull(call)
        assertTrue(call.argsJson.contains("Real"))
        assertTrue(!call.argsJson.contains("due"))
        assertTrue(!call.argsJson.contains("priority"))
        assertTrue(!call.argsJson.contains("repeat"))
    }

    @Test
    fun normalisesWhitespaceAndDashesInToolName() {
        val spaced = LocalToolCallParser.parseLocalToolCall(
            """{"tool": "create task", "arguments": {"title": "x"}}""",
            valid
        )
        assertNotNull(spaced)
        assertEquals("create_task", spaced.name)

        val dashed = LocalToolCallParser.parseLocalToolCall(
            """{"tool": "create-task", "arguments": {"title": "x"}}""",
            valid
        )
        assertNotNull(dashed)
        assertEquals("create_task", dashed.name)
    }

    @Test
    fun renderSurvivesMalformedArgs() {
        val out = LocalToolCallParser.renderLocalToolCall(
            LocalToolCallParser.LocalToolCall("list_tasks", "not json")
        )
        val parsed = org.json.JSONObject(out)
        assertEquals("list_tasks", parsed.getString("tool"))
        assertEquals(0, parsed.getJSONObject("arguments").length())
    }

    @Test
    fun rendersCompactCanonicalJson() {
        val out = LocalToolCallParser.renderLocalToolCall(
            LocalToolCallParser.LocalToolCall("create_task", """{"title":"Buy milk","due":"2026-01-01"}""")
        )
        val parsed = org.json.JSONObject(out)
        assertEquals("create_task", parsed.getString("tool"))
        val args = parsed.getJSONObject("arguments")
        assertEquals("Buy milk", args.getString("title"))
        assertEquals("2026-01-01", args.getString("due"))
    }

    @Test
    fun parsesLlamaPythonTagShape() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """<|python_tag|>{"name": "create_task", "parameters": {"title": "Buy milk"}}<|eom_id|>""",
            valid
        )
        assertNotNull(call)
        assertEquals("create_task", call.name)
        assertTrue(call.argsJson.contains("Buy milk"))
    }

    @Test
    fun parsesMistralToolCallsShape() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """[TOOL_CALLS] [{"name": "complete_task", "arguments": {"title": "Buy milk"}}]""",
            valid
        )
        assertNotNull(call)
        assertEquals("complete_task", call.name)
        assertTrue(call.argsJson.contains("Buy milk"))
    }

    @Test
    fun parsesHermesToolCallTagWithNewlines() {
        val call = LocalToolCallParser.parseLocalToolCall(
            "<tool_call>\n{\"name\": \"list_tasks\", \"arguments\": {}}\n</tool_call>",
            valid
        )
        assertNotNull(call)
        assertEquals("list_tasks", call.name)
    }

    @Test
    fun toleratesTrailingChatterAndNewlines() {
        val call = LocalToolCallParser.parseLocalToolCall(
            "{\"tool\": \"create_note\", \"arguments\": {\"title\": \"Trip\"}}\n\nHope that helps!\n",
            valid
        )
        assertNotNull(call)
        assertEquals("create_note", call.name)
        assertTrue(call.argsJson.contains("Trip"))
    }

    @Test
    fun toleratesAnUnclosedToolCallTag() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """<tool_call>{"tool": "read_note", "arguments": {"title": "Trip"}}""",
            valid
        )
        assertNotNull(call)
        assertEquals("read_note", call.name)
        assertTrue(call.argsJson.contains("Trip"))
    }

    @Test
    fun aCallSplitAcrossStreamedChunksOnlyParsesOnceTheWholeCallHasArrived() {
        val first = """<tool_call>{"name": "create_task", "argu"""
        val second = """ments": {"title": "Buy milk"}}</tool_call>"""
        assertNull(LocalToolCallParser.parseLocalToolCall(first, valid))
        val call = LocalToolCallParser.parseLocalToolCall(first + second, valid)
        assertNotNull(call)
        assertEquals("create_task", call.name)
        assertTrue(call.argsJson.contains("Buy milk"))
    }

    @Test
    fun localPromptCarriesExactlyTheProtocolTheParserAccepts() {
        val tools = com.lucent.app.tools.AppTools.definitions(includeWebSearch = false)
        val prompt = com.lucent.app.assistant.prompts.SystemPrompts.local(tools, userText = "add a task", compact = false)

        assertTrue(prompt.contains("""{"tool": "<tool_name>", "arguments": { ... }}"""))
        assertTrue(prompt.contains("EXACTLY ONE JSON object"))
        assertTrue(prompt.contains("Result of <tool>:"))

        val createTask = tools.first { it.name == "create_task" }
        assertTrue(prompt.contains("- create_task("))
        for (param in createTask.params) {
            assertTrue(prompt.contains(param.name), "the local prompt must name create_task's ${param.name} argument")
        }

        val shape = prompt.substringAfter("in this exact form:\n").substringBefore("\n")
        val parsed = LocalToolCallParser.parseLocalToolCall(
            "{\"tool\": \"create_task\", \"arguments\": {\"title\": \"Buy milk\"}}",
            tools.map { it.name }.toSet()
        )
        assertNotNull(parsed)
        assertEquals("create_task", parsed.name)
        assertEquals("""{"tool": "<tool_name>", "arguments": { ... }}""", shape)
    }

    @Test
    fun localPromptPutsTheToolProtocolAfterTheCatalogue() {
        val tools = com.lucent.app.tools.AppTools.definitions(includeWebSearch = false)
        val prompt = com.lucent.app.assistant.prompts.SystemPrompts.local(tools, userText = "add a task", compact = false)

        val protocolAt = prompt.indexOf("EXACTLY ONE JSON object")
        assertTrue(protocolAt > 0)
        for (tool in tools) {
            assertTrue(prompt.lastIndexOf("- " + tool.name) < protocolAt, "${tool.name} must precede the protocol")
        }
        assertTrue(
            prompt.length - protocolAt < prompt.length / 4,
            "the protocol must sit near the end, where the engine's front truncation cannot reach it"
        )
    }

    @Test
    fun localPromptFitsTheEngineContextWithRoomForTheReply() {
        val tools = com.lucent.app.tools.AppTools.definitions(includeWebSearch = false)
        val prompt = com.lucent.app.assistant.prompts.SystemPrompts.local(tools, userText = "add a task", compact = false)
        val budget = com.lucent.app.local.LocalLlm.N_CTX - com.lucent.app.local.LocalLlm.MAX_NEW_TOKENS
        val used = com.lucent.app.data.TokenEstimator.estimate(prompt)
        assertTrue(
            used < budget - com.lucent.app.local.LocalLlm.MAX_NEW_TOKENS / 2,
            "the local tool prompt must leave room in the engine context: $used tokens used, $budget available"
        )
    }
}
