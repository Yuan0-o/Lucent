package com.lucent.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

internal const val PEN_WRITE_START_MS = 300f
internal const val PEN_WRITE_END_MS = 2900f
internal const val PEN_HOLD_END_MS = 3350f
internal const val PEN_TOTAL_MS = 4300f

private const val PEN_WORD = "Lucent"
private const val PEN_FONT_SP = 58f

@Composable
internal fun PenSplashArtwork(elapsedMs: Float, tint: Color) {    val measurer = rememberTextMeasurer()
    val layout = remember(measurer) {
        measurer.measure(
            text = AnnotatedString(PEN_WORD),
            style = TextStyle(
                fontSize = PEN_FONT_SP.sp,
                fontFamily = FontFamily.Cursive,
                fontStyle = FontStyle.Italic
            )
        )
    }

    val writing = ((elapsedMs - PEN_WRITE_START_MS) / (PEN_WRITE_END_MS - PEN_WRITE_START_MS)).coerceIn(0f, 1f)
    val fade = ((elapsedMs - PEN_HOLD_END_MS) / (PEN_TOTAL_MS - PEN_HOLD_END_MS)).coerceIn(0f, 1f)
    val enter = (elapsedMs / PEN_WRITE_START_MS).coerceIn(0f, 1f)
    val alpha = (1f - fade * fade) * enter

    Canvas(modifier = Modifier.fillMaxSize()) {
        val wordWidth = layout.size.width.toFloat()
        val wordHeight = layout.size.height.toFloat()
        val left = (size.width - wordWidth) / 2f
        val top = (size.height - wordHeight) / 2f
        val nibX = left + wordWidth * writing
        val nibY = top + wordHeight * 0.82f

        clipRect(
            left = left - wordWidth,
            top = top - wordHeight,
            right = nibX,
            bottom = top + wordHeight + wordHeight
        ) {
            drawText(textLayoutResult = layout, color = tint, topLeft = Offset(left, top), alpha = alpha)
        }

        if (alpha > 0.01f) {
            drawPenNib(x = nibX, y = nibY, tint = tint, alpha = alpha)
        }
    }
}

private fun DrawScope.drawPenNib(x: Float, y: Float, tint: Color, alpha: Float) {
    val tip = Offset(x, y)
    withTransform({ rotate(degrees = -32f, pivot = tip) }) {
        val body = Path().apply {
            moveTo(tip.x - 10f, tip.y - 34f)
            lineTo(tip.x + 10f, tip.y - 34f)
            lineTo(tip.x + 7f, tip.y - 104f)
            lineTo(tip.x - 7f, tip.y - 104f)
            close()
        }
        drawPath(body, color = tint.copy(alpha = 0.92f * alpha))
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.35f * alpha),
            topLeft = Offset(tip.x - 10f, tip.y - 46f),
            size = Size(20f, 12f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        val nib = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - 9f, tip.y - 34f)
            lineTo(tip.x + 9f, tip.y - 34f)
            close()
        }
        drawPath(nib, color = tint.copy(alpha = 0.75f * alpha))
        drawPath(nib, color = Color.Black.copy(alpha = 0.35f * alpha), style = Stroke(width = 1.6f))
    }
}
