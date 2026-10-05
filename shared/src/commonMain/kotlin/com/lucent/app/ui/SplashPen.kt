package com.lucent.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.PI
import kotlin.math.sin

internal const val PEN_WRITE_START_MS = 260f
internal const val PEN_WRITE_END_MS = 3000f
internal const val PEN_HOLD_END_MS = 3150f
internal const val PEN_LIFT_MS = 3560f
internal const val PEN_TOTAL_MS = 4300f

private const val PEN_WORD = "Lucent"
private const val PEN_FONT_SP = 64f
private const val PEN_TILT_DEG = 34f
private const val PEN_LEAD_PX = 3f

@Composable
internal fun PenSplashArtwork(elapsed: MutableFloatState, tint: Color) {
    val measurer = rememberTextMeasurer()
    val script = splashScriptFont()
    val word = remember(measurer, script) {
        measurer.measure(
            text = AnnotatedString(PEN_WORD),
            style = TextStyle(
                fontSize = PEN_FONT_SP.sp,
                fontFamily = script ?: FontFamily.Cursive,
                fontStyle = FontStyle.Italic
            )
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = elapsed.floatValue
        val enter = (t / PEN_WRITE_START_MS).coerceIn(0f, 1f)
        val raw = ((t - PEN_WRITE_START_MS) / (PEN_WRITE_END_MS - PEN_WRITE_START_MS)).coerceIn(0f, 1f)
        val writing = raw * raw * (3f - 2f * raw)
        val lift = ((t - PEN_HOLD_END_MS) / (PEN_LIFT_MS - PEN_HOLD_END_MS)).coerceIn(0f, 1f)
        val liftEased = lift * lift
        val fade = ((t - PEN_LIFT_MS) / (PEN_TOTAL_MS - PEN_LIFT_MS)).coerceIn(0f, 1f)
        val inkAlpha = (1f - fade * fade) * enter
        val penAlpha = enter * (1f - liftEased)
        if (inkAlpha <= 0.002f && penAlpha <= 0.002f) return@Canvas

        val wordWidth = word.size.width.toFloat().coerceAtLeast(1f)
        val wordHeight = word.size.height.toFloat().coerceAtLeast(1f)
        val fit = (size.width * 0.76f / wordWidth).coerceIn(0.3f, 2.2f)
        val drawWidth = wordWidth * fit
        val drawHeight = wordHeight * fit
        val left = (size.width - drawWidth) / 2f
        val top = (size.height - drawHeight) / 2f
        val baseline = wordHeight * 0.72f
        val bob = sin(raw * 8f * PI.toFloat()) * wordHeight * 0.03f
        val sway = sin(raw * 5f * PI.toFloat()) * wordHeight * 0.012f
        val penX = wordWidth * writing
        val penY = baseline + bob
        val liftX = liftEased * wordHeight * 0.14f
        val liftY = liftEased * wordHeight * 0.3f
        val tilt = PEN_TILT_DEG + sin(raw * 7f * PI.toFloat()) * 3f

        withTransform({
            translate(left = left, top = top)
            scale(scaleX = fit, scaleY = fit, pivot = Offset.Zero)
        }) {
            clipRect(
                left = -wordWidth,
                top = -wordHeight,
                right = penX + PEN_LEAD_PX,
                bottom = wordHeight * 2f
            ) {
                drawText(
                    textLayoutResult = word,
                    color = tint,
                    topLeft = Offset.Zero,
                    alpha = inkAlpha
                )
            }
            if (penAlpha > 0.002f) {
                drawPenNib(
                    x = penX + liftX + sway,
                    y = penY - liftY,
                    tilt = tilt,
                    alpha = penAlpha
                )
            }
        }
    }
}

private fun DrawScope.drawPenNib(x: Float, y: Float, tilt: Float, alpha: Float) {
    val tip = Offset(x, y)
    withTransform({ rotate(degrees = tilt, pivot = tip) }) {
        val barrel = Path().apply {
            moveTo(tip.x - 10f, tip.y - 30f)
            lineTo(tip.x + 12f, tip.y - 34f)
            lineTo(tip.x + 17f, tip.y - 108f)
            lineTo(tip.x - 5f, tip.y - 102f)
            close()
        }
        drawPath(barrel, color = Barrel.copy(alpha = alpha))
        drawPath(barrel, color = BarrelRim.copy(alpha = 0.85f * alpha), style = Stroke(width = 1.6f))

        val grip = Path().apply {
            moveTo(tip.x - 9f, tip.y - 30f)
            lineTo(tip.x + 11f, tip.y - 34f)
            lineTo(tip.x + 13f, tip.y - 56f)
            lineTo(tip.x - 7f, tip.y - 52f)
            close()
        }
        drawPath(grip, color = Grip.copy(alpha = alpha))

        val nib = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - 13f, tip.y - 28f)
            lineTo(tip.x + 12f, tip.y - 33f)
            close()
        }
        drawPath(nib, color = Nib.copy(alpha = alpha))
        drawPath(nib, color = BarrelRim.copy(alpha = 0.7f * alpha), style = Stroke(width = 1.4f))
        drawLine(
            color = BarrelRim.copy(alpha = 0.75f * alpha),
            start = Offset(tip.x, tip.y - 2f),
            end = Offset(tip.x + 4f, tip.y - 26f),
            strokeWidth = 1.6f
        )
        drawCircle(NibTip.copy(alpha = alpha), radius = 1.7f, center = tip)
    }
}

private val Barrel = Color(0xFF2B3242)
private val BarrelRim = Color(0xFF12161F)
private val Grip = Color(0xFF5B6472)
private val Nib = Color(0xFFD9DEE8)
private val NibTip = Color(0xFF1B2029)
