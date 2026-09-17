package com.lucent.app.ui.settings

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.MemoryTier
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.MemoryTierRow
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

/**
 * P1-3 — extracted from the `MemoryPage` local composable that used to live inside
 * `SettingsScreen` (byte-identical on both platforms before this split).
 *
 * [onRequestSmallModelWarning] is a write-only trigger for a confirmation dialog that lives in,
 * and stays in, [SettingsScreen] (`showSmallModelWarn`) — this page only ever sets it to true, so
 * it is passed down as a bare callback rather than the state itself, the same way [onRoute] hides
 * navigation without this page needing to know how routing is stored.
 */
@Composable
fun MemorySettingsPage(
    repo: SettingsRepository,
    onRequestSmallModelWarning: () -> Unit,
    onRoute: (SettingsRoute) -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val localModelEnabled by repo.localModelEnabled.collectAsState(initial = false)
    val savedMemoryTier by repo.memoryTier.collectAsState(initial = MemoryTier.DEFAULT.key)
    val savedSmallModelMode by repo.smallModelModeEnabled.collectAsState(initial = false)
    val savedEmbeddingProvider by repo.embeddingProvider.collectAsState(initial = "local")

    BackHeader(S.settingsMemoryTitle) { onRoute(SettingsRoute.Assistant) }

    // Memory tier. Each option explains both what the assistant will remember and the
    // rough cost trade-off, since more context means more tokens per reply.
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.memoryCostTitle, color = onGradient, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            S.memoryCostDesc,
            color = onGradientMuted,
            fontSize = 13.sp
        )
        // Local mode changes what this page is allowed to offer (task 8): an on-device
        // model works from a short prompt, so the high tier is withdrawn while it is on.
        // Saying that here, before the rows, means the greyed row below is explained
        // before it is touched rather than only after.
        if (localModelEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(S.memoryLocalTierNote, color = onGradientMuted, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        val current = MemoryTier.fromKey(savedMemoryTier)

        MemoryTierRow(
            selected = current == MemoryTier.LOW,
            title = S.memoryLowTitle,
            detail = S.memoryLowDesc,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted,
            onClick = { AppScope.io.launch { repo.setMemoryTier(MemoryTier.LOW.key) } }
        )
        MemoryTierRow(
            selected = current == MemoryTier.MEDIUM,
            title = S.memoryMediumTitle,
            detail = S.memoryMediumDesc,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted,
            onClick = { AppScope.io.launch { repo.setMemoryTier(MemoryTier.MEDIUM.key) } }
        )
        // The high tier stays visible but greyed, and — importantly — stays TAPPABLE.
        // A control that simply ignores touches teaches the user nothing except that the
        // app is broken; this one answers with a bottom toast explaining why it's off and
        // that their previous choice is being held for them (task 8: a toast, never a
        // dialog — a modal for "you can't do that" is a punishment, not an explanation).
        MemoryTierRow(
            selected = current == MemoryTier.HIGH,
            title = S.memoryHighTitle,
            detail = S.memoryHighDesc,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted,
            dimmed = localModelEnabled,
            onClick = {
                if (localModelEnabled) LucentToast.show(context, S.memoryHighLocalDisabledHint)
                else AppScope.io.launch { repo.setMemoryTier(MemoryTier.HIGH.key) }
            }
        )
    }

    // ---- Optimize for small models (R3 report) ----
    // Moved here from Personalization so every knob about WHAT the assistant is fed — how
    // much history, and how the prompt is trimmed for a weak model — lives on the same
    // page, directly under the tier it modifies. Kept as its own glass card on purpose:
    // the two blocks must never merge, and the trade-off warning reads against the memory
    // choice made just above.
    Spacer(modifier = Modifier.height(12.dp))
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.smallModelModeTitle, color = onGradient, fontSize = 16.sp)
                Text(S.smallModelModeSub, color = onGradientMuted, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = savedSmallModelMode,
                onCheckedChange = { on ->
                    // Only turning it ON warns. Turning it off restores the full prompt,
                    // which needs no explanation and no permission.
                    if (on) onRequestSmallModelWarning()
                    else AppScope.io.launch { repo.setSmallModelModeEnabled(false) }
                }
            )
        }
    }

    // ---- Semantic search (P2-2) ----
    // Its own card, deliberately below memory tier and small-model mode: those two are about
    // what the assistant is TOLD; this is about how it FINDS a note in the first place, a
    // different question the user reads as separate even though all three end up feeding the
    // same conversation.
    Spacer(modifier = Modifier.height(12.dp))
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.embeddingProviderTitle, color = onGradient, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(S.embeddingProviderDesc, color = onGradientMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(12.dp))

        MemoryTierRow(
            selected = savedEmbeddingProvider != "cloud",
            title = S.embeddingProviderLocalTitle,
            detail = S.embeddingProviderLocalDesc,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted,
            onClick = { AppScope.io.launch { repo.setEmbeddingProvider("local") } }
        )
        MemoryTierRow(
            selected = savedEmbeddingProvider == "cloud",
            title = S.embeddingProviderCloudTitle,
            detail = S.embeddingProviderCloudDesc,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted,
            onClick = { AppScope.io.launch { repo.setEmbeddingProvider("cloud") } }
        )
    }
}
