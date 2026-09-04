package com.lucent.app.ui

import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The v2.7.2 palette trim was verified by script; these tests keep the *promise* (no duplicated
 * names or colours in the offered sets, exact counts) from silently regressing in a future branch.
 */
class PaletteLibraryTest {

    @Test
    fun themePickerOffersExactly18Tints() {
        val tints = LucentThemeMode.pickerEntries.filter {
            it != LucentThemeMode.SYSTEM && it != LucentThemeMode.LIGHT && it != LucentThemeMode.DARK
        }
        assertEquals(18, tints.size)
        val light = tints.map { it.backdrop(systemDark = false).toArgb() }
        assertEquals(light.size, light.toSet().size)
        val dark = tints.map { it.backdrop(systemDark = true).toArgb() }
        assertEquals(dark.size, dark.toSet().size)
    }

    @Test
    fun backgroundPickerOffersExactly36UniqueBoards() {
        val boards = LucentPalette.pickerEntries
        assertEquals(36, boards.size)
        assertEquals(boards.size, boards.map { it.name }.toSet().size)
        val hexes = boards.flatMap { it.colors.map { c -> c.toArgb() } }
        assertEquals(hexes.size, hexes.toSet().size, "duplicated hex in offered boards")
        // hidden boards remain valid stored keys through the full enum
        assertEquals(42, LucentPalette.entries.size)
    }
}
