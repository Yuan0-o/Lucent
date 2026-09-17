package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucent.app.i18n.S
import com.lucent.app.ui.NavCard
import com.lucent.app.ui.SettingsRoute

/**
 * P1-3 — extracted from the `RootPage` local composable that used to live inside `SettingsScreen`
 * (byte-identical on both platforms before this split, and confirmed so again by diffing against
 * both original files). It is the shortest of the sixteen pages and has no data or per-page local
 * state of its own: just the eight top-level cards and where each one navigates, so [onRoute] is
 * the only thing this page needs from its caller.
 */
@Composable
fun RootSettingsPage(onRoute: (SettingsRoute) -> Unit) {
    // Section order (task 11), and it is deliberately a journey from the cosmetic to the
    // irreversible: how it looks, what language it speaks, what it can do for you, how
    // you write, who can get in, what leaves the device, and finally the page that can
    // erase everything. The destructive page being last is the point — it is the one you
    // should have to travel to rather than the one you land on.
    NavCard(S.settingsAppearanceTitle, S.settingsAppearanceSub) { onRoute(SettingsRoute.Appearance) }
    Spacer(modifier = Modifier.height(12.dp))
    // Language sits beside Appearance because it answers the same kind of question —
    // "how does this app present itself to me" — and a user hunting for it will look
    // near the top, not under a technical heading (localization task).
    NavCard(S.settingsLanguageTitle, S.settingsLanguageSub) { onRoute(SettingsRoute.Language) }
    Spacer(modifier = Modifier.height(12.dp))
    // The subtitle lists what is actually behind this card. It used to stop at the API,
    // which quietly under-sold the section: memory and web search live here too, and a
    // subtitle that names three of four things reads as a complete list rather than a
    // truncated one — so the fourth looks like it isn't there.
    NavCard(S.settingsAssistantTitle, S.settingsAssistantSub) { onRoute(SettingsRoute.Assistant) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsEditorTitle, S.settingsEditorSub) { onRoute(SettingsRoute.Editor) }
    Spacer(modifier = Modifier.height(12.dp))
    // v2.7.5: cloud storage lives between Editor and Security - a capability of the app's
    // data (backup) side, not a privacy/security guarantee, hence the position.
    NavCard(S.cloudTitle, S.cloudSub) { onRoute(SettingsRoute.Cloud) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsSecurityTitle, S.settingsSecuritySub) { onRoute(SettingsRoute.Security) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsPrivacyTitle, S.settingsPrivacySub) { onRoute(SettingsRoute.Privacy) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsDataTitle, S.settingsDataSub) { onRoute(SettingsRoute.Data) }
}
