package com.lucent.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentThemeMode
import com.lucent.app.ui.LucentThemeSection
import com.lucent.app.ui.PaletteSwatch
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.rememberDynamicColorActive
import kotlinx.coroutines.launch

@Composable
internal fun ThemeSettingsPage(repo: SettingsRepository, onRoute: (SettingsRoute) -> Unit) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val savedTheme by repo.themeMode.collectAsState(initial = SettingsCache.themeMode)
    val dynamicColorActive = rememberDynamicColorActive(repo)

    BackHeader(S.settingsThemeTitle) { onRoute(SettingsRoute.Appearance) }


    if (dynamicColorActive) {
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(S.dynamicColorPausedTheme, color = onGradientMuted, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
    if (!dynamicColorActive) {
        val systemDark = isSystemInDarkTheme()
        val offered = LucentThemeMode.pickerEntries
        LucentThemeSection.entries.forEachIndexed { index, section ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
                Text(section.label, color = onGradient, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                offered.filter { it.section == section }.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                SettingsCache.themeMode = mode.key
                                AppScope.io.launch { repo.setThemeMode(mode.key) }
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = savedTheme == mode.key,
                            onClick = {
                                SettingsCache.themeMode = mode.key
                                AppScope.io.launch { repo.setThemeMode(mode.key) }
                            }
                        )
                        PaletteSwatch(mode.swatch(systemDark))
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(mode.label, color = onGradient)
                            Text(mode.detail, color = onGradientMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
