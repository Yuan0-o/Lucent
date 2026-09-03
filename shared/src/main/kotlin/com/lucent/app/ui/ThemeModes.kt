package com.lucent.app.ui

import androidx.compose.ui.graphics.Color

/**
 * Every appearance the app can wear, as one closed list.
 *
 * ### Why this exists (added task 1)
 *
 * The theme used to be three loose string literals — `"system"`, `"light"`, `"dark"` — compared with
 * `==` in `MainActivity`, in `SettingsScreen`, and nowhere else. Adding the four Monet tints to that
 * arrangement would have meant seven string comparisons in two files that must agree with each
 * other forever, and the first typo would be a theme that silently falls back to "system" with no
 * error anywhere. So the list of themes is now a type, and the two things a theme actually decides —
 * *is it a dark theme* and *what colour is the backdrop* — are answered by the theme itself.
 *
 * The stored value is still the plain [key] string, so every existing install keeps its choice and
 * nothing in [com.lucent.app.data.SettingsRepository] had to change: it stores a string, and
 * [fromKey] is lenient about anything it doesn't recognise.
 *
 * ### The Monet tints
 *
 * The four Monet options are *peers* of System/Light/Dark, not a sub-menu — they sit in the same
 * radio list and are chosen the same way. Each one is a light theme whose backdrop is a soft,
 * desaturated wash instead of the neutral off-white: the pale straw of a haystack at dawn, the green
 * of the water-garden, the blue-grey of morning on the Seine, the wisteria over the Giverny bridge.
 *
 * They are deliberately *quiet* — every one sits above 90% lightness. The backdrop is the surface the
 * drifting blobs and all the frosted glass are read against, so a saturated tint here would fight the
 * palette in front of it and drag the contrast of every piece of text down with it. A wash you notice
 * only when you look for it is the right amount for something the whole app sits on top of.
 */
enum class LucentThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    MONET_WHEAT("monet_yellow"),
    MONET_GARDEN("monet_green"),
    MONET_MORNING("monet_blue"),
    MONET_WISTERIA("monet_purple"),
    // Dark peers of the four Monet tints (added later): the same painterly families after dusk —
    // deep, muted, *coloured* darks rather than the neutral near-black of [DARK]. Never true black.
    MONET_NIGHT("monet_night"),
    MONET_PINE("monet_pine"),
    MONET_PLUM("monet_plum"),
    MONET_EMBER("monet_ember"),

    // ---- C-group task 15: four further appearances ----
    //
    // The existing eight tints cover yellow, green, blue and purple, each with a light and a dark
    // member. Two hue families were missing entirely from BOTH halves — rose and teal — so the
    // four added here are those two, again as a light/dark pair each. That keeps the set balanced
    // (six light, six dark) rather than growing one side of it.
    //
    // "No overlap" is the binding constraint, and the near-misses are called out at each colour
    // below rather than left for someone to discover on a phone screen in daylight.
    MONET_ROSE("monet_rose"),
    MONET_LAGOON("monet_lagoon"),
    MONET_INK("monet_ink"),
    MONET_GARNET("monet_garnet"),
    // ---- R3 report: four further appearances (light/dark pair of orange, light/dark pair of
    // orchid-mauve) ----
    // Orange was the one warm hue family with no member in EITHER half; mauve sits between the
    // existing violet (WISTERIA/PLUM) and pink (ROSE/GARNET) families at a hue neither of them
    // owns. Adding one light + one dark per family keeps the set balanced at eight light and
    // eight dark. "No overlap" is binding — see the notes at each colour below.
    MONET_PEACH("monet_peach"),
    MONET_CLAY("monet_clay"),
    MONET_ORCHID("monet_orchid"),
    MONET_ORCHID_NIGHT("monet_orchid_night");

    // Live i18n lookups (localization task); `label`/`detail` call sites are unchanged.
    val label: String
        get() = when (this) {
            SYSTEM -> com.lucent.app.i18n.S.themeSystem
            LIGHT -> com.lucent.app.i18n.S.themeLight
            DARK -> com.lucent.app.i18n.S.themeDark
            MONET_WHEAT -> com.lucent.app.i18n.S.themeMonetWheat
            MONET_GARDEN -> com.lucent.app.i18n.S.themeMonetGarden
            MONET_MORNING -> com.lucent.app.i18n.S.themeMonetMorning
            MONET_WISTERIA -> com.lucent.app.i18n.S.themeMonetWisteria
            MONET_NIGHT -> com.lucent.app.i18n.S.themeMonetNight
            MONET_PINE -> com.lucent.app.i18n.S.themeMonetPine
            MONET_PLUM -> com.lucent.app.i18n.S.themeMonetPlum
            MONET_EMBER -> com.lucent.app.i18n.S.themeMonetEmber
            MONET_ROSE -> com.lucent.app.i18n.S.themeMonetRose
            MONET_LAGOON -> com.lucent.app.i18n.S.themeMonetLagoon
            MONET_INK -> com.lucent.app.i18n.S.themeMonetInk
            MONET_GARNET -> com.lucent.app.i18n.S.themeMonetGarnet
            MONET_PEACH -> com.lucent.app.i18n.S.themeMonetPeach
            MONET_CLAY -> com.lucent.app.i18n.S.themeMonetClay
            MONET_ORCHID -> com.lucent.app.i18n.S.themeMonetOrchid
            MONET_ORCHID_NIGHT -> com.lucent.app.i18n.S.themeMonetOrchidNight
        }

    val detail: String
        get() = when (this) {
            SYSTEM -> com.lucent.app.i18n.S.themeSystemDesc
            LIGHT -> com.lucent.app.i18n.S.themeLightDesc
            DARK -> com.lucent.app.i18n.S.themeDarkDesc
            MONET_WHEAT -> com.lucent.app.i18n.S.themeMonetWheatDesc
            MONET_GARDEN -> com.lucent.app.i18n.S.themeMonetGardenDesc
            MONET_MORNING -> com.lucent.app.i18n.S.themeMonetMorningDesc
            MONET_WISTERIA -> com.lucent.app.i18n.S.themeMonetWisteriaDesc
            MONET_NIGHT -> com.lucent.app.i18n.S.themeMonetNightDesc
            MONET_PINE -> com.lucent.app.i18n.S.themeMonetPineDesc
            MONET_PLUM -> com.lucent.app.i18n.S.themeMonetPlumDesc
            MONET_EMBER -> com.lucent.app.i18n.S.themeMonetEmberDesc
            MONET_ROSE -> com.lucent.app.i18n.S.themeMonetRoseDesc
            MONET_LAGOON -> com.lucent.app.i18n.S.themeMonetLagoonDesc
            MONET_INK -> com.lucent.app.i18n.S.themeMonetInkDesc
            MONET_GARNET -> com.lucent.app.i18n.S.themeMonetGarnetDesc
            MONET_PEACH -> com.lucent.app.i18n.S.themeMonetPeachDesc
            MONET_CLAY -> com.lucent.app.i18n.S.themeMonetClayDesc
            MONET_ORCHID -> com.lucent.app.i18n.S.themeMonetOrchidDesc
            MONET_ORCHID_NIGHT -> com.lucent.app.i18n.S.themeMonetOrchidNightDesc
        }

    /**
     * Whether this theme draws light-on-dark. Only [SYSTEM] consults the device; every other option
     * is an explicit choice and ignores it, which is the entire point of choosing one.
     */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        DARK -> true
        // C-group task 15: MONET_INK and MONET_GARNET are dark tints and MUST be listed here.
        //
        // This branch is `else -> false`, so a new dark appearance that is not named would compile
        // cleanly and then render dark text on a dark backdrop — a silent, unreadable failure with
        // no compiler help at all. Any future dark tint goes in this list at the same time as it
        // goes in the enum; the two are not independent.
        MONET_NIGHT, MONET_PINE, MONET_PLUM, MONET_EMBER,
        MONET_INK, MONET_GARNET,
        // R3 report: the two new dark tints MUST be named here or they would compile cleanly and
        // render dark text on a dark backdrop — see the warning comment above.
        MONET_CLAY, MONET_ORCHID_NIGHT -> true
        else -> false
    }

    /** The flat colour painted behind the drifting blobs. */
    fun backdrop(systemDark: Boolean): Color = when (this) {
        SYSTEM -> if (systemDark) DARK_BACKDROP else LIGHT_BACKDROP
        DARK -> DARK_BACKDROP
        LIGHT -> LIGHT_BACKDROP
        MONET_WHEAT -> Color(0xFFF7F1DC)
        MONET_GARDEN -> Color(0xFFE9F2E5)
        MONET_MORNING -> Color(0xFFE5EDF7)
        MONET_WISTERIA -> Color(0xFFEEE9F7)
        MONET_NIGHT -> Color(0xFF1A2136)
        MONET_PINE -> Color(0xFF16241C)
        MONET_PLUM -> Color(0xFF241A2E)
        MONET_EMBER -> Color(0xFF2A1E19)
        // Light rose. No light pink existed; the nearest was MONET_WISTERIA (0xFFEEE9F7), which
        // is violet — this sits ~40 degrees round the wheel from it.
        MONET_ROSE -> Color(0xFFF9EEF0)
        // Light teal. MONET_GARDEN is green and MONET_MORNING is blue; teal is the gap between
        // them and reads as neither.
        MONET_LAGOON -> Color(0xFFE4F1EF)
        // Dark teal, against MONET_PINE's dark green and MONET_NIGHT's navy.
        MONET_INK -> Color(0xFF0F262A)
        // Dark crimson. MONET_EMBER (0xFF2A1E19) is a warm BROWN — hue ~18; this is ~345, i.e. on
        // the blue side of red rather than the yellow side. Side by side they are plainly two
        // different colours, which is the test that matters.
        MONET_GARNET -> Color(0xFF2E161C)
        // R3 additions: light peach — no light ORANGE existed (EMBER's 0xFF2A1E19 is a dark
        // brown at hue ~18; this pale apricot is the light half of the same family).
        MONET_PEACH -> Color(0xFFF9EFE1)
        // Dark orange, against MONET_EMBER's dark brown: same warm corner, more orange in it.
        MONET_CLAY -> Color(0xFF371B0C)
        // Light mauve — WISTERIA (0xFFEEE9F7) is violet, ROSE (0xFFF9EEF0) is pink; this sits
        // between them.
        MONET_ORCHID -> Color(0xFFF8EBF4)
        // Dark mauve — PLUM (0xFF241A2E) is indigo-violet, GARNET (0xFF2E161C) is crimson; this
        // is the blue-ish side of magenta, distinct from both.
        MONET_ORCHID_NIGHT -> Color(0xFF2C1024)
    }

    /**
     * The two-colour preview shown beside this option in Settings. For the tints it's the backdrop
     * itself deepened slightly, so the swatch shows the actual colour the app will take on rather
     * than a decorative stand-in.
     */
    fun swatch(systemDark: Boolean): List<Color> = when (this) {
        SYSTEM -> listOf(LIGHT_BACKDROP, DARK_BACKDROP)
        LIGHT -> listOf(LIGHT_BACKDROP, Color(0xFFDCDBE4))
        DARK -> listOf(Color(0xFF23232E), DARK_BACKDROP)
        MONET_WHEAT -> listOf(Color(0xFFFBF7EA), Color(0xFFE8DDB6))
        MONET_GARDEN -> listOf(Color(0xFFF1F7EF), Color(0xFFC9DEC2))
        MONET_MORNING -> listOf(Color(0xFFEEF4FB), Color(0xFFC2D5EA))
        MONET_WISTERIA -> listOf(Color(0xFFF5F1FB), Color(0xFFD3C6EA))
        MONET_NIGHT -> listOf(Color(0xFF2B3552), Color(0xFF141A2C))
        MONET_PINE -> listOf(Color(0xFF25392E), Color(0xFF101B15))
        MONET_PLUM -> listOf(Color(0xFF3A2B48), Color(0xFF1A121F))
        MONET_EMBER -> listOf(Color(0xFF43302A), Color(0xFF1E1512))
        MONET_ROSE -> listOf(Color(0xFFFDF5F6), Color(0xFFEECDD4))
        MONET_LAGOON -> listOf(Color(0xFFEFF7F6), Color(0xFFBFDEDA))
        MONET_INK -> listOf(Color(0xFF1B3A40), Color(0xFF0A1B1E))
        MONET_GARNET -> listOf(Color(0xFF46242C), Color(0xFF1F0F13))
        MONET_PEACH -> listOf(Color(0xFFFDF6EA), Color(0xFFEBD9B8))
        MONET_CLAY -> listOf(Color(0xFF59301A), Color(0xFF2A1307))
        MONET_ORCHID -> listOf(Color(0xFFFDF3F9), Color(0xFFE5CBDD))
        MONET_ORCHID_NIGHT -> listOf(Color(0xFF4A1E3E), Color(0xFF1F0B1A))
    }

    companion object {
        val LIGHT_BACKDROP = Color(0xFFF4F3F8)
        val DARK_BACKDROP = Color(0xFF0E0E14)

        /** Lenient: an unknown or missing key reads as [SYSTEM] rather than throwing. */
        fun fromKey(key: String?): LucentThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
