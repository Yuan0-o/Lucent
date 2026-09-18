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
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentThemeMode
import com.lucent.app.ui.PaletteSwatch
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.rememberDynamicColorActive
import kotlinx.coroutines.launch

/**
 * P1-3 — extracted from the `ThemePage` local composable that used to live inside `SettingsScreen`.
 * The two originals differed only in whether dynamic colour could be active at all: Android showed
 * a "paused" banner and hid the list while it was, desktop never could and always showed the list
 * unconditionally. [rememberDynamicColorActive] collapses that into one boolean — `dynamicColorOn &&
 * dynamicColorSupported` on Android, always `false` on desktop — so the two `if` blocks below now
 * read the same way on both platforms and reproduce each one's original behaviour exactly.
 */
@Composable
internal fun ThemeSettingsPage(repo: SettingsRepository, onRoute: (SettingsRoute) -> Unit) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val savedTheme by repo.themeMode.collectAsState(initial = "system")
    val dynamicColorActive = rememberDynamicColorActive(repo)

    BackHeader(S.settingsThemeTitle) { onRoute(SettingsRoute.Appearance) }

    if (dynamicColorActive) {
        // Material You has priority while it is on: the list below still edits the STORED
        // choice (so turning dynamic off restores exactly this), but nothing here changes
        // what is on screen until then. Say so instead of pretending the list is live.
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(S.dynamicColorPausedTheme, color = onGradientMuted, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
    // v2.7.2: while Material You is on, the tint list is hidden rather than merely paused —
    // dynamic colour outranks it in the read path, so rows that edit a choice the screen is
    // not using would only confuse. The banner above explains why the list is gone, and the
    // rows return the moment the wallpaper mode is switched off. The list is the picker
    // subset (18 of the 32 tints, see LucentThemeMode.pickerEntries); System/Light/Dark
    // and the tints are peers, each row previews the actual backdrop colour it selects, and
    // a tint that is no longer offered still resolves for anyone whose stored choice names it.
    // (On desktop, dynamicColorActive is always false, so this is the only appearance picker
    // desktop has and it always renders — exactly as it did before this page was shared.)
    if (!dynamicColorActive) {
        val systemDark = isSystemInDarkTheme()
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            LucentThemeMode.pickerEntries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { AppScope.io.launch { repo.setThemeMode(mode.key) } }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = savedTheme == mode.key,
                        onClick = { AppScope.io.launch { repo.setThemeMode(mode.key) } }
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
