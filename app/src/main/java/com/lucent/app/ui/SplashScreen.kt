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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Startup readiness, shared between [com.lucent.app.MainActivity] and whatever is waiting on it.
 *
 * Exactly one flag, and it exists because building the encrypted database is expensive enough that
 * it cannot happen on the main thread, yet the screens all ask for it the moment they compose. The
 * background build flips this when the database is genuinely open, and composition of the heavy
 * content waits for it — which is what keeps the first frame free to be the splash, and only the
 * splash.
 *
 * A plain global rather than anything cleverer: it is process-scoped state with exactly one writer,
 * and it must survive an Activity recreation (the database does).
 */
object AppReady {
    var databaseReady by mutableStateOf(false)
}

/**
 * The launch animation: a little cat waves hello, gives a playful blink, then turns into liquid glass.
 *
 * ### What this is actually for
 *
 * Cold start spends a moment doing real work — reading the saved theme, palette, font and lock state
 * from disk *before* the first frame (so the app never flashes the wrong colours), then composing a
 * large UI. Until now that moment was a flat, empty window: nothing was wrong, but nothing said so
 * either, and a blank screen always reads as "stuck" rather than "starting".
 *
 * So this fills it with something. Crucially it fills it *without adding to it*: the splash is drawn
 * **over** the real app, which composes underneath at the same time. The waiting that was already
 * happening now happens behind an animation, and by the time the cat fades the app behind it is
 * built and ready.
 *
 * ### The animation
 *
 * About seven and a half seconds, in five movements:
 *
 *  1. **Arrive** (0–0.7s) — the cat pops in with a slight overshoot, the way something alive enters.
 *  2. **Wave** (0.7–2.6s) — its two round paws swing about their shoulders, a cheeky little "hello";
 *     they mirror each other so the greeting reads as two-pawed and symmetric.
 *  3. **Blink** (2.6–3.3s) — a single playful blink: the eyes squeeze shut into happy little arcs and
 *     spring back open, with a tiny squash for character. This lands after the wave and before the
 *     glass, so the cat is unmistakably *itself* right before it transforms.
 *  4. **Become glass** (3.3–6.6s) — the solid cat cross-fades into the app's own material: a
 *     translucent pane tinted by the live palette, a bright rim, and a specular highlight that sweeps
 *     across it as it changes. It also *wobbles* — squashing and stretching, strongest at the
 *     midpoint and settling to nothing — which is what makes it read as having briefly turned to
 *     liquid rather than simply having faded. This is the part worth lingering on, so it gets the
 *     largest share of the running time.
 *  5. **Leave** (6.6–7.7s) — it floats up and dissolves, and the app is already there behind it.
 *
 * The cat is drawn with plain Canvas primitives — no image asset, no vector drawable — so it is a
 * few hundred bytes of code, scales to any screen without a folder of densities, and can be morphed
 * arbitrarily, which is the entire trick in movement 4.
 *
 * ### Getting out of it
 *
 * Seven-odd seconds is generous the first time and long the hundredth, so **the top-right "Skip"
 * control, or a back press, finishes it immediately** — and the app behind is already composed, so
 * skipping is instant rather than a shortcut into more waiting. A stray tap anywhere else does
 * nothing, so the animation can't be cut short by accident. Completion is also driven by a plain
 * `delay`, not by the frame clock that drives the drawing: if that clock were ever starved the cat
 * would freeze, and a frozen splash that never ends is an app that never starts. The animation may
 * stall; the launch may not.
 */
@Composable
fun LucentSplash(
    paletteColors: List<Color>,
    backdropColor: Color,
    onFinished: () -> Unit,
    // Whether the drifting blob background animates behind the cat. Passed straight through to
    // [FluidGlassBackground] so the splash obeys the SAME "drifting background" setting the app
    // does: off in Settings means off here too, from the very first frame — never blobs during the
    // cat and stillness after it.
    backgroundAnimated: Boolean = true
) {
    val onGradient = LocalOnGradient.current
    val inspection = LocalInspectionMode.current

    // Guards against the tap, the back press and the timer all racing to finish the same splash.
    var done by remember { mutableStateOf(false) }
    fun finish() {
        if (!done) {
            done = true
            onFinished()
        }
    }

    // Elapsed milliseconds, from a single frame clock — the same approach the drifting background
    // uses, and for the same reason: one snapshot read per frame instead of a dozen animation objects.
    var elapsed by remember { mutableFloatStateOf(0f) }
    if (!inspection) {
        LaunchedEffect(Unit) {
            val totalNanos = (TOTAL_MS * 1_000_000f).toLong()
            val start = withInfiniteAnimationFrameNanos { it }
            var now = start
            while (now - start < totalNanos) {
                now = withInfiniteAnimationFrameNanos { it }
                elapsed = (now - start) / 1_000_000f
            }
        }
    }

    // The authority on when the splash ends. Deliberately independent of the frame clock above.
    LaunchedEffect(Unit) {
        if (inspection) return@LaunchedEffect
        delay(TOTAL_MS.toLong())
        finish()
    }

    BackHandler(enabled = true) { finish() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Swallows every touch so nothing lands on the app composing underneath. It no longer
            // *finishes* on tap, though: skipping is now an explicit control (top-right), so a stray
            // tap can't cut the animation short by accident.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { })
            }
    ) {
        // The same living background the app itself uses, so the splash is the app arriving rather
        // than a separate screen shown in front of it — including whether it drifts at all, which
        // follows the user's "drifting background" setting exactly like the app behind it.
        FluidGlassBackground(
            palette = paletteColors,
            backdropColor = backdropColor,
            animated = backgroundAnimated,
            modifier = Modifier.fillMaxSize()
        )

        val t = elapsed
        // ---- Phase envelopes ----
        // Entrance: 0.62 -> 1.0 scale with a small overshoot, plus a fade in.
        val enter = (t / ENTER_MS).coerceIn(0f, 1f)
        val enterEased = 1f - (1f - enter) * (1f - enter)                   // ease-out
        val overshoot = sin(enter * PI.toFloat()) * 0.06f                    // brief bulge past 1.0
        val scaleIn = 0.62f + 0.38f * enterEased + overshoot

        // Wave: the paws swing about their shoulders, tapering off as the blink approaches.
        val waveT = ((t - ENTER_MS) / (WAVE_END_MS - ENTER_MS)).coerceIn(0f, 1f)
        val waveDeg = sin(waveT * WAVE_CYCLES * 2f * PI.toFloat()) * WAVE_AMP_DEG * (1f - waveT * 0.3f)

        // Blink: one playful close-and-open, sitting between the wave and the morph. eyeOpen runs
        // 1 -> 0 -> 1; blinkSquash gives the whole cat a tiny bounce while the eyes are shut.
        val blinkT = ((t - BLINK_START_MS) / (BLINK_END_MS - BLINK_START_MS)).coerceIn(0f, 1f)
        val blinking = t in BLINK_START_MS..BLINK_END_MS
        val eyeOpen = if (t < BLINK_START_MS) 1f else 1f - sin(blinkT * PI.toFloat())
        val blinkSquash = if (blinking) sin(blinkT * PI.toFloat()) * 0.03f else 0f

        // Morph: 0 = solid cat, 1 = glass cat.
        val glass = ((t - MORPH_START_MS) / (MORPH_END_MS - MORPH_START_MS)).coerceIn(0f, 1f)
        val glassEased = glass * glass * (3f - 2f * glass)
        // Liquid wobble: strongest halfway through the change, nothing at either end.
        val wobble = sin(glass * PI.toFloat()) * 0.055f * sin(t / 90f)

        // Exit: float up and dissolve.
        val exit = ((t - EXIT_START_MS) / (TOTAL_MS - EXIT_START_MS)).coerceIn(0f, 1f)
        val exitEased = exit * exit
        val alpha = (1f - exitEased).coerceIn(0f, 1f) * enterEased.coerceAtLeast(0.001f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val unit = size.minDimension / 470f
            val cx = size.width / 2f
            val cy = size.height / 2f - 30f * unit - exitEased * 90f * unit

            withTransform({
                translate(left = cx, top = cy)
                scale(
                    scaleX = unit * scaleIn * (1f + wobble),
                    scaleY = unit * scaleIn * (1f - wobble - blinkSquash),
                    pivot = Offset.Zero
                )
                // The paws swing about their own shoulders inside drawCat — a symmetric, two-pawed
                // "hello" — rather than the whole drawing rocking with the wave.
            }) {
                drawCat(
                    glass = glassEased,
                    alpha = alpha,
                    waveDeg = waveDeg,
                    eyeOpen = eyeOpen,
                    tint = paletteColors.firstOrNull() ?: Color.White
                )
            }
        }

        // The wordmark arrives with the glass, so the two land together.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 190.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Lucent",
                color = onGradient.copy(alpha = glassEased * (1f - exitEased) * 0.95f),
                fontSize = 30.sp,
                textAlign = TextAlign.Center
            )
        }

        // Skip control: a small line of text in the top-right, present from the very first frame (its
        // alpha does not depend on the animation's progress, only fading out as the splash itself
        // leaves) and tappable to end the splash at once. This replaces "tap anywhere to skip" — it is
        // deliberate and discoverable, and a stray tap elsewhere no longer cuts the animation short.
        Text(
            text = com.lucent.app.i18n.S.skipAnimation,
            color = onGradient.copy(alpha = (1f - exitEased) * 0.6f),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 10.dp, end = 18.dp)
                .clickable { finish() }
                .padding(6.dp)
        )
    }
}

/**
 * Draws the Lucent cat at the origin, in a space roughly 260 units wide.
 *
 * A cream marshmallow kitten with soft ROUNDED ears (never pointed), solid black eyes,
 * pink cheeks, a little ω nose, whiskers, a butter-yellow tummy, a curly tail and two round paws —
 * every part built from plain Canvas primitives (rounded rects, ovals, arcs, lines and a couple of
 * bezier paths), so it carries no image asset, scales to any screen and can be morphed arbitrarily,
 * which is the entire trick in the "become glass" phase.
 *
 * [glass] cross-fades between the drawn cat (0) and the glass one (1). The silhouette that turns to
 * glass is the body, ears and paws; the surface details (eyes, cheeks, nose, whiskers, tummy, tail,
 * inner ears, beans) fade out as the glass takes over, so the glass reads as the cat's *form* rather
 * than a cat wearing a painted face. [waveDeg] swings each paw about its shoulder, [eyeOpen] drives
 * the blink (1 = wide open, 0 = shut), and [tint] is the live palette colour the glass picks up.
 */
private fun DrawScope.drawCat(
    glass: Float,
    alpha: Float,
    waveDeg: Float,
    eyeOpen: Float,
    tint: Color
) {
    if (alpha <= 0.001f) return

    val solid = (1f - glass) * alpha    // fur, outline and every painted detail fade as glass arrives
    val glassy = glass * alpha          // the translucent glass rendition fades in over the top

    // Palette sampled straight from the reference art.
    val fur = Color(0xFFFFF6E2)     // warm cream body
    val line = Color(0xFF7A6560)    // soft "drawn" outline
    val eyeCol = Color(0xFF4A3C3A)  // eyes
    val inEar = Color(0xFFF0B4C4)   // inner ear
    val cheek = Color(0xFFF4BCC8)   // blush
    val belly = Color(0xFFFAEECA)   // butter-yellow tummy
    val nose = Color(0xFFD69292)    // nose
    val bean = Color(0xFFF2B6C6)    // paw beans

    // A small, soft ear — a low rounded dome with a broad, gently curved top, so there is no point
    // at all. Kept petite and wide so it reads cute rather than tall or pointed.
    // Built once per side and reused for both the fill pass and the outline pass.
    fun earPath(side: Float): Path = Path().apply {
        moveTo(side * 66f, -54f)                                       // outer base
        cubicTo(side * 71f, -78f, side * 63f, -89f, side * 51f, -90f)   // up the outer wall
        cubicTo(side * 44f, -91f, side * 37f, -91f, side * 30f, -90f)   // across a wide, rounded top
        cubicTo(side * 23f, -89f, side * 20f, -78f, side * 24f, -54f)   // down the inner wall
        close()
    }
    val earL = earPath(-1f)
    val earR = earPath(1f)

    // ---- Ears (drawn first; the body is drawn over their base for a clean, seamless join) ----
    for (i in 0..1) {
        val ear = if (i == 0) earL else earR
        if (solid > 0.002f) drawPath(ear, fur.copy(alpha = solid))
        if (glassy > 0.002f) {
            drawPath(ear, Color.White.copy(alpha = glassy * 0.16f))
            drawPath(ear, tint.copy(alpha = glassy * 0.20f))
        }
    }
    for (i in 0..1) {
        val side = if (i == 0) -1f else 1f
        val ear = if (i == 0) earL else earR
        if (solid > 0.002f) {
            drawOval(inEar.copy(alpha = solid), Offset(side * 45f - 11f, -83f), Size(22f, 24f))
            drawPath(ear, line.copy(alpha = solid), style = Stroke(width = 3f))
        }
        if (glassy > 0.002f) drawPath(ear, Color.White.copy(alpha = glassy * 0.7f), style = Stroke(width = 2.6f))
    }

    // ---- Tail: a curly bezier tucked behind the body ----
    val tail = Path().apply {
        moveTo(86f, 64f)
        cubicTo(158f, 66f, 150f, 6f, 112f, 20f)
    }
    if (solid > 0.002f) drawPath(tail, line.copy(alpha = solid), style = Stroke(width = 9f, cap = StrokeCap.Round))
    if (glassy > 0.002f) drawPath(tail, Color.White.copy(alpha = glassy * 0.6f), style = Stroke(width = 8f, cap = StrokeCap.Round))

    // ---- Body: a marshmallow rounded square, the mass everything else sits on ----
    val bodyTL = Offset(-94f, -65f)         // centre (0, 24), size 188 x 178
    val bodySize = Size(188f, 178f)
    val bodyR = CornerRadius(64f, 64f)
    if (solid > 0.002f) {
        drawRoundRect(fur.copy(alpha = solid), bodyTL, bodySize, bodyR)
        drawRoundRect(line.copy(alpha = solid), bodyTL, bodySize, bodyR, style = Stroke(width = 3.2f))
    }
    if (glassy > 0.002f) {
        drawRoundRect(Color.White.copy(alpha = glassy * 0.16f), bodyTL, bodySize, bodyR)
        drawRoundRect(tint.copy(alpha = glassy * 0.20f), bodyTL, bodySize, bodyR)
        drawRoundRect(Color.White.copy(alpha = glassy * 0.75f), bodyTL, bodySize, bodyR, style = Stroke(width = 2.8f))
        // A soft top sheen so the glass body catches the light.
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = glassy * 0.28f),
                0.6f to Color.Transparent
            ),
            topLeft = bodyTL,
            size = bodySize,
            cornerRadius = bodyR
        )
    }

    // ---- Tummy patch + two little feet (painted detail; fades under the glass) ----
    if (solid > 0.002f) {
        drawOval(belly.copy(alpha = solid), Offset(-47f, 55f), Size(94f, 50f))
        drawArc(line.copy(alpha = solid), 205f, 130f, false, Offset(-51f, 79f), Size(48f, 42f), style = Stroke(width = 4f, cap = StrokeCap.Round))
        drawArc(line.copy(alpha = solid), 205f, 130f, false, Offset(3f, 79f), Size(48f, 42f), style = Stroke(width = 4f, cap = StrokeCap.Round))
    }

    // ---- Cheeks + whiskers ----
    if (solid > 0.002f) {
        drawOval(cheek.copy(alpha = solid), Offset(-83f, 22.5f), Size(42f, 27f))
        drawOval(cheek.copy(alpha = solid), Offset(41f, 22.5f), Size(42f, 27f))
        for (i in 0..1) {
            val side = if (i == 0) -1f else 1f
            val b = side * 82f
            drawLine(line.copy(alpha = solid), Offset(b, 24f), Offset(b + side * 30f, 18f), 2.2f, StrokeCap.Round)
            drawLine(line.copy(alpha = solid), Offset(b, 32f), Offset(b + side * 34f, 32f), 2.2f, StrokeCap.Round)
            drawLine(line.copy(alpha = solid), Offset(b, 40f), Offset(b + side * 30f, 46f), 2.2f, StrokeCap.Round)
        }
    }

    // ---- Eyes: solid black ovals, collapsing to happy arcs during the blink ----
    if (solid > 0.002f) {
        for (i in 0..1) {
            val side = if (i == 0) -1f else 1f
            val ex = side * 33f
            if (eyeOpen > 0.22f) {
                val eh = 26f * eyeOpen
                // Solid black eyes, exactly like the reference — smaller, no catch-light highlight.
                drawOval(eyeCol.copy(alpha = solid), Offset(ex - 10.5f, 4f - eh / 2f), Size(21f, eh))
            } else {
                // Shut: a happy downward arc.
                drawArc(line.copy(alpha = solid), 205f, 130f, false, Offset(ex - 10f, -1.5f), Size(20f, 12f), style = Stroke(width = 3f, cap = StrokeCap.Round))
            }
        }
    }

    // ---- Nose + ω mouth ----
    if (solid > 0.002f) {
        drawOval(nose.copy(alpha = solid), Offset(-6.5f, 15.5f), Size(13f, 9f))
        drawArc(line.copy(alpha = solid), 300f, 140f, false, Offset(-14.5f, 18.5f), Size(15f, 13f), style = Stroke(width = 2.6f, cap = StrokeCap.Round))
        drawArc(line.copy(alpha = solid), 100f, 140f, false, Offset(-0.5f, 18.5f), Size(15f, 13f), style = Stroke(width = 2.6f, cap = StrokeCap.Round))
    }

    // ---- Two round paws that wave about their shoulders (this is movement 2, "hello") ----
    for (i in 0..1) {
        val side = if (i == 0) -1f else 1f
        val dir = if (i == 0) 1f else -1f         // mirror the swing so the two read as symmetric
        val pivot = Offset(side * 90f, 34f)        // the shoulder: where the paw meets the body
        withTransform({ rotate(degrees = waveDeg * dir, pivot = pivot) }) {
            val pcx = pivot.x + side * 14f
            val pcy = pivot.y - 8f
            val pTL = Offset(pcx - 20f, pcy - 20f)
            val pSize = Size(40f, 40f)
            if (solid > 0.002f) {
                drawOval(fur.copy(alpha = solid), pTL, pSize)
                drawOval(line.copy(alpha = solid), pTL, pSize, style = Stroke(width = 3f))
                // Three little beans.
                drawOval(bean.copy(alpha = solid), Offset(pcx - 4f, pcy - 11.5f), Size(8f, 7f))
                drawOval(bean.copy(alpha = solid), Offset(pcx - 9.5f, pcy - 4.5f), Size(7f, 7f))
                drawOval(bean.copy(alpha = solid), Offset(pcx + 2.5f, pcy - 4.5f), Size(7f, 7f))
            }
            if (glassy > 0.002f) {
                drawOval(Color.White.copy(alpha = glassy * 0.16f), pTL, pSize)
                drawOval(tint.copy(alpha = glassy * 0.20f), pTL, pSize)
                drawOval(Color.White.copy(alpha = glassy * 0.75f), pTL, pSize, style = Stroke(width = 2.6f))
                drawOval(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = glassy * 0.26f),
                        0.7f to Color.Transparent
                    ),
                    topLeft = pTL,
                    size = pSize
                )
            }
        }
    }
}

// ---- Timings, in milliseconds ----
// One place to retune the whole sequence; every phase above is expressed against these rather than
// against hard-coded numbers scattered through the drawing code.
// SPEED: overall launch-animation speed. A factor below 1 makes the whole sequence quicker; every
// duration below is multiplied by it, so the relative timing of the phases is preserved exactly.
private const val SPEED = 0.85f
private const val TOTAL_MS = 7700f * SPEED
private const val ENTER_MS = 700f * SPEED
private const val WAVE_END_MS = 2600f * SPEED     // paws wave from ENTER_MS up to here
private const val BLINK_START_MS = 2600f * SPEED  // the playful blink sits between wave and morph
private const val BLINK_END_MS = 3300f * SPEED
private const val MORPH_START_MS = 3300f * SPEED
private const val MORPH_END_MS = 6600f * SPEED
private const val EXIT_START_MS = 6600f * SPEED
private const val WAVE_CYCLES = 3.5f              // a count of paw swings, not a duration — left unscaled
private const val WAVE_AMP_DEG = 17f              // a slight up/down swing at the shoulder
