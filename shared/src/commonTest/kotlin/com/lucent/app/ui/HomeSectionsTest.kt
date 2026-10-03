package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeSectionsTest {

    private data class Item(val id: Long, val at: Long, val pinned: Boolean = false)

    private val now = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    private val items = listOf(
        Item(id = 1, at = now, pinned = true),
        Item(id = 2, at = now - 3_600_000L),
        Item(id = 3, at = now),
        Item(id = 4, at = now - 2 * day),
        Item(id = 5, at = now - 10 * day)
    )

    private fun sectioned() = sectionHomeItems(
        items = items,
        now = now,
        maxRecent = 1,
        id = { it.id },
        timestamp = { it.at },
        activityScore = { if (it.id == 2L) 10.0 else 0.0 },
        isPinned = { it.pinned }
    )

    @Test
    fun everyBucketIsPresentAndInDisplayOrder() {
        val order = sectioned().nonEmpty().map { it.first }
        assertEquals(
            listOf(
                HomeSection.PINNED,
                HomeSection.RECENT,
                HomeSection.TODAY,
                HomeSection.THREE_DAYS,
                HomeSection.OLDER
            ),
            order
        )
    }

    @Test
    fun everyItemSitsInExactlyOneBucket() {
        val ids = sectioned().nonEmpty().flatMap { (_, list) -> list.map { it.id } }
        assertEquals(items.map { it.id }, ids.sorted())
        assertEquals(ids.size, ids.toSet().size, "an item was placed in two sections")
    }

    @Test
    fun emptyBucketsAreSkipped() {
        val recentOnly = listOf(Item(id = 7, at = now))
        val sections = sectionHomeItems(
            items = recentOnly,
            now = now,
            maxRecent = 6,
            id = { it.id },
            timestamp = { it.at },
            activityScore = { 0.0 }
        )
        val buckets = sections.nonEmpty().map { it.first }
        assertTrue(HomeSection.RECENT in buckets)
        assertTrue(HomeSection.OLDER !in buckets)
        assertTrue(HomeSection.THREE_DAYS !in buckets)
    }

    @Test
    fun pinnedItemsNeverAppearTwice() {
        val buckets = sectioned().nonEmpty()
        val pinnedIds = buckets.first { it.first == HomeSection.PINNED }.second.map { it.id }
        assertEquals(listOf(1L), pinnedIds)
        val elsewhere = buckets.filter { it.first != HomeSection.PINNED }.flatMap { (_, l) -> l.map { it.id } }
        assertTrue(pinnedIds.none { it in elsewhere })
    }
}
