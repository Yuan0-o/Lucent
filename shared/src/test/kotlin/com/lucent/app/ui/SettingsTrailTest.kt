package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTrailTest {

    @Test
    fun theToolkitAndShizukuHangOffAdvanced() {
        assertEquals(SettingsRoute.Advanced, SettingsTrail.parent(SettingsRoute.Shizuku))
        assertEquals(SettingsRoute.Advanced, SettingsTrail.parent(SettingsRoute.Agent))
    }

    @Test
    fun everyFineSettingIsAToolkitSubPage() {
        val pages = listOf(
            SettingsRoute.Workspace,
            SettingsRoute.Permissions,
            SettingsRoute.Groups,
            SettingsRoute.Execution,
            SettingsRoute.Github,
            SettingsRoute.Plugins,
            SettingsRoute.Mcp,
            SettingsRoute.Audit
        )
        pages.forEach { page ->
            assertEquals(SettingsRoute.Agent, SettingsTrail.parent(page), page.name)
        }
    }

    @Test
    fun theToolkitSitsBetweenTheRootAndItsSubPages() {
        assertEquals(
            listOf(SettingsRoute.Root, SettingsRoute.Advanced, SettingsRoute.Agent, SettingsRoute.Plugins),
            SettingsTrail.trail(SettingsRoute.Plugins)
        )
        assertEquals(
            listOf(SettingsRoute.Root, SettingsRoute.Advanced, SettingsRoute.Shizuku),
            SettingsTrail.trail(SettingsRoute.Shizuku)
        )
    }

    @Test
    fun everySubPageHasATitle() {
        SettingsRoute.entries.forEach { route ->
            assertEquals(true, SettingsTrail.title(route).isNotBlank(), route.name)
        }
    }
}
