package com.lucent.app.data

import java.time.Instant
import java.time.ZoneId

/**
 * Exports every note as one plain Markdown document.
 *
 * The `.json` backup already round-trips perfectly *back into Lucent*, and that's the right format
 * for restoring. This is the other half of owning your data: a file that is still readable in ten
 * years by something that has never heard of Lucent. A backup you can only open with the app that
 * wrote it is a hostage, not a backup — and for an app whose whole pitch is that your notes live on
 * your device and answer to you, being able to walk away with them in a format every editor on
 * earth can read is not a nice-to-have, it's the point.
 *
 * So: no app-specific wrapper, no base64, no schema. Headings, tags, checkboxes, and a horizontal
 * rule between notes. Attachments are named but not embedded — a Markdown file cannot carry bytes,
 * and silently dropping them would be dishonest, so each note lists the files it had and the export
 * says plainly where to get them.
 *
 * ### Round R1, task 3 — this writer now speaks the user's language
 *
 * Every label below used to be an English literal. The DOCX, PDF and XLSX writers had already been
 * moved onto the shared catalog; Markdown — the *default* format, and therefore the one most people
 * actually receive — had been left behind, so a Chinese user exporting Chinese notes got a file
 * headed "Lucent notes" whose every row said "Updated", "Pinned" and "Attachments". It reads from
 * the same catalog as the rest of the app now, so an export matches the language on screen.
 *
 * Two things are deliberately NOT localized, and the distinction is worth stating because it is the
 * one that decides these cases: **Markdown syntax** (`#`, `---`, `- [x]`) is not prose, it is the
 * file format, and translating it would produce a file no parser can read. And **date/time
 * formatting** follows the app language through [com.lucent.app.i18n.LDates], not a hardcoded
 * pattern, so a Japanese export is stamped the way a Japanese reader expects.
 *
 * ### Doodles
 *
 * A drawing cannot be written into a text file, but pretending it does not exist is how a note made
 * of drawings came out of here as an empty heading. Each doodle note now states how many canvases
 * it has and names them, exactly as it does for attachments — and those names are real: tick the
 * canvas in the export picker and a PDF of that exact name lands beside this file in the archive.
 * See [DoodleExport].
 */
object MarkdownExport {

    // The timestamp pattern is a catalog entry, and the formatter is rebuilt when the language
    // changes (see LDates), so this is not a per-call cost and not a stale one either.
    private fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(com.lucent.app.i18n.LDates.of("yyyy-MM-dd HH:mm"))

    private val S get() = com.lucent.app.i18n.S

    /**
     * Render [notes] as a single Markdown document.
     *
     * Trashed notes are left out — they're deleted, as far as the user is concerned, and an export
     * that quietly resurrects them in a file they're about to email themselves would be a nasty
     * surprise. Archived notes *are* included, and labelled, because archiving means "put this
     * away", not "throw it out".
     */
    fun render(notes: List<Note>): String {
        val live = notes.filter { it.trashedAt == null }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })

        val sb = StringBuilder()
        sb.appendLine("# ${S.exportDocNotesTitle}")
        sb.appendLine()
        sb.appendLine("_${S.exportDocNoteCount(live.size)}, ${S.exportDocExportedAt(formatTime(System.currentTimeMillis()))}_")
        sb.appendLine()
        sb.appendLine(S.exportDocAttachmentsNote)
        sb.appendLine()

        if (live.isEmpty()) {
            sb.appendLine("---")
            sb.appendLine()
            sb.appendLine("_${S.exportDocNoNotes}_")
            return sb.toString()
        }

        live.forEach { note ->
            sb.appendLine("---")
            sb.appendLine()
            sb.appendLine("## ${note.title.ifBlank { S.untitled }}")
            sb.appendLine()

            val meta = buildList {
                add(S.exportDocUpdated(formatTime(note.updatedAt)))
                if (note.pinned) add(S.exportDocPinned)
                if (note.archived) add(S.exportDocArchived)
                // Task 3.2 — exported in the reader's language, not in the canonical form the
                // column stores. The canonical value exists so a tag keeps its identity across a
                // language switch; it was never meant to be the thing a person reads.
                val tags = NoteTags.parse(note.tags)
                if (tags.isNotEmpty()) add(tags.joinToString(" ") { "#" + NoteTags.label(it) })
            }
            sb.appendLine("_${meta.joinToString(" · ")}_")
            sb.appendLine()

            // Round R2, task 2 — the items and the body are both content, and a note may now carry
            // items, a drawing and a body at once. This was an `else if`, so a checklist note's
            // remarks never reached the file: the export silently disagreed with the page the user
            // had been reading.
            if (note.isChecklist) {
                val items = Checklist.parse(note.checklist)
                if (items.isEmpty()) {
                    sb.appendLine("_${S.exportDocEmptyChecklist}_")
                } else {
                    sb.appendLine(Checklist.toMarkdown(note.checklist))
                }
                sb.appendLine()
            }
            if (note.body.isNotBlank()) {
                // The body is already Markdown as far as the app is concerned (the detail page
                // renders it as such), so it goes out verbatim rather than being escaped — escaping
                // it would turn every heading the user wrote into a literal '#'.
                sb.appendLine(note.body.trimEnd())
                sb.appendLine()
            }

            // Round R1, task 3 — drawings are named here for the same reason files are: a note whose
            // content is a drawing must not export as a heading with nothing beneath it.
            val canvases = DoodleExport.canvasesOf(note)
            if (canvases.isNotEmpty()) {
                sb.appendLine("**${S.exportDocDoodleCanvases(canvases.size)}**")
                sb.appendLine()
                sb.appendLine(S.exportDocDoodleLine(canvases.joinToString(", ") { it.fileName }))
                sb.appendLine()
            }

            val attachments = Attachments.parse(note.attachments)
            if (attachments.isNotEmpty()) {
                sb.appendLine("**${S.exportDocAttachmentsLine(attachments.joinToString(", ") { it.name })}**")
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    /**
     * Render [tasks] as a single Markdown document — the task-side equivalent of [render] for notes,
     * added so tasks can be exported to a portable, Lucent-independent file too (previously only
     * notes could). Trashed tasks are excluded for the same reason as notes; completed tasks are kept
     * and marked done with a `[x]` checkbox so the file is a faithful record. Each task carries its
     * created/due/priority/repeat metadata, its notes, its subtasks as a checklist, and its
     * attachment names.
     */
    fun renderTasks(tasks: List<Task>): String {
        val live = tasks.filter { it.trashedAt == null }
            .sortedWith(
                compareByDescending<Task> { it.pinned }
                    .thenBy { it.isDone }
                    .thenByDescending { it.createdAt }
            )

        val sb = StringBuilder()
        sb.appendLine("# ${S.exportDocTasksTitle}")
        sb.appendLine()
        sb.appendLine("_${S.exportDocTaskCount(live.size)}, ${S.exportDocExportedAt(formatTime(System.currentTimeMillis()))}_")
        sb.appendLine()
        sb.appendLine(S.exportDocAttachmentsNote)
        sb.appendLine()

        if (live.isEmpty()) {
            sb.appendLine("---")
            sb.appendLine()
            sb.appendLine("_${S.exportDocNoTasks}_")
            return sb.toString()
        }

        live.forEach { task ->
            sb.appendLine("---")
            sb.appendLine()
            val box = if (task.isDone) "[x]" else "[ ]"
            sb.appendLine("## $box ${task.title.ifBlank { S.exportDocUntitledTask }}")
            sb.appendLine()

            val meta = buildList {
                add(S.exportDocCreated(formatTime(task.createdAt)))
                task.dueAt?.let { add(S.exportDocDue(formatTime(it))) }
                if (task.pinned) add(S.exportDocPinned)
                TaskPriority.fromValue(task.priority).takeIf { it != TaskPriority.NONE }?.let { add(S.exportDocPriority(it.label)) }
                RepeatRule.fromKey(task.repeatRule).takeIf { it != RepeatRule.NONE }?.let { add(S.exportDocRepeats(it.label)) }
                add(if (task.isDone) S.exportDocDone else S.exportDocOpen)
            }
            sb.appendLine("_${meta.joinToString(" · ")}_")
            sb.appendLine()

            if (task.notes.isNotBlank()) {
                sb.appendLine(task.notes.trimEnd())
                sb.appendLine()
            }

            val subtasks = Checklist.parse(task.subtasks)
            if (subtasks.isNotEmpty()) {
                sb.appendLine("**${S.exportDocSubtasks}:**")
                sb.appendLine(Checklist.toMarkdown(task.subtasks))
                sb.appendLine()
            }

            val attachments = Attachments.parse(task.attachments)
            if (attachments.isNotEmpty()) {
                sb.appendLine("**${S.exportDocAttachmentsLine(attachments.joinToString(", ") { it.name })}**")
                sb.appendLine()
            }
        }

        return sb.toString()
    }
}
