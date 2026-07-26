package com.lucent.app.data

/**
 * How a restore treats data that is already on this device (C-group task 16).
 *
 * ### The two modes
 *
 * **[PARALLEL]** — everything in the backup is added *alongside* what is here. Nothing existing is
 * ever modified or removed. This is what the app has always done and it stays the default, because
 * it is the only mode that cannot lose anything: the worst case is a duplicate, and a duplicate is
 * a nuisance you can delete, whereas an overwrite is a decision you cannot take back.
 *
 * **[OVERWRITE]** — for each item in the backup, look for the same item here. If it is here and the
 * local copy is *older*, replace it. If it is here and the local copy is *newer or the same*, keep
 * what is here. If it is not here at all, create it. This is what you want when the backup is the
 * authoritative copy — restoring a phone, or pulling changes back from another machine.
 *
 * ### What "the same item" means, and why it is not the id
 *
 * Row ids do not survive a restore: Room assigns fresh ones on insert, and the desktop store does
 * the same. Two devices that both created a note therefore have no id in common even for a note
 * that came from the same backup. So identity is derived from content:
 *
 *  - **Notes** are identified by **title**, compared trimmed and case-insensitively — the same rule
 *    `NoteLinks` already uses to resolve `[[wiki]]` links. Using the same rule in both places is
 *    deliberate: if a link considers two titles the same note, a restore had better agree, or a
 *    restore can silently split a note away from everything that points at it.
 *  - **Tasks** are identified by **title plus createdAt**. A task's title alone is a bad key —
 *    "Buy milk" recurs — but its creation instant is stable, travels in the backup, and is never
 *    edited, so the pair identifies one specific task.
 *
 * ### What "older" means
 *
 *  - **Notes** carry `updatedAt`, so the comparison is exact and direction is meaningful.
 *  - **Tasks carry no modification timestamp at all** — the entity has `createdAt`, `dueAt` and
 *    `completedAt`, and nothing that records "this row was last edited at". That is worth stating
 *    plainly rather than papering over: for tasks, OVERWRITE cannot ask "which is newer", so it
 *    applies the mode's plain meaning and lets the **backup win**. A user who chose "overwrite" is
 *    telling us the file is authoritative; for notes we can do better than that and we do, and for
 *    tasks we do exactly what was asked and no more.
 *
 * ### Why this is a separate file
 *
 * The decision — insert, replace, or skip — is a pure function of two values, so it lives here
 * where it can be reasoned about and tested without a database. `BackupManager` calls it and then
 * does the I/O. Restore logic that is tangled up with JSON parsing and DAO calls is restore logic
 * nobody can check, and this is the one code path in the app where being wrong destroys data.
 */
enum class ImportMode {
    /** Add everything alongside what is already here. Never modifies an existing row. */
    PARALLEL,

    /** Replace an older local copy; create anything missing; keep a newer local copy. */
    OVERWRITE;

    companion object {
        /** The safe default. See the class comment for why it is the one that cannot lose data. */
        val DEFAULT = PARALLEL
    }
}

/** What a restore should do with one incoming item. */
enum class ImportAction {
    /** Create a new row. */
    INSERT,

    /** Replace the matched local row, keeping its id. */
    REPLACE,

    /** Do nothing — the local copy is an exact duplicate, or is newer than the backup's. */
    SKIP
}

/**
 * The restore decision table. Pure; no database, no JSON, no context.
 *
 * Both functions take the *matched* local row (or null when nothing matched) so the caller does the
 * lookup once and this stays free of query concerns.
 */
object ImportDecision {

    /** Notes match on trimmed, case-insensitive title — the same rule `NoteLinks` resolves with. */
    fun noteKey(title: String): String = title.trim().lowercase()

    /** Tasks match on title plus creation instant; see the [ImportMode] comment for why. */
    fun taskKey(title: String, createdAt: Long): String = "${title.trim().lowercase()}\u0000$createdAt"

    /**
     * What to do with an incoming note.
     *
     * @param mode              the user's choice.
     * @param localUpdatedAt    `updatedAt` of the matched local note, or null if none matched.
     * @param backupUpdatedAt   `updatedAt` carried in the backup.
     * @param exactDuplicate    whether the matched local note is byte-identical to the backup's.
     *                          Checked by the caller because it needs the body, which this does not.
     */
    fun forNote(
        mode: ImportMode,
        localUpdatedAt: Long?,
        backupUpdatedAt: Long,
        exactDuplicate: Boolean
    ): ImportAction {
        // An exact duplicate is skipped in BOTH modes. In PARALLEL that is the long-standing
        // behaviour; in OVERWRITE, replacing a row with an identical copy would be a write that
        // changes nothing while resetting nothing useful — pure churn.
        if (exactDuplicate) return ImportAction.SKIP
        if (localUpdatedAt == null) return ImportAction.INSERT
        return when (mode) {
            // PARALLEL never touches an existing row: a same-titled note is a *different* note as
            // far as this mode is concerned, and it gets its own row.
            ImportMode.PARALLEL -> ImportAction.INSERT
            // Strictly newer wins. Equal timestamps are NOT overwritten: two rows that claim the
            // same modification instant carry no evidence which is later, and in the absence of
            // evidence the copy the user already has is the one to keep.
            ImportMode.OVERWRITE ->
                if (backupUpdatedAt > localUpdatedAt) ImportAction.REPLACE else ImportAction.SKIP
        }
    }

    /**
     * What to do with an incoming task.
     *
     * There is no timestamp to compare (see the [ImportMode] comment), so OVERWRITE replaces any
     * match and PARALLEL inserts alongside it — except for an exact duplicate, which is skipped in
     * both, exactly as for notes.
     */
    fun forTask(
        mode: ImportMode,
        matchedLocally: Boolean,
        exactDuplicate: Boolean
    ): ImportAction = when {
        exactDuplicate -> ImportAction.SKIP
        !matchedLocally -> ImportAction.INSERT
        mode == ImportMode.PARALLEL -> ImportAction.INSERT
        else -> ImportAction.REPLACE
    }
}
