package com.lucent.app.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppToolsTest {


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
        for (d in AppTools.definitions()) {
            assertTrue(d.description.isNotBlank(), "blank description on ${d.name}")
        }
    }


    @Test
    fun readOnlyToolsAreNotMutating() {
        for (name in listOf(
            "list_notes", "read_note", "list_tasks", "read_task", "search_items", "list_trash",
            "list_notebooks", "list_notebook_items", "search_notebook"
        )) {
            assertFalse(AppTools.isMutating(name), "$name should be read-only")
        }
    }

    @Test
    fun mutatingToolsRequireConfirmation() {
        for (name in listOf(
            "create_note", "update_note", "delete_note", "create_task", "complete_task", "pin_task",
            "create_notebook", "rename_notebook", "delete_notebook", "add_to_notebook",
            "remove_from_notebook", "move_to_notebook"
        )) {
            assertTrue(AppTools.isMutating(name), "$name should be mutating")
        }
    }


    @Test
    fun editableArgumentsOrderAndKeysForCreateTask() {
        val args = """{"title": "Call dentist", "due": "2026-02-01", "priority": "high"}"""
        val edits = AppTools.editableArguments("create_task", args)
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
        assertEquals("title", first.key)
    }

    @Test
    fun withArgumentsAppliesEditsAndSkipsBlanks() {
        val out = AppTools.withArguments(
            """{"title": "A", "due": "2026-02-01"}""",
            mapOf("title" to "B", "due" to "", "notes" to "added")
        )
        val parsed = org.json.JSONObject(out)
        assertEquals("B", parsed.getString("title"))
        assertEquals("2026-02-01", parsed.getString("due"))
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
        assertEquals("{broken", AppTools.withArgument("{broken", "title", "B"))
    }
}
