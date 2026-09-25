package com.lucent.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.SplashStyle
import com.lucent.app.data.StartupLog
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

@Composable
internal fun SplashSettingsPage(
    repo: SettingsRepository,
    onRoute: (SettingsRoute) -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()

    val enabled by repo.splashEnabled.collectAsState(initial = SettingsCache.splashEnabled)
    val savedStyle by repo.splashStyle.collectAsState(initial = SettingsCache.splashStyle)
    val current = SplashStyle.fromKey(savedStyle)

    BackHeader(onBack = { onRoute(SettingsRoute.Appearance) })

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.splashEnableTitle, color = onGradient, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    SettingsCache.splashEnabled = on
                    scope.launch { repo.setSplashEnabled(on) }
                    StartupLog.event(context, "splash: animation turned ${if (on) "on" else "off"}")
                }
            )
        }
    }

    if (enabled) {
        Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(
                S.splashStyleTitle,
                color = onGradient,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            SplashStyleRow(
                selected = current == SplashStyle.CAT,
                title = S.splashStyleCatTitle,
                detail = S.splashStyleCatDesc,
                onGradient = onGradient,
                onGradientMuted = onGradientMuted,
                onClick = {
                    SettingsCache.splashStyle = SplashStyle.CAT.key
                    scope.launch { repo.setSplashStyle(SplashStyle.CAT.key) }
                    StartupLog.event(context, "splash: animation set to ${SplashStyle.CAT.key}")
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SplashStyleRow(
                selected = current == SplashStyle.PEN,
                title = S.splashStylePenTitle,
                detail = S.splashStylePenDesc,
                onGradient = onGradient,
                onGradientMuted = onGradientMuted,
                onClick = {
                    SettingsCache.splashStyle = SplashStyle.PEN.key
                    scope.launch { repo.setSplashStyle(SplashStyle.PEN.key) }
                    StartupLog.event(context, "splash: animation set to ${SplashStyle.PEN.key}")
                }
            )
        }
    }
}

@Composable
private fun SplashStyleRow(
    selected: Boolean,
    title: String,
    detail: String,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, color = onGradient, fontSize = 15.sp)
            Text(detail, color = onGradientMuted, fontSize = 12.sp)
        }
    }
}
