package com.lucent.app

import com.lucent.app.ui.HomeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeTabTest {

    @Test
    fun bottomBarReadsHomeNotebooksAssistantSettings() {
        assertEquals(
            listOf(HomeTab.Home, HomeTab.Notebooks, HomeTab.Assistant, HomeTab.Settings),
            HomeTab.entries.toList()
        )
    }

    @Test
    fun tasksAndNotesBothLiveUnderHome() {
        assertEquals(HomeTab.Home, HomeTab.of(Screen.Tasks))
        assertEquals(HomeTab.Home, HomeTab.of(Screen.Notes))
        assertEquals(HomeTab.Notebooks, HomeTab.of(Screen.Notebooks))
        assertEquals(HomeTab.Assistant, HomeTab.of(Screen.Assistant))
        assertEquals(HomeTab.Settings, HomeTab.of(Screen.Settings))
    }

    @Test
    fun homeOpensOnTheLastModeUsed() {
        assertEquals(Screen.Tasks, HomeTab.Home.screen(HomeMode.Tasks))
        assertEquals(Screen.Notes, HomeTab.Home.screen(HomeMode.Notes))
        HomeMode.entries.forEach { mode ->
            assertEquals(Screen.Notebooks, HomeTab.Notebooks.screen(mode))
            assertEquals(Screen.Assistant, HomeTab.Assistant.screen(mode))
            assertEquals(Screen.Settings, HomeTab.Settings.screen(mode))
        }
    }

    @Test
    fun everyScreenRoundTripsThroughItsTab() {
        Screen.entries.forEach { screen ->
            val mode = HomeMode.of(screen) ?: HomeMode.Tasks
            assertEquals(screen, HomeTab.of(screen).screen(mode))
        }
    }

    @Test
    fun everyTabAndScreenHasALabel() {
        HomeTab.entries.forEach { assertTrue(it.label.isNotBlank()) }
        Screen.entries.forEach { assertTrue(it.label.isNotBlank()) }
    }
}
