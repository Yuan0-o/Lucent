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

/**
 * P1-3 — extracted from the `BackgroundPage` local composable that used to live inside
 * `SettingsScreen`. The only difference between the two originals was, again, dynamic colour: the
 * paused banner and the palette-hiding condition existed only on Android. Reuses
 * [rememberDynamicColorActive] from the Appearance/Theme split rather than adding a second seam
 * for the same underlying fact.
 */
@Composable
internal fun BackgroundSettingsPage(repo: SettingsRepository, onRoute: (SettingsRoute) -> Unit) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    val backgroundAnimationEnabled by repo.backgroundAnimationEnabled.collectAsState(initial = true)
    val savedPalette by repo.palette.collectAsState(initial = "SUNSET")
    val dynamicColorActive = rememberDynamicColorActive(repo)

    BackHeader(S.settingsBackgroundTitle) { onRoute(SettingsRoute.Appearance) }
    if (dynamicColorActive) {
        // Material You has priority while it is on: the controls below still edit the
        // STORED choices (so turning dynamic off restores exactly these), but nothing here
        // changes what is on screen until then. Say so instead of pretending it is live.
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            Text(S.dynamicColorPausedBackground, color = onGradientMuted, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
    // At the very top: the master switch for the drifting effect. Off = a still, flat
    // theme colour, and the palette choice below only takes visible effect once it's on.
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.backgroundAnimationTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = backgroundAnimationEnabled,
                onCheckedChange = { checked -> scope.launch { repo.setBackgroundAnimationEnabled(checked) } }
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    // The palette list only takes visible effect while the drifting effect is ON, so
    // with the switch off every colour row is disabled and greyed out rather than
    // pretending to work: the radio buttons go grey, the swatches and labels fade, and
    // a tap anywhere on a row answers with a toast at the bottom of the screen saying
    // the drifting background isn't on — instead of silently changing a setting whose
    // result can't be seen (fix task).
    // v2.7.2: while Material You is on, the whole palette section below is HIDDEN (the
    // banner above says why; the rows return when the wallpaper mode is switched off).
    if (!dynamicColorActive) {
        val paletteEnabled = backgroundAnimationEnabled
        // One alpha for everything in a disabled row, so swatch and label fade together.
        val paletteAlpha = if (paletteEnabled) 1f else 0.38f
        fun pickPalette(name: String) {
            if (paletteEnabled) {
                AppScope.io.launch { repo.setPalette(name) }
            } else {
                LucentToast.show(context, S.backgroundPaletteDisabledHint)
            }
        }
        Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
            // Auto-cycle: rotates through every palette over time. Its swatch previews the
            // spread of colours it moves through.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // The whole row stays tappable while disabled so the tap can EXPLAIN itself
                // (the toast) — a dead row that ignores touches just looks broken.
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

            // Random (v2.4.0): sibling of auto-cycle. Auto-cycle walks the palettes in order;
            // Random jumps to a different palette every RANDOM_SWITCH_MS. The row carries the
            // small hint so the behaviour is discoverable without opening anything.
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

            // Palettes grouped by style family (v2.4.0: eight sections), each with a small
            // colour preview. The sections come straight from the enum, so a new family can
            // never exist without its title and its picker section.
            PaletteGroup.entries.forEach { group ->
                val heading = group.title()
                Spacer(modifier = Modifier.height(10.dp))
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
