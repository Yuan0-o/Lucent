package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.NavCard
import com.lucent.app.ui.SettingsRoute

@Composable
internal fun AdvancedSettingsPage(onRoute: (SettingsRoute) -> Unit) {
    BackHeader(onBack = { onRoute(SettingsRoute.Root) })
    Column(modifier = Modifier.fillMaxWidth()) {
        NavCard(S.shizukuTitle, S.shizukuEnableDesc) { onRoute(SettingsRoute.Shizuku) }
        Spacer(modifier = Modifier.height(12.dp))
        NavCard(S.settingsAgentTitle, S.settingsAgentSub) { onRoute(SettingsRoute.Agent) }
    }
}
