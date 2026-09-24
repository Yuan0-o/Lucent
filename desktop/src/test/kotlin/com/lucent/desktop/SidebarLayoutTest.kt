package com.lucent.desktop

import com.lucent.app.Screen
import com.lucent.app.ui.HomePanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SidebarLayoutTest {

    @Test
    fun libraryPagesSitBetweenTheListsAndTheTools() {
        val sections = sidebarSections(hiddenVisible = false)
        assertEquals(3, sections.size)
        assertEquals(listOf(Screen.Tasks, Screen.Notes), sections[0])
        assertEquals(listOf(Screen.Notebooks, Screen.Drafts, Screen.Archive, Screen.Trash), sections[1])
        assertEquals(listOf(Screen.Assistant, Screen.Search, Screen.Insights, Screen.Settings), sections[2])
    }

    @Test
    fun hiddenOnlyAppearsOnceTheHiddenAreaIsOpen() {
        assertFalse(Screen.Hidden in sidebarSections(hiddenVisible = false).flatten())
        val open = sidebarSections(hiddenVisible = true)
        assertEquals(Screen.Hidden, open[1].last())
    }

    @Test
    fun everyScreenIsReachableExactlyOnce() {
        val all = sidebarSections(hiddenVisible = true).flatten()
        assertEquals(all.size, all.toSet().size)
        assertEquals(Screen.entries.toSet(), all.toSet())
    }

    @Test
    fun libraryScreensMapOntoPanels() {
        assertEquals(HomePanel.Drafts, Screen.Drafts.panel)
        assertEquals(HomePanel.Archive, Screen.Archive.panel)
        assertEquals(HomePanel.Trash, Screen.Trash.panel)
        assertEquals(HomePanel.Hidden, Screen.Hidden.panel)
        listOf(Screen.Tasks, Screen.Notes, Screen.Notebooks, Screen.Assistant, Screen.Search, Screen.Insights, Screen.Settings)
            .forEach { assertNull(it.panel) }
    }

    @Test
    fun everyScreenHasALabel() {
        Screen.entries.forEach { assertTrue(it.label.isNotBlank(), "blank label on ${it.name}") }
    }
}
