package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Characterisation tests for the shared search grammar (P0-5 domain group): [SearchQuery.parse]
 * must split quoted phrases from terms, fold #tag and tag:/is:/has:/priority:/due: filters into
 * their fields, keep unknown tokens searchable as text, and [SearchQuery.matches] must apply every
 * filter to notes and tasks without ever blurring the two kinds.
 */
class SearchQueryTest {

    private fun note(
        title: String = "",
        body: String = "",
        tags: String = "",
        pinned: Boolean = false,
        archived: Boolean = false,
        checklist: Boolean = false,
        checklistJson: String = "[]",
        attachments: String = "[]"
    ) = Note(
        title = title, body = body, tags = tags, pinned = pinned,
        archived = archived, isChecklist = checklist, checklist = checklistJson,
        attachments = attachments
    )

    private fun task(
        title: String = "",
        notes: String = "",
        isDone: Boolean = false,
        pinned: Boolean = false,
        priority: Int = 0,
        dueAt: Long? = null,
        reminderEnabled: Boolean = false,
        subtasksJson: String = "[]",
        attachments: String = "[]"
    ) = Task(
        title = title, notes = notes, isDone = isDone, pinned = pinned,
        priority = priority, dueAt = dueAt, reminderEnabled = reminderEnabled,
        subtasks = subtasksJson, attachments = attachments
    )

    // ---- Parsing ----

    @Test
    fun blankQueryIsEmptyAndMatchesEverything() {
        val q = SearchQuery.parse("   ")
        assertTrue(q.isEmpty)
        assertTrue(q.matches(note(title = "anything")))
        assertTrue(q.matches(task(title = "anything")))
    }

    @Test
    fun quotedPhraseIsLiteral() {
        val q = SearchQuery.parse("\"exact phrase\" word")
        assertEquals(listOf("word"), q.terms)
        assertEquals(listOf("exact phrase"), q.phrases)
        // A quoted "tag:x" searches for the text, not the filter.
        val quotedFilter = SearchQuery.parse("\"tag:work\"")
        assertTrue(quotedFilter.tags.isEmpty())
        assertEquals(listOf("tag:work"), quotedFilter.phrases)
    }

    @Test
    fun filtersFoldIntoTheirFields() {
        val q = SearchQuery.parse("budget #home tag:work is:pinned has:attachment")
        assertEquals(listOf("budget"), q.terms)
        assertTrue("home" in q.tags)
        assertTrue("work" in q.tags)
        assertTrue("pinned" in q.flags)
        assertTrue("attachment" in q.has)
    }

    @Test
    fun unknownTokensStaySearchableText() {
        val q = SearchQuery.parse("TODO:fix is:unpinned has:magic")
        // Unknown filter-shaped tokens are treated as literal text so they stay findable.
        assertEquals(listOf("todo:fix", "is:unpinned", "has:magic"), q.terms)
        assertTrue(q.flags.isEmpty())
        assertTrue(q.has.isEmpty())
    }

    // ---- Note matching ----

    @Test
    fun allTermsMustAppearSomewhere() {
        val haystack = note(title = "Trip to Rome", body = "book the hotel and the flight", tags = "travel")
        assertTrue(SearchQuery.parse("rome hotel").matches(haystack))
        assertFalse(SearchQuery.parse("rome paris").matches(haystack))
    }

    @Test
    fun tagFilterNeedsMatchingTag() {
        val n = note(title = "Budget", tags = "work, home")
        assertTrue(SearchQuery.parse("tag:work").matches(n))
        assertTrue(SearchQuery.parse("tag:home").matches(n))
        assertFalse(SearchQuery.parse("tag:family").matches(n))
        // #tag shorthand behaves identically.
        assertTrue(SearchQuery.parse("#home").matches(n))
    }

    @Test
    fun noteFlagsFilterNotes() {
        val pinned = note(title = "Top", pinned = true)
        val archived = note(title = "Old", archived = true)
        val checklistNote = note(title = "List", checklist = true, checklistJson = """[{"text":"a"}]""")
        assertTrue(SearchQuery.parse("is:pinned").matches(pinned))
        assertFalse(SearchQuery.parse("is:pinned").matches(note(title = "Plain")))
        assertTrue(SearchQuery.parse("is:archived").matches(archived))
        assertFalse(SearchQuery.parse("is:archived").matches(note(title = "Live")))
        assertTrue(SearchQuery.parse("is:checklist").matches(checklistNote))
        assertFalse(SearchQuery.parse("is:checklist").matches(note(title = "Text")))
    }

    @Test
    fun taskOnlyFlagsNeverMatchNotes() {
        // is:done / is:overdue on the notes side must yield nothing, not everything.
        assertFalse(SearchQuery.parse("is:done").matches(note(title = "Anything")))
        assertFalse(SearchQuery.parse("is:overdue").matches(note(title = "Anything")))
    }

    // ---- Task matching ----

    @Test
    fun taskFiltersApplyToTasks() {
        val done = task(title = "Pay rent", isDone = true)
        val pending = task(title = "Buy milk")
        val pinned = task(title = "Top task", pinned = true)
        val high = task(title = "Urgent", priority = TaskPriority.HIGH.value)
        assertTrue(SearchQuery.parse("is:done").matches(done))
        assertFalse(SearchQuery.parse("is:done").matches(pending))
        assertTrue(SearchQuery.parse("is:pinned").matches(pinned))
        assertFalse(SearchQuery.parse("is:pinned").matches(pending))
        assertTrue(SearchQuery.parse("priority:high").matches(high))
        assertFalse(SearchQuery.parse("priority:high").matches(task(title = "Low", priority = 0)))
    }

    @Test
    fun dueWindowsFilterByClock() {
        val now = 1_700_000_000_000L // a fixed instant for deterministic tests
        val overdue = task(title = "Late", dueAt = now - 3_600_000L)
        val todayDue = task(title = "Today", dueAt = now + 3_600_000L)
        val noDue = task(title = "None")
        assertTrue(SearchQuery.parse("due:overdue").matches(overdue, now))
        assertFalse(SearchQuery.parse("due:overdue").matches(todayDue, now))
        assertFalse(SearchQuery.parse("due:overdue").matches(noDue, now))
        // due:window on a task with no due date can never match.
        assertFalse(SearchQuery.parse("due:today").matches(noDue, now))
    }

    @Test
    fun noteOnlyFiltersNeverMatchTasks() {
        assertFalse(SearchQuery.parse("is:archived").matches(task(title = "Anything")))
        assertFalse(SearchQuery.parse("is:checklist").matches(task(title = "Anything")))
    }

    @Test
    fun tagFilterNeverMatchesTask() {
        assertFalse(SearchQuery.parse("tag:work").matches(task(title = "Anything")))
    }

    @Test
    fun hasAttachmentRequiresRealAttachments() {
        val withFile = note(title = "Receipt", attachments = """[{"mime":"image/png","data":"1","name":"r.png"}]""")
        assertTrue(SearchQuery.parse("has:attachment").matches(withFile))
        assertFalse(SearchQuery.parse("has:attachment").matches(note(title = "Empty")))
    }

    // ---- Ranking ----

    @Test
    fun titleHitsOutrankBodyHits() {
        val titleHit = note(title = "Budget 2026", body = "nothing about budgets")
        val bodyHit = note(title = "Random", body = "budget discussion here")
        val q = SearchQuery.parse("budget")
        val rTitle = q.rank(titleHit)
        val rBody = q.rank(bodyHit)
        assertTrue(rTitle > rBody)
        assertTrue(rTitle > 0)
    }

    @Test
    fun emptyQueryRanksZero() {
        assertEquals(0, SearchQuery.parse("").rank(note(title = "x")))
    }
}
