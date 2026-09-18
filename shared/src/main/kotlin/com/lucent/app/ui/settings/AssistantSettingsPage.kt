package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.NavCard
import com.lucent.app.ui.SettingsRoute

/**
 * P1-3 — extracted from the `AssistantPage` local composable that used to live inside
 * `SettingsScreen` (byte-identical on both platforms before this split). A pure navigation hub:
 * five cards into the Assistant section's sub-pages, with no local state of its own.
 *
 * [profiles] and [selectedProfileIdx] are passed in rather than re-derived here because the
 * parsing they need (falling back to a single seeded profile for installs upgrading from the
 * single-API version, and telling "no profiles saved yet" apart from "the user deleted the last
 * one on purpose") is nontrivial and already computed once, in [SettingsScreen], for the API page
 * that edits the same list — this page only ever reads the active profile's name from it.
 * [localModelEnabled] is a single repository flow, so unlike the profile list it is collected here
 * directly rather than threaded through as a parameter.
 */
@Composable
internal fun AssistantSettingsPage(
    repo: SettingsRepository,
    profiles: List<ApiProfile>,
    selectedProfileIdx: Int,
    onRoute: (SettingsRoute) -> Unit
) {
    val localModelEnabled by repo.localModelEnabled.collectAsState(initial = false)

    BackHeader(S.settingsAssistantTitle) { onRoute(SettingsRoute.Root) }

    // Order (task 10): Personalization, API, Memory & web, then Local model. The local
    // model comes last because it is the alternative to everything above it: with it on,
    // the API page's connection and the memory tier simply stop being consulted.
    NavCard(S.settingsPersonalizationTitle, S.settingsPersonalizationSub) { onRoute(SettingsRoute.Personalization) }

    Spacer(modifier = Modifier.height(12.dp))

    // API is its own hierarchical page. The subtitle shows which profile is active so
    // the user can see their current connection at a glance without opening it — except
    // while the local model is on, when that would be a small lie: the named profile is
    // exactly what the app is NOT using. The card then states the freeze instead, matching
    // the "Cloud API frozen" notice the page itself shows when opened.
    val activeName = profiles.getOrNull(selectedProfileIdx)?.name ?: ""
    NavCard(
        S.settingsApiTitle,
        if (localModelEnabled) S.settingsApiSubFrozen else S.settingsApiSub(activeName)
    ) { onRoute(SettingsRoute.Api) }

    Spacer(modifier = Modifier.height(12.dp))

    // Memory and Networking are now two separate cards: one page for how much the
    // assistant remembers (the memory tier), and a distinct page for going online (the
    // web-search toggle). They used to share a single "Memory & web" card.
    NavCard(S.settingsMemoryTitle, S.settingsMemorySub) { onRoute(SettingsRoute.Memory) }
    Spacer(modifier = Modifier.height(12.dp))
    NavCard(S.settingsNetworkTitle, S.settingsNetworkSub) { onRoute(SettingsRoute.Network) }

    Spacer(modifier = Modifier.height(12.dp))

    // On-device GGUF assistant (local-model task).
    NavCard(S.settingsLocalModelTitle, S.settingsLocalModelSub) { onRoute(SettingsRoute.LocalModel) }
}
