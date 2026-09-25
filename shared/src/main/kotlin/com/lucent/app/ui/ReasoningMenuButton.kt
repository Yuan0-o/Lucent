package com.lucent.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.ReasoningEffort
import com.lucent.app.data.ReasoningEfforts
import com.lucent.app.i18n.S

@Composable
fun ReasoningMenuButton(
    providerId: String,
    model: String,
    currentKey: String,
    onSelect: (ReasoningEffort) -> Unit,
    tint: Color,
    mutedTint: Color,
    modifier: Modifier = Modifier
) {
    val options = remember(providerId, model) { ReasoningEfforts.optionsFor(providerId, model) }
    if (options.size <= 1) return
    val current = remember(providerId, model, currentKey) { ReasoningEfforts.settled(providerId, model, currentKey) }
    var open by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Psychology,
                contentDescription = S.reasoningTitle,
                tint = if (current == ReasoningEffort.PROVIDER_DEFAULT) mutedTint else tint,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(S.reasoningTitle, fontSize = 13.sp, color = mutedTint) },
                enabled = false,
                onClick = {}
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(if (option == current) "${option.label} · ${option.detail}" else option.label, fontSize = 14.sp)
                    },
                    leadingIcon = if (option == current) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}
