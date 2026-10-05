package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.platform.LocalPlatformContext

internal val CapsuleLabelLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    danger: Boolean = false,
    compact: Boolean = false
) {
    val onGradient = LocalOnGradient.current
    val context = LocalPlatformContext.current
    val shape = RoundedCornerShape(percent = 50)
    val glassDark = isDarkGlass()

    val padH = if (compact) 14.dp else 22.dp
    val padV = if (compact) 8.dp else 13.dp
    val iconSize = if (compact) 15.dp else 18.dp
    val iconGap = if (compact) 6.dp else 8.dp
    val labelSize = if (compact) 13.sp else 15.sp

    val dangerFill = DANGER_RED
    val dangerRim = DANGER_RED_RIM
    val fill = when {
        danger -> dangerFill
        glassDark -> Color.White.copy(alpha = LucentGlass.CARD_FILL_DARK)
        else -> Color.White.copy(alpha = LucentGlass.CARD_FILL_LIGHT)
    }
    val label = if (danger) Color.White else onGradient
    val fade = if (enabled) 1f else 0.38f

    Row(
        modifier = modifier
            .clip(shape)
            .background(fill.copy(alpha = fill.alpha * fade))
            .then(
                if (danger) Modifier.border(1.dp, dangerRim.copy(alpha = dangerRim.alpha * fade), shape)
                else Modifier.border(1.dp, lucentGlassRim(strong = true), shape)
            )
            .clickable(enabled = enabled) {
                Haptics.tick(context)
                onClick()
            }
            .padding(horizontal = padH, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = label.copy(alpha = label.alpha * fade), modifier = Modifier.size(iconSize))
            Spacer(modifier = Modifier.width(iconGap))
        }
        Text(
            text,
            color = label.copy(alpha = label.alpha * fade),
            fontSize = labelSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.copy(lineHeightStyle = CapsuleLabelLineHeight)
        )
    }
}
