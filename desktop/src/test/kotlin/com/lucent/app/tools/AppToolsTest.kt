package com.lucent.app.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Characterisation tests for the assistant's in-app tool catalogue (P0-5 "tool parser"): the
 * definitions a model is allowed to call, the read-only set that skips confirmation, and the
 * argument-editing helpers that rewrite a proposed call before it runs. Assertions stick to
 * structure (names, keys, order, flags, JSON semantics) rather than localised labels, so the tests
 * stay valid in every locale CI runs in.
 */
class AppToolsTest {

    // ---- Tool catalogue ----

    @Test
    fun definitionsExcludeWebSearchByDefault() {
        val names = AppTools.definitions(includeWebSearch = false).map { it.name }.toSet()
        assertFalse("web_search" in names)
        assertTrue("create_note" in names)
        assertTrue("create_task" in names)
    }

    @Test
    fun definitionsAppendWebSearchWhenEnabled() {
        val off = AppTools.definitions(includeWebSearch = false).map { it.name }.toSet()
        val on = AppTools.definitions(includeWebSearch = true).map { it.name }.toSet()
        assertTrue("web_search" in on)
        assertEquals(off.size + 1, on.size)
    }

    @Test
    fun definitionsCarryRequiredParams() {
        val createTask = AppTools.definitions().first { it.name == "create_task" }
        val title = createTask.params.first { it.name == "title" }
        assertTrue(title.required)
        val deleteNote = AppTools.definitions().first { it.name == "delete_note" }
        assertTrue(deleteNote.params.any { it.name == "title" })
        // Every definition must have a non-blank description for the model.
        for (d in AppTools.definitions()) {
            assertTrue(d.description.isNotBlank(), "blank description on ${d.name}")
        }
    }

    // ---- Read-only / mutating split ----

    @Test
    fun readOnlyToolsAreNotMutating() {
        for (name in listOf("list_notes", "read_note", "list_tasks", "read_task", "search_items", "list_trash")) {
            assertFalse(AppTools.isMutating(name), "$name should be read-only")
        }
    }

    @Test
    fun mutatingToolsRequireConfirmation() {
        for (name in listOf("create_note", "update_note", "delete_note", "create_task", "complete_task", "pin_task")) {
            assertTrue(AppTools.isMutating(name), "$name should be mutating")
        }
    }

    // ---- Argument editing (what the confirm dialog can rewrite) ----

    @Test
    fun editableArgumentsOrderAndKeysForCreateTask() {
        val args = """{"title": "Call dentist", "due": "2026-02-01", "priority": "high"}"""
        val edits = AppTools.editableArguments("create_task", args)
        // title, notes, due, subtasks is the display order; only provided keys appear.
        assertEquals(listOf("title", "due"), edits.map { it.key })
        assertFalse(edits.any { it.multiline })
        assertEquals("Call dentist", edits[0].value)
    }

    @Test
    fun editableArgumentsMarkBodyFieldsMultiline() {
        val args = """{"title": "Note", "body": "line1\nline2"}"""
        val edits = AppTools.editableArguments("create_note", args)
        assertEquals(listOf("title", "body"), edits.map { it.key })
        assertTrue(edits.first { it.key == "body" }.multiline)
    }

    @Test
    fun updateUsesNewTitleNotLookupTitle() {
        // The lookup title identifies the item and must not be a field; the NEW title is.
        val args = """{"title": "Old note", "new_title": "Fresh title", "body": "new body"}"""
        val edits = AppTools.editableArguments("update_note", args)
        assertEquals(listOf("new_title", "body"), edits.map { it.key })
        assertNull(AppTools.editableArgument("delete_note", """{"title": "Old note"}"""))
    }

    @Test
    fun editableArgumentReturnsFirstOnly() {
        val args = """{"title": "A", "due": "2026-02-01"}"""
        val first = AppTools.editableArgument("create_task", args)
        assertNotNull(first)
        assertEquals("title", first!!.key)
    }

    @Test
    fun withArgumentsAppliesEditsAndSkipsBlanks() {
        val out = AppTools.withArguments(
            """{"title": "A", "due": "2026-02-01"}""",
            mapOf("title" to "B", "due" to "", "notes" to "added")
        )
        val parsed = org.json.JSONObject(out)
        assertEquals("B", parsed.getString("title"))
        assertEquals("2026-02-01", parsed.getString("due")) // blank edit skipped
        assertEquals("added", parsed.getString("notes"))
    }

    @Test
    fun withArgumentsFallsBackOnMalformedJson() {
        val junk = "{not json"
        assertEquals(junk, AppTools.withArguments(junk, mapOf("title" to "B")))
    }

    @Test
    fun withArgumentSetsSingleKey() {
        val out = AppTools.withArgument("""{"title": "A"}""", "title", "B")
        assertEquals("B", org.json.JSONObject(out).getString("title"))
        // A call we cannot read is a call we must not silently rewrite.
        assertEquals("{broken", AppTools.withArgument("{broken", "title", "B"))
    }
}
