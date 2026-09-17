package com.lucent.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

/**
 * P1-3 — extracted from the `NetworkPage` local composable that used to live inside
 * `SettingsScreen` (byte-identical on both platforms before this split, including the
 * `LucentToast`/`AppScope.io.launch` calls, both of which are already platform seams elsewhere in
 * this codebase). One toggle: whether the cloud assistant may search the web.
 */
@Composable
fun NetworkSettingsPage(repo: SettingsRepository, onRoute: (SettingsRoute) -> Unit) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val localModelEnabled by repo.localModelEnabled.collectAsState(initial = false)
    val savedWebSearch by repo.webSearchEnabled.collectAsState(initial = false)

    BackHeader(S.settingsNetworkTitle) { onRoute(SettingsRoute.Assistant) }

    // Web search toggle: lets the cloud assistant look things up online.
    //
    // Unavailable while the local model is on (tasks 3/8) — it answers with no network
    // at all, so a web-search switch in that mode would be a promise the app cannot
    // keep. The row is dimmed rather than removed: hiding it would leave the user
    // wondering where their setting went, and the value they had is coming back the
    // moment local mode is switched off (SettingsRepository parks it).
    val webSearchLocked = localModelEnabled
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // The whole row answers when it's locked, so a tap anywhere near the
                // switch — not only exactly on it — gets the explanation.
                .then(
                    if (webSearchLocked) Modifier.clickable {
                        LucentToast.show(context, S.webSearchLocalDisabledHint)
                    } else Modifier
                )
        ) {
            Column(modifier = Modifier.weight(1f).alpha(if (webSearchLocked) 0.38f else 1f)) {
                Text(S.webSearchTitle, color = onGradient, fontSize = 16.sp)
                Text(
                    S.webSearchDesc,
                    color = onGradientMuted,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = savedWebSearch && !webSearchLocked,
                enabled = !webSearchLocked,
                onCheckedChange = { on -> AppScope.io.launch { repo.setWebSearchEnabled(on) } }
            )
        }
        if (webSearchLocked) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(S.webSearchLocalDisabledHint, color = onGradientMuted, fontSize = 12.sp)
        }
    }
}
