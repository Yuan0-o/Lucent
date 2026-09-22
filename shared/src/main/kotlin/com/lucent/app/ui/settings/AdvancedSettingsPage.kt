package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.SettingsCache
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass

@Composable
internal fun AdvancedSettingsPage(
    shizukuReady: Boolean,
    onPairShizuku: () -> Unit,
    onRoute: (SettingsRoute) -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    BackHeader(S.advancedTitle) { onRoute(SettingsRoute.Root) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .padding(16.dp)
    ) {
        Text(S.shizukuTitle, color = onGradient, fontSize = 17.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(S.shizukuDesc, color = onGradientMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (shizukuReady) S.shizukuReady else S.shizukuNotReady,
                color = if (shizukuReady) onGradient else onGradientMuted,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onPairShizuku) {
                Text(S.shizukuPair, fontSize = 13.sp)
            }
        }
    }
}