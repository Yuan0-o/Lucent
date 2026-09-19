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

/**
 * P1-3 — extracted from the `PersonalizationPage` local composable that used to live inside
 * `SettingsScreen` (byte-identical on both platforms before this split).
 *
 * The name/style fields are the one place in the whole Settings screen with an unsaved-changes
 * guard, and that guard ([SettingsScreen]'s `assistantDirty`/`leavePersonalization`/the discard
 * dialog) reads and resets the same two variables from outside this page's own composition, so —
 * unlike everywhere else in these extracted pages — their `remember` state deliberately stays
 * owned by the parent rather than moving in here. [onBack] is the existing `leavePersonalization`
 * function, unchanged: this page never needs to know the guard exists, only that back might not
 * navigate immediately.
 */
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

    // Typing haptics lives here now (task 4). It's part of how the chat feels rather than
    // anything to do with memory or the web, so it moved onto Personalization when the old
    // combined "Memory & web" page was split apart. It writes immediately and isn't part
    // of the name/style "unsaved changes" tracking, so leaving without pressing Save above
    // never affects it.
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

    // Whether the assistant must ask before EVERY tool call — reads as well as writes,
    // cloud and on-device alike. Default ON. Turning it off removes the confirmation
    // modal entirely, because the switch trades oversight for convenience — and that
    // trade should never be made by accident.
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
