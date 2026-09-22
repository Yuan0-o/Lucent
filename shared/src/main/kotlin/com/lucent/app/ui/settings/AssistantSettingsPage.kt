package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.NavCard
import com.lucent.app.ui.SettingsRoute

@Composable
internal fun AssistantSettingsPage(
    repo: SettingsRepository,
    profiles: List<ApiProfile>,
    selectedProfileIdx: Int,
    onRoute: (SettingsRoute) -> Unit
) {
    var localModelEnabled by remember { mutableStateOf(SettingsCache.localModelEnabled) }
    LaunchedEffect(Unit) { repo.localModelEnabled.collect { localModelEnabled = it } }

    BackHeader(S.settingsAssistantTitle) { onRoute(SettingsRoute.Root) }

    NavCard(S.settingsPersonalizationTitle, S.settingsPersonalizationSub) { onRoute(SettingsRoute.Personalization) }

    Spacer(modifier = Modifier.height(12.dp))

    val activeName = profiles.getOrNull(selectedProfileIdx)?.name ?: ""
    NavCard(
        S.settingsApiTitle,
        if (localModelEnabled) S.settingsApiSubFrozen else S.settingsApiSub(activeName)
    ) { onRoute(SettingsRoute.Api) }

    Spacer(modifier = Modifier.height(12.dp))

    NavCard(S.settingsMemoryTitle, S.settingsMemorySub) { onRoute(SettingsRoute.Memory) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsNetworkTitle, S.settingsNetworkSub) { onRoute(SettingsRoute.Network) }

    Spacer(modifier = Modifier.height(12.dp))

    NavCard(S.settingsLocalModelTitle, S.settingsLocalModelSub) { onRoute(SettingsRoute.LocalModel) }
}
