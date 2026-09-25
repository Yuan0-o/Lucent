package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.MemoryTier
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.MemoryTierRow
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

@Composable
internal fun AssistantMemorySection(repo: SettingsRepository, local: Boolean) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val tierFlow = if (local) repo.memoryTierLocal else repo.memoryTier
    val savedMemoryTier by tierFlow.collectAsState(
        initial = if (local) SettingsCache.memoryTierLocal else SettingsCache.memoryTier
    )
    val current = MemoryTier.fromKey(savedMemoryTier)

    LaunchedEffect(current) {
        if (current == MemoryTier.HIGH) writeTier(repo, local, MemoryTier.MEDIUM)
    }

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.memoryCostTitle, color = onGradient, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(12.dp))

        MemoryTierRow(
            selected = current == MemoryTier.LOW,
            title = S.memoryWeakTitle,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted,
            onClick = { writeTier(repo, local, MemoryTier.LOW) }
        )
        MemoryTierRow(
            selected = current == MemoryTier.MEDIUM,
            title = S.memoryStrongTitle,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted,
            onClick = { writeTier(repo, local, MemoryTier.MEDIUM) }
        )
    }
}

private fun writeTier(repo: SettingsRepository, local: Boolean, tier: MemoryTier) {
    if (local) {
        SettingsCache.memoryTierLocal = tier.key
        AppScope.io.launch { repo.setMemoryTierLocal(tier.key) }
    } else {
        SettingsCache.memoryTier = tier.key
        AppScope.io.launch { repo.setMemoryTier(tier.key) }
    }
}
