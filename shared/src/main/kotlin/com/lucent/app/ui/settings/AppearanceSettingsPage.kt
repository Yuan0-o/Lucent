package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.DynamicColorRow
import com.lucent.app.ui.NavCard
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.rememberDynamicColorActive

@Composable
internal fun AppearanceSettingsPage(repo: SettingsRepository, onRoute: (SettingsRoute) -> Unit) {
    val dynamicColorActive = rememberDynamicColorActive(repo)

    BackHeader(onBack = { onRoute(SettingsRoute.Root) })

    DynamicColorRow(repo)

    if (!dynamicColorActive) {
        NavCard(S.settingsThemeTitle, S.settingsThemeSub) { onRoute(SettingsRoute.Theme) }
        Spacer(modifier = Modifier.height(12.dp))
        NavCard(S.settingsBackgroundTitle, S.settingsBackgroundSub) { onRoute(SettingsRoute.Background) }
        Spacer(modifier = Modifier.height(12.dp))
    }
    NavCard(S.settingsSplashTitle, S.settingsSplashSub) { onRoute(SettingsRoute.Splash) }
}
