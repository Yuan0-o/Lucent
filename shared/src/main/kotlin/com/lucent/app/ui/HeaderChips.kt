package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val HeaderChipDiameter = 40.dp

@Composable
fun HeaderPanelChip(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Box(modifier = modifier.size(HeaderChipDiameter)) {
        GlassRoundButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = onClick,
            tint = tint,
            diameter = HeaderChipDiameter
        )
        if (!badge.isNullOrBlank()) {
            HeaderChipBadge(
                text = badge,
                tint = tint,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun HeaderChipBadge(text: String, tint: Color, modifier: Modifier = Modifier) {
    val ink = if (tint.luminance() > 0.5f) Color(0xFF20202B) else Color.White
    Box(
        modifier = modifier
            .offset(x = 5.dp, y = (-5).dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(tint)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = ink,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}
