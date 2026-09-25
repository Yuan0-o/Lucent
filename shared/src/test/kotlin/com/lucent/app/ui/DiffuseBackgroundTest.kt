package com.lucent.app.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiffuseBackgroundTest {

    private val warmPalette = intArrayOf(
        0xFFFF6B4A.toInt(), 0xFF4ADEDE.toInt(), 0xFF9B6BFF.toInt()
    )
    private val nightBackdrop = 0xFF101018.toInt()

    @Test
    fun fieldFillsItsWholeTextureOpaquely() {
        val field = DiffuseGradientField(16)
        val pixels = field.render(0.0, warmPalette, nightBackdrop, dark = true)
        assertEquals(16 * 16, pixels.size)
        assertTrue(pixels.all { (it ushr 24) and 255 == 255 }, "every texture pixel must be opaque")
    }

    @Test
    fun theGradientActuallyMoves() {
        val field = DiffuseGradientField(24)
        val first = field.render(0.0, warmPalette, nightBackdrop, dark = true).copyOf()
        val later = field.render(9.0, warmPalette, nightBackdrop, dark = true).copyOf()
        assertFalse(first.contentEquals(later), "the field must change as time advances")
    }

    @Test
    fun sameTimeRendersTheSameTexture() {
        val a = DiffuseGradientField(12).render(4.25, warmPalette, nightBackdrop, dark = false)
        val b = DiffuseGradientField(12).render(4.25, warmPalette, nightBackdrop, dark = false)
        assertTrue(a.contentEquals(b), "the field is a pure function of time and colours")
    }

    @Test
    fun coloursKeepChangingEvenWhenTheWavesAreFrozen() {
        val field = DiffuseGradientField(24)
        val atStart = field.render(0.0, 5.0, warmPalette, nightBackdrop, dark = true).copyOf()
        val later = field.render(0.0, 40.0, warmPalette, nightBackdrop, dark = true).copyOf()
        assertFalse(
            atStart.contentEquals(later),
            "with the waves frozen, 35 seconds of palette drift must still repaint the field"
        )
        val repeat = field.render(0.0, 40.0, warmPalette, nightBackdrop, dark = true)
        assertTrue(repeat.contentEquals(later))
    }

    @Test
    fun fieldIsAContinuumNotFlatBlocks() {
        val pixels = DiffuseGradientField(32).render(3.0, warmPalette, nightBackdrop, dark = true)
        val distinct = pixels.toSet().size
        assertTrue(distinct > 200, "expected a continuum of shades, got $distinct")
    }

    @Test
    fun theLayeredFusionStaysFreeOfHardEdgesAndNoise() {
        val field = DiffuseGradientField(96)
        var worst = 0
        for (step in 0 until 24) {
            val seconds = step * 2.0
            worst = maxOf(worst, worstNeighbourGap(field.render(seconds, seconds, warmPalette, nightBackdrop, dark = true)))
        }
        assertTrue(worst <= 5, "adjacent pixels must stay within 5/255, worst was $worst")
    }

    @Test
    fun theWavesStillMoveWhenOnlyTheSpatialClockAdvances() {
        val field = DiffuseGradientField(24)
        val frozenColours = field.render(0.0, 0.0, warmPalette, nightBackdrop, dark = true).copyOf()
        val advanced = field.render(13.0, 0.0, warmPalette, nightBackdrop, dark = true).copyOf()
        assertFalse(
            frozenColours.contentEquals(advanced),
            "13 seconds of the spatial clock must reshape the field even with the palette stopped"
        )
    }

    @Test
    fun everyCornerAndRegionOfTheTextureIsPainted() {
        val edge = 64
        val pixels = DiffuseGradientField(edge).render(3.0, warmPalette, nightBackdrop, dark = true)
        val corners = intArrayOf(0, edge - 1, edge * (edge - 1), edge * edge - 1)
        for (corner in corners) {
            assertTrue(
                channelGap(pixels[corner], nightBackdrop) >= 10,
                "corner ${corner / edge},${corner % edge} must carry colour, not the bare backdrop"
            )
        }
        for (cellY in 0 until 4) {
            for (cellX in 0 until 4) {
                val mean = cellMean(pixels, edge, cellX, cellY)
                assertTrue(
                    channelGap(mean, nightBackdrop) >= 10,
                    "cell $cellX,$cellY must be painted rather than left flat"
                )
            }
        }
    }

    @Test
    fun theColourSpansTheWholeHeightInsteadOfOneRegion() {
        val edge = 64
        val pixels = DiffuseGradientField(edge).render(4.0, warmPalette, nightBackdrop, dark = true)
        val top = rowBandMean(pixels, edge, 0, edge / 4)
        val middle = rowBandMean(pixels, edge, 3 * edge / 8, 5 * edge / 8)
        val bottom = rowBandMean(pixels, edge, 3 * edge / 4, edge)
        assertTrue(channelGap(top, middle) >= 10, "the hues must keep changing below the top of the field")
        assertTrue(channelGap(middle, bottom) >= 10, "the hues must keep changing above the bottom of the field")
        assertTrue(channelGap(top, bottom) >= 20, "the top and the bottom must not read as one tone")
    }

    @Test
    fun everyPaletteHueOwnsPartOfTheTexture() {
        val edge = 64
        val rgb = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        val pixels = DiffuseGradientField(edge).render(2.0, rgb, nightBackdrop, dark = true)
        var reds = 0
        var greens = 0
        var blues = 0
        for (pixel in pixels) {
            val r = (pixel ushr 16) and 255
            val g = (pixel ushr 8) and 255
            val b = pixel and 255
            when {
                r > g && r > b -> reds++
                g > b -> greens++
                else -> blues++
            }
        }
        val share = pixels.size / 20
        assertTrue(reds > share, "the first palette colour must own a region, got $reds of ${pixels.size}")
        assertTrue(greens > share, "the second palette colour must own a region, got $greens of ${pixels.size}")
        assertTrue(blues > share, "the third palette colour must own a region, got $blues of ${pixels.size}")
    }

    @Test
    fun theTextureKeepsCoveringItsWholeAreaAtEveryQualityTier() {
        for (edge in intArrayOf(96, 80, 64, 48)) {
            val pixels = DiffuseGradientField(edge).render(7.5, warmPalette, nightBackdrop, dark = false)
            assertEquals(edge * edge, pixels.size)
            assertTrue(pixels.all { (it ushr 24) and 255 == 255 }, "tier $edge must stay opaque")
            assertTrue(
                channelGap(cellMean(pixels, edge, 0, 0), cellMean(pixels, edge, 3, 3)) >= 10,
                "tier $edge must paint every part of the field, corners included"
            )
        }
    }

    private fun cellMean(pixels: IntArray, edge: Int, cellX: Int, cellY: Int): Int {
        val span = edge / 4
        var r = 0
        var g = 0
        var b = 0
        for (y in cellY * span until (cellY + 1) * span) {
            for (x in cellX * span until (cellX + 1) * span) {
                val pixel = pixels[y * edge + x]
                r += (pixel ushr 16) and 255
                g += (pixel ushr 8) and 255
                b += pixel and 255
            }
        }
        val count = span * span
        return (255 shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
    }

    private fun rowBandMean(pixels: IntArray, edge: Int, fromRow: Int, toRow: Int): Int {
        var r = 0
        var g = 0
        var b = 0
        var count = 0
        for (y in fromRow until toRow) {
            for (x in 0 until edge) {
                val pixel = pixels[y * edge + x]
                r += (pixel ushr 16) and 255
                g += (pixel ushr 8) and 255
                b += pixel and 255
                count++
            }
        }
        return (255 shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
    }

    private fun worstNeighbourGap(pixels: IntArray): Int {
        var worst = 0
        for (y in 0 until 96) {
            for (x in 0 until 96) {
                val here = pixels[y * 96 + x]
                if (x + 1 < 96) worst = maxOf(worst, channelGap(here, pixels[y * 96 + x + 1]))
                if (y + 1 < 96) worst = maxOf(worst, channelGap(here, pixels[(y + 1) * 96 + x]))
            }
        }
        return worst
    }

    private fun channelGap(a: Int, b: Int): Int {
        val r = abs(((a ushr 16) and 255) - ((b ushr 16) and 255))
        val g = abs(((a ushr 8) and 255) - ((b ushr 8) and 255))
        val bl = abs((a and 255) - (b and 255))
        return maxOf(r, g, bl)
    }

    @Test
    fun paletteAndBackdropBothReachTheTexture() {
        val field = DiffuseGradientField(20)
        val left = field.render(2.0, warmPalette, nightBackdrop, dark = true).copyOf()
        val recoloured = field.render(2.0, intArrayOf(0xFF00FF00.toInt()), nightBackdrop, dark = true).copyOf()
        assertFalse(left.contentEquals(recoloured), "a palette change must repaint the texture")

        val clear = field.render(2.0, intArrayOf(0x00000000), nightBackdrop, dark = true)
        assertTrue(clear.all { pixel -> pixel == nightBackdrop || ((pixel ushr 24) and 255) == 255 })
    }

    @Test
    fun timelineNeverJumpsAfterAStall() {
        val timeline = BackgroundTimeline()
        timeline.advance(0L)
        val steady = timeline.advance(33_000_000L)
        assertEquals(0.033, steady, 1e-6)
        val afterStall = timeline.advance(10_000_000_000L)
        assertEquals(0.133, afterStall, 1e-6)
    }

    @Test
    fun timelineResumesWithoutCountingThePausedWindow() {
        val timeline = BackgroundTimeline()
        timeline.advance(0L)
        timeline.advance(33_000_000L)
        timeline.pause()
        val resumed = timeline.advance(600_000_000_000L)
        assertEquals(0.033, resumed, 1e-6)
    }

    @Test
    fun policyDropsQualityWhenFramesAreExpensive() {
        val policy = BackgroundFramePolicy()
        assertEquals(0, policy.tier)
        var frame = 0L
        repeat(6) {
            frame += 33_333_333L
            policy.record(frame, renderNanos = 20_000_000L)
        }
        assertEquals(1, policy.tier, "six expensive frames must shed one quality tier")
        assertTrue(policy.edge < 96, "a lower tier must use a smaller texture")
        assertTrue(policy.intervalNanos > 33_333_333L, "a lower tier must redraw less often")
    }

    @Test
    fun policyClimbsBackOnlyAfterSustainedCheapFrames() {
        val policy = BackgroundFramePolicy()
        var frame = 0L
        repeat(6) {
            frame += 33_333_333L
            policy.record(frame, renderNanos = 20_000_000L)
        }
        assertEquals(1, policy.tier)
        repeat(179) {
            frame += 33_333_333L
            policy.record(frame, renderNanos = 1_000_000L)
        }
        assertEquals(1, policy.tier, "recovery must be slow and evidence-based")
        frame += 33_333_333L
        policy.record(frame, renderNanos = 1_000_000L)
        assertEquals(0, policy.tier, "180 cheap frames earn the full tier back")
    }

    @Test
    fun policyNeverDropsBelowTheLowestTier() {
        val policy = BackgroundFramePolicy()
        var frame = 0L
        repeat(60) {
            frame += 100_000_000L
            policy.record(frame, renderNanos = 90_000_000L)
        }
        assertEquals(3, policy.tier, "the policy bottoms out instead of degrading forever")
    }

    @Test
    fun theMotionHoldStopsAndRestartsTheDrift() {
        BackgroundMotion.hold(false)
        assertFalse(BackgroundMotion.resting)
        BackgroundMotion.hold(true)
        assertTrue(BackgroundMotion.resting)
        BackgroundMotion.hold(false)
        assertFalse(BackgroundMotion.resting)
    }

    @Test
    fun policyOnlyRendersWhenTheNextFrameIsDue() {
        val policy = BackgroundFramePolicy()
        assertTrue(policy.isDue(0L), "the first frame is always due")
        policy.record(0L, renderNanos = 1_000_000L)
        assertFalse(policy.isDue(5_000_000L), "an early frame must be skipped")
        assertTrue(policy.isDue(34_000_000L), "the next budgeted frame is due")
    }
}
