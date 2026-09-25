package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.GlassButton
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

@Composable
fun PersonalizationSettingsPage(
    repo: SettingsRepository,
    assistantName: String,
    onAssistantNameChange: (String) -> Unit,
    assistantStyle: String,
    onAssistantStyleChange: (String) -> Unit,
    onSave: () -> Unit,
    onRequestSmallModelWarning: () -> Unit,
    onBack: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val savedTypingHaptics by repo.typingHapticsEnabled.collectAsState(initial = SettingsCache.typingHapticsEnabled)
    val savedConfirmTools by repo.assistantConfirmToolsEnabled.collectAsState(
        initial = SettingsCache.assistantConfirmToolsEnabled
    )
    val savedSmallModelMode by repo.smallModelModeEnabled.collectAsState(
        initial = SettingsCache.smallModelModeEnabled
    )
    val savedWebSearch by repo.webSearchEnabled.collectAsState(initial = SettingsCache.webSearchEnabled)

    BackHeader(onBack = onBack)
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        OutlinedTextField(
            value = assistantName,
            onValueChange = onAssistantNameChange,
            label = { Text(S.fieldAssistantName) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = assistantStyle,
            onValueChange = onAssistantStyleChange,
            label = { Text(S.fieldChatStyle) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        GlassButton(text = S.actionSave, onClick = onSave)
    }

    Spacer(modifier = Modifier.height(12.dp))

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        ToggleRow(
            title = S.typingHapticsTitle,
            detail = S.typingHapticsSub,
            checked = savedTypingHaptics,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted
        ) { on ->
            SettingsCache.typingHapticsEnabled = on
            AppScope.io.launch { repo.setTypingHapticsEnabled(on) }
        }

        Spacer(modifier = Modifier.height(14.dp))

        ToggleRow(
            title = S.webSearchTitle,
            detail = S.webSearchSub,
            checked = savedWebSearch,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted
        ) { on ->
            SettingsCache.webSearchEnabled = on
            AppScope.io.launch { repo.setWebSearchEnabled(on) }
        }

        Spacer(modifier = Modifier.height(14.dp))

        ToggleRow(
            title = S.assistantConfirmToolsTitle,
            detail = S.assistantConfirmToolsSub,
            checked = savedConfirmTools,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted
        ) { on ->
            SettingsCache.assistantConfirmToolsEnabled = on
            AppScope.io.launch { repo.setAssistantConfirmTools(on) }
        }

        Spacer(modifier = Modifier.height(14.dp))

        ToggleRow(
            title = S.smallModelModeTitle,
            detail = S.smallModelModeSub,
            checked = savedSmallModelMode,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted
        ) { on ->
            if (on) {
                onRequestSmallModelWarning()
            } else {
                SettingsCache.smallModelModeEnabled = false
                AppScope.io.launch { repo.setSmallModelModeEnabled(false) }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onGradient: androidx.compose.ui.graphics.Color,
    onGradientMuted: androidx.compose.ui.graphics.Color,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = onGradient, fontSize = 16.sp)
            if (detail.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(detail, color = onGradientMuted, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
