package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class NotebookColor(val key: String, val cover: Color) {
    CRIMSON("crimson", Color(0xFFB3261E)),
    OCEAN("ocean", Color(0xFF1565C0)),
    FOREST("forest", Color(0xFF2E7D32)),
    AMBER("amber", Color(0xFFEF6C00)),
    PLUM("plum", Color(0xFF6A1B9A));

    val label: String
        get() = when (this) {
            CRIMSON -> com.lucent.app.i18n.S.coverCrimson
            OCEAN -> com.lucent.app.i18n.S.coverOcean
            FOREST -> com.lucent.app.i18n.S.coverForest
            AMBER -> com.lucent.app.i18n.S.coverAmber
            PLUM -> com.lucent.app.i18n.S.coverPlum
        }

    companion object {
        val DEFAULT = CRIMSON

        fun fromKey(key: String?): NotebookColor =
            entries.firstOrNull { it.key == (key?.trim()?.lowercase() ?: "") } ?: DEFAULT
    }
}

internal fun coverBrush(color: NotebookColor): Brush = Brush.horizontalGradient(
    0f to color.cover.copy(alpha = 0.70f),
    0.06f to color.cover.copy(alpha = 0.70f),
    0.07f to color.cover,
    1f to color.cover
)

@Composable
fun NotebookCoverPicker(
    selected: NotebookColor,
    onSelect: (NotebookColor) -> Unit,
    modifier: Modifier = Modifier
) {
    val onGradientMuted = LocalOnGradientMuted.current
    val context = LocalContext.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NotebookColor.entries.forEach { option ->
            val isSelected = option == selected
            val shape = RoundedCornerShape(6.dp)
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 46.dp)
                    .clip(shape)
                    .background(coverBrush(option))
                    .then(if (isSelected) Modifier.border(2.dp, onGradientMuted, shape) else Modifier)
                    .clickable {
                        Haptics.tick(context)
                        onSelect(option)
                    }
                    .semantics {
                        contentDescription = com.lucent.app.i18n.S.notebookCoverA11y(option.label)
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = if (option.cover.luminance() < 0.5f) Color.White else Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NotebookCover(
    colorKey: String,
    label: String?,
    modifier: Modifier = Modifier
) {
    val color = NotebookColor.fromKey(colorKey)
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(coverBrush(color))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .semantics {
                contentDescription = com.lucent.app.i18n.S.notebookCoverA11y(label ?: color.label)
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(6.dp)
                .background(Color.Black.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 10.dp)
                .size(width = 16.dp, height = 3.dp)
                .background(Color.White.copy(alpha = 0.35f))
        )
    }
}
