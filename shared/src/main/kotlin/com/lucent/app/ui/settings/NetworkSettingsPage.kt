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
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

@Composable
internal fun NetworkSettingsPage(repo: SettingsRepository, onRoute: (SettingsRoute) -> Unit) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val localModelEnabled by repo.localModelEnabled.collectAsState(initial = SettingsCache.localModelEnabled)
    val savedWebSearch by repo.webSearchEnabled.collectAsState(initial = SettingsCache.webSearchEnabled)

    BackHeader(S.settingsNetworkTitle) { onRoute(SettingsRoute.Assistant) }

    val webSearchLocked = localModelEnabled
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (webSearchLocked) Modifier.clickable {
                        LucentToast.show(context, S.webSearchLocalDisabledHint)
                    } else Modifier
                )
        ) {
            Column(modifier = Modifier.weight(1f).alpha(if (webSearchLocked) 0.38f else 1f)) {
                Text(S.webSearchTitle, color = onGradient, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = savedWebSearch && !webSearchLocked,
                enabled = !webSearchLocked,
                onCheckedChange = { on -> AppScope.io.launch { repo.setWebSearchEnabled(on) } }
            )
        }
        if (webSearchLocked) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(S.webSearchLocalDisabledHint, color = onGradientMuted, fontSize = 12.sp)
        }
    }
}
