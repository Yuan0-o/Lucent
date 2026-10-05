package com.lucent.app.data

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

object MarkdownExport {

    private val timeFormat = LocalDateTime.Format {
        year()
        char('-')
        monthNumber()
        char('-')
        dayOfMonth()
        char(' ')
        hour()
        char(':')
        minute()
    }

    private fun formatTime(millis: Long): String =
        timeFormat.format(
            Instant.fromEpochMilliseconds(millis)
                .toLocalDateTime(TimeZone.currentSystemDefault())
        )

    private val S get() = com.lucent.app.i18n.S

    fun render(notes: List<Note>): String {
        val live = notes.filter { it.trashedAt == null }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })

        val sb = StringBuilder()
        sb.appendLine("# ${S.exportDocNotesTitle}")
        sb.appendLine()
        sb.appendLine("_${S.exportDocNoteCount(live.size)}, ${S.exportDocExportedAt(formatTime(Clock.System.now().toEpochMilliseconds()))}_")
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
                val tags = NoteTags.parse(note.tags)
                if (tags.isNotEmpty()) add(tags.joinToString(" ") { "#" + NoteTags.label(it) })
            }
            sb.appendLine("_${meta.joinToString(" · ")}_")
            sb.appendLine()

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
                sb.appendLine(note.body.trimEnd())
                sb.appendLine()
            }

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
        sb.appendLine("_${S.exportDocTaskCount(live.size)}, ${S.exportDocExportedAt(formatTime(Clock.System.now().toEpochMilliseconds()))}_")
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
                TaskPriority.fromValue(task.priority).takeIf { p -> p != TaskPriority.NONE }?.let { p -> add(S.exportDocPriority(p.label)) }
                RepeatRule.fromKey(task.repeatRule).takeIf { r -> r != RepeatRule.NONE }?.let { r -> add(S.exportDocRepeats(r.label)) }
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
