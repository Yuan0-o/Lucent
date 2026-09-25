package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.WebSearchEngine
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
    val savedEngine by repo.webSearchEngine.collectAsState(initial = SettingsCache.webSearchEngine)
    val localModelEnabled by repo.localModelEnabled.collectAsState(initial = SettingsCache.localModelEnabled)
    val agentModeOn by repo.agentMode.collectAsState(initial = SettingsCache.agentMode)

    val toolsAvailable = !localModelEnabled || agentModeOn
    val webSearchShown = toolsAvailable
    val webSearchChecked = savedWebSearch && toolsAvailable
    var engineMenuOpen by remember { mutableStateOf(false) }
    val engine = remember(savedEngine) { WebSearchEngine.fromKey(savedEngine) }

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
            title = S.agentModeTitle,
            detail = S.agentModeSub,
            checked = agentModeOn,
            onGradient = onGradient,
            onGradientMuted = onGradientMuted
        ) { on ->
            SettingsCache.agentMode = on
            AppScope.io.launch { repo.setAgentMode(on) }
        }

        if (webSearchShown) {
            Spacer(modifier = Modifier.height(14.dp))

            ToggleRow(
                title = S.webSearchTitle,
                detail = S.webSearchSub,
                checked = webSearchChecked,
                onGradient = onGradient,
                onGradientMuted = onGradientMuted
            ) { on ->
                SettingsCache.webSearchEnabled = on
                AppScope.io.launch { repo.setWebSearchEnabled(on) }
            }

            if (webSearchChecked) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(S.webSearchEngineTitle, color = onGradient, fontSize = 14.sp)
                        Text(S.webSearchEngineSub, color = onGradientMuted, fontSize = 12.sp)
                    }
                    Box {
                        IconButton(onClick = { engineMenuOpen = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(engine.label, color = onGradient, fontSize = 13.sp)
                                Icon(
                                    Icons.Default.ExpandMore,
                                    contentDescription = S.webSearchEngineTitle,
                                    tint = onGradient
                                )
                            }
                        }
                        DropdownMenu(expanded = engineMenuOpen, onDismissRequest = { engineMenuOpen = false }) {
                            WebSearchEngine.PICKER.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label, fontSize = 14.sp) },
                                    leadingIcon = if (option == engine) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        engineMenuOpen = false
                                        SettingsCache.webSearchEngine = option.key
                                        AppScope.io.launch { repo.setWebSearchEngine(option.key) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
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
