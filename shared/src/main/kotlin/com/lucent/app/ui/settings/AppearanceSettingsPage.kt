package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.DynamicColorRow
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.NavCard
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.rememberDynamicColorActive

private const val PAUSED_CARD_ALPHA = 0.38f

@Composable
internal fun AppearanceSettingsPage(repo: SettingsRepository, onRoute: (SettingsRoute) -> Unit) {
    val dynamicColorActive = rememberDynamicColorActive(repo)
    var pausedTarget by remember { mutableStateOf<SettingsRoute?>(null) }
    val paused = if (dynamicColorActive) pausedTarget else null
    val cardModifier = Modifier
        .fillMaxWidth()
        .then(if (dynamicColorActive) Modifier.alpha(PAUSED_CARD_ALPHA) else Modifier)

    BackHeader(S.settingsAppearanceTitle) { onRoute(SettingsRoute.Root) }

    DynamicColorRow(repo)
    Box(modifier = cardModifier) {
        NavCard(S.settingsThemeTitle, S.settingsThemeSub) {
            if (dynamicColorActive) pausedTarget = SettingsRoute.Theme else onRoute(SettingsRoute.Theme)
        }
    }
    if (paused == SettingsRoute.Theme) {
        Spacer(modifier = Modifier.height(12.dp))
        PausedNotice(S.dynamicColorPausedTheme)
    }
    Spacer(modifier = Modifier.height(12.dp))
    Box(modifier = cardModifier) {
        NavCard(S.settingsBackgroundTitle, S.settingsBackgroundSub) {
            if (dynamicColorActive) pausedTarget = SettingsRoute.Background else onRoute(SettingsRoute.Background)
        }
    }
    if (paused == SettingsRoute.Background) {
        Spacer(modifier = Modifier.height(12.dp))
        PausedNotice(S.dynamicColorPausedBackground)
    }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsSplashTitle, S.settingsSplashSub) { onRoute(SettingsRoute.Splash) }
}

@Composable
private fun PausedNotice(message: String) {
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(message, color = LocalOnGradientMuted.current, fontSize = 13.sp)
    }
}
