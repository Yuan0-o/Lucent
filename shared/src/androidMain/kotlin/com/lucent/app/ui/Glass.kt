package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val LocalHazeState = compositionLocalOf { HazeState() }
val LocalOnGradient = compositionLocalOf { Color.White }
val LocalOnGradientMuted = compositionLocalOf { Color.White.copy(alpha = 0.65f) }

val LocalBottomBarInset = compositionLocalOf { 0.dp }

object LucentGlass {
    val HazeContainerDark = Color(0xFF0E0E14)
    val HazeContainerLight = Color(0xFFF6F5FA)

    const val CARD_FILL_DARK = 0.09f
    const val CARD_FILL_LIGHT = 0.15f

    const val BLURRED_FILL_DARK = 0.06f
    const val BLURRED_FILL_LIGHT = 0.14f

    const val NAV_FILL_DARK = 0.07f
    const val NAV_FILL_LIGHT = 0.11f
}

val DANGER_RED = Color(0xFFD92D20)
val DANGER_RED_RIM = Color(0xFF9B1C14)

@Composable
fun isDarkGlass(): Boolean = LocalOnGradient.current.luminance() > 0.5f

@Composable
fun lucentGlassRim(strong: Boolean = false): Brush {
    val dark = isDarkGlass()
    val ink = LocalOnGradient.current
    return if (dark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (strong) 0.42f else 0.32f),
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (strong) 0.95f else 0.90f),
                ink.copy(alpha = 0.07f),
                ink.copy(alpha = if (strong) 0.24f else 0.20f)
            )
        )
    }
}

fun Modifier.frostedGlass(cornerRadius: Dp = 20.dp, tint: Color = Color.White): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    val darkTheme = isDarkGlass()
    val tinted = tint != Color.White

    val fillColor = if (tinted) tint else Color.White
    val fillAlpha = when {
        tinted && darkTheme -> 0.20f
        tinted -> 0.26f
        darkTheme -> LucentGlass.CARD_FILL_DARK
        else -> LucentGlass.CARD_FILL_LIGHT
    }

    clip(shape)
        .background(fillColor.copy(alpha = fillAlpha))
        .then(
            if (tinted) Modifier.border(1.dp, tint.copy(alpha = if (darkTheme) 0.45f else 0.55f), shape)
            else Modifier.border(1.dp, lucentGlassRim(), shape)
        )
}

private val timestampFormatter get() = com.lucent.app.i18n.LDates.of(com.lucent.app.i18n.S.patternTimestamp)

fun formatTimestamp(millis: Long): String {
    val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return zoned.format(timestampFormatter)
}

@androidx.compose.runtime.Composable
fun rememberFormattedTimestamp(millis: Long): String {
    val language = com.lucent.app.i18n.L.current
    return androidx.compose.runtime.remember(millis, language) { formatTimestamp(millis) }
}

private val dateFormatter get() = com.lucent.app.i18n.LDates.of(com.lucent.app.i18n.S.patternDateFull)

fun formatDate(millis: Long): String {
    val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return zoned.format(dateFormatter)
}

fun sameLocalDay(a: Long, b: Long): Boolean {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
        Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
}

fun withinLocalDayRange(itemMillis: Long, startMillis: Long, endMillis: Long): Boolean {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(itemMillis).atZone(zone).toLocalDate()
    val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
    val lo = if (start.isAfter(end)) end else start
    val hi = if (start.isAfter(end)) start else end
    return !day.isBefore(lo) && !day.isAfter(hi)
}

fun formatDateRange(startMillis: Long, endMillis: Long): String {
    val start = formatDate(startMillis)
    val end = formatDate(endMillis)
    return if (start == end) start else "$start – $end"
}

enum class PaletteGroup { DAWN, BLOSSOM, EVERGREEN, AQUA, SUNFIRE, VIVID, EARTH, VELVET }

const val PALETTE_CYCLE = "CYCLE"
const val PALETTE_RANDOM = "RANDOM"
const val RANDOM_SWITCH_MS = 20_000L
const val RANDOM_FADE_MS = 1_800L
private const val RANDOM_FADE_STEPS = 24

@androidx.compose.runtime.Composable
fun rememberRandomPaletteColors(
    animated: Boolean = true,
    environment: BackgroundEnvironment = LocalBackgroundEnvironment.current
): List<Color> {
    val boards = LucentPalette.pickerEntries
    val colors = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(boards.first().colors)
    }
    val running = animated && environment.active && environment.motionEnabled &&
        !androidx.compose.ui.platform.LocalInspectionMode.current
    if (!running) return colors.value
    val seed = androidx.compose.runtime.remember { kotlin.random.Random.nextInt() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var startNanos = -1L
        var cycle = -1L
        var prevIdx = -1
        var holdColors: List<Color> = colors.value
        var nextColors: List<Color> = holdColors
        var lastStep = -1
        val period = (RANDOM_SWITCH_MS + RANDOM_FADE_MS).toDouble()
        while (true) {
            androidx.compose.animation.core.withInfiniteAnimationFrameNanos { frameNanos ->
                if (startNanos < 0L) {
                    startNanos = frameNanos
                    return@withInfiniteAnimationFrameNanos
                }
                val t = (frameNanos - startNanos) / 1_000_000.0
                val cyc = (t / period).toLong()
                if (cyc != cycle) {
                    cycle = cyc
                    var idx = (((cyc * 2_654_435_761L) xor seed.toLong()) % boards.size).toInt()
                    if (idx < 0) idx = -idx
                    var guard = 0
                    while (idx == prevIdx && guard++ < boards.size) idx = (idx + 1) % boards.size
                    prevIdx = idx
                    holdColors = colors.value
                    nextColors = boards[idx].colors
                    lastStep = -1
                }
                val inCycle = t - cycle * period
                val fade = ((inCycle - RANDOM_SWITCH_MS) / RANDOM_FADE_MS).coerceIn(0.0, 1.0)
                if (fade <= 0.0) {
                    if (lastStep != 0 && colors.value !== holdColors) {
                        lastStep = 0
                        colors.value = holdColors
                    }
                } else {
                    val eased = fade * fade * (3.0 - 2.0 * fade)
                    val step = (eased * RANDOM_FADE_STEPS).toInt().coerceAtMost(RANDOM_FADE_STEPS - 1)
                    if (step != lastStep) {
                        lastStep = step
                        val f = eased.toFloat()
                        colors.value = holdColors.indices.map { i ->
                            androidx.compose.ui.graphics.lerp(holdColors[i], nextColors[i], f)
                        }
                    }
                }
            }
        }
    }
    return colors.value
}

fun PaletteGroup.title(): String = when (this) {
    PaletteGroup.DAWN -> com.lucent.app.i18n.S.paletteGroupDawn
    PaletteGroup.BLOSSOM -> com.lucent.app.i18n.S.paletteGroupBlossom
    PaletteGroup.EVERGREEN -> com.lucent.app.i18n.S.paletteGroupEvergreen
    PaletteGroup.AQUA -> com.lucent.app.i18n.S.paletteGroupAqua
    PaletteGroup.SUNFIRE -> com.lucent.app.i18n.S.paletteGroupSunfire
    PaletteGroup.VIVID -> com.lucent.app.i18n.S.paletteGroupVivid
    PaletteGroup.EARTH -> com.lucent.app.i18n.S.paletteGroupEarth
    PaletteGroup.VELVET -> com.lucent.app.i18n.S.paletteGroupVelvet
}

enum class LucentPalette(val colors: List<Color>, val group: PaletteGroup, val featured: Boolean = true) {
    SUNSET(listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B)), PaletteGroup.DAWN),
    OCEAN(listOf(Color(0xFF00507A), Color(0xFF3A6EA5), Color(0xFF4FD9C4)), PaletteGroup.AQUA),
    FOREST(listOf(Color(0xFF0F4C3A), Color(0xFF1F9E6B), Color(0xFFB6E388)), PaletteGroup.EVERGREEN),
    BERRY(listOf(Color(0xFF5B2C82), Color(0xFFB43D8F), Color(0xFFFF7CA3)), PaletteGroup.BLOSSOM),
    MIDNIGHT(listOf(Color(0xFF16213E), Color(0xFF0F3460), Color(0xFF533483)), PaletteGroup.VELVET),

    BLUSH(listOf(Color(0xFF7A2E43), Color(0xFFC96A80), Color(0xFFF3B8C6)), PaletteGroup.BLOSSOM, featured = false),
    LAVENDER(listOf(Color(0xFF4B3A6B), Color(0xFF8A6FB0), Color(0xFFCBB6E8)), PaletteGroup.VIVID),
    SAGE(listOf(Color(0xFF2F4A3C), Color(0xFF5E8B6F), Color(0xFFAFCBB4)), PaletteGroup.EVERGREEN),
    SAND(listOf(Color(0xFF7A5C36), Color(0xFFC1996A), Color(0xFFEAD6B8)), PaletteGroup.DAWN),
    SLATE(listOf(Color(0xFF2A3A4A), Color(0xFF4F6B84), Color(0xFF9DB4C8)), PaletteGroup.AQUA),
    TERRACOTTA(listOf(Color(0xFF7A3B2E), Color(0xFFC26A50), Color(0xFFEAB59B)), PaletteGroup.SUNFIRE),
    TEAL(listOf(Color(0xFF10403B), Color(0xFF2E7E76), Color(0xFF8FC9C0)), PaletteGroup.EVERGREEN),

    AURORA(listOf(Color(0xFF0FA3A3), Color(0xFF6A5AE0), Color(0xFFE85D9E)), PaletteGroup.VIVID),
    PEACH_DUSK(listOf(Color(0xFFFF8A5B), Color(0xFFEE4D8F), Color(0xFF8A4FD8)), PaletteGroup.DAWN),
    COSMIC(listOf(Color(0xFF1E5AE8), Color(0xFF9B2FE8), Color(0xFF2ED0C0)), PaletteGroup.VIVID),


    AMBER(listOf(Color(0xFF7A6108), Color(0xFFD4A81A), Color(0xFFF5E39B)), PaletteGroup.DAWN),
    CRIMSON(listOf(Color(0xFF6E1410), Color(0xFFC0392B), Color(0xFFF0A79C)), PaletteGroup.SUNFIRE),
    INDIGO(listOf(Color(0xFF232A63), Color(0xFF4A55A8), Color(0xFFA3AAE0)), PaletteGroup.VIVID),
    OLIVE(listOf(Color(0xFF4A4A22), Color(0xFF86864A), Color(0xFFCBCB93)), PaletteGroup.EVERGREEN),
    PLUM(listOf(Color(0xFF4A1F3A), Color(0xFF8A4270), Color(0xFFD69EC0)), PaletteGroup.VELVET),
    GRAPHITE(listOf(Color(0xFF2B2B30), Color(0xFF5A5A63), Color(0xFFA8A8B2)), PaletteGroup.EARTH),

    CITRUS(listOf(Color(0xFF7ED321), Color(0xFFF5A623), Color(0xFFFF6B6B)), PaletteGroup.SUNFIRE),
    GLACIER(listOf(Color(0xFFA8D8F0), Color(0xFF5E9CC7), Color(0xFF1E3A5F)), PaletteGroup.AQUA),
    NEBULA(listOf(Color(0xFFE0218A), Color(0xFF7B2FBE), Color(0xFF2A2A8C)), PaletteGroup.VIVID),
    EMBERGLOW(listOf(Color(0xFF8C1C13), Color(0xFFE2571E), Color(0xFFF2B705)), PaletteGroup.SUNFIRE),
    MERIDIAN(listOf(Color(0xFF0E6E6E), Color(0xFF7A9A3C), Color(0xFFE3C88F)), PaletteGroup.EVERGREEN),
    ORCHID(listOf(Color(0xFFF28FC2), Color(0xFFB57BE0), Color(0xFF7C9BE8)), PaletteGroup.BLOSSOM),

    TOBACCO(listOf(Color(0xFF3A2113), Color(0xFF8A5A33), Color(0xFFDCC09A)), PaletteGroup.EARTH),
    STONE(listOf(Color(0xFF403E3A), Color(0xFF807A70), Color(0xFFD2CBC0)), PaletteGroup.EARTH),
    NOCTURNE(listOf(Color(0xFF101728), Color(0xFF35425F), Color(0xFF8796B8)), PaletteGroup.AQUA, featured = false),
    CHERRY(listOf(Color(0xFF450F1E), Color(0xFF94263F), Color(0xFFE2A0B4)), PaletteGroup.BLOSSOM),
    TANGERINE(listOf(Color(0xFF5C2A07), Color(0xFFAC5F14), Color(0xFFF0C780)), PaletteGroup.SUNFIRE, featured = false),
    ROYAL(listOf(Color(0xFF17275C), Color(0xFF3E63B0), Color(0xFF9DB8EE)), PaletteGroup.AQUA),
    AMETHYST(listOf(Color(0xFF34164A), Color(0xFF6E3D9E), Color(0xFFC3A2E0)), PaletteGroup.VELVET),
    ALPINE(listOf(Color(0xFF1C2E28), Color(0xFF4F7360), Color(0xFFA8C8B8)), PaletteGroup.EARTH, featured = false),
    PEARL(listOf(Color(0xFF4E4A52), Color(0xFF9C95A6), Color(0xFFE4DEE8)), PaletteGroup.EARTH, featured = false),

    EGGSHELL(listOf(Color(0xFF6E6657), Color(0xFFB0A693), Color(0xFFF0E9DA)), PaletteGroup.DAWN),

    CONFETTI(listOf(Color(0xFFFF5E5B), Color(0xFFFFC24B), Color(0xFF6DD5ED)), PaletteGroup.SUNFIRE),
    GRAPEVINE(listOf(Color(0xFF3C2A5C), Color(0xFF7A4FA0), Color(0xFFE08FC2)), PaletteGroup.BLOSSOM, featured = false),
    SEABREEZE(listOf(Color(0xFF123A54), Color(0xFF2F8FB3), Color(0xFFA8E2E0)), PaletteGroup.EVERGREEN),
    TWILIGHT(listOf(Color(0xFF0F1B4C), Color(0xFF5C2E91), Color(0xFFE55D87)), PaletteGroup.VELVET),
    MISTY(listOf(Color(0xFF2F3542), Color(0xFF57606F), Color(0xFFA4B0BE)), PaletteGroup.VELVET);

    val label: String
        get() = when (this) {
            SUNSET -> com.lucent.app.i18n.S.paletteSunset
            OCEAN -> com.lucent.app.i18n.S.paletteOcean
            FOREST -> com.lucent.app.i18n.S.paletteForest
            BERRY -> com.lucent.app.i18n.S.paletteBerry
            MIDNIGHT -> com.lucent.app.i18n.S.paletteMidnight
            BLUSH -> com.lucent.app.i18n.S.paletteBlush
            LAVENDER -> com.lucent.app.i18n.S.paletteLavender
            SAGE -> com.lucent.app.i18n.S.paletteSage
            SAND -> com.lucent.app.i18n.S.paletteSand
            SLATE -> com.lucent.app.i18n.S.paletteSlate
            TERRACOTTA -> com.lucent.app.i18n.S.paletteTerracotta
            TEAL -> com.lucent.app.i18n.S.paletteTeal
            AURORA -> com.lucent.app.i18n.S.paletteAurora
            PEACH_DUSK -> com.lucent.app.i18n.S.palettePeachDusk
            COSMIC -> com.lucent.app.i18n.S.paletteCosmic
            AMBER -> com.lucent.app.i18n.S.paletteAmber
            CRIMSON -> com.lucent.app.i18n.S.paletteCrimson
            INDIGO -> com.lucent.app.i18n.S.paletteIndigo
            OLIVE -> com.lucent.app.i18n.S.paletteOlive
            PLUM -> com.lucent.app.i18n.S.palettePlum
            GRAPHITE -> com.lucent.app.i18n.S.paletteGraphite
            CITRUS -> com.lucent.app.i18n.S.paletteCitrus
            GLACIER -> com.lucent.app.i18n.S.paletteGlacier
            NEBULA -> com.lucent.app.i18n.S.paletteNebula
            EMBERGLOW -> com.lucent.app.i18n.S.paletteEmberglow
            MERIDIAN -> com.lucent.app.i18n.S.paletteMeridian
            ORCHID -> com.lucent.app.i18n.S.paletteOrchid
            TOBACCO -> com.lucent.app.i18n.S.paletteTobacco
            STONE -> com.lucent.app.i18n.S.paletteStone
            NOCTURNE -> com.lucent.app.i18n.S.paletteNocturne
            CHERRY -> com.lucent.app.i18n.S.paletteCherry
            TANGERINE -> com.lucent.app.i18n.S.paletteTangerine
            ROYAL -> com.lucent.app.i18n.S.paletteRoyal
            AMETHYST -> com.lucent.app.i18n.S.paletteAmethyst
            ALPINE -> com.lucent.app.i18n.S.paletteAlpine
            PEARL -> com.lucent.app.i18n.S.palettePearl
            EGGSHELL -> com.lucent.app.i18n.S.paletteEggshell
            CONFETTI -> com.lucent.app.i18n.S.paletteConfetti
            GRAPEVINE -> com.lucent.app.i18n.S.paletteGrapevine
            SEABREEZE -> com.lucent.app.i18n.S.paletteSeabreeze
            TWILIGHT -> com.lucent.app.i18n.S.paletteTwilight
            MISTY -> com.lucent.app.i18n.S.paletteMisty
        }

    companion object {
        val pickerEntries: List<LucentPalette> = entries.filter { it.featured }
    }
}
