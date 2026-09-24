package com.lucent.app.ui

import com.lucent.app.AppNavigation
import com.lucent.app.Screen
import com.lucent.app.i18n.L
import com.lucent.app.i18n.S
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeNavigationTest {

    @BeforeTest
    fun setUp() {
        L.apply("en")
        drain()
        HomeSearch.clear()
        LastScreen.current = Screen.Tasks
        LastScreen.home = Screen.Tasks
        LastScreen.homeMode = HomeMode.Tasks
    }

    @AfterTest
    fun tearDown() {
        drain()
        HomeSearch.clear()
    }

    private fun drain() {
        AppNavigation.consumeScreen()
        AppNavigation.consumePanel()
        AppNavigation.consumeEditNoteId()
        AppNavigation.consumeEditTaskId()
        AppNavigation.consumeNoteId()
        AppNavigation.consumeTaskId()
        AppNavigation.consumeReturnScreen()
        AppNavigation.consumeComposeNote()
        AppNavigation.consumeComposeTask()
    }

    @Test
    fun homeModesMapToTheirScreensAndBack() {
        assertEquals(listOf(HomeMode.Tasks, HomeMode.Notes), HomeMode.entries.toList())
        HomeMode.entries.forEach { mode ->
            assertEquals(mode, HomeMode.of(mode.screen))
        }
        assertNull(HomeMode.of(Screen.Settings))
        assertNull(HomeMode.of(Screen.Assistant))
        assertNull(HomeMode.of(null))
    }

    @Test
    fun otherFlipsBetweenTheTwoModes() {
        assertEquals(HomeMode.Notes, HomeMode.Tasks.other())
        assertEquals(HomeMode.Tasks, HomeMode.Notes.other())
    }

    @Test
    fun modeLabelsFollowTheTabStrings() {
        assertEquals(S.tabTasks, HomeMode.Tasks.label)
        assertEquals(S.tabNotes, HomeMode.Notes.label)
    }

    @Test
    fun panelsKeepTheDraftsArchiveTrashOrder() {
        assertEquals(
            listOf(HomePanel.Drafts, HomePanel.Archive, HomePanel.Trash),
            HomePanel.visible(hiddenVisible = false)
        )
        assertEquals(
            listOf(HomePanel.Drafts, HomePanel.Archive, HomePanel.Trash, HomePanel.Hidden),
            HomePanel.visible(hiddenVisible = true)
        )
    }

    @Test
    fun archiveIsNamedForWhatItHoldsInEachMode() {
        assertEquals(S.screenArchivedNotes, HomePanel.Archive.label(HomeMode.Notes))
        assertEquals(S.screenCompletedTasks, HomePanel.Archive.label(HomeMode.Tasks))
        assertEquals(S.navArchive, HomePanel.Archive.title)
        assertEquals(HomePanel.Trash.title, HomePanel.Trash.label(HomeMode.Tasks))
        assertEquals(HomePanel.Drafts.label(HomeMode.Notes), HomePanel.Drafts.label(HomeMode.Tasks))
    }

    @Test
    fun panelLogKeysAreDistinctAndLowercase() {
        val keys = HomePanel.entries.map { it.logKey }
        assertEquals(keys.size, keys.toSet().size)
        keys.forEach { assertEquals(it.lowercase(), it) }
    }

    @Test
    fun panelRequestIsConsumedExactlyOnce() {
        AppNavigation.requestPanel(HomePanel.Trash)
        assertEquals(HomePanel.Trash, AppNavigation.requestedPanel)
        assertEquals(HomePanel.Trash, AppNavigation.consumePanel())
        assertNull(AppNavigation.consumePanel())
    }

    @Test
    fun editNoteRoutesToNotesWithThePendingId() {
        AppNavigation.editNote(42L)
        assertEquals(Screen.Notes, AppNavigation.consumeScreen())
        assertEquals(42L, AppNavigation.consumeEditNoteId())
        assertNull(AppNavigation.consumeEditNoteId())
        assertNull(AppNavigation.consumeEditTaskId())
    }

    @Test
    fun editTaskRoutesToTasksWithThePendingId() {
        AppNavigation.editTask(7L)
        assertEquals(Screen.Tasks, AppNavigation.consumeScreen())
        assertEquals(7L, AppNavigation.consumeEditTaskId())
        assertNull(AppNavigation.consumeEditNoteId())
    }

    @Test
    fun openNoteRemembersWhereToReturn() {
        AppNavigation.openNote(5L, from = Screen.Tasks)
        assertEquals(Screen.Notes, AppNavigation.consumeScreen())
        assertEquals(5L, AppNavigation.consumeNoteId())
        assertEquals(Screen.Tasks, AppNavigation.consumeReturnScreen())
        assertNull(AppNavigation.consumeReturnScreen())
    }

    @Test
    fun composeRequestsLandOnTheirModes() {
        AppNavigation.requestComposeTask()
        assertEquals(Screen.Tasks, AppNavigation.consumeScreen())
        assertTrue(AppNavigation.consumeComposeTask())
        assertFalse(AppNavigation.consumeComposeTask())
        AppNavigation.requestComposeNote()
        assertEquals(Screen.Notes, AppNavigation.consumeScreen())
        assertTrue(AppNavigation.consumeComposeNote())
    }

    @Test
    fun lastScreenTracksTheHomeModeSeparately() {
        LastScreen.remember(Screen.Notes)
        assertEquals(HomeMode.Notes, LastScreen.homeMode)
        LastScreen.remember(Screen.Assistant)
        assertEquals(HomeMode.Notes, LastScreen.homeMode)
        assertEquals(Screen.Assistant, LastScreen.home)
        LastScreen.remember(Screen.Settings)
        assertEquals(Screen.Assistant, LastScreen.home)
        assertEquals(Screen.Settings, LastScreen.current)
        LastScreen.remember(Screen.Tasks)
        assertEquals(HomeMode.Tasks, LastScreen.homeMode)
    }

    @Test
    fun hydrateRestoresTheModeAndIgnoresUnknownNames() {
        LastScreen.hydrate("Notes")
        assertEquals(Screen.Notes, LastScreen.current)
        assertEquals(HomeMode.Notes, LastScreen.homeMode)
        LastScreen.hydrate("NoSuchScreen")
        assertEquals(Screen.Tasks, LastScreen.current)
        assertEquals(HomeMode.Tasks, LastScreen.homeMode)
        LastScreen.hydrate("")
        assertEquals(Screen.Tasks, LastScreen.current)
        assertEquals(Screen.Tasks.name, LastScreen.persistedName())
    }

    @Test
    fun searchQueryIsSharedAndClearable() {
        HomeSearch.query.value = "tag:work"
        assertEquals("tag:work", HomeSearch.query.value)
        HomeSearch.clear()
        assertEquals("", HomeSearch.query.value)
    }

    @Test
    fun newStringsExistInEveryLanguage() {
        listOf("en", "zh", "ja", "ko").forEach { lang ->
            L.apply(lang)
            assertTrue(S.tabHome.isNotBlank())
            assertTrue(S.navArchive.isNotBlank())
            assertTrue(S.drawerOpen.isNotBlank())
            assertTrue(S.drawerSectionLibrary.isNotBlank())
            assertTrue(S.drawerFooter("9.9.9").contains("9.9.9"))
            assertTrue(S.notebooksTotal(3).contains("3"))
        }
        L.apply("en")
        assertEquals("1 notebook", S.notebooksTotal(1))
        assertEquals("2 notebooks", S.notebooksTotal(2))
        L.apply("zh")
        assertNotEquals(S.tabHome, "Home")
        L.apply("en")
    }
}
