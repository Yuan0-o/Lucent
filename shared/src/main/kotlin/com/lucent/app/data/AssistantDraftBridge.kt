package com.lucent.app.data

import android.content.Context
import com.lucent.app.ui.AssistantController

/**
 * INTEGRATION — the seam group B left open for group A's draft area (B-group task 3 x A-group
 * task 10).
 *
 * ### What was left undone, and by whom
 *
 * Group B rebuilt the assistant's confirmation flow so the dialog IS the editor and runs BEFORE
 * anything is written (task B3). That fixed the real defect — you used to approve first and review
 * afterwards, by which point "cancel" meant deleting a note rather than declining one — but it
 * created a new exposure that B could not close on its own: while the dialog is open, the proposed
 * note or task exists ONLY in the dialog's own state. Kill the app there and it is gone.
 *
 * Group A's draft area is exactly the thing that is supposed to catch that (task A10: an abnormal
 * close must land in drafts rather than vanish). B was instructed not to implement it, and left the
 * hook comments instead. This file is that implementation.
 *
 * ### What it does
 *
 * While a create/update confirmation is on screen, the proposal is mirrored into a draft row —
 * `isDraft = true`, so every list query already excludes it and nothing appears anywhere in the UI
 * except the drafts area. The row is removed again the moment the user decides, whichever way they
 * decide.
 *
 * ### Why it is cleared on ALL THREE outcomes, including cancel
 *
 * B's whole argument for the edit-first flow is that cancelling costs exactly zero and leaves
 * exactly nothing behind. A cancel that quietly left a draft lying around would put that back —
 * the user declines something and finds it in their drafts anyway. Same for "keep refining": the
 * conversation continues and the next proposal opens its own dialog, which mirrors itself afresh.
 * And on approve, the tool writes the real item, so keeping the mirror would leave a duplicate.
 *
 * The draft is therefore a crash net for exactly the window it is needed in, and invisible outside
 * it.
 *
 * ### Why only create_*
 *
 * An update_* proposal describes changes to an item that ALREADY EXISTS. Mirroring it into a
 * separate draft row would put a second, near-identical item in front of the user after a crash,
 * with no indication which one is the real one. The original is not at risk — it is untouched on
 * disk until the tool runs — so there is nothing here to rescue. See [shouldMirror].
 */
object AssistantDraftBridge {

    /** The draft row currently mirroring an open confirmation, or null when none is open. */
    @Volatile private var mirroredNoteId: Long? = null

    @Volatile private var mirroredTaskId: Long? = null

    /**
     * True when a proposal of this shape is worth mirroring: it would CREATE something, so the only
     * copy of the content is the one on screen.
     */
    fun shouldMirror(toolName: String): Boolean =
        toolName == "create_note" || toolName == "create_task"

    /**
     * Mirror [edits] into the draft area, replacing any previous mirror. Safe to call repeatedly —
     * the dialog re-mirrors as the user types, and each call updates the same row rather than
     * accumulating rows.
     */
    suspend fun mirror(
        appContext: Context,
        toolName: String,
        edits: Map<String, String>
    ) {
        if (!shouldMirror(toolName)) return
        val db = AppDatabase.getInstance(appContext)
        val now = System.currentTimeMillis()
        when (toolName) {
            "create_note" -> {
                val existing = mirroredNoteId?.let { db.noteDao().getByIdOnce(it) }
                val row = (existing ?: Note(title = "", body = "", updatedAt = now)).copy(
                    title = edits["title"].orEmpty(),
                    body = edits["body"] ?: edits["content"].orEmpty(),
                    tags = edits["tags"] ?: existing?.tags.orEmpty(),
                    checklist = edits["checklist"] ?: existing?.checklist ?: "[]",
                    isChecklist = (edits["checklist"] ?: existing?.checklist).isNullOrBlank().not() &&
                        (edits["checklist"] ?: existing?.checklist) != "[]",
                    updatedAt = now,
                    isDraft = true,
                    draftSavedAt = now
                )
                mirroredNoteId = if (existing == null) {
                    db.noteDao().insert(row)
                } else {
                    db.noteDao().update(row); existing.id
                }
            }
            "create_task" -> {
                val existing = mirroredTaskId?.let { db.taskDao().getByIdOnce(it) }
                val row = (existing ?: Task(title = "", createdAt = now)).copy(
                    title = edits["title"].orEmpty(),
                    notes = edits["notes"].orEmpty(),
                    subtasks = edits["subtasks"] ?: existing?.subtasks ?: "[]",
                    isDraft = true,
                    draftSavedAt = now
                )
                mirroredTaskId = if (existing == null) {
                    db.taskDao().insert(row)
                } else {
                    db.taskDao().update(row); existing.id
                }
            }
        }
    }

    /**
     * Drop the mirror. Called from every branch of
     * [AssistantController.resolveConfirmation] — approve, cancel and refine alike.
     *
     * Deleted outright rather than moved to the trash: this row was never something the user made,
     * it was a safety copy of something they were still deciding about, and a trash full of
     * proposals nobody accepted is noise.
     */
    suspend fun clear(appContext: Context) {
        val db = AppDatabase.getInstance(appContext)
        mirroredNoteId?.let { id ->
            db.noteDao().getByIdOnce(id)?.let { if (it.isDraft) db.noteDao().delete(it) }
        }
        mirroredTaskId?.let { id ->
            db.taskDao().getByIdOnce(id)?.let { if (it.isDraft) db.taskDao().delete(it) }
        }
        mirroredNoteId = null
        mirroredTaskId = null
    }

    /**
     * Forget the ids WITHOUT touching the database — used when the process is going away with a
     * confirmation still open. The row deliberately stays behind: that is the crash this whole file
     * exists for, and A's startup prompt ("you had unsaved edits, open them?") is what surfaces it.
     */
    fun forgetWithoutDeleting() {
        mirroredNoteId = null
        mirroredTaskId = null
    }
}
