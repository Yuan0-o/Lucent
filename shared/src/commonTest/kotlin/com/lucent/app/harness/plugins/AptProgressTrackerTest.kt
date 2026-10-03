package com.lucent.app.harness.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AptProgressTrackerTest {
    @Test
    fun testIgnoresLinesBeforeSummary() {
        val calls = mutableListOf<Pair<Float, String>>()
        val tracker = AptProgressTracker { f, s -> calls.add(f to s) }
        tracker.onLine("Get:1 http://mirror/pkg1 1.0 [10 kB]")
        tracker.onLine("Setting up base (1.0) ...")
        assertTrue(calls.isEmpty())
    }

    @Test
    fun testDownloadAndInstallProgress() {
        val calls = mutableListOf<Pair<Float, String>>()
        val tracker = AptProgressTracker { f, s -> calls.add(f to s) }
        tracker.onLine("0 upgraded, 2 newly installed, 0 to remove and 0 not upgraded.")
        assertTrue(calls.isEmpty())
        tracker.onLine("Get:1 http://mirror/pkg1 1.0 [10 kB]")
        assertEquals(0.60f + 0.15f * 0.5f, calls.last().first, 0.001f)
        assertEquals("downloading packages", calls.last().second)
        tracker.onLine("Get:2 http://mirror/pkg2 2.0 [20 kB]")
        assertEquals(0.75f, calls.last().first, 0.001f)
        tracker.onLine("Unpacking pkg1 (1.0) ...")
        assertEquals(0.75f + 0.24f * 0.25f, calls.last().first, 0.001f)
        assertEquals("installing packages", calls.last().second)
        tracker.onLine("Setting up pkg1 (1.0) ...")
        tracker.onLine("Unpacking pkg2 (2.0) ...")
        tracker.onLine("Setting up pkg2 (2.0) ...")
        assertEquals(0.99f, calls.last().first, 0.001f)
    }

    @Test
    fun testResetsOnRetry() {
        val calls = mutableListOf<Pair<Float, String>>()
        val tracker = AptProgressTracker { f, s -> calls.add(f to s) }
        tracker.onLine("0 upgraded, 2 newly installed, 0 to remove and 0 not upgraded.")
        tracker.onLine("Get:1 http://mirror/pkg1 1.0 [10 kB]")
        tracker.onLine("0 upgraded, 2 newly installed, 0 to remove and 0 not upgraded.")
        tracker.onLine("Get:1 http://mirror/pkg1 1.0 [10 kB]")
        assertEquals(0.60f + 0.15f * 0.5f, calls.last().first, 0.001f)
    }
}
