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
 * ### Why null rather than a default string
 *
 * [assistantName] is deliberately nullable and starts null. Null means "not known yet", which is
 * information the UI can act on honestly — it renders no name tag for that frame rather than the
 * wrong one. A non-null default would only move the lie from "Lucent" to whatever else we picked.
 * In practice the value is already populated by the time anything composes, because
 * [SettingsRepository.startupPrefsOnce] is awaited before `setContent`, so the null window is
 * usually zero frames wide.
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

    /** Seed the cache from the synchronous startup preferences read. Safe to call more than once. */
    fun seed(prefs: SettingsRepository.StartupPrefs) {
        assistantName = prefs.assistantName
    }
}
