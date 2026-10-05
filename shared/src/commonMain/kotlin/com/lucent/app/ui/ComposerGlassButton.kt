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

val ComposerButtonDiameter = 46.dp

@Composable
fun ComposerGlassButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = ComposerButtonDiameter
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .size(diameter)
            .clip(shape)
            .frostedGlass(cornerRadius = diameter / 2)
            .background(tint.copy(alpha = 0.18f))
            .border(1.dp, tint.copy(alpha = 0.42f), shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(diameter * 0.44f)
        )
    }
}
