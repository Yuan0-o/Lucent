package com.lucent.app.data

import org.json.JSONObject

/**
 * Automatic backup (C-group task 7) — the platform-neutral half.
 *
 * ### What this file is, and is not
 *
 * This is the **policy**: what gets backed up, when the next run is due, what to name the file, and
 * which old files to delete. It is pure — no `WorkManager`, no `ScheduledExecutorService`, no file
 * I/O — so both platforms share one definition of the rules and only the *triggering* differs
 * (Android: `WorkManager`; desktop: a scheduled executor started with the app; see the per-platform
 * runners).
 *
 * That split is the point. "Every six hours" means the same thing on both, retention means the same
 * thing on both, and the answer to "why did my Windows copy keep five files and my phone three?"
 * is that it does not.
 *
 * ### The six-hour floor
 *
 * [MIN_INTERVAL_HOURS] is 6, as specified, and it is a floor rather than a suggestion. A full
 * backup reads the whole database and copies every attachment; running that hourly on a phone is a
 * battery and storage cost with almost no benefit, since the notes it protects change far more
 * slowly than that. Android's `WorkManager` independently refuses periodic work under 15 minutes,
 * so a lower value could not be honoured anyway — better to state a limit than to accept a number
 * and quietly ignore it.
 *
 * ### What is excluded, and why it is worth saying out loud
 *
 * Local model files are excluded — that is the requirement, and it is also the right call:
 * [BackupManager.BackupModule.LOCAL_MODEL_FILES] can be several gigabytes, it is the one thing here that can be
 * downloaded again, and copying it every six hours would fill a disk to protect something nobody
 * would miss. Everything else travels: notes, tasks, chats, settings, API profiles, the local
 * assistant's configuration and the attachments.
 *
 * ### Retention
 *
 * Keeping exactly one file would mean a corrupt or half-written run destroys the only good copy;
 * keeping every file fills the folder. So [DEFAULT_KEEP] recent files are kept and older ones are
 * deleted **after** a new one is successfully written, never before — a deletion pass that runs
 * first would, on a failing run, leave the user with fewer backups than they started with.
 */
object AutoBackup {

    /** The hard floor on how often an automatic backup may run. */
    const val MIN_INTERVAL_HOURS = 6

    /** Offered intervals, in hours. Fixed choices rather than free entry: see [MIN_INTERVAL_HOURS]. */
    val INTERVAL_CHOICES = listOf(6, 12, 24, 24 * 7)

    /** Default interval — twice a day is frequent enough for a notes app and cheap enough to ignore. */
    const val DEFAULT_INTERVAL_HOURS = 12

    /** How many generated files to keep. */
    const val DEFAULT_KEEP = 5
    val KEEP_RANGE = 1..30

    /** File name prefix, so the retention sweep can recognise its own output and nothing else's. */
    const val FILE_PREFIX = "lucent-auto-"
    const val FILE_SUFFIX = ".lcb"

    /**
     * Exactly what an automatic backup contains: everything except the local model files.
     *
     * This delegates to [BackupManager.DEFAULT_MODULES], which is already defined as "everything
     * except the model files", rather than restating the same subtraction here. Two independent
     * definitions of "what a backup contains" would agree today and drift the first time a module
     * is added, and the drift would be invisible — the failure mode is "your automatic backups
     * quietly stopped covering something", which nobody discovers until they need the backup.
     */
    val MODULES: Set<BackupManager.BackupModule>
        get() = BackupManager.DEFAULT_MODULES

    /**
     * The persisted state of the feature. One JSON blob, for the same reason
     * [PasswordAttempts.State] is one: the fields are only ever meaningful together.
     *
     * @param enabled       whether automatic backup is on.
     * @param folderUri     where to write. Opaque here on purpose — Android stores a SAF tree Uri,
     *                      desktop an absolute path, and this file must not care which.
     * @param intervalHours how often, in hours.
     * @param keep          how many generated files to retain.
     * @param lastRunAt     wall-clock millis of the last SUCCESSFUL run, or 0 if never.
     * @param lastError     the last failure's short reason, or "" — surfaced in Settings, because a
     *                      backup feature that has been failing for a month in silence is worse
     *                      than no backup feature, which at least nobody was relying on.
     */
    data class State(
        val enabled: Boolean = false,
        val folderUri: String = "",
        val intervalHours: Int = DEFAULT_INTERVAL_HOURS,
        val keep: Int = DEFAULT_KEEP,
        val lastRunAt: Long = 0L,
        val lastError: String = ""
    ) {
        /** Whether this state can actually produce a backup. A folder is not optional. */
        val runnable: Boolean get() = enabled && folderUri.isNotBlank()

        fun toJson(): String = JSONObject()
            .put("enabled", enabled)
            .put("folderUri", folderUri)
            .put("intervalHours", intervalHours)
            .put("keep", keep)
            .put("lastRunAt", lastRunAt)
            .put("lastError", lastError)
            .toString()

        companion object {
            val EMPTY = State()

            fun fromJson(json: String): State {
                if (json.isBlank()) return EMPTY
                return try {
                    val o = JSONObject(json)
                    State(
                        enabled = o.optBoolean("enabled", false),
                        folderUri = o.optString("folderUri", ""),
                        intervalHours = o.optInt("intervalHours", DEFAULT_INTERVAL_HOURS)
                            .coerceAtLeast(MIN_INTERVAL_HOURS),
                        keep = o.optInt("keep", DEFAULT_KEEP).coerceIn(KEEP_RANGE),
                        lastRunAt = o.optLong("lastRunAt", 0L),
                        lastError = o.optString("lastError", "")
                    )
                } catch (_: Throwable) {
                    // Unlike the throttle record, an unreadable state here fails to OFF. The
                    // conservative direction differs because the risk differs: a lockout that fails
                    // open is a security hole, whereas a backup that fails open would start writing
                    // files to a folder the user may no longer have chosen.
                    EMPTY
                }
            }
        }
    }

    /**
     * Wall-clock millis at which the next run becomes due, given the last successful run.
     *
     * A [State.lastRunAt] of 0 (never run) returns 0, i.e. "due now" — turning the feature on
     * produces a backup promptly rather than after a silent first interval, so the user can see
     * that it works while they are still looking at the screen that switched it on.
     */
    fun nextDueAt(state: State): Long =
        if (state.lastRunAt <= 0L) 0L
        else state.lastRunAt + state.intervalHours.coerceAtLeast(MIN_INTERVAL_HOURS) * 3_600_000L

    /** Whether a run is due at [now]. */
    fun isDue(state: State, now: Long = System.currentTimeMillis()): Boolean =
        state.runnable && now >= nextDueAt(state)

    /**
     * The file name for a run at [timestamp].
     *
     * Sorts chronologically as text, which is what makes [filesToDelete] able to work on names
     * alone — it never has to stat a file or trust a modification time that a copy or a sync client
     * may have rewritten.
     */
    fun fileNameFor(timestamp: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
        return "$FILE_PREFIX${fmt.format(java.util.Date(timestamp))}$FILE_SUFFIX"
    }

    /** Whether [name] is one of ours, and therefore ours to delete. */
    fun isOurs(name: String): Boolean = name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)

    /**
     * Which of [existingNames] to delete to honour [keep].
     *
     * Only files matching [isOurs] are ever considered: a folder is the user's, and it may well
     * hold manual `.lcb` exports they made themselves. Deleting one of those because it happened to
     * share a folder would be indefensible, so the prefix is the entire licence to delete.
     *
     * Call this only after a new backup has been written successfully — see the class comment.
     */
    fun filesToDelete(existingNames: List<String>, keep: Int): List<String> {
        val ours = existingNames.filter { isOurs(it) }.sorted()
        val surplus = ours.size - keep.coerceIn(KEEP_RANGE)
        return if (surplus <= 0) emptyList() else ours.take(surplus)
    }
}
