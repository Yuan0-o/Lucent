package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomTemplatesTest {

    private fun tpl(id: String, name: String = "T-$id") = CustomTemplates.Template(
        id = id, name = name, title = "Title $id", body = "Body $id",
        tags = listOf("a", "b"), colorKey = "note_red", pinned = true,
        isChecklist = true, checklistTexts = listOf("one", "two")
    )

    @Test
    fun roundTrip() {
        val json = CustomTemplates.serialize(listOf(tpl("1"), tpl("2")))
        val parsed = CustomTemplates.parse(json)
        assertEquals(2, parsed.size)
        assertEquals(tpl("1"), parsed[0])
        assertEquals(tpl("2"), parsed[1])
    }

    @Test
    fun upsertReplacesByIdAndCaps() {
        var json = "[]"
        repeat(CustomTemplates.MAX + 3) { json = CustomTemplates.upsert(json, tpl(it.toString())) }
        assertEquals(CustomTemplates.MAX, CustomTemplates.parse(json).size)
        // replacement keeps position semantics: same id replaces
        val before = CustomTemplates.parse(json)
        val victim = before[0]
        json = CustomTemplates.upsert(json, victim.copy(name = "renamed"))
        val after = CustomTemplates.parse(json)
        assertEquals(CustomTemplates.MAX, after.size)
        assertEquals("renamed", after.first { it.id == victim.id }.name)
    }

    @Test
    fun removeById() {
        val json = CustomTemplates.serialize(listOf(tpl("1"), tpl("2"), tpl("3")))
        val after = CustomTemplates.parse(CustomTemplates.remove(json, "2"))
        assertEquals(listOf("1", "3"), after.map { it.id })
    }

    @Test
    fun corruptJsonYieldsEmpty() {
        assertTrue(CustomTemplates.parse("{ not json").isEmpty())
        assertTrue(CustomTemplates.parse(null).isEmpty())
        assertTrue(CustomTemplates.parse("").isEmpty())
        // entries without an id are dropped rather than crashing
        assertTrue(CustomTemplates.parse("[{\"name\":\"x\"}]").isEmpty())
    }

    @Test
    fun draftRoundTripAndEmpty() {
        val d = CustomTemplates.Draft(
            title = "T", body = "B", tags = listOf("x"),
            colorKey = "note_blue", pinned = true, isChecklist = true,
            checklistTexts = listOf("i")
        )
        val parsed = CustomTemplates.parseDraft(CustomTemplates.draftToJson(d))
        assertEquals(d, parsed)
        assertTrue(CustomTemplates.draftEmpty(CustomTemplates.Draft()))
        assertTrue(CustomTemplates.draftEmpty(CustomTemplates.Draft(title = "")))
        assertTrue(!CustomTemplates.draftEmpty(CustomTemplates.Draft(title = "x")))
    }
}
