package com.lucent.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

enum class LucentThemeSection {
    LIGHT,
    DARK,
    SYSTEM;

    val label: String
        get() = when (this) {
            LIGHT -> com.lucent.app.i18n.S.themeSectionLight
            DARK -> com.lucent.app.i18n.S.themeSectionDark
            SYSTEM -> com.lucent.app.i18n.S.themeSectionSystem
        }
}

enum class LucentThemeMode(val key: String, val featured: Boolean = true) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    MONET_WHEAT("monet_yellow"),
    MONET_GARDEN("monet_green"),
    MONET_MORNING("monet_blue"),
    MONET_WISTERIA("monet_purple"),
    MONET_NIGHT("monet_night"),
    MONET_PINE("monet_pine"),
    MONET_PLUM("monet_plum"),
    MONET_EMBER("monet_ember"),

    MONET_ROSE("monet_rose"),
    MONET_LAGOON("monet_lagoon"),
    MONET_INK("monet_ink"),
    MONET_GARNET("monet_garnet"),
    MONET_PEACH("monet_peach"),
    MONET_CLAY("monet_clay"),
    MONET_ORCHID("monet_orchid"),
    MONET_ORCHID_NIGHT("monet_orchid_night"),
    MONET_HONEY("monet_honey"),
    MONET_MINT("monet_mint", featured = false),
    MONET_PERIWINKLE("monet_periwinkle", featured = false),
    MONET_CORAL("monet_coral", featured = false),
    MONET_SNOWDROP("monet_snowdrop", featured = false),
    MONET_HONEYDEW("monet_honeydew", featured = false),
    MONET_LINEN("monet_linen", featured = false),
    MONET_FROST("monet_frost", featured = false),
    MONET_UMBER("monet_umber", featured = false),
    MONET_DEEP_MOSS("monet_deep_moss", featured = false),
    MONET_BLUEBERRY("monet_blueberry"),
    MONET_BRICK("monet_brick", featured = false),
    MONET_OBSIDIAN("monet_obsidian", featured = false),
    MONET_FIR("monet_fir", featured = false),
    MONET_WALNUT("monet_walnut", featured = false),
    MONET_ABYSS("monet_abyss", featured = false),;

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
            MONET_HONEY -> com.lucent.app.i18n.S.themeMonetHoney
            MONET_MINT -> com.lucent.app.i18n.S.themeMonetMint
            MONET_PERIWINKLE -> com.lucent.app.i18n.S.themeMonetPeriwinkle
            MONET_CORAL -> com.lucent.app.i18n.S.themeMonetCoral
            MONET_SNOWDROP -> com.lucent.app.i18n.S.themeMonetSnowdrop
            MONET_HONEYDEW -> com.lucent.app.i18n.S.themeMonetHoneydew
            MONET_LINEN -> com.lucent.app.i18n.S.themeMonetLinen
            MONET_FROST -> com.lucent.app.i18n.S.themeMonetFrost
            MONET_UMBER -> com.lucent.app.i18n.S.themeMonetUmber
            MONET_DEEP_MOSS -> com.lucent.app.i18n.S.themeMonetDeepMoss
            MONET_BLUEBERRY -> com.lucent.app.i18n.S.themeMonetBlueberry
            MONET_BRICK -> com.lucent.app.i18n.S.themeMonetBrick
            MONET_OBSIDIAN -> com.lucent.app.i18n.S.themeMonetObsidian
            MONET_FIR -> com.lucent.app.i18n.S.themeMonetFir
            MONET_WALNUT -> com.lucent.app.i18n.S.themeMonetWalnut
            MONET_ABYSS -> com.lucent.app.i18n.S.themeMonetAbyss
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
            MONET_HONEY -> com.lucent.app.i18n.S.themeMonetHoneyDesc
            MONET_MINT -> com.lucent.app.i18n.S.themeMonetMintDesc
            MONET_PERIWINKLE -> com.lucent.app.i18n.S.themeMonetPeriwinkleDesc
            MONET_CORAL -> com.lucent.app.i18n.S.themeMonetCoralDesc
            MONET_SNOWDROP -> com.lucent.app.i18n.S.themeMonetSnowdropDesc
            MONET_HONEYDEW -> com.lucent.app.i18n.S.themeMonetHoneydewDesc
            MONET_LINEN -> com.lucent.app.i18n.S.themeMonetLinenDesc
            MONET_FROST -> com.lucent.app.i18n.S.themeMonetFrostDesc
            MONET_UMBER -> com.lucent.app.i18n.S.themeMonetUmberDesc
            MONET_DEEP_MOSS -> com.lucent.app.i18n.S.themeMonetDeepMossDesc
            MONET_BLUEBERRY -> com.lucent.app.i18n.S.themeMonetBlueberryDesc
            MONET_BRICK -> com.lucent.app.i18n.S.themeMonetBrickDesc
            MONET_OBSIDIAN -> com.lucent.app.i18n.S.themeMonetObsidianDesc
            MONET_FIR -> com.lucent.app.i18n.S.themeMonetFirDesc
            MONET_WALNUT -> com.lucent.app.i18n.S.themeMonetWalnutDesc
            MONET_ABYSS -> com.lucent.app.i18n.S.themeMonetAbyssDesc
        }

    val section: LucentThemeSection
        get() = when (this) {
            SYSTEM -> LucentThemeSection.SYSTEM
            LIGHT -> LucentThemeSection.LIGHT
            DARK -> LucentThemeSection.DARK
            else -> if (isDark(systemDark = false)) LucentThemeSection.DARK else LucentThemeSection.LIGHT
        }

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        DARK -> true
        MONET_NIGHT, MONET_PINE, MONET_PLUM, MONET_EMBER,
        MONET_INK, MONET_GARNET,
        MONET_CLAY, MONET_ORCHID_NIGHT,
        MONET_UMBER, MONET_DEEP_MOSS, MONET_BLUEBERRY, MONET_BRICK,
        MONET_OBSIDIAN, MONET_FIR, MONET_WALNUT, MONET_ABYSS -> true
        else -> false
    }

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
        MONET_ROSE -> Color(0xFFF9EEF0)
        MONET_LAGOON -> Color(0xFFE4F1EF)
        MONET_INK -> Color(0xFF0F262A)
        MONET_GARNET -> Color(0xFF2E161C)
        MONET_PEACH -> Color(0xFFF9EFE1)
        MONET_CLAY -> Color(0xFF371B0C)
        MONET_ORCHID -> Color(0xFFF8EBF4)
        MONET_ORCHID_NIGHT -> Color(0xFF2C1024)
        MONET_HONEY -> Color(0xFFFCF6DC)
        MONET_MINT -> Color(0xFFE4F6ED)
        MONET_PERIWINKLE -> Color(0xFFE7EBF9)
        MONET_CORAL -> Color(0xFFFBEAE6)
        MONET_SNOWDROP -> Color(0xFFF7F7FF)
        MONET_HONEYDEW -> Color(0xFFEFF7E4)
        MONET_LINEN -> Color(0xFFFBF3E8)
        MONET_FROST -> Color(0xFFEAF6F5)
        MONET_UMBER -> Color(0xFF2C1806)
        MONET_DEEP_MOSS -> Color(0xFF0E2B1C)
        MONET_BLUEBERRY -> Color(0xFF101B44)
        MONET_BRICK -> Color(0xFF3B130B)
        MONET_OBSIDIAN -> Color(0xFF101622)
        MONET_FIR -> Color(0xFF0D2A1E)
        MONET_WALNUT -> Color(0xFF3A2412)
        MONET_ABYSS -> Color(0xFF032A26)
    }

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
        MONET_HONEY -> listOf(Color(0xFFFEF7E0), Color(0xFFE3D191))
        MONET_MINT -> listOf(Color(0xFFF0FAF4), Color(0xFFC6E4D2))
        MONET_PERIWINKLE -> listOf(Color(0xFFF3F5FD), Color(0xFFC9D2F0))
        MONET_CORAL -> listOf(Color(0xFFFDF3EF), Color(0xFFEFC9BD))
        MONET_SNOWDROP -> listOf(Color(0xFFFDFDFF), Color(0xFFD6D6EC))
        MONET_HONEYDEW -> listOf(Color(0xFFF7FBF0), Color(0xFFD4E6BB))
        MONET_LINEN -> listOf(Color(0xFFFDF6EC), Color(0xFFE4D3B4))
        MONET_FROST -> listOf(Color(0xFFF4FBFB), Color(0xFFC3E4E2))
        MONET_UMBER -> listOf(Color(0xFF4A2C16), Color(0xFF1C1003))
        MONET_DEEP_MOSS -> listOf(Color(0xFF1D4632), Color(0xFF081D12))
        MONET_BLUEBERRY -> listOf(Color(0xFF26315E), Color(0xFF0A1130))
        MONET_BRICK -> listOf(Color(0xFF5A2118), Color(0xFF280D08))
        MONET_OBSIDIAN -> listOf(Color(0xFF232C3E), Color(0xFF080C16))
        MONET_FIR -> listOf(Color(0xFF1B3A29), Color(0xFF081D12))
        MONET_WALNUT -> listOf(Color(0xFF5A3D20), Color(0xFF241505))
        MONET_ABYSS -> listOf(Color(0xFF0D403A), Color(0xFF021B18))
    }

    companion object {
        val LIGHT_BACKDROP = Color(0xFFF4F3F8)
        val DARK_BACKDROP = Color(0xFF0E0E14)

        fun fromKey(key: String?): LucentThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM

        val pickerEntries: List<LucentThemeMode> = entries.filter { it.featured }
    }
}

fun dynamicBlobPalette(scheme: ColorScheme): List<Color> =
    listOf(scheme.primary, scheme.secondary, scheme.tertiary)
