package com.lucent.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

internal const val AVATAR_SPEED = 0.615f
internal const val AVATAR_TOTAL_MS = 7700f * AVATAR_SPEED
internal const val AVATAR_ENTER_MS = 700f * AVATAR_SPEED
internal const val AVATAR_WAVE_END_MS = 2600f * AVATAR_SPEED
internal const val AVATAR_BLINK_START_MS = 2600f * AVATAR_SPEED
internal const val AVATAR_BLINK_END_MS = 3300f * AVATAR_SPEED
internal const val AVATAR_MORPH_START_MS = 3300f * AVATAR_SPEED
internal const val AVATAR_MORPH_END_MS = 6600f * AVATAR_SPEED
internal const val AVATAR_EXIT_START_MS = 6600f * AVATAR_SPEED
internal const val AVATAR_WAVE_CYCLES = 3.5f
internal const val AVATAR_WAVE_AMP_DEG = 14f

private val AvatarFur = Color(0xFFE78B93)
private val AvatarLine = Color(0xFFB25A63)
private val AvatarFace = Color(0xFFFFF4D9)
private val AvatarEye = Color(0xFF262626)
private val AvatarSmile = Color(0xFF7A4B52)
private val AvatarBlush = Color(0xFFF4A9BC)

private class AvatarArt {
    val blushLeft: Brush = Brush.radialGradient(
        0f to AvatarBlush,
        0.6f to AvatarBlush.copy(alpha = 0.45f),
        1f to Color.Transparent,
        center = Offset(-88f, -58f),
        radius = 30f
    )
    val blushRight: Brush = Brush.radialGradient(
        0f to AvatarBlush,
        0.6f to AvatarBlush.copy(alpha = 0.45f),
        1f to Color.Transparent,
        center = Offset(88f, -58f),
        radius = 30f
    )
    val bodyGlass: Brush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.28f),
        1f to Color.Transparent,
        startY = -160f,
        endY = 200f
    )
    val faceGlass: Brush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.28f),
        1f to Color.Transparent,
        startY = -138f,
        endY = 12f
    )
}

@Composable
private fun avatarArt(): AvatarArt = remember { AvatarArt() }

@Composable
internal fun AvatarSplashArtwork(elapsed: MutableFloatState, tint: Color, onGradient: Color) {
    val measurer = rememberTextMeasurer()
    val art = avatarArt()
    val textStyle = androidx.compose.material3.LocalTextStyle.current
    val word = remember(measurer, textStyle) {
        measurer.measure(
            text = AnnotatedString("Lucent"),
            style = textStyle.copy(fontSize = 30.sp)
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = elapsed.floatValue
        val enter = (t / AVATAR_ENTER_MS).coerceIn(0f, 1f)
        val enterEased = 1f - (1f - enter) * (1f - enter)
        val overshoot = sin(enter * PI.toFloat()) * 0.06f
        val scaleIn = 0.62f + 0.38f * enterEased + overshoot

        val waveT = ((t - AVATAR_ENTER_MS) / (AVATAR_WAVE_END_MS - AVATAR_ENTER_MS)).coerceIn(0f, 1f)
        val waveDeg = sin(waveT * AVATAR_WAVE_CYCLES * 2f * PI.toFloat()) * AVATAR_WAVE_AMP_DEG * (1f - waveT * 0.3f)

        val blinkT = ((t - AVATAR_BLINK_START_MS) / (AVATAR_BLINK_END_MS - AVATAR_BLINK_START_MS)).coerceIn(0f, 1f)
        val eyeOpen = if (t < AVATAR_BLINK_START_MS) 1f else 1f - sin(blinkT * PI.toFloat())

        val glass = ((t - AVATAR_MORPH_START_MS) / (AVATAR_MORPH_END_MS - AVATAR_MORPH_START_MS)).coerceIn(0f, 1f)
        val glassEased = glass * glass * (3f - 2f * glass)
        val wobble = sin(glass * PI.toFloat()) * 0.04f * sin(t / 90f)

        val exit = ((t - AVATAR_EXIT_START_MS) / (AVATAR_TOTAL_MS - AVATAR_EXIT_START_MS)).coerceIn(0f, 1f)
        val exitEased = exit * exit
        val alpha = (1f - exitEased).coerceIn(0f, 1f) * enterEased.coerceAtLeast(0.001f)

        val unit = size.minDimension / 780f
        val cx = size.width / 2f
        val cy = size.height / 2f - 36f * unit - exitEased * 90f * unit

        withTransform({
            translate(left = cx, top = cy)
            scale(
                scaleX = unit * scaleIn * (1f + wobble),
                scaleY = unit * scaleIn * (1f - wobble),
                pivot = Offset.Zero
            )
        }) {
            drawAvatar(
                art = art,
                glass = glassEased,
                alpha = alpha,
                waveDeg = waveDeg,
                eyeOpen = eyeOpen,
                tint = tint
            )
        }

        val wordAlpha = glassEased * (1f - exitEased) * 0.95f
        if (wordAlpha > 0.004f) {
            val left = (size.width - word.size.width) / 2f
            val top = size.height / 2f - word.size.height / 2f + 300.dp.toPx()
            drawText(
                textLayoutResult = word,
                color = onGradient,
                topLeft = Offset(left, top),
                alpha = wordAlpha
            )
        }
    }
}

private fun DrawScope.drawAvatar(
    art: AvatarArt,
    glass: Float,
    alpha: Float,
    waveDeg: Float,
    eyeOpen: Float,
    tint: Color
) {
    if (alpha <= 0.001f) return

    val solid = (1f - glass) * alpha
    val glassy = glass * alpha

    val bodyRect = Rect(-150f, -160f, 150f, 200f)
    val bodyCorner = CornerRadius(140f, 140f)
    val faceRect = Rect(-112f, -138f, 112f, 12f)
    val faceCorner = CornerRadius(62f, 62f)
    val footLeft = Rect(-105f, 168f, -45f, 232f)
    val footRight = Rect(45f, 168f, 105f, 232f)
    val footCorner = CornerRadius(30f, 30f)

    if (solid > 0.002f) {
        drawRoundRect(AvatarFur.copy(alpha = solid), footLeft.topLeft, footLeft.size, footCorner)
        drawRoundRect(AvatarFur.copy(alpha = solid), footRight.topLeft, footRight.size, footCorner)
        drawRoundRect(AvatarLine.copy(alpha = solid), footLeft.topLeft, footLeft.size, footCorner, style = Stroke(width = 6f))
        drawRoundRect(AvatarLine.copy(alpha = solid), footRight.topLeft, footRight.size, footCorner, style = Stroke(width = 6f))

        withTransform({ rotate(degrees = waveDeg * -0.35f, pivot = Offset(-148f, 30f)) }) {
            val arm = Rect(-200f, -8f, -146f, 118f)
            val corner = CornerRadius(24f, 24f)
            drawRoundRect(AvatarFur.copy(alpha = solid), arm.topLeft, arm.size, corner)
            drawRoundRect(AvatarLine.copy(alpha = solid), arm.topLeft, arm.size, corner, style = Stroke(width = 6f))
        }
        withTransform({ rotate(degrees = waveDeg, pivot = Offset(148f, 28f)) }) {
            val arm = Rect(124f, -132f, 176f, 32f)
            val corner = CornerRadius(24f, 24f)
            drawRoundRect(AvatarFur.copy(alpha = solid), arm.topLeft, arm.size, corner)
            drawRoundRect(AvatarLine.copy(alpha = solid), arm.topLeft, arm.size, corner, style = Stroke(width = 6f))
        }

        drawRoundRect(AvatarFur.copy(alpha = solid), bodyRect.topLeft, bodyRect.size, bodyCorner)
        drawRoundRect(AvatarLine.copy(alpha = solid), bodyRect.topLeft, bodyRect.size, bodyCorner, style = Stroke(width = 7f))

        drawArc(AvatarLine.copy(alpha = solid), 225f, 90f, false, Offset(-64f, -190f), Size(28f, 24f), style = Stroke(width = 5f, cap = StrokeCap.Round))
        drawArc(AvatarLine.copy(alpha = solid), 225f, 90f, false, Offset(-14f, -196f), Size(28f, 24f), style = Stroke(width = 5f, cap = StrokeCap.Round))
        drawArc(AvatarLine.copy(alpha = solid), 225f, 90f, false, Offset(36f, -190f), Size(28f, 24f), style = Stroke(width = 5f, cap = StrokeCap.Round))

        drawRoundRect(AvatarFace.copy(alpha = solid), faceRect.topLeft, faceRect.size, faceCorner)

        drawCircle(brush = art.blushLeft, radius = 30f, center = Offset(-88f, -58f), alpha = solid)
        drawCircle(brush = art.blushRight, radius = 30f, center = Offset(88f, -58f), alpha = solid)

        for (i in 0..1) {
            val side = if (i == 0) -1f else 1f
            val ex = side * 52f
            if (eyeOpen > 0.22f) {
                val eh = 14f * eyeOpen
                drawOval(AvatarEye.copy(alpha = solid), Offset(ex - 11f, -92f - eh), Size(22f, eh * 2f))
            } else {
                drawArc(
                    AvatarEye.copy(alpha = solid), 200f, 140f, false,
                    Offset(ex - 11f, -98f), Size(22f, 14f), style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }
        }

        drawArc(
            AvatarSmile.copy(alpha = solid), 25f, 130f, false,
            Offset(-24f, -64f), Size(48f, 32f), style = Stroke(width = 6f, cap = StrokeCap.Round)
        )
    }

    if (glassy > 0.002f) {
        val panes = listOf(
            Triple(bodyRect.topLeft, bodyRect.size, bodyCorner),
            Triple(faceRect.topLeft, faceRect.size, faceCorner)
        )
        for ((topLeft, paneSize, corner) in panes) {
            drawRoundRect(Color.White.copy(alpha = glassy * 0.16f), topLeft, paneSize, corner)
            drawRoundRect(tint.copy(alpha = glassy * 0.20f), topLeft, paneSize, corner)
            drawRoundRect(Color.White.copy(alpha = glassy * 0.75f), topLeft, paneSize, corner, style = Stroke(width = 3f))
        }
        drawRoundRect(brush = art.bodyGlass, topLeft = bodyRect.topLeft, size = bodyRect.size, cornerRadius = bodyCorner, alpha = glassy)
        drawRoundRect(brush = art.faceGlass, topLeft = faceRect.topLeft, size = faceRect.size, cornerRadius = faceCorner, alpha = glassy)
    }
}
