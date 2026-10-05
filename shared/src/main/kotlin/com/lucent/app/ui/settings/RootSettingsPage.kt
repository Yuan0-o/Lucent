package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucent.app.i18n.S
import com.lucent.app.ui.NavCard
import com.lucent.app.ui.SettingsRoute

@Composable
internal fun RootSettingsPage(onRoute: (SettingsRoute) -> Unit) {
    NavCard(S.settingsAppearanceTitle, S.settingsAppearanceSub) { onRoute(SettingsRoute.Appearance) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsLanguageTitle, S.settingsLanguageSub) { onRoute(SettingsRoute.Language) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsAssistantTitle, S.settingsAssistantSub) { onRoute(SettingsRoute.Assistant) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsEditorTitle, S.settingsEditorSub) { onRoute(SettingsRoute.Editor) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.cloudTitle, S.cloudSub) { onRoute(SettingsRoute.Cloud) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsSecurityTitle, S.settingsSecuritySub) { onRoute(SettingsRoute.Security) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsPrivacyTitle, S.settingsPrivacySub) { onRoute(SettingsRoute.Privacy) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsDataTitle, S.settingsDataSub) { onRoute(SettingsRoute.Data) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsAdvancedTitle, S.settingsAdvancedSub) { onRoute(SettingsRoute.Advanced) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsAboutTitle, S.settingsAboutSub) { onRoute(SettingsRoute.About) }
}
