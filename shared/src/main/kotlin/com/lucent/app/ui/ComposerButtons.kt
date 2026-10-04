package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassRoundButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    diameter: Dp = 46.dp,
    filled: Boolean = false,
    plain: Boolean = false,
    iconSize: Dp = if (plain) diameter * 0.5f else diameter * 0.44f
) {
    val shape = RoundedCornerShape(percent = 50)
    val ink = if (enabled) tint else tint.copy(alpha = 0.35f)
    val skin = when {
        plain -> Modifier
        filled -> Modifier
            .background(tint.copy(alpha = if (enabled) 0.24f else 0.08f))
            .border(1.dp, tint.copy(alpha = if (enabled) 0.42f else 0.14f), shape)
        else -> Modifier.frostedGlass(cornerRadius = diameter / 2)
    }
    Box(
        modifier = modifier
            .size(diameter)
            .clip(shape)
            .then(skin)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = ink,
            modifier = Modifier.size(iconSize)
        )
    }
}
