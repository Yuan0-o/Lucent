package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SavedSearchesTest {

    @Test
    fun addReplacesByNameAndCaps() {
        var json: String? = null
        repeat(SavedSearches.MAX + 5) { json = SavedSearches.add(json, "q$it", "tag:$it") }
        assertEquals(SavedSearches.MAX, SavedSearches.parse(json).size)
        json = SavedSearches.add(json, "q3", "priority:high")
        val e = SavedSearches.parse(json).first { it.name == "q3" }
        assertEquals("priority:high", e.query)
        assertEquals(1, SavedSearches.parse(json).count { it.name == "q3" })
    }

    @Test
    fun removeByName() {
        val json = SavedSearches.add(SavedSearches.add(null, "a", "x"), "b", "y")
        val after = SavedSearches.parse(SavedSearches.remove(json, "a"))
        assertEquals(listOf("b"), after.map { it.name })
    }

    @Test
    fun corruptIsTolerated() {
        assertTrue(SavedSearches.parse("not json").isEmpty())
        assertTrue(SavedSearches.parse("").isEmpty())
    }
}
