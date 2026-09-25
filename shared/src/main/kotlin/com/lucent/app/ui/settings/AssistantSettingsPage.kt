package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    onRoute: (SettingsRoute) -> Unit,
    onRequestCloudOn: () -> Unit,
    onRequestLocalOn: () -> Unit
) {
    val localModelEnabled by repo.localModelEnabled.collectAsState(initial = SettingsCache.localModelEnabled)
    val cloudOn = !localModelEnabled

    BackHeader(onBack = { onRoute(SettingsRoute.Root) })

    NavCard(S.settingsPersonalizationTitle, S.settingsPersonalizationSub) { onRoute(SettingsRoute.Personalization) }

    Spacer(modifier = Modifier.height(12.dp))

    val activeName = profiles.getOrNull(selectedProfileIdx)?.name ?: ""
    NavCard(
        title = S.settingsCloudModelTitle,
        subtitle = if (cloudOn) S.settingsCloudModelSub(activeName) else S.settingsCloudModelSubOff,
        onClick = { onRoute(SettingsRoute.CloudModel) },
        trailing = {
            Switch(
                checked = cloudOn,
                onCheckedChange = { on ->
                    if (on) {
                        if (!cloudOn) onRequestCloudOn()
                    } else if (cloudOn) {
                        onRequestLocalOn()
                    }
                }
            )
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    NavCard(
        title = S.settingsLocalModelTitle,
        subtitle = if (localModelEnabled) S.settingsLocalModelSubOn else S.settingsLocalModelSub,
        onClick = { onRoute(SettingsRoute.LocalModel) },
        trailing = {
            Switch(
                checked = localModelEnabled,
                onCheckedChange = { on ->
                    if (on) {
                        if (!localModelEnabled) onRequestLocalOn()
                    } else if (localModelEnabled) {
                        onRequestCloudOn()
                    }
                }
            )
        }
    )
}
