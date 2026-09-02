package com.lucent.app.data

/**
 * A process-lifetime cache for the handful of *settings* the very first frame renders, seeded
 * synchronously at startup.
 *
 * ### The problem it exists for (B-group task 9)
 *
 * The Assistant screen read the assistant's name with
 * `repo.assistantName.collectAsState(initial = "Lucent")`. That literal is not a default — it is a
 * *placeholder shown to every user*, including the ones who renamed their assistant. DataStore is
 * asynchronous, so for the frame or two before the real value arrived, everyone with a custom name
 * saw "Lucent" and then watched it snap to their own name. Same class of defect as the theme and
 * palette flash [SettingsRepository.startupPrefsOnce] was written to prevent, and the fix is the
 * same shape: read the value once, synchronously, on the startup path, and let the first frame use
 * the real thing.
 *
 * ### Round R1, task 2 — the same defect, with a much louder symptom
 *
 * The home lists read their sort with `collectAsState(initial = "recent")`. For a user whose saved
 * sort is **Custom** — which is to say, anyone who has ever dragged a card — that literal meant the
 * first frame was laid out in RECENCY order, and the real value arriving a frame or two later
 * re-sorted the whole list underneath `Modifier.animateItem`. The result was that every launch
 * *replayed the user's own reordering as an animation*: cards visibly sliding from where they used
 * to be to where the user had already put them, which reads as the app undoing and redoing their
 * work in front of them, and janks the first interactive frame while it does it.
 *
 * So [notesSort] and [tasksSort] ride in the same single startup read, and the lists seed their
 * `collectAsState` from here. The first frame is laid out in the user's own order, and there is
 * nothing left to animate.
 *
 * ### Why null rather than a default string
 *
 * [assistantName] is deliberately nullable and starts null. Null means "not known yet", which is
 * information the UI can act on honestly — it renders no name tag for that frame rather than the
 * wrong one. A non-null default would only move the lie from "Lucent" to whatever else we picked.
 * In practice the value is already populated by the time anything composes, because
 * [SettingsRepository.startupPrefsOnce] is awaited before `setContent`, so the null window is
 * usually zero frames wide. The sort keys follow the same rule for the same reason: a caller that
 * finds null falls back to the same literal it used before, so a failed startup read is no worse
 * than the old behaviour and a successful one is exact.
 *
 * Kept separate from [DataCache], which caches note/task *content* off the database. This one holds
 * settings and is seeded from the same single preferences read startup already performs, so it adds
 * no I/O at all.
 */
object SettingsCache {

    /**
     * The user's assistant name, or null until the first preferences read completes. Plain
     * `@Volatile` rather than Compose state: it is written once on the startup path, before any
     * composition exists, and only ever read as the `initial` seed of a collectAsState — the live
     * Flow remains the source of truth for every subsequent value, so this never becomes a second
     * one that could go stale.
     */
    @Volatile
    var assistantName: String? = null
        private set

    /**
     * The persisted Notes / Tasks sort keys, or null until the startup read completes. Same
     * contract as [assistantName]: seed-only, never a second source of truth. See the class comment
     * for what reading these late actually looked like on screen.
     */
    @Volatile
    var notesSort: String? = null
        private set

    @Volatile
    var tasksSort: String? = null
        private set

    /**
     * The recovery snapshot left behind by the previous run, verbatim (see [SessionRestore]), or
     * null when the app was closed cleanly. Carried here rather than read separately because it is
     * needed *before* the first frame — the prompt is asked once per launch — and it lives in the
     * same preferences file every other startup value does.
     */
    @Volatile
    var sessionSnapshot: String? = null
        private set

    /** Seed the cache from the synchronous startup preferences read. Safe to call more than once. */
    fun seed(prefs: SettingsRepository.StartupPrefs) {
        assistantName = prefs.assistantName
        notesSort = prefs.notesSort
        tasksSort = prefs.tasksSort
        sessionSnapshot = prefs.sessionSnapshot.ifBlank { null }
    }
}
