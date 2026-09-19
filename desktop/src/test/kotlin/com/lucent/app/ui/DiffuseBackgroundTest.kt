package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * v2.7.9 — the diffuse gradient replaced the drifting blobs, and its two promises are testable
 * without a GPU: the field is a real continuous gradient that actually moves, and the frame policy
 * is what keeps a slow device slow *on purpose* instead of stuttering.
 */
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
    fun fieldIsAContinuumNotFlatBlocks() {
        // A diffuse gradient must produce many distinct shades, not a handful of painted patches.
        val pixels = DiffuseGradientField(32).render(3.0, warmPalette, nightBackdrop, dark = true)
        val distinct = pixels.toSet().size
        assertTrue(distinct > 200, "expected a continuum of shades, got $distinct")
    }

    @Test
    fun paletteAndBackdropBothReachTheTexture() {
        val field = DiffuseGradientField(20)
        val left = field.render(2.0, warmPalette, nightBackdrop, dark = true).copyOf()
        val recoloured = field.render(2.0, intArrayOf(0xFF00FF00.toInt()), nightBackdrop, dark = true).copyOf()
        assertFalse(left.contentEquals(recoloured), "a palette change must repaint the texture")

        // A palette of fully transparent colours leaves the backdrop untouched.
        val clear = field.render(2.0, intArrayOf(0x00000000), nightBackdrop, dark = true)
        assertTrue(clear.all { it == nightBackdrop or (it ushr 24) == 255 })
    }

    @Test
    fun timelineNeverJumpsAfterAStall() {
        val timeline = BackgroundTimeline()
        timeline.advance(0L)
        val steady = timeline.advance(33_000_000L)
        assertEquals(0.033, steady, 1e-6)
        // A ten-second stall (app resumed) must not teleport the gradient.
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
    fun policyOnlyRendersWhenTheNextFrameIsDue() {
        val policy = BackgroundFramePolicy()
        assertTrue(policy.isDue(0L), "the first frame is always due")
        policy.record(0L, renderNanos = 1_000_000L)
        assertFalse(policy.isDue(5_000_000L), "an early frame must be skipped")
        assertTrue(policy.isDue(34_000_000L), "the next budgeted frame is due")
    }
}
