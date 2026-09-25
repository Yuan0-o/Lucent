package com.lucent.app.tools

import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Note
import com.lucent.app.data.RichSpan
import com.lucent.app.data.RichText
import com.lucent.app.data.Task
import com.lucent.app.network.ToolDefinition
import com.lucent.app.network.ToolExecResult
import com.lucent.app.network.ToolParam
import org.json.JSONObject

object FormattingTools {

    private val HIGHLIGHT_NAMES = listOf("yellow", "green", "blue", "pink", "orange", "purple", "teal", "red")

    private val TEXT_COLOUR_NAMES = listOf("default", "green", "yellow", "blue", "red", "teal", "purple", "orange", "pink")

    private val SIZE_NAMES = listOf("default", "small", "medium", "large", "huge")

    private val SPAN_KIND_LABELS = mapOf(
        RichSpan.Kind.BOLD to "bold",
        RichSpan.Kind.LIGHT to "light",
        RichSpan.Kind.ITALIC to "italic"
    )

    fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "format_note_text",
            description = "Change how a NOTE's text LOOKS — bold, light, italic, a highlighter colour, a text colour, or a font size (small, medium, large, huge) — the same rich-text formatting the note editor's toolbar offers. Name the exact words to format in \"find\"; leave find out to restyle the whole note. Every occurrence is formatted unless all is false. Formatting only changes appearance, never the words themselves. Call read_note_formatting first when you need to know what the text currently looks like, and read_note when you need the exact wording.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the note"),
                ToolParam("find", "string", "The exact text inside the note to format. Leave it out to format every bit of the note's text.", required = false),
                ToolParam("bold", "boolean", "true to make it bold, false to remove bold. Leave out to leave boldness alone.", required = false),
                ToolParam("italic", "boolean", "true for italic, false to remove italic. Leave out to leave it alone.", required = false),
                ToolParam("light", "boolean", "true for the light (thin) weight, false to remove it. Leave out to leave it alone.", required = false),
                ToolParam("size", "string", "Font size: default, small, medium, large, huge. Leave out to leave the size alone.", required = false),
                ToolParam("highlight", "string", "Highlighter colour: yellow, green, blue, pink, orange, purple, teal, red, or none to clear the highlight. Leave out to leave it alone.", required = false),
                ToolParam("colour", "string", "Text colour: default, green, yellow, blue, red, teal, purple, orange, pink. Leave out to leave it alone.", required = false),
                ToolParam("all", "boolean", "true (the default) formats every occurrence of the text; false formats only the first one.", required = false)
            )
        ),
        ToolDefinition(
            name = "format_task_notes",
            description = "Change how a TASK's notes/description text LOOKS — bold, light, italic, a highlighter colour, a text colour, or a font size (small, medium, large, huge) — the same rich-text formatting the task editor's toolbar offers. Name the exact words to format in \"find\"; leave find out to restyle the whole notes. Every occurrence is formatted unless all is false. Formatting only changes appearance, never the words.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the task"),
                ToolParam("find", "string", "The exact text inside the task's notes to format. Leave it out to format all of the notes.", required = false),
                ToolParam("bold", "boolean", "true to make it bold, false to remove bold. Leave out to leave boldness alone.", required = false),
                ToolParam("italic", "boolean", "true for italic, false to remove italic. Leave out to leave it alone.", required = false),
                ToolParam("light", "boolean", "true for the light (thin) weight, false to remove it. Leave out to leave it alone.", required = false),
                ToolParam("size", "string", "Font size: default, small, medium, large, huge. Leave out to leave the size alone.", required = false),
                ToolParam("highlight", "string", "Highlighter colour: yellow, green, blue, pink, orange, purple, teal, red, or none to clear the highlight. Leave out to leave it alone.", required = false),
                ToolParam("colour", "string", "Text colour: default, green, yellow, blue, red, teal, purple, orange, pink. Leave out to leave it alone.", required = false),
                ToolParam("all", "boolean", "true (the default) formats every occurrence of the text; false formats only the first one.", required = false)
            )
        ),
        ToolDefinition(
            name = "read_note_formatting",
            description = "Show how the text of a NOTE or TASK is styled right now: which words are bold, light or italic, which have a size, a text colour or a highlighter colour, matched by its title. Read-only — it changes nothing. Call it before restyling so you know what is already there, and call read_note or read_task when you also need the exact words.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the note or task"),
                ToolParam("item_type", "string", "Optional: note or task. Leave out to look in both.", required = false)
            )
        ),
        ToolDefinition(
            name = "append_to_note",
            description = "Add text to the END of a NOTE's body without touching what is already written, matched by its title. Use this when the user wants something added on rather than rewritten; use update_note when they want the text replaced. The previous text is saved to the note's history either way. Does not work on checklist or doodle notes.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the note"),
                ToolParam("text", "string", "The text to add at the end of the note")
            )
        ),
        ToolDefinition(
            name = "append_to_task_notes",
            description = "Add text to the END of a TASK's notes/description without touching what is already written, matched by its title. Use this when the user wants something added on rather than rewritten; use update_task when they want the text replaced.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the task"),
                ToolParam("text", "string", "The text to add at the end of the task's notes")
            )
        ),
        ToolDefinition(
            name = "set_note_format",
            description = "Choose how a NOTE's body is rendered, matched by its title — the same choice the editor's format button offers: automatic (the app decides), markdown, or rich text. Use this when the person wants a note shown as Markdown, or as formatted rich text.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the note"),
                ToolParam("format", "string", "One of: auto, markdown, rich")
            )
        ),
        ToolDefinition(
            name = "set_task_format",
            description = "Choose how a TASK's notes are rendered, matched by its title — automatic (the app decides), markdown, or rich text.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the task"),
                ToolParam("format", "string", "One of: auto, markdown, rich")
            )
        ),
        ToolDefinition(
            name = "set_note_hidden",
            description = "Hide a NOTE in the app's hidden area, or bring it back out, matched by its title. Hidden notes stay encrypted on the device but disappear from the notes list, from search, and from list_notes. Bring one back with hidden false.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the note"),
                ToolParam("hidden", "boolean", "true to hide it, false to bring it back into the normal list")
            )
        ),
        ToolDefinition(
            name = "set_task_hidden",
            description = "Hide a TASK in the app's hidden area, or bring it back out, matched by its title. Hidden tasks disappear from the task list, from search, and from list_tasks. Bring one back with hidden false.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the task"),
                ToolParam("hidden", "boolean", "true to hide it, false to bring it back into the normal list")
            )
        ),
        ToolDefinition(
            name = "move_note",
            description = "Move a NOTE to another position in the manually ordered notes list, matched by its title — the same order the person gets by dragging notes around when the sort is set to Custom. Use it when they ask for a note to go to the top, to the bottom, or before/after another note.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the note to move"),
                ToolParam("position", "string", "One of: top, bottom, before, after"),
                ToolParam("relative_to", "string", "For before/after: the title (or part of it) of the note to sit next to", required = false)
            )
        ),
        ToolDefinition(
            name = "move_task",
            description = "Move a TASK to another position in the manually ordered task list, matched by its title — the same order the person gets by dragging tasks around when the sort is set to Custom. Use it when they ask for a task to go to the top, to the bottom, or before/after another task.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the task to move"),
                ToolParam("position", "string", "One of: top, bottom, before, after"),
                ToolParam("relative_to", "string", "For before/after: the title (or part of it) of the task to sit next to", required = false)
            )
        )
    )

    fun describeToolCall(name: String, args: JSONObject): String? {
        fun s(vararg keys: String): String {
            for (key in keys) {
                val value = args.optString(key, "")
                if (value.isNotBlank()) return value
            }
            return ""
        }
        return when (name) {
            "format_note_text" -> com.lucent.app.i18n.S.ccFormatNote(s("find"), s("title"))
            "format_task_notes" -> com.lucent.app.i18n.S.ccFormatTask(s("find"), s("title"))
            "append_to_note" -> com.lucent.app.i18n.S.ccAppendNote(s("title"))
            "append_to_task_notes" -> com.lucent.app.i18n.S.ccAppendTask(s("title"))
            "set_note_format" -> com.lucent.app.i18n.S.ccSetNoteFormat(s("title"), s("format"))
            "set_task_format" -> com.lucent.app.i18n.S.ccSetTaskFormat(s("title"), s("format"))
            "set_note_hidden" ->
                if (args.optBoolean("hidden", true)) com.lucent.app.i18n.S.ccHideNote(s("title"))
                else com.lucent.app.i18n.S.ccShowNote(s("title"))
            "set_task_hidden" ->
                if (args.optBoolean("hidden", true)) com.lucent.app.i18n.S.ccHideTask(s("title"))
                else com.lucent.app.i18n.S.ccShowTask(s("title"))
            "read_note_formatting" -> com.lucent.app.i18n.S.ccReadFormatting(s("title"))
            "move_note" -> com.lucent.app.i18n.S.ccMoveNote(s("title"), s("position"))
            "move_task" -> com.lucent.app.i18n.S.ccMoveTask(s("title"), s("position"))
            else -> null
        }
    }

    fun editableArguments(name: String, args: JSONObject): List<AppTools.EditableArgument> {
        fun of(key: String, label: String, multiline: Boolean = false): AppTools.EditableArgument? =
            args.optString(key, "").takeIf { it.isNotBlank() }
                ?.let { AppTools.EditableArgument(key, label, it, multiline) }
        return when (name) {
            "format_note_text", "format_task_notes" -> listOfNotNull(
                of("find", com.lucent.app.i18n.S.confirmEditItemLabel)
            )
            "append_to_note", "append_to_task_notes" -> listOfNotNull(
                of("text", com.lucent.app.i18n.S.confirmEditContentLabel, multiline = true)
            )
            "move_note", "move_task" -> listOfNotNull(
                of("position", com.lucent.app.i18n.S.confirmEditItemLabel),
                of("relative_to", com.lucent.app.i18n.S.confirmEditTitleLabel)
            )
            else -> emptyList()
        }
    }

    suspend fun execute(db: AppDatabase, name: String, args: JSONObject): ToolExecResult? = when (name) {
        "format_note_text" -> formatNote(db, args)
        "format_task_notes" -> formatTaskNotes(db, args)
        "append_to_note" -> appendToNote(db, args)
        "append_to_task_notes" -> appendToTaskNotes(db, args)
        "set_note_format" -> setNoteFormat(db, args)
        "set_task_format" -> setTaskFormat(db, args)
        "set_note_hidden" -> setNoteHidden(db, args)
        "set_task_hidden" -> setTaskHidden(db, args)
        "read_note_formatting" -> readFormatting(db, args)
        "move_note" -> moveNote(db, args)
        "move_task" -> moveTask(db, args)
        else -> null
    }

    private class Styles(
        val bold: Boolean?,
        val italic: Boolean?,
        val light: Boolean?,
        val size: Int?,
        val highlight: Int?,
        val colour: Int?,
        val all: Boolean
    ) {
        val anything: Boolean
            get() = bold != null || italic != null || light != null || size != null ||
                highlight != null || colour != null
    }

    private data class StyleRead(val error: ToolExecResult?, val styles: Styles?)

    private fun readStyles(args: JSONObject): StyleRead {
        val bold = if (args.has("bold")) args.optBoolean("bold") else null
        val italic = if (args.has("italic")) args.optBoolean("italic") else null
        val light = if (args.has("light")) args.optBoolean("light") else null
        var highlight: Int? = null
        if (args.has("highlight")) {
            val raw = args.optString("highlight").trim().lowercase()
            if (raw.isEmpty() || raw == "none") {
                highlight = -1
            } else {
                val index = HIGHLIGHT_NAMES.indexOf(raw)
                if (index < 0) {
                    return StyleRead(
                        ToolExecResult(
                            "The highlighter colour \"$raw\" isn't one this app has. Use one of: " +
                                HIGHLIGHT_NAMES.joinToString(", ") + ", or none to clear it.",
                            success = false
                        ),
                        null
                    )
                }
                highlight = index
            }
        }
        var size: Int? = null
        if (args.has("size")) {
            val raw = args.optString("size").trim().lowercase()
            val index = SIZE_NAMES.indexOf(raw)
            if (index < 0) {
                return StyleRead(
                    ToolExecResult(
                        "The size \"$raw\" isn't one this app has. Use one of: " +
                            SIZE_NAMES.joinToString(", ") + ".",
                        success = false
                    ),
                    null
                )
            }
            size = index
        }
        var colour: Int? = null
        if (args.has("colour") || args.has("color")) {
            val key = if (args.has("colour")) "colour" else "color"
            val raw = args.optString(key).trim().lowercase()
            val index = TEXT_COLOUR_NAMES.indexOf(raw)
            if (index < 0) {
                return StyleRead(
                    ToolExecResult(
                        "The text colour \"$raw\" isn't one this app has. Use one of: " +
                            TEXT_COLOUR_NAMES.joinToString(", ") + ".",
                        success = false
                    ),
                    null
                )
            }
            colour = index
        }
        val all = if (args.has("all")) args.optBoolean("all", true) else true
        return StyleRead(null, Styles(bold, italic, light, size, highlight, colour, all))
    }

    private fun occurrences(haystack: String, needle: String): List<IntRange> {
        if (needle.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        var from = 0
        while (true) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) break
            out.add(at until at + needle.length)
            from = at + needle.length
        }
        return out
    }

    private fun applyStyles(spans: List<RichSpan>, range: IntRange, styles: Styles): List<RichSpan> {
        var out = spans
        fun set(kind: RichSpan.Kind, on: Boolean, colour: Int = 0) {
            out = RichText.remove(out, range.first, range.last + 1, kind, if (kind == RichSpan.Kind.HIGHLIGHT) colour else null)
            if (on) {
                if (kind == RichSpan.Kind.BOLD) {
                    out = RichText.remove(out, range.first, range.last + 1, RichSpan.Kind.LIGHT, null)
                } else if (kind == RichSpan.Kind.LIGHT) {
                    out = RichText.remove(out, range.first, range.last + 1, RichSpan.Kind.BOLD, null)
                }
                out = RichText.normalise(out + RichSpan(range.first, range.last + 1, kind, colour))
            }
        }
        styles.bold?.let { set(RichSpan.Kind.BOLD, it) }
        styles.light?.let { set(RichSpan.Kind.LIGHT, it) }
        styles.italic?.let { set(RichSpan.Kind.ITALIC, it) }
        styles.highlight?.let { index ->
            if (index < 0) set(RichSpan.Kind.HIGHLIGHT, false)
            else {
                out = RichText.remove(out, range.first, range.last + 1, RichSpan.Kind.HIGHLIGHT, null)
                out = RichText.normalise(out + RichSpan(range.first, range.last + 1, RichSpan.Kind.HIGHLIGHT, index))
            }
        }
        styles.colour?.let { index ->
            if (index == RichText.TEXT_COLOR_DEFAULT) set(RichSpan.Kind.COLOR, false)
            else {
                out = RichText.remove(out, range.first, range.last + 1, RichSpan.Kind.COLOR, null)
                out = RichText.normalise(out + RichSpan(range.first, range.last + 1, RichSpan.Kind.COLOR, index))
            }
        }
        styles.size?.let { index ->
            if (index == RichText.TEXT_SIZE_DEFAULT) set(RichSpan.Kind.SIZE, false)
            else {
                out = RichText.remove(out, range.first, range.last + 1, RichSpan.Kind.SIZE, null)
                out = RichText.normalise(out + RichSpan(range.first, range.last + 1, RichSpan.Kind.SIZE, index))
            }
        }
        return RichText.normalise(out)
    }

    private fun describeStyles(styles: Styles): String {
        val parts = ArrayList<String>()
        styles.bold?.let { parts.add(if (it) "bold" else "not bold") }
        styles.light?.let { parts.add(if (it) "light" else "normal weight") }
        styles.italic?.let { parts.add(if (it) "italic" else "not italic") }
        styles.size?.let { parts.add(if (it == RichText.TEXT_SIZE_DEFAULT) "default size" else "size ${SIZE_NAMES[it]}") }
        styles.highlight?.let { parts.add(if (it < 0) "no highlight" else "highlighted ${HIGHLIGHT_NAMES[it]}") }
        styles.colour?.let { parts.add("coloured ${TEXT_COLOUR_NAMES[it]}") }
        return parts.joinToString(", ")
    }

    private suspend fun formatNote(db: AppDatabase, args: JSONObject): ToolExecResult {
        val titleQuery = args.optString("title", "")
        val find = args.optString("find", "")
        val read = readStyles(args)
        read.error?.let { return it }
        val styles = read.styles ?: return ToolExecResult("No formatting was asked for.", success = false)
        if (!styles.anything) {
            return ToolExecResult(
                "No formatting was asked for — give at least one of bold, italic, light, size, " +
                    "highlight or colour.",
                success = false
            )
        }
        val note = AppTools.resolveNote(AppTools.editableNotes(db), titleQuery)
            ?: return AppTools.noteNotFound(db, titleQuery)
        if (note.isDoodle) {
            return ToolExecResult(
                "The note \"${note.title}\" is a doodle note: it holds a drawing, not text, so there is " +
                    "nothing to format on it.",
                success = false
            )
        }
        if (note.isChecklist) {
            return ToolExecResult(
                "The note \"${note.title}\" is a checklist, and rich text applies to a note's body text, " +
                    "not to checklist items. Switch it back to plain text first with set_note_checklist_mode.",
                success = false
            )
        }
        if (find.isBlank() && note.body.isBlank()) {
            return ToolExecResult("The note \"${note.title}\" is empty, so there is nothing to format.")
        }
        val hits = if (find.isBlank()) listOf(note.body.indices) else occurrences(note.body, find)
        if (hits.isEmpty()) {
            return ToolExecResult(
                "The text \"$find\" does not appear in the note \"${note.title}\", so nothing was " +
                    "formatted. Read the note first and pass the wording exactly as it is written, " +
                    "or leave find out to format the whole note.",
                success = false
            )
        }
        val chosen = if (styles.all || find.isBlank()) hits else listOf(hits.first())
        var spans = RichText.load(note.bodySpans, note.body)
        chosen.forEach { spans = applyStyles(spans, it, styles) }
        db.noteDao().update(
            note.copy(bodySpans = RichText.encode(spans), updatedAt = System.currentTimeMillis())
        )
        val where = if (find.isBlank()) "the whole note" else "\"$find\""
        val times = if (find.isBlank() || chosen.size == 1) "" else " (${chosen.size} times)"
        return ToolExecResult(
            "Formatted $where in the note \"${note.title}\"$times: ${describeStyles(styles)}. " +
                "The words themselves are unchanged.",
            openNoteId = note.id
        )
    }

    private suspend fun formatTaskNotes(db: AppDatabase, args: JSONObject): ToolExecResult {
        val titleQuery = args.optString("title", "")
        val find = args.optString("find", "")
        val read = readStyles(args)
        read.error?.let { return it }
        val styles = read.styles ?: return ToolExecResult("No formatting was asked for.", success = false)
        if (!styles.anything) {
            return ToolExecResult(
                "No formatting was asked for — give at least one of bold, italic, light, size, " +
                    "highlight or colour.",
                success = false
            )
        }
        val task = AppTools.resolveTask(AppTools.editableTasks(db), titleQuery)
            ?: return AppTools.taskNotFound(db, titleQuery)
        if (find.isBlank() && task.notes.isBlank()) {
            return ToolExecResult("The task \"${task.title}\" has no notes text to format.")
        }
        val hits = if (find.isBlank()) listOf(task.notes.indices) else occurrences(task.notes, find)
        if (hits.isEmpty()) {
            return ToolExecResult(
                "The text \"$find\" does not appear in the notes of the task \"${task.title}\", so " +
                    "nothing was formatted. Read the task first and pass the wording exactly as " +
                    "written, or leave find out to format all of the notes.",
                success = false
            )
        }
        val chosen = if (styles.all || find.isBlank()) hits else listOf(hits.first())
        var spans = RichText.load(task.notesSpans, task.notes)
        chosen.forEach { spans = applyStyles(spans, it, styles) }
        db.taskDao().update(task.copy(notesSpans = RichText.encode(spans)))
        val where = if (find.isBlank()) "the whole description" else "\"$find\""
        val times = if (find.isBlank() || chosen.size == 1) "" else " (${chosen.size} times)"
        return ToolExecResult(
            "Formatted $where in the notes of \"${task.title}\"$times: ${describeStyles(styles)}. " +
                "The words themselves are unchanged.",
            openTaskId = task.id
        )
    }

    private fun styleLabel(span: RichSpan): String = when (span.kind) {
        RichSpan.Kind.HIGHLIGHT -> "highlighted " + HIGHLIGHT_NAMES.getOrElse(span.color) { "?" }
        RichSpan.Kind.COLOR -> "coloured " + TEXT_COLOUR_NAMES.getOrElse(span.color) { "?" }
        RichSpan.Kind.SIZE -> "size " + SIZE_NAMES.getOrElse(span.color) { "?" }
        else -> SPAN_KIND_LABELS[span.kind] ?: span.kind.name.lowercase()
    }

    private fun styleReport(text: String, spansJson: String): String {
        val spans = RichText.load(spansJson, text)
        if (spans.isEmpty()) return "Nothing is styled — the text is plain."
        val lines = ArrayList<String>()
        spans.forEach { span ->
            val slice = text.substring(
                span.start.coerceIn(0, text.length),
                span.end.coerceIn(0, text.length)
            ).replace("\n", " ").trim()
            if (slice.isNotEmpty()) {
                lines.add("- \"" + slice.take(90) + "\" is " + styleLabel(span))
            }
        }
        if (lines.isEmpty()) return "Nothing is styled — the text is plain."
        return "Styled parts (" + lines.size + "):\n" + lines.joinToString("\n")
    }

    private suspend fun readFormatting(db: AppDatabase, args: JSONObject): ToolExecResult {
        val titleQuery = args.optString("title", "")
        val type = args.optString("item_type", args.optString("kind", args.optString("type", "")))
            .trim().lowercase()
        val wantNote = type != "task" && type != "tasks"
        val wantTask = type != "note" && type != "notes"
        val note = if (wantNote) AppTools.resolveNote(AppTools.editableNotes(db), titleQuery) else null
        val task = if (wantTask) AppTools.resolveTask(AppTools.editableTasks(db), titleQuery) else null
        if (note == null && task == null) {
            return if (wantNote) AppTools.noteNotFound(db, titleQuery) else AppTools.taskNotFound(db, titleQuery)
        }
        if (note != null && task != null) {
            return ToolExecResult(
                "Both a note (\"${note.title}\") and a task (\"${task.title}\") match \"$titleQuery\". " +
                    "Pass item_type = note or item_type = task to say which one.",
                success = false
            )
        }
        if (note != null) {
            if (note.isDoodle) {
                return ToolExecResult(
                    "The note \"${note.title}\" is a doodle note: it holds a drawing, so it has no text " +
                        "styling at all.",
                    success = false
                )
            }
            if (note.isChecklist) {
                return ToolExecResult(
                    "The note \"${note.title}\" is a checklist, so it has no body text to style — " +
                        "rich text applies to a note's body text, not to checklist items.",
                    success = false
                )
            }
            return ToolExecResult(
                "How the note \"${note.title}\" is styled:\n" + styleReport(note.body, note.bodySpans)
            )
        }
        val found = task ?: return AppTools.taskNotFound(db, titleQuery)
        return ToolExecResult(
            "How the notes on the task \"${found.title}\" are styled:\n" +
                styleReport(found.notes, found.notesSpans)
        )
    }

    private suspend fun appendToNote(db: AppDatabase, args: JSONObject): ToolExecResult {
        val titleQuery = args.optString("title", "")
        val text = args.optString("text", "")
        if (text.isBlank()) {
            return ToolExecResult("There was no text to add, so the note is unchanged.", success = false)
        }
        val note = AppTools.resolveNote(AppTools.editableNotes(db), titleQuery)
            ?: return AppTools.noteNotFound(db, titleQuery)
        if (note.isChecklist || note.isDoodle) {
            return ToolExecResult(
                "The note \"${note.title}\" is a ${if (note.isDoodle) "doodle" else "checklist"} note, " +
                    "so it has no body text to add to. Use add_note_checklist_item for a checklist " +
                    "note, or set_note_checklist_mode to bring its text body back.",
                success = false
            )
        }
        val previous = note.body
        val merged = if (previous.isBlank()) text else previous.trimEnd('\n') + "\n" + text
        com.lucent.app.data.NoteHistory.recordIfChanged(
            db, note, note.title, merged, note.tags, note.isChecklist, note.checklist
        )
        db.noteDao().update(note.copy(body = merged, updatedAt = System.currentTimeMillis()))
        return ToolExecResult(
            "Added the text to the end of the note \"${note.title}\". Everything that was already " +
                "there is kept, and the previous version sits in the note's history.",
            openNoteId = note.id
        )
    }

    private suspend fun appendToTaskNotes(db: AppDatabase, args: JSONObject): ToolExecResult {
        val titleQuery = args.optString("title", "")
        val text = args.optString("text", "")
        if (text.isBlank()) {
            return ToolExecResult("There was no text to add, so the task is unchanged.", success = false)
        }
        val task = AppTools.resolveTask(AppTools.editableTasks(db), titleQuery)
            ?: return AppTools.taskNotFound(db, titleQuery)
        val previous = task.notes
        val merged = if (previous.isBlank()) text else previous.trimEnd('\n') + "\n" + text
        com.lucent.app.data.TaskHistory.recordIfChanged(
            db, task, task.title, merged, task.subtasks, task.priority, task.dueAt
        )
        db.taskDao().update(task.copy(notes = merged))
        return ToolExecResult(
            "Added the text to the end of the notes on \"${task.title}\". Everything that was already " +
                "there is kept.",
            openTaskId = task.id
        )
    }

    private fun formatKey(raw: String): String? = when (raw.trim().lowercase()) {
        "", "auto", "automatic", "none" -> null
        "markdown", "md" -> com.lucent.app.data.ContentFormats.MARKDOWN_KEY
        "rich", "rich text", "richtext" -> com.lucent.app.data.ContentFormats.RICH_TEXT_KEY
        else -> "?"
    }

    private suspend fun setNoteFormat(db: AppDatabase, args: JSONObject): ToolExecResult {
        val titleQuery = args.optString("title", "")
        val key = formatKey(args.optString("format", ""))
        if (key == "?") {
            return ToolExecResult(
                "That isn't a format this app has. Use one of: auto, markdown, rich.",
                success = false
            )
        }
        val note = AppTools.resolveNote(AppTools.editableNotes(db), titleQuery)
            ?: return AppTools.noteNotFound(db, titleQuery)
        db.noteDao().update(note.copy(formatOverride = key, updatedAt = System.currentTimeMillis()))
        val label = when (key) {
            com.lucent.app.data.ContentFormats.MARKDOWN_KEY -> "Markdown"
            com.lucent.app.data.ContentFormats.RICH_TEXT_KEY -> "rich text"
            else -> "automatic"
        }
        return ToolExecResult("The note \"${note.title}\" now displays as $label.", openNoteId = note.id)
    }

    private suspend fun setTaskFormat(db: AppDatabase, args: JSONObject): ToolExecResult {
        val titleQuery = args.optString("title", "")
        val key = formatKey(args.optString("format", ""))
        if (key == "?") {
            return ToolExecResult(
                "That isn't a format this app has. Use one of: auto, markdown, rich.",
                success = false
            )
        }
        val task = AppTools.resolveTask(AppTools.editableTasks(db), titleQuery)
            ?: return AppTools.taskNotFound(db, titleQuery)
        db.taskDao().update(task.copy(formatOverride = key))
        val label = when (key) {
            com.lucent.app.data.ContentFormats.MARKDOWN_KEY -> "Markdown"
            com.lucent.app.data.ContentFormats.RICH_TEXT_KEY -> "rich text"
            else -> "automatic"
        }
        return ToolExecResult("The task \"${task.title}\" now displays its notes as $label.", openTaskId = task.id)
    }

    private suspend fun setNoteHidden(db: AppDatabase, args: JSONObject): ToolExecResult {
        val titleQuery = args.optString("title", "")
        val hidden = args.optBoolean("hidden", true)
        if (hidden) {
            val note = AppTools.resolveNote(AppTools.editableNotes(db), titleQuery)
                ?: return AppTools.noteNotFound(db, titleQuery)
            db.noteDao().update(note.copy(hidden = true, updatedAt = System.currentTimeMillis()))
            return ToolExecResult(
                "The note \"${note.title}\" is now hidden. It no longer appears in the notes list or " +
                    "in searches; it is still encrypted on the device, and the person can bring it " +
                    "back from the hidden area.",
                openNoteId = note.id
            )
        }
        val note = AppTools.resolveNote(AppTools.hiddenNotes(db), titleQuery)
        if (note == null) {
            val visible = AppTools.resolveNote(AppTools.editableNotes(db), titleQuery)
            return if (visible != null) {
                ToolExecResult("The note \"${visible.title}\" isn't hidden, so there was nothing to bring back.")
            } else {
                AppTools.noteNotFound(db, titleQuery)
            }
        }
        db.noteDao().update(note.copy(hidden = false, updatedAt = System.currentTimeMillis()))
        return ToolExecResult("The note \"${note.title}\" is back in the normal notes list.", openNoteId = note.id)
    }

    private suspend fun setTaskHidden(db: AppDatabase, args: JSONObject): ToolExecResult {
        val titleQuery = args.optString("title", "")
        val hidden = args.optBoolean("hidden", true)
        if (hidden) {
            val task = AppTools.resolveTask(AppTools.editableTasks(db), titleQuery)
                ?: return AppTools.taskNotFound(db, titleQuery)
            db.taskDao().update(task.copy(hidden = true))
            return ToolExecResult(
                "The task \"${task.title}\" is now hidden. It no longer appears in the task list or in " +
                    "searches, and the person can bring it back from the hidden area.",
                openTaskId = task.id
            )
        }
        val task = AppTools.resolveTask(AppTools.hiddenTasks(db), titleQuery)
        if (task == null) {
            val visible = AppTools.resolveTask(AppTools.editableTasks(db), titleQuery)
            return if (visible != null) {
                ToolExecResult("The task \"${visible.title}\" isn't hidden, so there was nothing to bring back.")
            } else {
                AppTools.taskNotFound(db, titleQuery)
            }
        }
        db.taskDao().update(task.copy(hidden = false))
        return ToolExecResult("The task \"${task.title}\" is back in the normal task list.", openTaskId = task.id)
    }

    private fun placement(
        ordered: List<Long>,
        movingId: Long,
        position: String,
        relativeId: Long?
    ): Int? {
        val without = ordered.filter { it != movingId }
        return when (position) {
            "top" -> 0
            "bottom" -> without.size
            "before" -> relativeId?.let { id -> without.indexOf(id).takeIf { it >= 0 } }
            "after" -> relativeId?.let { id -> without.indexOf(id).takeIf { it >= 0 }?.plus(1) }
            else -> null
        }
    }

    private suspend fun moveNote(db: AppDatabase, args: JSONObject): ToolExecResult {
        val titleQuery = args.optString("title", "")
        val position = args.optString("position", "").trim().lowercase()
        val note = AppTools.resolveNote(AppTools.editableNotes(db), titleQuery)
            ?: return AppTools.noteNotFound(db, titleQuery)
        val others = AppTools.activeNotes(db)
        val ordered = others.sortedBy { it.manualOrder }.map { it.id }
        val relative = if (position == "before" || position == "after") {
            val query = args.optString("relative_to", "")
            if (query.isBlank()) {
                return ToolExecResult(
                    "\"$position\" needs relative_to — the title of the note it should sit next to.",
                    success = false
                )
            }
            val target = AppTools.resolveNote(others.filter { it.id != note.id }, query)
                ?: return AppTools.noteNotFound(db, query)
            target.id
        } else null
        val index = placement(ordered, note.id, position, relative)
        if (index == null) {
            return ToolExecResult(
                "\"$position\" isn't a position this app has. Use one of: top, bottom, before, after.",
                success = false
            )
        }
        val finalOrder = ordered.filter { it != note.id }.toMutableList().also { it.add(index, note.id) }
        val byId = others.associateBy { it.id }
        finalOrder.forEachIndexed { i, id ->
            byId[id]?.let { db.noteDao().update(it.copy(manualOrder = i * 1000)) }
        }
        val where = when (position) {
            "top" -> "at the top"
            "bottom" -> "at the bottom"
            "before" -> "just before \"${args.optString("relative_to")}\""
            else -> "just after \"${args.optString("relative_to")}\""
        }
        return ToolExecResult(
            "Moved the note \"${note.title}\" $where of the manually ordered list. The order is used " +
                "when the person sorts their notes by Custom.",
            openNoteId = note.id
        )
    }

    private suspend fun moveTask(db: AppDatabase, args: JSONObject): ToolExecResult {
        val titleQuery = args.optString("title", "")
        val position = args.optString("position", "").trim().lowercase()
        val task = AppTools.resolveTask(AppTools.editableTasks(db), titleQuery)
            ?: return AppTools.taskNotFound(db, titleQuery)
        val others = AppTools.activeTasks(db)
        val ordered = others.sortedBy { it.manualOrder }.map { it.id }
        val relative = if (position == "before" || position == "after") {
            val query = args.optString("relative_to", "")
            if (query.isBlank()) {
                return ToolExecResult(
                    "\"$position\" needs relative_to — the title of the task it should sit next to.",
                    success = false
                )
            }
            val target = AppTools.resolveTask(others.filter { it.id != task.id }, query)
                ?: return AppTools.taskNotFound(db, query)
            target.id
        } else null
        val index = placement(ordered, task.id, position, relative)
        if (index == null) {
            return ToolExecResult(
                "\"$position\" isn't a position this app has. Use one of: top, bottom, before, after.",
                success = false
            )
        }
        val finalOrder = ordered.filter { it != task.id }.toMutableList().also { it.add(index, task.id) }
        val byId = others.associateBy { it.id }
        finalOrder.forEachIndexed { i, id ->
            byId[id]?.let { db.taskDao().update(it.copy(manualOrder = i * 1000)) }
        }
        val where = when (position) {
            "top" -> "at the top"
            "bottom" -> "at the bottom"
            "before" -> "just before \"${args.optString("relative_to")}\""
            else -> "just after \"${args.optString("relative_to")}\""
        }
        return ToolExecResult(
            "Moved the task \"${task.title}\" $where of the manually ordered list. The order is used " +
                "when the person sorts their tasks by Custom.",
            openTaskId = task.id
        )
    }
}
