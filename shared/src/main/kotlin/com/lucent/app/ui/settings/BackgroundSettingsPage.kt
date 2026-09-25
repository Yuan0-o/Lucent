package com.lucent.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import com.lucent.app.ui.LucentPalette
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.PALETTE_CYCLE
import com.lucent.app.ui.PALETTE_RANDOM
import com.lucent.app.ui.PaletteGroup
import com.lucent.app.ui.PaletteSwatch
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.rememberDynamicColorActive
import com.lucent.app.ui.title
import kotlinx.coroutines.launch

@Composable
internal fun BackgroundSettingsPage(repo: SettingsRepository, onRoute: (SettingsRoute) -> Unit) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    val backgroundAnimationEnabled by repo.backgroundAnimationEnabled.collectAsState(
        initial = SettingsCache.backgroundAnimationEnabled
    )
    val savedPalette by repo.palette.collectAsState(initial = SettingsCache.palette)
    val dynamicColorActive = rememberDynamicColorActive(repo)

    BackHeader(onBack = { onRoute(SettingsRoute.Appearance) })

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.backgroundAnimationTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = backgroundAnimationEnabled,
                onCheckedChange = { checked ->
                    SettingsCache.backgroundAnimationEnabled = checked
                    scope.launch { repo.setBackgroundAnimationEnabled(checked) }
                }
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    if (!dynamicColorActive) {
        val paletteEnabled = backgroundAnimationEnabled
        val paletteAlpha = if (paletteEnabled) 1f else 0.38f
        fun pickPalette(name: String) {
            if (paletteEnabled) {
                SettingsCache.palette = name
                AppScope.io.launch { repo.setPalette(name) }
            } else {
                LucentToast.show(context, S.backgroundPaletteDisabledHint)
            }
        }
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { pickPalette(PALETTE_CYCLE) }
            ) {
                RadioButton(
                    selected = savedPalette == PALETTE_CYCLE,
                    enabled = paletteEnabled,
                    onClick = { pickPalette(PALETTE_CYCLE) }
                )
                Box(modifier = Modifier.alpha(paletteAlpha)) {
                    PaletteSwatch(LucentPalette.pickerEntries.map { it.colors.first() })
                }
                Text(
                    S.paletteCycleAuto,
                    color = onGradient.copy(alpha = onGradient.alpha * paletteAlpha),
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().clickable { pickPalette(PALETTE_RANDOM) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(
                        selected = savedPalette == PALETTE_RANDOM,
                        enabled = paletteEnabled,
                        onClick = { pickPalette(PALETTE_RANDOM) }
                    )
                    Box(modifier = Modifier.alpha(paletteAlpha)) {
                        PaletteSwatch(LucentPalette.pickerEntries.shuffled().take(4).flatMap { it.colors })
                    }
                    Text(
                        S.paletteRandomAuto,
                        color = onGradient.copy(alpha = onGradient.alpha * paletteAlpha),
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }

            PaletteGroup.entries.forEach { group ->
                val heading = group.title()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    heading,
                    color = onGradientMuted.copy(alpha = onGradientMuted.alpha * paletteAlpha),
                    fontSize = 13.sp
                )
                LucentPalette.pickerEntries.filter { it.group == group }.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { pickPalette(p.name) }
                    ) {
                        RadioButton(
                            selected = savedPalette == p.name,
                            enabled = paletteEnabled,
                            onClick = { pickPalette(p.name) }
                        )
                        Box(modifier = Modifier.alpha(paletteAlpha)) {
                            PaletteSwatch(p.colors)
                        }
                        Text(
                            p.label,
                            color = onGradient.copy(alpha = onGradient.alpha * paletteAlpha),
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
