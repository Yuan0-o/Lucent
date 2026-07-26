package com.lucent.app.ui

/**
 * Splits a home list into four labelled sections — **Pinned**, **Recent**, **Today** and
 * **Older** — instead
 * of one long undifferentiated scroll.
 *
 * - **Pinned** holds everything the user pinned, and it is always first (task A13). Pinning is an
 *   explicit "keep this at the top" instruction, but the sort that honoured it ran *before* the
 *   sectioning, which then re-bucketed the results by date — so a pinned note that hadn't been
 *   touched today landed under "Today" or even "Older", i.e. below the unpinned ones. Pulling
 *   pinned items out first is what makes the instruction mean what it says.
 * - **Recent** holds up to [maxRecent] of the most *active* items, ranked by the usage-frequency
 *   score (see [com.lucent.app.data.UsageTracker]) — the things you keep coming back to or just
 *   edited, surfaced whether or not they happen to be from today.
 * - **Today** holds the rest of the items whose own timestamp falls on the current calendar day.
 * - **Older** holds everything else.
 *
 * The three are **disjoint**: an item chosen for Recent never also appears under Today or Older, so
 * nothing is shown twice. Within Recent, items are ordered by score (most active first); Today and
 * Older keep whatever order the caller already sorted them into (the user's chosen sort), so picking
 * "Title A–Z" still orders those sections alphabetically.
 */
enum class HomeSection {
    PINNED, RECENT, TODAY, OLDER;

    // Live i18n lookup (localization task); call sites keep reading `section.label`.
    val label: String
        get() = when (this) {
            PINNED -> com.lucent.app.i18n.S.sectionPinned
            RECENT -> com.lucent.app.i18n.S.sectionRecent
            TODAY -> com.lucent.app.i18n.S.sectionToday
            OLDER -> com.lucent.app.i18n.S.sectionOlder
        }
}

data class Sectioned<T>(
    val pinned: List<T>,
    val recent: List<T>,
    val today: List<T>,
    val older: List<T>
) {
    /** Section/list pairs in display order, skipping any that are empty. */
    fun nonEmpty(): List<Pair<HomeSection, List<T>>> = buildList {
        if (pinned.isNotEmpty()) add(HomeSection.PINNED to pinned)
        if (recent.isNotEmpty()) add(HomeSection.RECENT to recent)
        if (today.isNotEmpty()) add(HomeSection.TODAY to today)
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
    /**
     * When true, Recent is *displayed* in the caller's order rather than by activity score.
     *
     * Which items belong in Recent is still decided by how much they have been used — that is what
     * makes the section mean anything. But under the Custom sort the caller's order IS the user's
     * hand-made arrangement, and re-sorting the bucket by activity would throw away every drag they
     * performed inside it. Membership by activity, sequence by the user.
     */
    orderWithinSections: Boolean = false
): Sectioned<T> {
    if (items.isEmpty()) return Sectioned(emptyList(), emptyList(), emptyList(), emptyList())

    // Task A13: pinned first, and removed from consideration for every later bucket — the four
    // sections stay disjoint, so a pinned item is shown once, at the top, and never again further
    // down. Their relative order is whatever the caller's sort produced.
    val pinned = items.filter { isPinned(it) }
    val unpinned = items.filterNot { isPinned(it) }

    // Pick the most active items for Recent. Ties fall back to the newer timestamp so the choice is
    // stable and sensible rather than arbitrary.
    val recentPicked = unpinned
        .sortedWith(
            compareByDescending<T> { activityScore(it) }.thenByDescending { timestamp(it) }
        )
        .take(maxRecent.coerceAtLeast(0))
    val recentIds = recentPicked.map(id).toHashSet()
    // Chosen by activity, then put back into the caller's sequence when asked. Every other bucket
    // already preserves it — Recent was the only one that re-sorted, and therefore the only one
    // where dragging a card had no lasting effect.
    val recent = if (orderWithinSections) unpinned.filter { id(it) in recentIds } else recentPicked

    val remaining = unpinned.filter { id(it) !in recentIds }
    val today = remaining.filter { sameLocalDay(timestamp(it), now) }
    val older = remaining.filter { !sameLocalDay(timestamp(it), now) }

    return Sectioned(pinned = pinned, recent = recent, today = today, older = older)
}
