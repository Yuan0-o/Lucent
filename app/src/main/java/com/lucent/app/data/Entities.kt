package com.lucent.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    // Indices for the list queries (settings task 8). The home grid filters on archived + trashedAt
    // and sorts by updatedAt; the archive and trash screens filter on archived / trashedAt. Indexing
    // those columns lets SQLite satisfy the WHERE/ORDER BY without scanning every row. Names follow
    // Room's own convention (index_<table>_<column>) so a migrated DB and a freshly created one
    // match and schema validation passes — see MIGRATION_10_11, which creates these by those names.
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["archived"]),
        Index(value = ["trashedAt"])
    ]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val tags: String = "",
    // JSON array of attachments: [{"mime":..,"data":<id>,"name":..}, ...]
    // With disk-backed storage, "data" is now an AttachmentStore id (a UUID string) instead
    // of a Base64 payload. Legacy rows still hold Base64 until the startup migration rewrites
    // them; see AttachmentMigration.
    val attachments: String = "[]",
    // Archiving: an archived note is hidden from the Notes home page and only shown on the
    // dedicated archive screen. archivedAt records when it was archived so the archive can sort
    // by time; it is null for notes that have never been archived.
    val archived: Boolean = false,
    val archivedAt: Long? = null,
    // Pinned notes float to the top of the home list regardless of the chosen sort order.
    val pinned: Boolean = false,
    // A NoteColor key ("" = default/no tint, otherwise "red"/"orange"/... — see ui/NoteColors.kt).
    // Rendered as a tinted frosted-glass card so colour-coding still reads as glass, not a flat chip.
    val color: String = "",
    // JSON array of checklist items: [{"id":..,"text":..,"done":..}, ...] — see Checklist.kt.
    // Only meaningful when isChecklist is true; kept even when false so switching a note back and
    // forth between plain-text and checklist mode never throws away either version of its content.
    val isChecklist: Boolean = false,
    val checklist: String = "[]",
    // Soft-delete: a trashed note is hidden from the home list, the archive, and search, and is
    // shown only on the Trash screen, until it's restored or TrashCleanup permanently removes it
    // after TrashCleanup.RETENTION_DAYS days. Null = not in the trash.
    val trashedAt: Long? = null,
    // ---- 1.1.0, group A ----------------------------------------------------------------------
    // Task A16: the user's own order. Every existing row migrates in at 0, so they all tie and the
    // sort falls through to its normal secondary key — i.e. "custom order" starts out identical to
    // the order the user was already looking at, rather than scrambling the list the first time it
    // is selected. Real values are assigned on the first drag.
    val manualOrder: Int = 0,
    // Task A10: a note that was saved as (or auto-saved into) a draft. Kept in this table rather
    // than a table of its own for the same reason trashedAt is a column: a draft IS the note, at an
    // earlier moment, and giving it a second home would mean two schemas, two write paths and two
    // things to keep in step whenever a note gains a field.
    val isDraft: Boolean = false,
    val draftSavedAt: Long? = null,
    // Task A21: hidden from every list until the user turns the hidden area on in Settings.
    val hidden: Boolean = false,
    // Task A22: a third kind of note, alongside plain text and checklist. Its own flag rather than
    // converting the pair into an enum column: the existing rows already encode "plain vs
    // checklist" in isChecklist, and rewriting every one of them would be a data migration whose
    // only product is a tidier-looking schema.
    // INTEGRATION (C task 20): rich-text spans for [body], stored BESIDE it as JSON rather than as
    // inline markup, so every existing reader of `body` — assistant tools, exports, backup, the
    // [[wiki]] scanner — keeps seeing plain text. See data/RichText.kt.
    val bodySpans: String = "",
    val isDoodle: Boolean = false,
    // Serialized strokes (see ui/DoodleCanvas.kt). Held separately from `body` for the same reason
    // `checklist` is its own column: switching a note between kinds must never destroy the other
    // kind's content.
    val doodle: String = ""
)

@Entity(
    tableName = "tasks",
    // Indices for the list queries (settings task 8): the active list filters isDone + trashedAt
    // and sorts by createdAt; completed and trash filter on isDone / trashedAt. Same Room naming
    // convention as above so MIGRATION_10_11 creates matching indices and validation passes.
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["isDone"]),
        Index(value = ["trashedAt"])
    ]
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    // JSON array of attachments: [{"mime":..,"data":<id>,"name":..}, ...]
    // With disk-backed storage, "data" is now an AttachmentStore id (a UUID string) instead
    // of a Base64 payload. Legacy rows still hold Base64 until the startup migration rewrites
    // them; see AttachmentMigration.
    val attachments: String = "[]",
    // Optional user-set estimated completion time. Null = no due date set.
    val dueAt: Long? = null,
    // Optional free-text description / remarks for the task.
    val notes: String = "",
    // The moment the user marked this task complete. Null while it's still pending. The
    // completed-tasks history page sorts by this so newly-finished tasks land at the top;
    // the home list shows only rows where isDone = 0, so completed tasks disappear from it
    // automatically the instant they're checked off.
    val completedAt: Long? = null,
    // A TaskPriority.value (0 none, 1 low, 2 medium, 3 high) — see data/TaskPriority.kt. Stored as
    // a plain Int so it sorts naturally, and so old rows (which default to 0) read as NONE.
    val priority: Int = 0,
    // Pinned tasks float to the top of the active list regardless of the chosen sort order.
    val pinned: Boolean = false,
    // JSON array of subtask checklist items: [{"id":..,"text":..,"done":..}, ...] — see
    // Checklist.kt. A task's own small to-do list, separate from its free-text notes.
    val subtasks: String = "[]",
    // A RepeatRule.key (see data/Recurrence.kt). Only meaningful when dueAt is set — recurrence
    // needs a base instant to advance from each time the task is completed.
    val repeatRule: String = "NONE",
    // Whether a local notification should fire at dueAt. See reminders/ReminderScheduler.kt.
    val reminderEnabled: Boolean = false,
    // Soft-delete: a trashed task is hidden from the active list, the completed-tasks history, and
    // search, and is shown only on the Trash screen, until it's restored or TrashCleanup
    // permanently removes it. Null = not in the trash.
    val trashedAt: Long? = null,
    // ---- 1.1.0, group A: identical trio to Note, for identical reasons (see above) -------------
    val manualOrder: Int = 0,
    val isDraft: Boolean = false,
    val draftSavedAt: Long? = null,
    val hidden: Boolean = false,
    // INTEGRATION (C task 20): the task twin of Note.bodySpans, covering the `notes` field. Task 20
    // says "notes or tasks", and the two editors share a code path — a rich-text note that goes
    // plain when its text is moved into a task is exactly the asymmetry C's handoff warned about.
    val notesSpans: String = ""
)

/**
 * One historical revision of a note's text, captured immediately *before* an edit overwrites it.
 *
 * This is the local, offline answer to "I just wiped out a paragraph and saved" — the same safety
 * net mature note apps provide, except the history lives entirely on the device next to the note.
 * Nothing is uploaded and nothing leaves the phone.
 *
 * Only text is snapshotted (title/body/tags/checklist), never attachments: an attachment's bytes
 * live once in [AttachmentStore] and are referenced by id, so copying that reference into a
 * version row would let restoring an old version resurrect a file the note had already dropped —
 * or let deleting the note orphan a file a version still pointed at. Text is cheap, safe, and it's
 * what people actually lose.
 *
 * Rows are capped per note by [NoteVersionDao.trimTo] so history can never grow without bound, and
 * they are deleted along with their note wherever a note is permanently removed.
 */
@Entity(
    tableName = "note_versions",
    indices = [Index(value = ["noteId"])]
)
data class NoteVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val title: String,
    val body: String,
    val tags: String = "",
    val isChecklist: Boolean = false,
    val checklist: String = "[]",
    // When this revision was the note's live content — i.e. the note's own updatedAt at the moment
    // it was replaced. Shown in the history list, so a version reads as "what the note said on
    // July 3rd", which is the question people actually ask.
    val savedAt: Long = System.currentTimeMillis()
)

/**
 * One historical revision of a task (task A19) — the exact counterpart of [NoteVersion], and
 * deliberately a separate table rather than a shared one with a "kind" column: the two carry
 * different fields (a note has a body and tags; a task has subtasks, a priority and a due date),
 * and a shared table would have to make every one of them nullable and then remember which half
 * applies to which row.
 *
 * Snapshots the text-ish state only, never attachments — same reasoning as [NoteVersion]: an
 * attachment's bytes are referenced by id, so copying that reference into a version row would let
 * restoring an old version resurrect a file the task had already dropped.
 */
@Entity(
    tableName = "task_versions",
    indices = [Index(value = ["taskId"])]
)
data class TaskVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val notes: String = "",
    val subtasks: String = "[]",
    val priority: Int = 0,
    val dueAt: Long? = null,
    /** The task's own updatedAt-equivalent at the moment this revision was replaced. */
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentMime: String? = null,
    val attachmentData: String? = null,
    val attachmentName: String? = null,
    // Multiple-attachment support (R3 task #15): a JSON array (see Attachments.serialize/parse)
    // holding EVERY attachment of this message. The three legacy columns above stay populated with
    // the FIRST attachment so pre-existing readers never break; readers that understand multiple
    // files use the merged view (see ChatMessage.allAttachments) instead. Null on rows created
    // before schema v16 and whenever a message has at most one attachment.
    val attachmentList: String? = null,
    // Which conversation this message belongs to. Lets the user start a new conversation while
    // keeping the old ones (see ChatConversation). Existing pre-sessions rows are migrated to
    // conversation id 1 — the initial conversation — by MIGRATION_7_8.
    val conversationId: Long = 1,
    // Approximate tokens this turn cost, shown as a muted footnote under the reply (issue 9). Only
    // meaningful on assistant messages — it's the estimated input-context + output size for the
    // turn that produced this reply. 0 on user messages and on rows created before the column
    // existed, in which case the footnote is simply hidden. See data/TokenEstimator.kt.
    val tokens: Int = 0,
    // Which USER message this assistant reply answers (B-group task 12: resend / multiple replies).
    //
    // 0 on user messages, on replies saved before this column existed, and on anything the pairing
    // could not be established for. A non-zero value groups every reply that answers the same
    // question: asking again appends another row with the SAME replyToId, and the chat shows one of
    // them at a time with a 1/2 switcher. Deliberately a plain id rather than a separate
    // "variants" table — a reply is still exactly one row, so search, export, token accounting and
    // deletion all keep working with no special-casing at all.
    val replyToId: Long = 0
)

/**
 * A single assistant conversation (chat session). "Start new conversation" inserts one of these
 * and points new messages at it; the previous conversations stay in the database and can be
 * reopened from the conversation list. [title] is a short auto-derived label (first user message,
 * trimmed) shown in that list; [updatedAt] is bumped on each new message so the list can sort
 * most-recent-first.
 */
@Entity(tableName = "chat_conversations")
data class ChatConversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "New conversation",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
