package com.lucent.app.ui

private const val THREE_DAYS_MS = 3L * 24L * 60L * 60L * 1000L

enum class HomeSection {
    PINNED, RECENT, TODAY, THREE_DAYS, OLDER;

    val label: String
        get() = when (this) {
            PINNED -> com.lucent.app.i18n.S.sectionPinned
            RECENT -> com.lucent.app.i18n.S.sectionRecent
            TODAY -> com.lucent.app.i18n.S.sectionToday
            THREE_DAYS -> com.lucent.app.i18n.S.sectionThreeDays
            OLDER -> com.lucent.app.i18n.S.sectionOlder
        }
}

data class Sectioned<T>(
    val pinned: List<T>,
    val recent: List<T>,
    val today: List<T>,
    val threeDays: List<T>,
    val older: List<T>
) {
    fun nonEmpty(): List<Pair<HomeSection, List<T>>> = buildList {
        if (pinned.isNotEmpty()) add(HomeSection.PINNED to pinned)
        if (recent.isNotEmpty()) add(HomeSection.RECENT to recent)
        if (today.isNotEmpty()) add(HomeSection.TODAY to today)
        if (threeDays.isNotEmpty()) add(HomeSection.THREE_DAYS to threeDays)
        if (older.isNotEmpty()) add(HomeSection.OLDER to older)
    }
}

fun <T> sectionHomeItems(
    items: List<T>,
    now: Long,
    maxRecent: Int,
    id: (T) -> Long,
    timestamp: (T) -> Long,
    activityScore: (T) -> Double,
    isPinned: (T) -> Boolean = { false },
    orderWithinSections: Boolean = false
): Sectioned<T> {
    if (items.isEmpty()) return Sectioned(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    val pinned = items.filter { isPinned(it) }
    val unpinned = items.filterNot { isPinned(it) }

    val recentPicked = unpinned
        .sortedWith(
            compareByDescending<T> { activityScore(it) }.thenByDescending { timestamp(it) }
        )
        .take(maxRecent.coerceAtLeast(0))
    val recentIds = recentPicked.map(id).toHashSet()
    val recent = if (orderWithinSections) unpinned.filter { id(it) in recentIds } else recentPicked

    val remaining = unpinned.filter { id(it) !in recentIds }
    val today = remaining.filter { sameLocalDay(timestamp(it), now) }
    val notToday = remaining.filter { !sameLocalDay(timestamp(it), now) }
    val threeDays = notToday.filter { now - timestamp(it) <= THREE_DAYS_MS }
    val older = notToday.filter { now - timestamp(it) > THREE_DAYS_MS }

    return Sectioned(pinned = pinned, recent = recent, today = today, threeDays = threeDays, older = older)
}
