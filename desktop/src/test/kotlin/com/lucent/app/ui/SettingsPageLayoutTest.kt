package com.lucent.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.lucent.app.ui.settings.AgentSettingsPage
import com.lucent.app.ui.settings.AuditSettingsPage
import com.lucent.app.ui.settings.McpSettingsPage
import com.lucent.app.ui.settings.PluginSettingsPage
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SettingsPageLayoutTest {

    private fun renderInsideTheScrollingHost(page: @Composable () -> Unit) = runComposeUiTest {
        setContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                page()
            }
        }
        waitForIdle()
    }

    @Test
    fun theToolkitPageLaysOutInsideTheScrollingSettingsHost() {
        renderInsideTheScrollingHost { AgentSettingsPage(onRoute = {}) }
    }

    @Test
    fun thePluginPageLaysOutInsideTheScrollingSettingsHost() {
        renderInsideTheScrollingHost { PluginSettingsPage(onRoute = {}) }
    }

    @Test
    fun theConnectorPageLaysOutInsideTheScrollingSettingsHost() {
        renderInsideTheScrollingHost { McpSettingsPage(onRoute = {}) }
    }

    @Test
    fun theAuditPageLaysOutInsideTheScrollingSettingsHost() {
        renderInsideTheScrollingHost { AuditSettingsPage(onRoute = {}) }
    }
}
