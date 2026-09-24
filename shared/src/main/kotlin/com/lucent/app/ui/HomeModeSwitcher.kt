package com.lucent.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeModeSwitcher(
    mode: HomeMode,
    onSelect: (HomeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val onGradient = LocalOnGradient.current
    val capsule = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .clip(capsule)
            .background(onGradient.copy(alpha = 0.07f))
            .border(1.dp, onGradient.copy(alpha = 0.14f), capsule)
            .padding(3.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        HomeMode.entries.forEach { entry ->
            HomeModeOption(
                label = entry.label,
                selected = entry == mode,
                onClick = { if (entry != mode) onSelect(entry) }
            )
        }
    }
}

@Composable
private fun HomeModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val fill by animateColorAsState(
        if (selected) onGradient.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = tween(220),
        label = "homeModeFill"
    )
    val dotFill by animateColorAsState(
        if (selected) onGradient else Color.Transparent,
        animationSpec = tween(220),
        label = "homeModeDot"
    )
    val dotSize by animateDpAsState(if (selected) 9.dp else 8.dp, animationSpec = tween(220), label = "homeModeDotSize")
    val textColor = if (selected) onGradient else onGradientMuted
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = {
                    Haptics.tick(context)
                    onClick()
                }
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotFill)
                .border(1.5.dp, if (selected) onGradient else onGradientMuted, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            label,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
