package com.lucent.app.assistant.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Characterisation tests for [LocalToolCallParser], extracted verbatim from AssistantController
 * (v2.7.6). These lock in the tolerant parser behaviour small GGUF models depend on, so a future
 * refactor can move the code without silently changing what counts as a tool call.
 */
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
        assertEquals("create_task", call!!.name)
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
        assertEquals("complete_task", call!!.name)
    }

    @Test
    fun parsesToolCallTag() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """<tool_call>{"tool": "list_tasks", "arguments": {}}</tool_call>""",
            valid
        )
        assertNotNull(call)
        assertEquals("list_tasks", call!!.name)
    }

    @Test
    fun unwrapsNestedWrapperObject() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """{"function_call": {"name": "read_note", "arguments": {"title": "Trip"}}}""",
            valid
        )
        assertNotNull(call)
        assertEquals("read_note", call!!.name)
        assertTrue(call.argsJson.contains("Trip"))
    }

    @Test
    fun acceptsAlternateArgumentKeys() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """{"tool": "create_note", "params": {"title": "A note"}}""",
            valid
        )
        assertNotNull(call)
        assertEquals("create_note", call!!.name)
        assertTrue(call.argsJson.contains("A note"))
    }

    @Test
    fun parsesDoubleEncodedArguments() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """{"tool": "update_note", "arguments": "{\"title\": \"New\"}"}""",
            valid
        )
        assertNotNull(call)
        assertEquals("update_note", call!!.name)
        assertTrue(call.argsJson.contains("New"))
    }

    @Test
    fun mapsSynonymVerbsToRealTools() {
        // The reported bug: Qwen2.5-0.5B emitting "add_task" for create_task.
        val call = LocalToolCallParser.parseLocalToolCall(
            """{"tool": "add_task", "arguments": {"title": "x"}}""",
            valid
        )
        assertNotNull(call)
        assertEquals("create_task", call!!.name)
    }

    @Test
    fun mapsSingularPluralNouns() {
        val call = LocalToolCallParser.parseLocalToolCall(
            """{"tool": "list_task", "arguments": {}}""",
            valid
        )
        assertNotNull(call)
        assertEquals("list_tasks", call!!.name)
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
        // JSON-shaped prose that names no valid tool still falls through to null.
        assertNull(
            LocalToolCallParser.parseLocalToolCall("Try {"tool": "nonsense"} later.", valid)
        )
    }

    @Test
    fun attemptedCallNamesShapeOnly() {
        // A snake_case name plus no args still counts as "tried to call".
        assertEquals("web_search", LocalToolCallParser.attemptedToolCallName("""{"tool": "web_search"}"""))
        assertEquals("web_search", LocalToolCallParser.attemptedToolCallName("""{"tool": "web_search", "arguments": {}}"""))
        assertEquals("(unnamed)", LocalToolCallParser.attemptedToolCallName("""{"arguments": {"q": "x"}}"""))
        // Prose and empty output are not attempts.
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
        assertEquals("create_task", reparsed!!.name)
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
        assertTrue(call!!.argsJson.contains("Real"))
        assertTrue(!call.argsJson.contains("due"))
        assertTrue(!call.argsJson.contains("priority"))
        assertTrue(!call.argsJson.contains("repeat"))
    }

    @Test
    fun normalisesWhitespaceAndDashesInToolName() {
        // A weak model may write "create task" or "create-task" instead of "create_task".
        val spaced = LocalToolCallParser.parseLocalToolCall(
            """{"tool": "create task", "arguments": {"title": "x"}}""",
            valid
        )
        assertNotNull(spaced)
        assertEquals("create_task", spaced!!.name)

        val dashed = LocalToolCallParser.parseLocalToolCall(
            """{"tool": "create-task", "arguments": {"title": "x"}}""",
            valid
        )
        assertNotNull(dashed)
        assertEquals("create_task", dashed!!.name)
    }

    @Test
    fun renderSurvivesMalformedArgs() {
        // Malformed args JSON must not crash the serialiser; it falls back to an empty object.
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
}
