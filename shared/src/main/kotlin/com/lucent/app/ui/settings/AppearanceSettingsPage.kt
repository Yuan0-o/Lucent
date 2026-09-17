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

/**
 * P1-3 — extracted from the `AppearancePage` local composable that used to live inside
 * `SettingsScreen`. Diffing the two originals turned up exactly one difference: the Material You
 * dynamic-colour card, which existed only on Android. That block is now the [DynamicColorRow] seam
 * (Android: the toggle card and its own trailing gap; Windows: nothing) — everything else here,
 * the header and the two navigation cards, was already byte-identical.
 */
@Composable
fun AppearanceSettingsPage(repo: SettingsRepository, onRoute: (SettingsRoute) -> Unit) {
    BackHeader(S.settingsAppearanceTitle) { onRoute(SettingsRoute.Root) }

    // Material You dynamic colour (task 2): pinned, highest priority — sits ABOVE the Theme and
    // Background cards on purpose, since while it is on the wallpaper palette outranks both of
    // them (override in the read path; the stored theme and palette are never rewritten). See
    // [DynamicColorRow] for why the row itself only exists on Android.
    DynamicColorRow(repo)

    // Two hierarchical entries, mirroring the Assistant screen's structure. (Font moved
    // to the Language screen — it's as much a writing choice as a visual one.)
    NavCard(S.settingsThemeTitle, S.settingsThemeSub) { onRoute(SettingsRoute.Theme) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsBackgroundTitle, S.settingsBackgroundSub) { onRoute(SettingsRoute.Background) }
}
