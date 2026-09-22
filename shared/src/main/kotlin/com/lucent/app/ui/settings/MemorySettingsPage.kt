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
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.MemoryTier
import com.lucent.app.data.SettingsCache
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

@Composable
internal fun MemorySettingsPage(
    repo: SettingsRepository,
    onRequestSmallModelWarning: () -> Unit,
    onRoute: (SettingsRoute) -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var localModelEnabled by remember { mutableStateOf(SettingsCache.localModelEnabled) }
    LaunchedEffect(Unit) { repo.localModelEnabled.collect { localModelEnabled = it } }
    val savedMemoryTier by repo.memoryTier.collectAsState(initial = null)
    var savedSmallModelMode by remember { mutableStateOf(SettingsCache.smallModelModeEnabled) }
    LaunchedEffect(Unit) { repo.smallModelModeEnabled.collect { savedSmallModelMode = it } }
    val savedEmbeddingProvider by repo.embeddingProvider.collectAsState(initial = null)

    BackHeader(S.settingsMemoryTitle) { onRoute(SettingsRoute.Assistant) }

    if (savedMemoryTier == null || savedEmbeddingProvider == null) return

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.memoryCostTitle, color = onGradient, fontSize = 16.sp)

        Spacer(modifier = Modifier.height(12.dp))

        val current = MemoryTier.fromKey(savedMemoryTier)

        MemoryTierRow(
            selected = current == MemoryTier.LOW,
            title = S.memoryLowTitle,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted,
            onClick = {
                SettingsCache.memoryTier = MemoryTier.LOW.key
                AppScope.io.launch { repo.setMemoryTier(MemoryTier.LOW.key) }
            }
        )
        MemoryTierRow(
            selected = current == MemoryTier.MEDIUM,
            title = S.memoryMediumTitle,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted,
            onClick = {
                SettingsCache.memoryTier = MemoryTier.MEDIUM.key
                AppScope.io.launch { repo.setMemoryTier(MemoryTier.MEDIUM.key) }
            }
        )
        MemoryTierRow(
            selected = current == MemoryTier.HIGH,
            title = S.memoryHighTitle,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted,
            dimmed = localModelEnabled,
            onClick = {
                if (localModelEnabled) LucentToast.show(context, S.memoryHighLocalDisabledHint)
                else {
                    SettingsCache.memoryTier = MemoryTier.HIGH.key
                    AppScope.io.launch { repo.setMemoryTier(MemoryTier.HIGH.key) }
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.embeddingProviderTitle, color = onGradient, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(12.dp))

        MemoryTierRow(
            selected = savedEmbeddingProvider != "cloud",
            title = S.embeddingProviderLocalTitle,
            detail = S.embeddingProviderLocalDesc,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted,
            onClick = {
                SettingsCache.embeddingProvider = "local"
                AppScope.io.launch { repo.setEmbeddingProvider("local") }
            }
        )
        MemoryTierRow(
            selected = savedEmbeddingProvider == "cloud",
            title = S.embeddingProviderCloudTitle,
            detail = S.embeddingProviderCloudDesc,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted,
            onClick = {
                SettingsCache.embeddingProvider = "cloud"
                AppScope.io.launch { repo.setEmbeddingProvider("cloud") }
            }
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.smallModelModeTitle, color = onGradient, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = savedSmallModelMode,
                onCheckedChange = { on ->
                    if (on) onRequestSmallModelWarning()
                    else {
                        SettingsCache.smallModelModeEnabled = false
                        AppScope.io.launch { repo.setSmallModelModeEnabled(false) }
                    }
                }
            )
        }
    }
}
