package com.lucent.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TextFormat
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
import com.lucent.app.data.ContentFormats

@Composable
internal fun FormatOverrideButton(
    current: String?,
    onSelect: (String?) -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Default.TextFormat,
                contentDescription = com.lucent.app.i18n.S.a11yRichTextToolbar,
                tint = if (current == null) onGradientMuted else onGradient
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FormatOverrideItem(
                label = com.lucent.app.i18n.S.formatAutoDetect,
                selected = current == null,
                onClick = {
                    open = false
                    onSelect(null)
                }
            )
            FormatOverrideItem(
                label = com.lucent.app.i18n.S.formatMarkdown,
                selected = current == ContentFormats.MARKDOWN_KEY,
                onClick = {
                    open = false
                    onSelect(ContentFormats.MARKDOWN_KEY)
                }
            )
            FormatOverrideItem(
                label = com.lucent.app.i18n.S.richTextTitle,
                selected = current == ContentFormats.RICH_TEXT_KEY,
                onClick = {
                    open = false
                    onSelect(ContentFormats.RICH_TEXT_KEY)
                }
            )
        }
    }
}

@Composable
private fun FormatOverrideItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                if (selected) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                contentDescription = null
            )
        },
        onClick = onClick
    )
}
