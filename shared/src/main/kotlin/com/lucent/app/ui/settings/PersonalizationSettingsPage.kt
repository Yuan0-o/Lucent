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
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.GlassButton
import com.lucent.app.ui.LocalOnGradient
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
    onBack: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val savedTypingHaptics by repo.typingHapticsEnabled.collectAsState(initial = true)
    val savedConfirmTools by repo.assistantConfirmToolsEnabled.collectAsState(initial = true)

    BackHeader(S.settingsPersonalizationTitle) { onBack() }
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
        Spacer(modifier = Modifier.height(16.dp))
        GlassButton(text = S.actionSave, onClick = onSave)
    }

    Spacer(modifier = Modifier.height(12.dp))

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.typingHapticsTitle, color = onGradient, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = savedTypingHaptics,
                onCheckedChange = { on -> AppScope.io.launch { repo.setTypingHapticsEnabled(on) } }
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.assistantConfirmToolsTitle, color = onGradient, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = savedConfirmTools,
                onCheckedChange = { on -> AppScope.io.launch { repo.setAssistantConfirmTools(on) } }
            )
        }
    }
}
