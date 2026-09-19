package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class NoteColor(val key: String, val swatch: Color) {
    DEFAULT("", Color.White),
    RED("red", Color(0xFFD32F2F)),
    ORANGE("orange", Color(0xFFEF6C00)),
    YELLOW("yellow", Color(0xFFF9A825)),
    GREEN("green", Color(0xFF2E7D32)),
    TEAL("teal", Color(0xFF00897B)),
    BLUE("blue", Color(0xFF1565C0)),
    PURPLE("purple", Color(0xFF8E24AA)),
    PINK("pink", Color(0xFFC2185B));

    val label: String
        get() = when (this) {
            DEFAULT -> com.lucent.app.i18n.S.colorDefault
            RED -> com.lucent.app.i18n.S.colorRed
            ORANGE -> com.lucent.app.i18n.S.colorOrange
            YELLOW -> com.lucent.app.i18n.S.colorYellow
            GREEN -> com.lucent.app.i18n.S.colorGreen
            TEAL -> com.lucent.app.i18n.S.colorTeal
            BLUE -> com.lucent.app.i18n.S.colorBlue
            PURPLE -> com.lucent.app.i18n.S.colorPurple
            PINK -> com.lucent.app.i18n.S.colorPink
        }

    companion object {
        fun fromKey(key: String?): NoteColor = entries.firstOrNull { it.key == (key?.trim() ?: "") } ?: DEFAULT
    }
}

@Composable
fun ColorPickerRow(selected: NoteColor, onSelect: (NoteColor) -> Unit, modifier: Modifier = Modifier) {
    val onGradientMuted = LocalOnGradientMuted.current
    val context = LocalContext.current
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NoteColor.entries.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .then(
                        if (option == NoteColor.DEFAULT) {
                            Modifier.border(1.5.dp, onGradientMuted, CircleShape)
                        } else {
                            Modifier.background(option.swatch)
                        }
                    )
                    .clickable {
                        Haptics.tick(context)
                        onSelect(option)
                    }
                    .semantics {
                        contentDescription = com.lucent.app.i18n.S.noteColorA11y(option.label)
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    val checkTint = when {
                        option == NoteColor.DEFAULT -> onGradientMuted
                        option.swatch.luminance() < 0.5f -> Color.White
                        else -> Color.Black.copy(alpha = 0.65f)
                    }
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = checkTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NoteColorDot(colorKey: String, modifier: Modifier = Modifier, size: Dp = 9.dp) {
    val color = NoteColor.fromKey(colorKey)
    if (color == NoteColor.DEFAULT) return
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.swatch)
            .semantics { contentDescription = com.lucent.app.i18n.S.noteWithColorA11y(color.label) }
    )
}
