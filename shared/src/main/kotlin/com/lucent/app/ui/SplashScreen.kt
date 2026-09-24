package com.lucent.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.SplashStyle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

object AppReady {
    var databaseReady by mutableStateOf(false)
}

@Composable
fun LucentSplash(
    paletteColors: List<Color>,
    backdropColor: Color,
    onFinished: () -> Unit,
    backgroundAnimated: Boolean = true,
    style: SplashStyle = SplashStyle.DEFAULT
) {
    val onGradient = LocalOnGradient.current
    val inspection = LocalInspectionMode.current
    val totalMs = if (style == SplashStyle.PEN) PEN_TOTAL_MS else TOTAL_MS

    var done by remember { mutableStateOf(false) }
    fun finish() {
        if (!done) {
            done = true
            onFinished()
        }
    }

    var elapsed by remember { mutableFloatStateOf(0f) }
    if (!inspection) {
        LaunchedEffect(Unit) {
            val totalNanos = (totalMs * 1_000_000f).toLong()
            val start = withInfiniteAnimationFrameNanos { it }
            var now = start
            while (now - start < totalNanos) {
                now = withInfiniteAnimationFrameNanos { it }
                elapsed = (now - start) / 1_000_000f
            }
        }
    }

    LaunchedEffect(Unit) {
        if (inspection) return@LaunchedEffect
        delay(totalMs.toLong())
        finish()
    }

    BackHandler(enabled = true) { finish() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { })
            }
    ) {
        SplashBackground(
            palette = paletteColors,
            backdropColor = backdropColor,
            animated = backgroundAnimated,
            modifier = Modifier.fillMaxSize()
        )

        if (style == SplashStyle.PEN) {
            PenSplashArtwork(elapsedMs = elapsed, tint = paletteColors.firstOrNull() ?: onGradient)
        } else {
            val t = elapsed
            val enter = (t / ENTER_MS).coerceIn(0f, 1f)
            val enterEased = 1f - (1f - enter) * (1f - enter)
            val overshoot = sin(enter * PI.toFloat()) * 0.06f
            val scaleIn = 0.62f + 0.38f * enterEased + overshoot

            val waveT = ((t - ENTER_MS) / (WAVE_END_MS - ENTER_MS)).coerceIn(0f, 1f)
            val waveDeg = sin(waveT * WAVE_CYCLES * 2f * PI.toFloat()) * WAVE_AMP_DEG * (1f - waveT * 0.3f)

            val blinkT = ((t - BLINK_START_MS) / (BLINK_END_MS - BLINK_START_MS)).coerceIn(0f, 1f)
            val blinking = t in BLINK_START_MS..BLINK_END_MS
            val eyeOpen = if (t < BLINK_START_MS) 1f else 1f - sin(blinkT * PI.toFloat())
            val blinkSquash = if (blinking) sin(blinkT * PI.toFloat()) * 0.03f else 0f

            val glass = ((t - MORPH_START_MS) / (MORPH_END_MS - MORPH_START_MS)).coerceIn(0f, 1f)
            val glassEased = glass * glass * (3f - 2f * glass)
            val wobble = sin(glass * PI.toFloat()) * 0.055f * sin(t / 90f)

            val fishBob = sin(t / FISH_BOB_MS) * FISH_BOB_AMP * (1f - glassEased)

            val exit = ((t - EXIT_START_MS) / (TOTAL_MS - EXIT_START_MS)).coerceIn(0f, 1f)
            val exitEased = exit * exit
            val alpha = (1f - exitEased).coerceIn(0f, 1f) * enterEased.coerceAtLeast(0.001f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val unit = size.minDimension / 780f
                val cx = size.width / 2f
                val cy = size.height / 2f - 36f * unit - exitEased * 90f * unit

                withTransform({
                    translate(left = cx, top = cy)
                    scale(
                        scaleX = unit * scaleIn * (1f + wobble),
                        scaleY = unit * scaleIn * (1f - wobble - blinkSquash),
                        pivot = Offset.Zero
                    )
                }) {
                    drawCat(
                        glass = glassEased,
                        alpha = alpha,
                        waveDeg = waveDeg,
                        eyeOpen = eyeOpen,
                        fishBob = fishBob,
                        tint = paletteColors.firstOrNull() ?: Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 300.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Lucent",
                    color = onGradient.copy(alpha = glassEased * (1f - exitEased) * 0.95f),
                    fontSize = 30.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Text(
            text = com.lucent.app.i18n.S.skipAnimation,
            color = onGradient.copy(alpha = skipLabelAlpha(style, elapsed, totalMs) * 0.6f),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .splashTopInset()
                .padding(top = 10.dp, end = 18.dp)
                .clickable { finish() }
                .padding(6.dp)
        )
    }
}

private fun skipLabelAlpha(style: SplashStyle, elapsedMs: Float, totalMs: Float): Float {
    val fadeStart = if (style == SplashStyle.PEN) PEN_HOLD_END_MS else EXIT_START_MS
    val fade = ((elapsedMs - fadeStart) / (totalMs - fadeStart)).coerceIn(0f, 1f)
    return (1f - fade * fade).coerceIn(0f, 1f)
}

private val Fur = Color(0xFFFFFDFA)
private val Line = Color(0xFF8C7276)
private val EyeColor = Color(0xFF4E3B40)
private val BlushPink = Color(0xFFF6B8CE)
private val PawPadPink = Color(0xFFF2A2C0)
private val WhiskerPink = Color(0xFFF0A6BE)
private val MouthPink = Color(0xFFEE8FB0)
private val BellyCream = Color(0xFFF7EFC8)
private val FishFill = Color(0xFFDFF0FC)
private val FishLine = Color(0xFF8FB4D8)
private val FishEye = Color(0xFF4A7BAA)

private fun DrawScope.drawCat(
    glass: Float,
    alpha: Float,
    waveDeg: Float,
    eyeOpen: Float,
    fishBob: Float,
    tint: Color
) {
    if (alpha <= 0.001f) return

    val solid = (1f - glass) * alpha
    val glassy = glass * alpha


    val head = Path().apply {
        addRoundRect(RoundRect(Rect(-130f, -152f, 130f, 28f), CornerRadius(82f, 82f)))
    }
        .union(earPath(-1f)).union(earPath(1f))
        .union(circlePath(-98f, -4f, 45f)).union(circlePath(98f, -4f, 45f))

    val body = Path().apply {
        addRoundRect(RoundRect(Rect(-120f, 0f, 120f, 210f), CornerRadius(68f, 68f)))
    }

    val pawHalfWidth = 29f
    val pawHalfHeight = 57f
    val pawSize = Size(pawHalfWidth * 2f, pawHalfHeight * 2f)
    val pawCorner = CornerRadius(pawHalfWidth, pawHalfWidth)
    val pawL = Offset(-78f, 96f)
    val pawR = Offset(78f, 96f)

    val fy = -180f + fishBob
    val fish = Path().apply {
        addOval(Rect(-40f, fy - 38f, 52f, fy + 38f))
    }.union(fishPetal(fy, -1f)).union(fishPetal(fy, 1f))

    val tail = Path().apply {
        moveTo(-110f, 180f)
        cubicTo(-128f, 192f, -142f, 192f, -149f, 182f)
        cubicTo(-155f, 173f, -153f, 160f, -146f, 154f)
    }

    if (solid > 0.002f) {
        drawPath(tail, Line.copy(alpha = solid), style = Stroke(width = 19f, cap = StrokeCap.Round))
        drawPath(tail, Fur.copy(alpha = solid), style = Stroke(width = 11f, cap = StrokeCap.Round))

        drawPath(body, Fur.copy(alpha = solid))
        drawPath(body, Line.copy(alpha = solid), style = Stroke(width = 7f))
        clipPath(body) { blush(Offset(0f, 200f), 70f, 0.43f * solid) }

        val bellyRect = Rect(-44f, 86f, 44f, 146f)
        val bellyPath = Path().apply { addRoundRect(RoundRect(bellyRect, CornerRadius(26f, 26f))) }
        drawPath(bellyPath, BellyCream.copy(alpha = solid))
        drawPath(bellyPath, Line.copy(alpha = solid), style = Stroke(width = 4f))

        for (i in 0..1) {
            val side = if (i == 0) -1f else 1f
            val c = if (i == 0) pawL else pawR
            val bottomPivot = Offset(c.x, c.y + pawHalfHeight)
            withTransform({ rotate(degrees = waveDeg * -side, pivot = bottomPivot) }) {
                val topLeft = Offset(c.x - pawHalfWidth, c.y - pawHalfHeight)
                drawRoundRect(Fur.copy(alpha = solid), topLeft, pawSize, pawCorner)
                drawRoundRect(Line.copy(alpha = solid), topLeft, pawSize, pawCorner, style = Stroke(width = 7f))
                blush(Offset(c.x, c.y - 38f), 30f, 0.53f * solid)
                drawOval(PawPadPink.copy(alpha = solid), Offset(c.x - 11f, c.y - 38f), Size(22f, 16f))
                drawOval(PawPadPink.copy(alpha = solid), Offset(c.x - 17.25f, c.y - 50.5f), Size(8.5f, 10f))
                drawOval(PawPadPink.copy(alpha = solid), Offset(c.x - 4.25f, c.y - 54.5f), Size(8.5f, 10f))
                drawOval(PawPadPink.copy(alpha = solid), Offset(c.x + 8.75f, c.y - 50.5f), Size(8.5f, 10f))
            }
        }

        drawPath(head, Fur.copy(alpha = solid))
        drawPath(head, Line.copy(alpha = solid), style = Stroke(width = 7f))
        clipPath(head) {
            for (i in 0..1) {
                val side = if (i == 0) -1f else 1f
                blush(Offset(side * 92f, -152f), 30f, 0.49f * solid)
                blush(Offset(side * 100f, -6f), 54f, 0.65f * solid)
            }
        }

        drawPath(fish, FishFill.copy(alpha = solid))
        drawPath(fish, FishLine.copy(alpha = solid), style = Stroke(width = 5f))
        drawCircle(FishEye.copy(alpha = solid), 5f, Offset(32f, fy - 7f))
        drawArc(
            FishLine.copy(alpha = solid), -55f, 110f, false,
            Offset(4f, fy - 13f), Size(20f, 26f), style = Stroke(width = 3.2f, cap = StrokeCap.Round)
        )
        drawCircle(FishLine.copy(alpha = solid), 5.5f, Offset(-4f, fy + 7f), style = Stroke(width = 2.6f))

        for (i in 0..1) {
            val side = if (i == 0) -1f else 1f
            drawLine(WhiskerPink.copy(alpha = solid), Offset(side * 102f, -34f), Offset(side * 138f, -42f), 5.5f, StrokeCap.Round)
            drawLine(WhiskerPink.copy(alpha = solid), Offset(side * 102f, -20f), Offset(side * 138f, -20f), 5.5f, StrokeCap.Round)
            drawLine(WhiskerPink.copy(alpha = solid), Offset(side * 102f, -6f), Offset(side * 138f, 0f), 5.5f, StrokeCap.Round)
        }

        for (i in 0..1) {
            val side = if (i == 0) -1f else 1f
            val ex = side * 48f
            if (eyeOpen > 0.22f) {
                val eh = 9.5f * eyeOpen
                drawOval(EyeColor.copy(alpha = solid), Offset(ex - 9.5f, -34f - eh), Size(19f, eh * 2f))
            } else {
                drawArc(
                    EyeColor.copy(alpha = solid), 205f, 130f, false,
                    Offset(ex - 10f, -39f), Size(20f, 14f), style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }
        }

        drawArc(
            MouthPink.copy(alpha = solid), 10f, 160f, false,
            Offset(-18f, -26f), Size(20f, 16f), style = Stroke(width = 6.5f, cap = StrokeCap.Round)
        )
        drawArc(
            MouthPink.copy(alpha = solid), 10f, 160f, false,
            Offset(-2f, -26f), Size(20f, 16f), style = Stroke(width = 6.5f, cap = StrokeCap.Round)
        )
    }

    if (glassy > 0.002f) {
        drawPath(tail, Color.White.copy(alpha = glassy * 0.55f), style = Stroke(width = 9f, cap = StrokeCap.Round))

        val panes = listOf(body, head, fish)
        for (pane in panes) {
            drawPath(pane, Color.White.copy(alpha = glassy * 0.16f))
            drawPath(pane, tint.copy(alpha = glassy * 0.20f))
            drawPath(pane, Color.White.copy(alpha = glassy * 0.75f), style = Stroke(width = 3f))
        }
        for (i in 0..1) {
            val side = if (i == 0) -1f else 1f
            val c = if (i == 0) pawL else pawR
            val bottomPivot = Offset(c.x, c.y + pawHalfHeight)
            withTransform({ rotate(degrees = waveDeg * -side, pivot = bottomPivot) }) {
                val topLeft = Offset(c.x - pawHalfWidth, c.y - pawHalfHeight)
                drawRoundRect(Color.White.copy(alpha = glassy * 0.16f), topLeft, pawSize, pawCorner)
                drawRoundRect(tint.copy(alpha = glassy * 0.20f), topLeft, pawSize, pawCorner)
                drawRoundRect(Color.White.copy(alpha = glassy * 0.75f), topLeft, pawSize, pawCorner, style = Stroke(width = 3f))
            }
        }

        drawPath(
            path = head,
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = glassy * 0.28f),
                1f to Color.Transparent,
                startY = -152f,
                endY = -20f
            )
        )
        drawPath(
            path = body,
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = glassy * 0.28f),
                1f to Color.Transparent,
                startY = 0f,
                endY = 110f
            )
        )
    }
}

private fun DrawScope.blush(center: Offset, radius: Float, alpha: Float) {
    if (alpha <= 0.004f) return
    drawCircle(
        brush = Brush.radialGradient(
            0f to BlushPink.copy(alpha = alpha),
            0.55f to BlushPink.copy(alpha = alpha * 0.5f),
            1f to Color.Transparent,
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

private fun fishPetal(fy: Float, sg: Float): Path = Path().apply {
    moveTo(-32f, fy + sg * 10f)
    cubicTo(-46f, fy + sg * 20f, -58f, fy + sg * 28f, -68f, fy + sg * 24f)
    cubicTo(-74f, fy + sg * 20f, -74f, fy + sg * 14f, -66f, fy + sg * 7f)
    cubicTo(-59f, fy + sg * 2f, -50f, fy, -32f, fy)
    close()
}

private fun circlePath(cx: Float, cy: Float, r: Float): Path =
    Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }

private fun earPath(sg: Float): Path = Path().apply {
    moveTo(sg * 56f, -138f)
    lineTo(sg * 90.3f, -180.1f)
    cubicTo(sg * 96.8f, -188f, sg * 101.3f, -185.4f, sg * 104f, -172.2f)
    lineTo(sg * 118f, -102f)
    close()
}

private fun Path.union(other: Path): Path = Path.combine(PathOperation.Union, this, other)

private const val SPEED = 0.615f
private const val TOTAL_MS = 7700f * SPEED
private const val ENTER_MS = 700f * SPEED
private const val WAVE_END_MS = 2600f * SPEED
private const val BLINK_START_MS = 2600f * SPEED
private const val BLINK_END_MS = 3300f * SPEED
private const val MORPH_START_MS = 3300f * SPEED
private const val MORPH_END_MS = 6600f * SPEED
private const val EXIT_START_MS = 6600f * SPEED
private const val WAVE_CYCLES = 3.5f
private const val WAVE_AMP_DEG = 14f
private const val FISH_BOB_MS = 260f
private const val FISH_BOB_AMP = 2.2f
