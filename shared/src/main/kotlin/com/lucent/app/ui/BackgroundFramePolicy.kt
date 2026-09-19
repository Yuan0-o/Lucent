package com.lucent.app.ui

internal class BackgroundTimeline {
    private var previousNanos: Long? = null
    private var elapsedNanos = 0L
    val seconds: Double get() = elapsedNanos / 1_000_000_000.0

    fun advance(frameNanos: Long): Double {
        previousNanos?.let { previous ->
            elapsedNanos += (frameNanos - previous).coerceIn(0L, 100_000_000L)
        }
        previousNanos = frameNanos
        return seconds
    }

    fun pause() { previousNanos = null }
}

internal class BackgroundFramePolicy {
    var tier: Int = 0
        private set
    private var slowFrames = 0
    private var fastFrames = 0
    private var lastFrameNanos: Long? = null
    val edge: Int get() = EDGES[tier]
    val intervalNanos: Long get() = INTERVALS[tier]

    fun isDue(frameNanos: Long): Boolean = lastFrameNanos?.let {
        frameNanos - it >= intervalNanos - 1_000_000L
    } ?: true

    fun record(frameNanos: Long, renderNanos: Long): Boolean {
        val gap = lastFrameNanos?.let { (frameNanos - it).coerceAtLeast(0L) }
        lastFrameNanos = frameNanos
        val slow = renderNanos > intervalNanos / 2 ||
            (gap != null && gap > intervalNanos * 5 / 3)
        val fast = renderNanos < intervalNanos / 5 &&
            (gap == null || gap <= intervalNanos * 5 / 4)
        slowFrames = if (slow) slowFrames + 1 else 0
        fastFrames = if (fast) fastFrames + 1 else 0
        val previousTier = tier
        if (slowFrames >= 6 && tier < EDGES.lastIndex) {
            tier++
            slowFrames = 0
            fastFrames = 0
        } else if (fastFrames >= 180 && tier > 0) {
            tier--
            fastFrames = 0
            slowFrames = 0
        }
        return previousTier != tier
    }

    fun resume() {
        lastFrameNanos = null
        slowFrames = 0
        fastFrames = 0
    }

    private companion object {
        val EDGES = intArrayOf(96, 80, 64, 48)
        val INTERVALS = longArrayOf(33_333_333L, 50_000_000L, 66_666_667L, 100_000_000L)
    }
}
