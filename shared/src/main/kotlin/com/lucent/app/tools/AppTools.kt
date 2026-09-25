package com.lucent.app.tools

import android.content.Context
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentLimits
import com.lucent.app.data.AttachmentStore
import com.lucent.app.data.Attachments
import com.lucent.app.data.Checklist
import com.lucent.app.data.DueParsing
import com.lucent.app.data.Note
import com.lucent.app.data.NoteHistory
import com.lucent.app.data.RepeatRule
import com.lucent.app.data.SearchQuery
import com.lucent.app.data.Task
import com.lucent.app.data.TaskPriority
import com.lucent.app.data.filterBySearch
import com.lucent.app.network.ToolDefinition
import com.lucent.app.ui.NoteColor
import com.lucent.app.network.ToolExecResult
import com.lucent.app.network.ToolImage
import com.lucent.app.network.ToolParam
import com.lucent.app.network.WebSearchClient
import com.lucent.app.reminders.ReminderScheduler
import org.json.JSONObject

object AppTools {

    private const val MATCH_MIN_SCORE = 300

    private val TITLE_NOISE_WORDS = setOf(
        "the", "a", "an", "my", "our", "of", "called", "named",
        "note", "notes", "task", "tasks", "item", "items", "draft", "drafts"
    )

    fun definitions(includeWebSearch: Boolean = false): List<ToolDefinition> =
        baseDefinitions() + NotebookTools.definitions() + FormattingTools.definitions() +
            (if (includeWebSearch) listOf(webSearchDefinition()) else emptyList())

    private fun webSearchDefinition(): ToolDefinition = ToolDefinition(
        name = "web_search",
        description = "Search the public web for current or factual information — news, prices, recent events, definitions, or anything you might not know or that could have changed since your training. This is only available because the user turned Web Search on. It returns a short digest of results; read it, then answer in the user's language, and say plainly if nothing useful came back.",
        params = listOf(ToolParam("query", "string", "What to look up on the web"))
    )

    private val READ_ONLY_TOOLS = setOf(
        "list_notes", "read_note", "list_tasks", "read_task", "search_items", "recall_notes", "web_search",
        "read_attachment", "list_note_versions", "list_trash", "list_drafts",
        "list_notebooks", "list_notebook_items", "search_notebook", "list_notebook_trash"
    )

    fun isMutating(name: String): Boolean = name !in READ_ONLY_TOOLS

    fun describeToolCall(name: String, argumentsJson: String): String {
        val a = try { JSONObject(argumentsJson) } catch (e: Exception) { JSONObject() }
        fun s(vararg keys: String): String {
            for (k in keys) { val v = a.optString(k, ""); if (v.isNotBlank()) return v }
            return ""
        }
        val title = s("title", "note_title", "task_title", "new_title")
        return when (name) {
            "create_note" -> com.lucent.app.i18n.S.ccCreateNote(s("title"))
            "update_note" -> com.lucent.app.i18n.S.ccEditNote(title) + newTitleSuffix(a)
            "delete_note" -> com.lucent.app.i18n.S.ccDeleteNote(title)
            "delete_draft" -> com.lucent.app.i18n.S.ccDeleteDraft(title)
            "pin_note" -> if (a.optBoolean("pinned", true)) com.lucent.app.i18n.S.ccPinNote(title) else com.lucent.app.i18n.S.ccUnpinNote(title)
            "archive_note" -> if (a.optBoolean("archived", true)) com.lucent.app.i18n.S.ccArchiveNote(title) else com.lucent.app.i18n.S.ccUnarchiveNote(title)
            "set_note_color" -> com.lucent.app.i18n.S.ccSetNoteColor(title, s("color", "colour"))
            "add_note_checklist_item" -> com.lucent.app.i18n.S.ccAddNoteItem(s("item"), title)
            "set_note_checklist_item_done" -> com.lucent.app.i18n.S.ccCheckNoteItem(s("item"), title)
            "edit_note_checklist_item" -> com.lucent.app.i18n.S.ccEditNoteItem(s("item"), title)
            "remove_note_checklist_item" -> com.lucent.app.i18n.S.ccRemoveNoteItem(s("item"), title)
            "set_note_attachment" -> com.lucent.app.i18n.S.ccSaveFileOnNote(s("file_name"), title)
            "remove_note_attachment" -> com.lucent.app.i18n.S.ccRemoveFileFromNote(s("file_name"), title)
            "attach_upload_to_note" -> com.lucent.app.i18n.S.ccAttachUploadToNote(title)
            "set_note_checklist_mode" -> if (a.optBoolean("checklist", true)) com.lucent.app.i18n.S.ccNoteToChecklist(title) else com.lucent.app.i18n.S.ccNoteToText(title)
            "restore_note_version" -> com.lucent.app.i18n.S.ccRestoreNoteVersion(title, a.optInt("version", 1).toString())
            "restore_note_from_trash" -> com.lucent.app.i18n.S.ccRestoreNoteFromTrash(title)
            "create_task" -> com.lucent.app.i18n.S.ccCreateTask(s("title")) + dueSuffix(s("due"))
            "complete_task" -> com.lucent.app.i18n.S.ccCompleteTask(title)
            "reopen_task" -> com.lucent.app.i18n.S.ccReopenTask(title)
            "update_task" -> com.lucent.app.i18n.S.ccEditTask(title) + newTitleSuffix(a)
            "delete_task" -> com.lucent.app.i18n.S.ccDeleteTask(title)
            "pin_task" -> if (a.optBoolean("pinned", true)) com.lucent.app.i18n.S.ccPinTask(title) else com.lucent.app.i18n.S.ccUnpinTask(title)
            "set_task_priority" -> com.lucent.app.i18n.S.ccSetPriority(title, s("priority"))
            "set_task_due_date" -> com.lucent.app.i18n.S.ccSetDueDate(title, s("due_at"))
            "add_subtask" -> com.lucent.app.i18n.S.ccAddSubtask(s("item"), title)
            "set_subtask_done" -> com.lucent.app.i18n.S.ccCheckSubtask(s("item"), title)
            "remove_subtask" -> com.lucent.app.i18n.S.ccRemoveSubtask(s("item"), title)
            "edit_subtask" -> com.lucent.app.i18n.S.ccEditSubtask(s("item"), title)
            "set_task_attachment" -> com.lucent.app.i18n.S.ccSaveFileOnTask(s("file_name"), title)
            "remove_task_attachment" -> com.lucent.app.i18n.S.ccRemoveFileFromTask(s("file_name"), title)
            "attach_upload_to_task" -> com.lucent.app.i18n.S.ccAttachUploadToTask(title)
            "restore_task_from_trash" -> com.lucent.app.i18n.S.ccRestoreTaskFromTrash(title)
            else -> FormattingTools.describeToolCall(name, a)
                ?: NotebookTools.describeToolCall(name, a)
                ?: com.lucent.app.i18n.S.ccRunGeneric(name)
        }
    }

    data class EditableArgument(
        val key: String,
        val label: String,
        val value: String,
        val multiline: Boolean = false
    )

    fun editableArgument(name: String, argumentsJson: String): EditableArgument? =
        editableArguments(name, argumentsJson).firstOrNull()

    fun editableArguments(name: String, argumentsJson: String): List<EditableArgument> {
        val a = try { JSONObject(argumentsJson) } catch (e: Exception) { return emptyList() }
        fun of(key: String, label: String, multiline: Boolean = false): EditableArgument? =
            a.optString(key, "").takeIf { it.isNotBlank() }
                ?.let { EditableArgument(key, label, it, multiline) }

        return when (name) {
            "create_note" -> listOfNotNull(
                of("title", com.lucent.app.i18n.S.confirmEditTitleLabel),
                of("body", com.lucent.app.i18n.S.confirmEditBodyLabel, multiline = true),
                of("checklist", com.lucent.app.i18n.S.confirmEditChecklistLabel, multiline = true),
                of("tags", com.lucent.app.i18n.S.confirmEditTagsLabel)
            )
            "create_task" -> listOfNotNull(
                of("title", com.lucent.app.i18n.S.confirmEditTitleLabel),
                of("notes", com.lucent.app.i18n.S.confirmEditNotesLabel, multiline = true),
                of("due", com.lucent.app.i18n.S.confirmEditDueLabel),
                of("subtasks", com.lucent.app.i18n.S.confirmEditSubtasksLabel, multiline = true)
            )
            "update_note" -> listOfNotNull(
                of("new_title", com.lucent.app.i18n.S.confirmEditNewTitleLabel),
                of("body", com.lucent.app.i18n.S.confirmEditBodyLabel, multiline = true)
            )
            "update_task" -> listOfNotNull(
                of("new_title", com.lucent.app.i18n.S.confirmEditNewTitleLabel),
                of("notes", com.lucent.app.i18n.S.confirmEditNotesLabel, multiline = true),
                of("new_due", com.lucent.app.i18n.S.confirmEditDueLabel)
            )
            "set_task_due_date" -> listOfNotNull(of("due_at", com.lucent.app.i18n.S.confirmEditDueLabel))
            "add_subtask" -> listOfNotNull(of("item", com.lucent.app.i18n.S.confirmEditItemLabel))
            "add_note_checklist_item" -> listOfNotNull(of("item", com.lucent.app.i18n.S.confirmEditItemLabel))
            "edit_subtask" -> listOfNotNull(of("new_text", com.lucent.app.i18n.S.confirmEditNewTextLabel))
            "edit_note_checklist_item" -> listOfNotNull(of("new_text", com.lucent.app.i18n.S.confirmEditNewTextLabel))
            "set_note_attachment", "set_task_attachment" -> listOfNotNull(
                of("file_name", com.lucent.app.i18n.S.confirmEditFileNameLabel),
                of("content", com.lucent.app.i18n.S.confirmEditContentLabel, multiline = true)
            )
            else -> FormattingTools.editableArguments(name, a)
                .ifEmpty { NotebookTools.editableArguments(name, a) }
        }
    }

    fun withArguments(argumentsJson: String, edits: Map<String, String>): String = try {
        val o = JSONObject(argumentsJson)
        edits.forEach { (k, v) -> if (v.isNotBlank()) o.put(k, v) }
        o.toString()
    } catch (e: Exception) {
        argumentsJson
    }

    fun withArgument(argumentsJson: String, key: String, value: String): String = try {
        JSONObject(argumentsJson).put(key, value).toString()
    } catch (e: Exception) {
        argumentsJson
    }

    private fun newTitleSuffix(a: JSONObject): String {
        val nt = a.optString("new_title", "")
        return if (nt.isNotBlank()) com.lucent.app.i18n.S.ccRenameSuffix(nt) else ""
    }

    private fun dueSuffix(due: String): String = if (due.isNotBlank()) com.lucent.app.i18n.S.ccDueSuffix(due) else ""

    private fun baseDefinitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "create_note",
            description = "Create a NOTE (a titled piece of written information with a body). Use this for information to remember, never for a to-do item. The body supports Markdown, and [[Note title]] creates a link to another note. To create a CHECKLIST note (checkable items instead of a body), pass the checklist field.",
            params = listOf(
                ToolParam("title", "string", "The title of the note"),
                ToolParam("body", "string", "The body text of the note"),
                ToolParam("tags", "string", "Optional comma-separated tags, e.g. \"Work,Ideas\"", required = false),
                ToolParam("checklist", "string", "Optional: item texts separated by a newline or a semicolon. When given, the note is created in checklist mode showing these checkable items instead of its body.", required = false)
            )
        ),
        ToolDefinition(
            name = "list_notes",
            description = "List the user's NOTES, including each note's tags, pin state, and the file names of any attachments. Returns only names/summaries, not attachment contents. If there are many notes, prefer search_items to find the relevant ones.",
            params = emptyList()
        ),
        ToolDefinition(
            name = "read_note",
            description = "Read the FULL contents of a single NOTE (matched by its title): its body or checklist, tags, and its attachments. Text attachments are included as text; image attachments are shown to you directly so you can look at them. Use this whenever you need to actually see what is written in a note or view a file attached to it.",
            params = listOf(ToolParam("title", "string", "The title (or part of it) of the note to read"))
        ),
        ToolDefinition(
            name = "update_note",
            description = "Edit an existing NOTE, matched by its current title text. Provide a new title and/or new body; leave a field out to keep it unchanged. The note's previous text is automatically saved to its version history, so an edit can always be undone by the user.",
            params = listOf(
                ToolParam("title", "string", "The current title (or part of it) of the note to edit"),
                ToolParam("new_title", "string", "The new title for the note", required = false),
                ToolParam("new_body", "string", "The new body text for the note", required = false),
                ToolParam("new_tags", "string", "New comma-separated tags for the note", required = false)
            )
        ),
        ToolDefinition(
            name = "delete_note",
            description = "Delete a NOTE (doodle notes included), matched by its title text. This moves it to Trash rather than erasing it — the user can restore it themselves for 30 days afterwards, so you can be matter-of-fact about deleting when asked.",
            params = listOf(ToolParam("title", "string", "The title (or part of it) of the note to delete"))
        ),
        ToolDefinition(
            name = "pin_note",
            description = "Pin or unpin a NOTE, matched by its title text. Pinned notes stay at the top of the notes list.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the note"),
                ToolParam("pinned", "boolean", "true to pin, false to unpin")
            )
        ),
        ToolDefinition(
            name = "archive_note",
            description = "Archive or unarchive a NOTE, matched by its title text. An archived note is hidden from the notes home page and kept on the Archive screen; unarchiving brings it back home.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the note"),
                ToolParam("archived", "boolean", "true to archive, false to unarchive")
            )
        ),
        ToolDefinition(
            name = "set_note_color",
            description = "Set a NOTE's accent colour, matched by its title text. Colours: default (no tint), red, orange, yellow, green, teal, blue, purple, pink.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the note"),
                ToolParam("color", "string", "One of: default, red, orange, yellow, green, teal, blue, purple, pink")
            )
        ),
        ToolDefinition(
            name = "add_note_checklist_item",
            description = "Add one checklist item to a NOTE, matched by its title text. If the note isn't a checklist yet it becomes one (its body text is kept, and comes back if the user switches the note back to plain text).",
            params = listOf(
                ToolParam("note_title", "string", "The title (or part of it) of the note"),
                ToolParam("item", "string", "The text of the checklist item to add")
            )
        ),
        ToolDefinition(
            name = "set_note_checklist_item_done",
            description = "Check or uncheck one checklist item on a checklist NOTE, matched by the note title and the item text (a close partial match is accepted). Read the note first if you need its exact item text.",
            params = listOf(
                ToolParam("note_title", "string", "The title (or part of it) of the note"),
                ToolParam("item", "string", "The text (or part of it) of the checklist item"),
                ToolParam("done", "boolean", "true to check it, false to uncheck it. Leave out to toggle its current state.", required = false)
            )
        ),
        ToolDefinition(
            name = "edit_note_checklist_item",
            description = "Rewrite the text of one checklist item on a checklist NOTE, matched by the note title and the item's current text (a close partial match is accepted). The item keeps its checked state.",
            params = listOf(
                ToolParam("note_title", "string", "The title (or part of it) of the note"),
                ToolParam("item", "string", "The current text (or part of it) of the checklist item"),
                ToolParam("new_text", "string", "The new text for the item")
            )
        ),
        ToolDefinition(
            name = "remove_note_checklist_item",
            description = "Remove one checklist item from a checklist NOTE, matched by the note title and the item text (a close partial match is accepted).",
            params = listOf(
                ToolParam("note_title", "string", "The title (or part of it) of the note"),
                ToolParam("item", "string", "The text (or part of it) of the checklist item to remove")
            )
        ),
        ToolDefinition(
            name = "set_note_checklist_mode",
            description = "Switch a NOTE between checklist mode and plain-text mode, matched by its title — the same toggle the note editor has. Nothing is lost either way: the body text and the checklist items are both kept on the note, and switching back brings the other one up.",
            params = listOf(
                ToolParam("note_title", "string", "The title (or part of it) of the note"),
                ToolParam("checklist", "boolean", "true to show the note as a checklist, false to show its plain-text body")
            )
        ),
        ToolDefinition(
            name = "set_note_attachment",
            description = "Add or replace a text-file attachment on a NOTE (matched by title). If a file with the same name already exists it is overwritten, so this both adds and modifies attachments.",
            params = listOf(
                ToolParam("note_title", "string", "The title (or part of it) of the note"),
                ToolParam("file_name", "string", "The attachment file name, e.g. summary.txt"),
                ToolParam("content", "string", "The text content of the attachment")
            )
        ),
        ToolDefinition(
            name = "remove_note_attachment",
            description = "Remove an attachment from a NOTE, matched by the note title and the attachment's file name (a close partial name is accepted). Read the note first so you use the real file name.",
            params = listOf(
                ToolParam("note_title", "string", "The title (or part of it) of the note"),
                ToolParam("file_name", "string", "The file name (or part of it) of the attachment to remove")
            )
        ),
        ToolDefinition(
            name = "attach_upload_to_note",
            description = "Attach the file the user UPLOADED in this chat (a photo, image, or document) onto a NOTE, matched by title. Acts on their most recent upload in this conversation, so it also works when the file was sent a few messages earlier. Use this whenever the user uploads a file and wants it saved on a note. This is the only way to attach an uploaded file; set_note_attachment cannot (it only writes text).",
            params = listOf(
                ToolParam("note_title", "string", "The title (or part of it) of the note to attach the uploaded file to"),
                ToolParam("file_name", "string", "Optional name to save the file as; leave out to keep the uploaded file's own name", required = false)
            )
        ),
        ToolDefinition(
            name = "list_note_versions",
            description = "List a NOTE's saved history versions (matched by its title), newest first: each saved version's time, title, and a short preview, numbered from 1 for the most recently saved. Every edit records the previous text automatically, so this is how you see what a note used to say — call it before restore_note_version.",
            params = listOf(ToolParam("title", "string", "The title (or part of it) of the note"))
        ),
        ToolDefinition(
            name = "restore_note_version",
            description = "Restore a NOTE (matched by its title) to one of its saved history versions — pass the version number from list_note_versions (1 = the most recently saved). The note's current text is recorded to history first, so a restore is itself undoable. Call list_note_versions first so you restore the right one.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the note"),
                ToolParam("version", "number", "The version number from list_note_versions (1 = most recent)")
            )
        ),

        ToolDefinition(
            name = "create_task",
            description = "Create a TASK (a to-do item that can be marked done). Use this for something the user needs to do, never for storing information. Optionally set notes, a priority, a due date, a repeat schedule, a reminder, and an initial checklist — all in this one call.",
            params = listOf(
                ToolParam("title", "string", "The title of the task"),
                ToolParam("notes", "string", "Optional notes/description for the task", required = false),
                ToolParam("priority", "string", "Optional priority: none, low, medium, or high", required = false),
                ToolParam("due", "string", "Optional due date as an ABSOLUTE local date: \"YYYY-MM-DD\" or \"YYYY-MM-DD HH:mm\" (24-hour). Work the concrete date out yourself from today's date; never pass a word like \"tomorrow\".", required = false),
                ToolParam("repeat", "string", "Optional repeat schedule: daily, weekly, monthly, or yearly. Leave out for a one-off task.", required = false),
                ToolParam("reminder", "boolean", "Optional: true to notify the user at the due time. Needs a due date to have any effect.", required = false),
                ToolParam("subtasks", "string", "Optional initial checklist: item texts separated by a newline or a semicolon", required = false)
            )
        ),
        ToolDefinition(
            name = "list_tasks",
            description = "List the user's TASKS, including which are done or pending, their priority, due date, pin state, repeat schedule, checklist progress, and the file names of any attachments. Returns only names/summaries, not attachment contents. If there are many tasks, prefer search_items.",
            params = emptyList()
        ),
        ToolDefinition(
            name = "read_task",
            description = "Read the FULL contents of a single TASK (matched by its title): its notes/description, done state, priority, due date, repeat schedule, reminder, full checklist, and its attachments. Text attachments are included as text; image attachments are shown to you directly. Read the task before toggling or removing one of its subtasks, so you have the exact subtask text.",
            params = listOf(ToolParam("title", "string", "The title (or part of it) of the task to read"))
        ),
        ToolDefinition(
            name = "complete_task",
            description = "Mark a TASK as done, matched by its title text. If the task repeats, the next occurrence is created automatically with its due date advanced and its checklist reset.",
            params = listOf(ToolParam("title", "string", "The title (or part of it) of the task to complete"))
        ),
        ToolDefinition(
            name = "reopen_task",
            description = "Send a completed TASK back to the active list (mark it NOT done), matched by its title text. Its reminder is re-armed if it still has a future due time. Use this when the user says something was ticked off by mistake or isn't actually finished.",
            params = listOf(ToolParam("title", "string", "The title (or part of it) of the completed task to reopen"))
        ),
        ToolDefinition(
            name = "update_task",
            description = "Edit an existing TASK, matched by its current title text. Provide any of the new_* fields to change them; leave a field out to keep it unchanged.",
            params = listOf(
                ToolParam("title", "string", "The current title (or part of it) of the task to edit"),
                ToolParam("new_title", "string", "The new title for the task", required = false),
                ToolParam("new_notes", "string", "The new notes/description text for the task", required = false),
                ToolParam("new_priority", "string", "New priority: none, low, medium, or high", required = false),
                ToolParam("new_due", "string", "New due date as \"YYYY-MM-DD\" or \"YYYY-MM-DD HH:mm\" (absolute, local). Pass \"none\" to clear it.", required = false),
                ToolParam("new_repeat", "string", "New repeat schedule: daily, weekly, monthly, yearly, or \"none\" to stop repeating", required = false),
                ToolParam("new_reminder", "boolean", "true to notify at the due time, false to turn the reminder off", required = false)
            )
        ),
        ToolDefinition(
            name = "delete_task",
            description = "Delete a TASK, matched by its title text. This moves it to Trash rather than erasing it — the user can restore it themselves for 30 days afterwards.",
            params = listOf(ToolParam("title", "string", "The title (or part of it) of the task to delete"))
        ),
        ToolDefinition(
            name = "pin_task",
            description = "Pin or unpin a TASK, matched by its title text. Pinned tasks stay at the top of the active task list.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the task"),
                ToolParam("pinned", "boolean", "true to pin, false to unpin")
            )
        ),
        ToolDefinition(
            name = "set_task_priority",
            description = "Set how important a TASK is, matched by its title text. Higher-priority tasks are highlighted and can be sorted to the top.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the task"),
                ToolParam("priority", "string", "One of: none, low, medium, high")
            )
        ),
        ToolDefinition(
            name = "set_task_due_date",
            description = "Set, change, or clear a TASK's due date, matched by its title text, and optionally make it repeat or turn its reminder on. Pass \"none\" as due_at to clear the due date (which also clears any repeat, since a repeat needs a due date to advance from).",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the task"),
                ToolParam("due_at", "string", "The due date as \"YYYY-MM-DD\" or \"YYYY-MM-DD HH:mm\" (absolute, local), or \"none\" to clear it"),
                ToolParam("repeat", "string", "Optional: none, daily, weekly, monthly, or yearly", required = false),
                ToolParam("reminder", "boolean", "Optional: true to notify the user at the due time", required = false)
            )
        ),
        ToolDefinition(
            name = "add_subtask",
            description = "Add one checklist item to a TASK, matched by its title text.",
            params = listOf(
                ToolParam("task_title", "string", "The title (or part of it) of the task"),
                ToolParam("item", "string", "The text of the checklist item to add")
            )
        ),
        ToolDefinition(
            name = "set_subtask_done",
            description = "Check or uncheck one of a TASK's checklist items, matched by the task title and the item text (a close partial match is accepted). Read the task first if you need its exact item text.",
            params = listOf(
                ToolParam("task_title", "string", "The title (or part of it) of the task"),
                ToolParam("item", "string", "The text (or part of it) of the checklist item"),
                ToolParam("done", "boolean", "true to check it, false to uncheck it. Leave out to toggle its current state.", required = false)
            )
        ),
        ToolDefinition(
            name = "edit_subtask",
            description = "Rewrite the text of one of a TASK's checklist items, matched by the task title and the item's current text (a close partial match is accepted). The item keeps its checked state.",
            params = listOf(
                ToolParam("task_title", "string", "The title (or part of it) of the task"),
                ToolParam("item", "string", "The current text (or part of it) of the checklist item"),
                ToolParam("new_text", "string", "The new text for the item")
            )
        ),
        ToolDefinition(
            name = "remove_subtask",
            description = "Remove one checklist item from a TASK, matched by the task title and the item text (a close partial match is accepted).",
            params = listOf(
                ToolParam("task_title", "string", "The title (or part of it) of the task"),
                ToolParam("item", "string", "The text (or part of it) of the checklist item to remove")
            )
        ),
        ToolDefinition(
            name = "set_task_attachment",
            description = "Add or replace a text-file attachment on a TASK (matched by title). If a file with the same name already exists it is overwritten.",
            params = listOf(
                ToolParam("task_title", "string", "The title (or part of it) of the task"),
                ToolParam("file_name", "string", "The attachment file name, e.g. notes.txt"),
                ToolParam("content", "string", "The text content of the attachment")
            )
        ),
        ToolDefinition(
            name = "remove_task_attachment",
            description = "Remove an attachment from a TASK, matched by the task title and the attachment's file name (a close partial name is accepted). Read the task first so you use the real file name.",
            params = listOf(
                ToolParam("task_title", "string", "The title (or part of it) of the task"),
                ToolParam("file_name", "string", "The file name (or part of it) of the attachment to remove")
            )
        ),
        ToolDefinition(
            name = "attach_upload_to_task",
            description = "Attach the file the user UPLOADED in this chat (a photo, image, or document) onto a TASK, matched by title. Acts on their most recent upload in this conversation, so it also works when the file was sent a few messages earlier. Use this whenever the user uploads a file and wants it saved on a task. This is the only way to attach an uploaded file; set_task_attachment cannot (it only writes text).",
            params = listOf(
                ToolParam("task_title", "string", "The title (or part of it) of the task to attach the uploaded file to"),
                ToolParam("file_name", "string", "Optional name to save the file as; leave out to keep the uploaded file's own name", required = false)
            )
        ),

        ToolDefinition(
            name = "list_drafts",
            description = "List the user's DRAFTS: notes and tasks that were saved to the draft area (manually, or automatically when the app closed unexpectedly) and have not been committed yet. Returns titles and when each draft was saved. The assistant cannot edit or promote drafts — only the user can, from the Drafts screen; the assistant can delete one with delete_draft if asked.",
            params = emptyList()
        ),
        ToolDefinition(
            name = "delete_draft",
            description = "Permanently delete a DRAFT note or task, matched by its title. Unlike delete_note/delete_task this does NOT go to the Trash — it matches the app's own Drafts screen, where discarding a draft is final. Only use when the user clearly asks to discard a draft.",
            params = listOf(
                ToolParam("kind", "string", "Which kind of draft: note or task"),
                ToolParam("title", "string", "The title (or part of it) of the draft to delete")
            )
        ),
        ToolDefinition(
            name = "list_trash",
            description = "List what's currently in the Trash: recently deleted notes and/or tasks, with when each was deleted. Deleted items stay there for 30 days before they're removed for good. Use this to find something the user deleted and wants back, then bring it back with restore_note_from_trash or restore_task_from_trash.",
            params = listOf(ToolParam("type", "string", "What to list: notes, tasks, or both. Defaults to both.", required = false))
        ),
        ToolDefinition(
            name = "restore_note_from_trash",
            description = "Bring a deleted NOTE back out of the Trash, matched by its title. It returns to wherever it lived before — the notes page, or the Archive screen if it was archived. Call list_trash first to see what's there.",
            params = listOf(ToolParam("title", "string", "The title (or part of it) of the trashed note to restore"))
        ),
        ToolDefinition(
            name = "restore_task_from_trash",
            description = "Bring a deleted TASK back out of the Trash, matched by its title. Its reminder is re-armed if it still has a future due time. Call list_trash first to see what's there.",
            params = listOf(ToolParam("title", "string", "The title (or part of it) of the trashed task to restore"))
        ),

        ToolDefinition(
            name = "read_attachment",
            description = "Read ONE attachment by name from a note or task, without pulling in the rest of the item. Text files come back as text; an image is shown to you directly so you can look at it. Use it when the person asks about a specific file whose name you know (or have just listed); use read_note/read_task instead when you also need the item's own contents.",
            params = listOf(
                ToolParam("item_type", "string", "Where the attachment lives: note or task. Leave out to search both.", required = false),
                ToolParam("title", "string", "The title (or part of it) of the note or task"),
                ToolParam("file_name", "string", "The file name (or part of it) of the attachment to read")
            )
        ),
        ToolDefinition(
            name = "search_items",
            description = "Search the user's notes and/or tasks and get back only the matching ones. Prefer this over list_notes/list_tasks when the user has a lot of them, or when you're looking for something specific. Supports plain words, \"exact phrases\", and filters: tag:work, is:pinned, is:checklist, is:archived, is:done, is:overdue, has:attachment, has:due, has:reminder, has:subtasks, priority:high, due:today (or tomorrow / week / overdue). Everything you give must match.",
            params = listOf(
                ToolParam("query", "string", "The search query, e.g. \"budget tag:work\" or \"priority:high due:week\""),
                ToolParam("type", "string", "What to search: notes, tasks, or both. Defaults to both.", required = false)
            )
        ),
        ToolDefinition(
            name = "recall_notes",
            description = "Find notes by MEANING rather than exact wording — use this for a vague or conceptual question like \"what did I write about the Osaka trip\" where you don't know the exact words the note uses. Prefer search_items instead whenever the user gives you specific words, tags, or filters to match. May report that it isn't available yet, depending on the user's embedding provider setting — if so, fall back to search_items with your best guess at keywords.",
            params = listOf(
                ToolParam("query", "string", "A natural-language description of what to find, e.g. \"notes about the Osaka trip\"")
            )
        )
    )


    private fun JSONObject.firstString(vararg keys: String): String {
        for (key in keys) {
            if (has(key)) {
                val value = optString(key, "")
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }

    private fun JSONObject.hasAny(vararg keys: String): Boolean = keys.any { has(it) }

    internal suspend fun activeNotes(db: AppDatabase): List<Note> =
        db.noteDao().getAllOnce().filter { it.trashedAt == null && !it.hidden && !it.isDraft }

    internal suspend fun activeTasks(db: AppDatabase): List<Task> =
        db.taskDao().getAllOnce().filter { it.trashedAt == null && !it.hidden && !it.isDraft }

    internal suspend fun editableNotes(db: AppDatabase): List<Note> =
        db.noteDao().getAllOnce().filter { it.trashedAt == null && !it.hidden }

    internal suspend fun editableTasks(db: AppDatabase): List<Task> =
        db.taskDao().getAllOnce().filter { it.trashedAt == null && !it.hidden }

    internal suspend fun hiddenNotes(db: AppDatabase): List<Note> =
        db.noteDao().getAllOnce().filter { it.hidden }

    internal suspend fun hiddenTasks(db: AppDatabase): List<Task> =
        db.taskDao().getAllOnce().filter { it.hidden }

    private suspend fun draftNotes(db: AppDatabase): List<Note> =
        db.noteDao().getAllOnce().filter { it.isDraft && it.trashedAt == null && !it.hidden }

    private suspend fun draftTasks(db: AppDatabase): List<Task> =
        db.taskDao().getAllOnce().filter { it.isDraft && it.trashedAt == null && !it.hidden }

    private suspend fun trashedNotes(db: AppDatabase): List<Note> =
        db.noteDao().getAllOnce().filter { it.trashedAt != null && !it.hidden }

    private suspend fun trashedTasks(db: AppDatabase): List<Task> =
        db.taskDao().getAllOnce().filter { it.trashedAt != null && !it.hidden }

    private fun matchNote(notes: List<Note>, query: String): Note? =
        bestMatch(notes, query, { it.title }, { it.updatedAt })

    private fun matchTask(tasks: List<Task>, query: String): Task? =
        bestMatch(tasks, query, { it.title }, { it.createdAt })

    internal fun resolveNote(notes: List<Note>, query: String): Note? = matchNote(notes, query)

    internal fun resolveTask(tasks: List<Task>, query: String): Task? = matchTask(tasks, query)

    private fun composed(value: String): String =
        try {
            java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC)
        } catch (t: Throwable) {
            value
        }

    private fun normalizeTitle(value: String): String =
        composed(value).lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun meaningfulTitle(value: String): String =
        normalizeTitle(value).split(' ')
            .filter { it.isNotBlank() && it !in TITLE_NOISE_WORDS }
            .joinToString(" ")

    private fun titleScore(title: String, query: String): Int {
        val storedRaw = composed(title.trim())
        val askedRaw = composed(query.trim())
        if (storedRaw.isEmpty() || askedRaw.isEmpty()) return 0
        if (storedRaw.equals(askedRaw, ignoreCase = true)) return 1000

        val stored = normalizeTitle(storedRaw)
        val asked = normalizeTitle(askedRaw)
        if (stored.isNotBlank() && asked.isNotBlank()) {
            if (stored == asked) return 990
            val storedCore = meaningfulTitle(stored)
            val askedCore = meaningfulTitle(asked)
            if (storedCore.isNotBlank() && storedCore == askedCore) return 900
            if (stored.startsWith(asked)) return 800 + asked.length.coerceAtMost(60)
            if (stored.contains(asked)) return 700 + asked.length.coerceAtMost(60)
            if (asked.contains(stored) && stored.length >= 3) return 600 + stored.length.coerceAtMost(60)
            val askedTokens = askedCore.split(' ').filter { it.length >= 2 }
            val storedTokens = storedCore.split(' ').filter { it.isNotBlank() }
            if (askedTokens.isNotEmpty() && storedTokens.isNotEmpty()) {
                val hits = askedTokens.count { token ->
                    storedTokens.any { it.contains(token) || token.contains(it) }
                }
                val coverage = hits.toDouble() / askedTokens.size
                if (coverage >= 0.6) return 300 + (coverage * 100).toInt()
            }
        }

        if (storedRaw.contains(askedRaw, ignoreCase = true)) return 690 + askedRaw.length.coerceAtMost(60)
        if (askedRaw.contains(storedRaw, ignoreCase = true) && storedRaw.length >= 2) {
            return 590 + storedRaw.length.coerceAtMost(60)
        }
        return 0
    }

    private fun <T> rankByTitle(items: List<T>, query: String, titleOf: (T) -> String): List<Pair<T, Int>> =
        items.map { it to titleScore(titleOf(it), query) }

    private fun <T> bestMatch(
        items: List<T>,
        query: String,
        titleOf: (T) -> String,
        recencyOf: (T) -> Long,
        minScore: Int = MATCH_MIN_SCORE
    ): T? = rankByTitle(items, query, titleOf)
        .filter { it.second >= minScore }
        .sortedWith(compareByDescending<Pair<T, Int>> { it.second }.thenByDescending { recencyOf(it.first) })
        .firstOrNull()
        ?.first

    private fun <T> closestTitles(
        items: List<T>,
        query: String,
        titleOf: (T) -> String,
        recencyOf: (T) -> Long,
        limit: Int = 5
    ): List<String> {
        if (items.isEmpty()) return emptyList()
        val ranked = rankByTitle(items, query, titleOf)
        val ordered = if (ranked.any { it.second > 0 }) {
            ranked.sortedWith(compareByDescending<Pair<T, Int>> { it.second }.thenByDescending { recencyOf(it.first) })
        } else {
            ranked.sortedByDescending { recencyOf(it.first) }
        }
        return ordered.take(limit).map { titleOf(it.first) }.filter { it.isNotBlank() }
    }

    private fun noMatchMessage(kind: String, query: String, candidates: List<String>): String {
        val retry = "Call the same tool again with one of those exact titles."
        if (candidates.isEmpty()) {
            return "No $kind matched \"$query\", and there is nothing to match against yet. $retry"
        }
        val list = candidates.joinToString("; ") { "\"$it\"" }
        return "No $kind matched \"$query\". Closest existing titles: $list. $retry"
    }

    internal fun noteNotFound(
        query: String,
        candidates: List<Note>,
        kind: String = "note",
        extra: String = ""
    ): ToolExecResult =
        ToolExecResult(
            noMatchMessage(kind, query, closestTitles(candidates, query, { it.title }, { it.updatedAt })) + extra,
            success = false
        )

    private fun blankTitleResult(kind: String): ToolExecResult = ToolExecResult(
        "No $kind title was given in the title field, so nothing could be looked up. Find the item " +
            "first (list_notes, list_tasks, or read_note/read_task) and call the tool again with its " +
            "exact current title.",
        success = false
    )

    internal suspend fun noteNotFound(db: AppDatabase, query: String): ToolExecResult {
        if (query.isBlank()) return blankTitleResult("note")
        bestMatch(hiddenNotes(db), query, { it.title }, { it.updatedAt }, minScore = 700)?.let { hit ->
            return ToolExecResult(
                "The note \"${hit.title}\" exists but it is HIDDEN in the app, which is why it is not " +
                    "among the notes you can change. Do not treat it as missing: tell the user it is hidden " +
                    "and that they can unhide it in Settings, Privacy, Hidden area.",
                success = false
            )
        }
        bestMatch(trashedNotes(db), query, { it.title }, { it.updatedAt }, minScore = 700)?.let { hit ->
            return ToolExecResult(
                "The note \"${hit.title}\" is in the Trash, which is why it is not among the current " +
                    "notes. If the user means that one, call restore_note_from_trash with that exact title " +
                    "first, then make the change.",
                success = false
            )
        }
        return noteNotFound(query, editableNotes(db))
    }

    internal fun taskNotFound(
        query: String,
        candidates: List<Task>,
        kind: String = "task",
        extra: String = ""
    ): ToolExecResult =
        ToolExecResult(
            noMatchMessage(kind, query, closestTitles(candidates, query, { it.title }, { it.createdAt })) + extra,
            success = false
        )

    internal suspend fun taskNotFound(db: AppDatabase, query: String): ToolExecResult {
        if (query.isBlank()) return blankTitleResult("task")
        bestMatch(hiddenTasks(db), query, { it.title }, { it.createdAt }, minScore = 700)?.let { hit ->
            return ToolExecResult(
                "The task \"${hit.title}\" exists but it is HIDDEN in the app, which is why it is not " +
                    "among the tasks you can change. Do not treat it as missing: tell the user it is hidden " +
                    "and that they can unhide it in Settings, Privacy, Hidden area.",
                success = false
            )
        }
        bestMatch(trashedTasks(db), query, { it.title }, { it.createdAt }, minScore = 700)?.let { hit ->
            return ToolExecResult(
                "The task \"${hit.title}\" is in the Trash, which is why it is not among the current " +
                    "tasks. If the user means that one, call restore_task_from_trash with that exact title " +
                    "first, then make the change.",
                success = false
            )
        }
        return taskNotFound(query, editableTasks(db))
    }

    private fun attachmentSummary(json: String): String {
        val names = Attachments.parse(json).map { it.name }
        return if (names.isEmpty()) "no attachments" else "attachments: ${names.joinToString(", ")}"
    }

    private fun describeAttachment(context: Context, att: Attachment): String {
        return if (att.isImage) {
            "[image \"${att.name}\" - shown to you below]"
        } else {
            val text = Attachments.decodeText(context, att)
            if (text != null) {
                "----- attachment \"${att.name}\" -----\n$text\n----- end \"${att.name}\" -----"
            } else {
                "[attachment \"${att.name}\" (${att.mime}) - binary file, contents can't be shown as text]"
            }
        }
    }

    private fun imagesFrom(context: Context, list: List<Attachment>): List<ToolImage> =
        list.filter { it.isImage }.mapNotNull { att ->
            val base64 = Attachments.readAsBase64(context, att) ?: return@mapNotNull null
            ToolImage(mime = att.mime, data = base64, name = att.name)
        }

    private fun resolveAttachmentName(list: List<Attachment>, query: String): String? {
        list.firstOrNull { it.name.equals(query, ignoreCase = true) }?.let { return it.name }
        val partial = list.filter { it.name.contains(query, ignoreCase = true) }
        return if (partial.size == 1) partial.first().name else null
    }

    internal fun summarize(task: Task): String {
        val sb = StringBuilder()
        sb.append(task.title)
        sb.append(" [").append(if (task.isDone) "done" else "pending").append("]")
        if (task.pinned) sb.append(" [pinned]")
        TaskPriority.fromValue(task.priority).takeIf { it != TaskPriority.NONE }?.let {
            sb.append(" {priority: ${it.key}}")
        }
        task.dueAt?.let { sb.append(" {due: ${DueParsing.format(it)}}") }
        RepeatRule.fromKey(task.repeatRule).takeIf { it != RepeatRule.NONE }?.let {
            sb.append(" {repeats: ${it.key.lowercase()}}")
        }
        if (task.reminderEnabled && task.dueAt != null && !task.isDone) sb.append(" {reminder: on}")
        Checklist.progress(task.subtasks)?.let { (done, total) -> sb.append(" {checklist: $done/$total}") }
        sb.append(" (").append(attachmentSummary(task.attachments)).append(")")
        return sb.toString()
    }

    internal fun summarize(note: Note): String {
        val sb = StringBuilder()
        sb.append(note.title.ifBlank { "Untitled" })
        if (note.pinned) sb.append(" [pinned]")
        if (note.archived) sb.append(" [archived]")
        if (note.isDoodle) sb.append(" {drawing}")
        if (note.isChecklist) {
            Checklist.progress(note.checklist)?.let { (done, total) -> sb.append(" {checklist: $done/$total}") }
        } else {
            sb.append(": ").append(note.body.take(160))
        }
        if (note.tags.isNotBlank()) sb.append(" {tags: ${note.tags}}")
        sb.append(" (").append(attachmentSummary(note.attachments)).append(")")
        return sb.toString()
    }

    private suspend fun storeUpload(
        context: Context,
        db: AppDatabase,
        uploadMime: String?,
        uploadData: String?,
        uploadName: String?,
        requestedName: String,
        onReady: suspend (Attachment) -> ToolExecResult
    ): ToolExecResult {
        if (uploadData.isNullOrBlank()) {
            return ToolExecResult("There's no uploaded file in this conversation yet. Ask the user to attach the file in the chat box, then try again.", success = false)
        }
        val bytes = try {
            android.util.Base64.decode(uploadData, android.util.Base64.DEFAULT)
        } catch (t: Throwable) {
            null
        } ?: return ToolExecResult("Couldn't read the uploaded file.", success = false)

        val check = AttachmentLimits.checkSingle(bytes.size.toLong())
        if (!check.allowed) return ToolExecResult(check.message, success = false)

        val id = AttachmentStore.importBytes(context, bytes)
            ?: return ToolExecResult("Couldn't save the uploaded file (disk error).", success = false)
        val fileName = when {
            requestedName.isNotBlank() -> requestedName
            !uploadName.isNullOrBlank() -> uploadName
            else -> defaultUploadName(uploadMime)
        }
        val mime = if (uploadMime.isNullOrBlank()) "application/octet-stream" else uploadMime
        return onReady(Attachment(mime = mime, data = id, name = fileName))
    }

    private fun defaultUploadName(mime: String?): String = when {
        mime == null -> "upload"
        mime.startsWith("image/jpeg") || mime.startsWith("image/jpg") -> "image.jpg"
        mime.startsWith("image/png") -> "image.png"
        mime.startsWith("image/webp") -> "image.webp"
        mime.startsWith("image/gif") -> "image.gif"
        mime.startsWith("image/") -> "image"
        mime == "application/pdf" -> "document.pdf"
        else -> "upload"
    }


    suspend fun execute(
        context: Context,
        db: AppDatabase,
        name: String,
        argumentsJson: String,
        uploadMime: String? = null,
        uploadData: String? = null,
        uploadName: String? = null
    ): ToolExecResult {
        val appContext = context.applicationContext
        val args = try { JSONObject(argumentsJson) } catch (e: Exception) { JSONObject() }

        return when (name) {


            "create_note" -> {
                val title = args.optString("title", "Untitled")
                val body = args.optString("body", "")
                val tags = args.optString("tags", "")
                val checklistJson = Checklist.addAll("[]", args.optString("checklist", ""))
                val isChecklist = checklistJson != "[]"
                val newNoteId = db.noteDao().insert(
                    Note(title = title, body = body, tags = tags, isChecklist = isChecklist, checklist = checklistJson)
                )
                val suffix = Checklist.progress(checklistJson)?.let { (_, total) ->
                    " as a checklist with $total item${if (total == 1) "" else "s"}"
                } ?: ""
                ToolExecResult("Created note \"$title\"$suffix.", openNoteId = newNoteId)
            }

            "list_notes" -> {
                val notes = activeNotes(db)
                val summary = if (notes.isEmpty()) "There are no notes yet."
                else notes.joinToString("; ") { summarize(it) }
                ToolExecResult(summary)
            }

            "read_note" -> {
                val titleQuery = args.optString("title", "")
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else {
                    val attachments = Attachments.parse(match.attachments)
                    val sb = StringBuilder()
                    sb.append("Note \"${match.title}\".\n")
                    if (match.pinned) sb.append("Pinned: yes\n")
                    if (match.archived) sb.append("Archived: yes\n")
                    if (match.tags.isNotBlank()) sb.append("Tags: ${match.tags}\n")
                    if (match.isDoodle) {
                        val strokes = com.lucent.app.ui.Doodle.parse(match.doodle)
                        sb.append("This is a doodle note — a freehand drawing (${strokes.size} strokes). ")
                        sb.append("The drawing itself cannot be read as text.\n")
                        sb.append("Caption:\n${match.body.ifBlank { "(no caption)" }}\n")
                    } else if (match.isChecklist) {
                        val items = Checklist.parse(match.checklist)
                        if (items.isEmpty()) {
                            sb.append("Checklist: (empty)\n")
                        } else {
                            sb.append("Checklist (${items.size}):\n")
                            items.forEach { sb.append(if (it.done) "[x] " else "[ ] ").append(it.text).append("\n") }
                        }
                    } else {
                        sb.append("Body:\n${match.body.ifBlank { "(empty)" }}\n")
                    }
                    if (attachments.isEmpty()) {
                        sb.append("Attachments: none.")
                    } else {
                        sb.append("Attachments (${attachments.size}):\n")
                        attachments.forEach { sb.append(describeAttachment(appContext, it)).append("\n") }
                    }
                    ToolExecResult(sb.toString().trim(), imagesFrom(appContext, attachments))
                }
            }

            "update_note" -> {
                val titleQuery = args.optString("title", "")
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else if (match.isDoodle) {
                    ToolExecResult("\"${match.title}\" is a doodle note. The assistant can delete doodle notes but cannot edit them — the user edits the drawing in the app.", success = false)
                } else if (!args.hasAny("new_title", "new_body", "new_tags")) {
                    ToolExecResult("No changes were provided.", success = false)
                } else {
                    val newTitle = args.optString("new_title", "")
                    val updated = match.copy(
                        title = if (newTitle.isNotBlank()) newTitle else match.title,
                        body = if (args.has("new_body")) args.optString("new_body") else match.body,
                        tags = if (args.has("new_tags")) args.optString("new_tags") else match.tags
                    )
                    TaskActions.updateNoteWithHistory(db, match, updated)
                    ToolExecResult("Updated note \"${updated.title}\". Its previous version was saved to history.", openNoteId = match.id)
                }
            }

            "delete_note" -> {
                val titleQuery = args.optString("title", "")
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else {
                    TaskActions.trashNote(db, match)
                    ToolExecResult("Moved note \"${match.title}\" to Trash. The user can restore it from there.")
                }
            }

            "pin_note" -> {
                val titleQuery = args.optString("title", "")
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else if (match.isDoodle) {
                    ToolExecResult("\"${match.title}\" is a doodle note; the assistant can currently only delete doodle notes.", success = false)
                } else {
                    val pinned = args.optBoolean("pinned", true)
                    db.noteDao().update(match.copy(pinned = pinned))
                    ToolExecResult("${if (pinned) "Pinned" else "Unpinned"} note \"${match.title}\".")
                }
            }

            "archive_note" -> {
                val titleQuery = args.optString("title", "")
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else if (match.isDoodle) {
                    ToolExecResult("\"${match.title}\" is a doodle note; the assistant can currently only delete doodle notes.", success = false)
                } else {
                    val archived = args.optBoolean("archived", true)
                    db.noteDao().update(
                        match.copy(archived = archived, archivedAt = if (archived) System.currentTimeMillis() else null)
                    )
                    ToolExecResult(
                        if (archived) "Archived note \"${match.title}\" — it now lives on the Archive screen."
                        else "Unarchived note \"${match.title}\" — it's back on the notes page."
                    )
                }
            }

            "set_note_color" -> {
                val titleQuery = args.optString("title", "")
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else {
                    val raw = args.firstString("color", "colour").trim().lowercase()
                    val color = when {
                        raw.isBlank() || raw == "default" || raw == "none" -> NoteColor.DEFAULT
                        else -> NoteColor.entries.firstOrNull { it.key == raw }
                    }
                    if (color == null) {
                        ToolExecResult("Unknown colour \"$raw\". Use one of: default, red, orange, yellow, green, teal, blue, purple, pink.", success = false)
                    } else {
                        db.noteDao().update(match.copy(color = color.key))
                        ToolExecResult(
                            if (color == NoteColor.DEFAULT) "Cleared the colour on note \"${match.title}\"."
                            else "Set note \"${match.title}\" to ${color.key}."
                        )
                    }
                }
            }

            "add_note_checklist_item" -> {
                val titleQuery = args.firstString("note_title", "title")
                val item = args.firstString("item", "text")
                val match = matchNote(editableNotes(db), titleQuery)
                when {
                    match == null -> noteNotFound(db, titleQuery)
                    item.isBlank() -> ToolExecResult("No checklist item text was provided.", success = false)
                    else -> {
                        val becameChecklist = !match.isChecklist
                        val updated = match.copy(isChecklist = true, checklist = Checklist.add(match.checklist, item))
                        TaskActions.updateNoteWithHistory(db, match, updated)
                        val extra = if (becameChecklist) " (the note is now a checklist)" else ""
                        ToolExecResult("Added \"$item\" to the checklist on note \"${match.title}\"$extra.")
                    }
                }
            }

            "set_note_checklist_item_done" -> {
                val titleQuery = args.firstString("note_title", "title")
                val itemQuery = args.firstString("item", "text")
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else {
                    val item = Checklist.findByText(match.checklist, itemQuery)
                    if (item == null) {
                        val names = Checklist.parse(match.checklist).joinToString(", ") { it.text }.ifBlank { "none" }
                        ToolExecResult("No checklist item matching \"$itemQuery\" on note \"${match.title}\". It has: $names.", success = false)
                    } else {
                        val done = if (args.has("done")) args.optBoolean("done", true) else !item.done
                        db.noteDao().update(match.copy(checklist = Checklist.setDone(match.checklist, item.id, done)))
                        ToolExecResult("${if (done) "Checked" else "Unchecked"} \"${item.text}\" on note \"${match.title}\".")
                    }
                }
            }

            "edit_note_checklist_item" -> {
                val titleQuery = args.firstString("note_title", "title")
                val itemQuery = args.firstString("item", "text")
                val newText = args.firstString("new_text", "new_item")
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else if (newText.isBlank()) {
                    ToolExecResult("No new text was provided for the item.", success = false)
                } else {
                    val item = Checklist.findByText(match.checklist, itemQuery)
                    if (item == null) {
                        val names = Checklist.parse(match.checklist).joinToString(", ") { it.text }.ifBlank { "none" }
                        ToolExecResult("No checklist item matching \"$itemQuery\" on note \"${match.title}\". It has: $names.", success = false)
                    } else {
                        val updated = match.copy(checklist = Checklist.updateText(match.checklist, item.id, newText))
                        TaskActions.updateNoteWithHistory(db, match, updated)
                        ToolExecResult("Changed \"${item.text}\" to \"$newText\" on note \"${match.title}\".")
                    }
                }
            }

            "remove_note_checklist_item" -> {
                val titleQuery = args.firstString("note_title", "title")
                val itemQuery = args.firstString("item", "text")
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else {
                    val item = Checklist.findByText(match.checklist, itemQuery)
                    if (item == null) {
                        val names = Checklist.parse(match.checklist).joinToString(", ") { it.text }.ifBlank { "none" }
                        ToolExecResult("No checklist item matching \"$itemQuery\" on note \"${match.title}\". It has: $names.", success = false)
                    } else {
                        val updated = match.copy(checklist = Checklist.remove(match.checklist, item.id))
                        TaskActions.updateNoteWithHistory(db, match, updated)
                        ToolExecResult("Removed \"${item.text}\" from the checklist on note \"${match.title}\".")
                    }
                }
            }

            "set_note_checklist_mode" -> {
                val titleQuery = args.firstString("note_title", "title")
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else {
                    val toChecklist = args.optBoolean("checklist", !match.isChecklist)
                    if (toChecklist == match.isChecklist) {
                        ToolExecResult("Note \"${match.title}\" is already in ${if (toChecklist) "checklist" else "plain-text"} mode.")
                    } else {
                        db.noteDao().update(match.copy(isChecklist = toChecklist))
                        ToolExecResult(
                            if (toChecklist) "Note \"${match.title}\" now shows as a checklist. Its body text is kept and comes back if it's switched to plain text again."
                            else "Note \"${match.title}\" now shows its plain-text body. Its checklist items are kept and come back if it's switched to checklist mode again."
                        )
                    }
                }
            }

            "set_note_attachment" -> {
                val titleQuery = args.firstString("note_title", "title")
                val fileName = args.optString("file_name", "note.txt")
                val content = args.optString("content", "")
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else {
                    val att = Attachments.textAttachment(appContext, fileName, content)
                    if (att == null) {
                        ToolExecResult("Couldn't write attachment \"$fileName\" (disk error).", success = false)
                    } else {
                        val list = Attachments.upsert(appContext, Attachments.parse(match.attachments), att)
                        db.noteDao().update(match.copy(attachments = Attachments.serialize(list), updatedAt = System.currentTimeMillis()))
                        ToolExecResult("Saved attachment \"$fileName\" on note \"${match.title}\".")
                    }
                }
            }

            "remove_note_attachment" -> {
                val titleQuery = args.firstString("note_title", "title")
                val fileName = args.optString("file_name", "")
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else {
                    val existing = Attachments.parse(match.attachments)
                    val resolved = resolveAttachmentName(existing, fileName)
                    if (resolved == null) {
                        val names = existing.joinToString(", ") { it.name }.ifBlank { "none" }
                        ToolExecResult("No attachment named \"$fileName\" on note \"${match.title}\". It currently has: $names.", success = false)
                    } else {
                        val list = Attachments.removeByName(appContext, existing, resolved)
                        db.noteDao().update(match.copy(attachments = Attachments.serialize(list), updatedAt = System.currentTimeMillis()))
                        val after = db.noteDao().getByIdOnce(match.id)
                        val stillThere = after != null &&
                            Attachments.parse(after.attachments).any { it.name.equals(resolved, ignoreCase = true) }
                        if (stillThere) {
                            ToolExecResult("Tried to remove \"$resolved\" from note \"${match.title}\", but it's still attached — the deletion did not go through.", success = false)
                        } else {
                            ToolExecResult("Removed attachment \"$resolved\" from note \"${match.title}\".")
                        }
                    }
                }
            }

            "attach_upload_to_note" -> {
                val titleQuery = args.firstString("note_title", "title")
                val requestedName = args.optString("file_name", "")
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else {
                    storeUpload(appContext, db, uploadMime, uploadData, uploadName, requestedName) { att ->
                        val list = Attachments.upsert(appContext, Attachments.parse(match.attachments), att)
                        db.noteDao().update(match.copy(attachments = Attachments.serialize(list), updatedAt = System.currentTimeMillis()))
                        ToolExecResult("Attached \"${att.name}\" to note \"${match.title}\".")
                    }
                }
            }

            "list_note_versions" -> {
                val titleQuery = args.optString("title", "")
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else {
                    val versions = db.noteVersionDao().getForNoteOnce(match.id)
                    if (versions.isEmpty()) {
                        ToolExecResult("Note \"${match.title}\" has no saved history versions yet — a version is captured the first time it's edited.")
                    } else {
                        val sb = StringBuilder("Saved versions of note \"${match.title}\" (1 = most recent):\n")
                        versions.forEachIndexed { i, v ->
                            val preview = (if (v.isChecklist) Checklist.parse(v.checklist).joinToString("; ") { it.text } else v.body)
                                .replace('\n', ' ').trim().take(80)
                            sb.append(i + 1).append(". [").append(DueParsing.format(v.savedAt)).append("] \"").append(v.title).append("\"")
                            if (preview.isNotBlank()) sb.append(" — ").append(preview)
                            sb.append("\n")
                        }
                        ToolExecResult(sb.toString().trim())
                    }
                }
            }

            "restore_note_version" -> {
                val titleQuery = args.optString("title", "")
                val versionIndex = args.optInt("version", 0)
                val match = matchNote(editableNotes(db), titleQuery)
                if (match == null) {
                    noteNotFound(db, titleQuery)
                } else {
                    val versions = db.noteVersionDao().getForNoteOnce(match.id)
                    val version = versions.getOrNull(versionIndex - 1)
                    if (version == null) {
                        ToolExecResult(
                            if (versions.isEmpty()) "Note \"${match.title}\" has no saved history versions to restore."
                            else "Note \"${match.title}\" has ${versions.size} saved version(s); $versionIndex isn't one of them. Call list_note_versions to see the numbers.",
                            success = false
                        )
                    } else {
                        val current = db.noteDao().getByIdOnce(match.id)
                        if (current == null) {
                            ToolExecResult("Note \"${match.title}\" disappeared before it could be restored.", success = false)
                        } else {
                            val restored = NoteHistory.applyTo(current, version)
                            NoteHistory.recordIfChanged(
                                db = db,
                                existing = current,
                                newTitle = restored.title,
                                newBody = restored.body,
                                newTags = restored.tags,
                                newIsChecklist = restored.isChecklist,
                                newChecklist = restored.checklist,
                                savedAt = System.currentTimeMillis()
                            )
                            db.noteDao().update(restored)
                            ToolExecResult("Restored note \"${restored.title}\" to its version from ${DueParsing.format(version.savedAt)}. What it said just before the restore was saved to history too, so this can be undone.")
                        }
                    }
                }
            }


            "create_task" -> {
                val title = args.optString("title", "Untitled task")
                val notes = args.optString("notes", "")
                val priority = TaskPriority.fromKey(args.optString("priority", "")).value
                val due = if (args.has("due")) DueParsing.parse(args.optString("due")) else null
                val repeat = if (due != null && args.has("repeat")) {
                    RepeatRule.fromKey(args.optString("repeat"))
                } else {
                    RepeatRule.NONE
                }
                val reminder = args.optBoolean("reminder", false)
                val subtasks = Checklist.addAll("[]", args.optString("subtasks", ""))

                val toInsert = Task(
                    title = title,
                    notes = notes,
                    priority = priority,
                    dueAt = due,
                    repeatRule = repeat.key,
                    reminderEnabled = reminder,
                    subtasks = subtasks
                )
                val newId = db.taskDao().insert(toInsert)
                ReminderScheduler.sync(appContext, toInsert.copy(id = newId))

                val extras = buildList {
                    TaskPriority.fromValue(priority).takeIf { it != TaskPriority.NONE }?.let {
                        add("${it.label.lowercase()} priority")
                    }
                    due?.let { add("due ${DueParsing.format(it)}") }
                    if (repeat != RepeatRule.NONE) add("repeats ${repeat.key.lowercase()}")
                    if (reminder && due != null) add("reminder on")
                    Checklist.progress(subtasks)?.let { (_, total) -> add("$total checklist item${if (total == 1) "" else "s"}") }
                }
                val suffix = if (extras.isEmpty()) "" else " (${extras.joinToString(", ")})"
                val warning = if (args.has("due") && due == null) {
                    " I couldn't understand the due date you gave, so the task has none — pass an absolute date like 2026-07-15 or 2026-07-15 14:00."
                } else {
                    ""
                }
                ToolExecResult("Created task \"$title\"$suffix.$warning", openTaskId = newId)
            }

            "list_tasks" -> {
                val tasks = activeTasks(db)
                val summary = if (tasks.isEmpty()) "There are no tasks yet."
                else tasks.joinToString("; ") { summarize(it) }
                ToolExecResult(summary)
            }

            "read_task" -> {
                val titleQuery = args.optString("title", "")
                val match = matchTask(editableTasks(db), titleQuery)
                if (match == null) {
                    taskNotFound(db, titleQuery)
                } else {
                    val attachments = Attachments.parse(match.attachments)
                    val subtaskItems = Checklist.parse(match.subtasks)
                    val priority = TaskPriority.fromValue(match.priority)
                    val repeat = RepeatRule.fromKey(match.repeatRule)

                    val sb = StringBuilder()
                    sb.append("Task \"${match.title}\" [${if (match.isDone) "done" else "pending"}].\n")
                    if (match.pinned) sb.append("Pinned: yes\n")
                    if (priority != TaskPriority.NONE) sb.append("Priority: ${priority.label}\n")
                    match.dueAt?.let { sb.append("Due: ${DueParsing.format(it)}\n") }
                    if (repeat != RepeatRule.NONE) sb.append("Repeats: ${repeat.label}\n")
                    if (match.reminderEnabled && match.dueAt != null && !match.isDone) sb.append("Reminder: on\n")
                    if (match.notes.isNotBlank()) sb.append("Notes/description:\n${match.notes}\n")
                    if (subtaskItems.isEmpty()) {
                        sb.append("Checklist: none.\n")
                    } else {
                        sb.append("Checklist (${subtaskItems.size}):\n")
                        subtaskItems.forEach { sb.append(if (it.done) "[x] " else "[ ] ").append(it.text).append("\n") }
                    }
                    if (attachments.isEmpty()) {
                        sb.append("Attachments: none.")
                    } else {
                        sb.append("Attachments (${attachments.size}):\n")
                        attachments.forEach { sb.append(describeAttachment(appContext, it)).append("\n") }
                    }
                    ToolExecResult(sb.toString().trim(), imagesFrom(appContext, attachments))
                }
            }

            "complete_task" -> {
                val titleQuery = args.optString("title", "")
                val match = matchTask(editableTasks(db), titleQuery)
                if (match == null) {
                    taskNotFound(db, titleQuery)
                } else {
                    val next = TaskActions.complete(appContext, db, match)
                    val note = next?.dueAt?.let { " It repeats, so the next occurrence was created, due ${DueParsing.format(it)}." } ?: ""
                    ToolExecResult("Marked \"${match.title}\" as done.$note")
                }
            }

            "reopen_task" -> {
                val titleQuery = args.optString("title", "")
                val match = matchTask(editableTasks(db).filter { it.isDone }, titleQuery)
                if (match == null) {
                    ToolExecResult("No completed task found matching \"$titleQuery\".", success = false)
                } else {
                    TaskActions.restore(appContext, db, match)
                    ToolExecResult("Reopened \"${match.title}\" — it's back on the active list.")
                }
            }

            "update_task" -> {
                val titleQuery = args.optString("title", "")
                val match = matchTask(editableTasks(db), titleQuery)
                if (match == null) {
                    taskNotFound(db, titleQuery)
                } else if (!args.hasAny("new_title", "new_notes", "new_priority", "new_due", "new_repeat", "new_reminder")) {
                    ToolExecResult("No changes were provided.", success = false)
                } else {
                    val newTitle = args.optString("new_title", "")

                    var dueParseFailed = false
                    val newDue: Long? = if (args.has("new_due")) {
                        val raw = args.optString("new_due")
                        when {
                            DueParsing.isClearRequest(raw) -> null
                            else -> DueParsing.parse(raw) ?: match.dueAt.also { dueParseFailed = true }
                        }
                    } else {
                        match.dueAt
                    }

                    val newPriority = if (args.has("new_priority")) {
                        TaskPriority.fromKey(args.optString("new_priority")).value
                    } else {
                        match.priority
                    }
                    val newRepeat = if (args.has("new_repeat")) {
                        RepeatRule.fromKey(args.optString("new_repeat"))
                    } else {
                        RepeatRule.fromKey(match.repeatRule)
                    }
                    val newReminder = if (args.has("new_reminder")) {
                        args.optBoolean("new_reminder")
                    } else {
                        match.reminderEnabled
                    }

                    val updated = match.copy(
                        title = if (newTitle.isNotBlank()) newTitle else match.title,
                        notes = if (args.has("new_notes")) args.optString("new_notes") else match.notes,
                        priority = newPriority,
                        dueAt = newDue,
                        repeatRule = if (newDue == null) RepeatRule.NONE.key else newRepeat.key,
                        reminderEnabled = newReminder
                    )
                    db.taskDao().update(updated)
                    ReminderScheduler.sync(appContext, updated)

                    val warning = if (dueParseFailed) {
                        " (I couldn't understand the date you gave, so the existing due date was left as it was — pass an absolute date like 2026-07-15 14:00.)"
                    } else {
                        ""
                    }
                    ToolExecResult("Updated task \"${updated.title}\".$warning", openTaskId = match.id)
                }
            }

            "delete_task" -> {
                val titleQuery = args.optString("title", "")
                val match = matchTask(editableTasks(db), titleQuery)
                if (match == null) {
                    taskNotFound(db, titleQuery)
                } else {
                    TaskActions.trash(appContext, db, match)
                    ToolExecResult("Moved task \"${match.title}\" to Trash. The user can restore it from there.")
                }
            }

            "pin_task" -> {
                val titleQuery = args.optString("title", "")
                val match = matchTask(editableTasks(db), titleQuery)
                if (match == null) {
                    taskNotFound(db, titleQuery)
                } else {
                    val pinned = args.optBoolean("pinned", true)
                    db.taskDao().update(match.copy(pinned = pinned))
                    ToolExecResult("${if (pinned) "Pinned" else "Unpinned"} task \"${match.title}\".")
                }
            }

            "set_task_priority" -> {
                val titleQuery = args.optString("title", "")
                val match = matchTask(editableTasks(db), titleQuery)
                if (match == null) {
                    taskNotFound(db, titleQuery)
                } else {
                    val priority = TaskPriority.fromKey(args.optString("priority", "none"))
                    db.taskDao().update(match.copy(priority = priority.value))
                    ToolExecResult(
                        if (priority == TaskPriority.NONE) "Cleared the priority on \"${match.title}\"."
                        else "Set \"${match.title}\" to ${priority.label.lowercase()} priority."
                    )
                }
            }

            "set_task_due_date" -> {
                val titleQuery = args.optString("title", "")
                val match = matchTask(editableTasks(db), titleQuery)
                if (match == null) {
                    taskNotFound(db, titleQuery)
                } else {
                    val raw = args.firstString("due_at", "due")
                    if (DueParsing.isClearRequest(raw)) {
                        TaskActions.setSchedule(appContext, db, match, null, RepeatRule.NONE, null)
                        ToolExecResult("Cleared the due date on \"${match.title}\" (and any repeat, since a repeat needs a due date).")
                    } else {
                        val parsed = DueParsing.parse(raw)
                        if (parsed == null) {
                            ToolExecResult(
                                "Couldn't understand the date \"$raw\". Pass an absolute local date such as 2026-07-15 or 2026-07-15 14:00.",
                                success = false
                            )
                        } else {
                            val repeat = if (args.has("repeat")) RepeatRule.fromKey(args.optString("repeat")) else null
                            val reminder = if (args.has("reminder")) args.optBoolean("reminder") else null
                            val updated = TaskActions.setSchedule(appContext, db, match, parsed, repeat, reminder)
                            val bits = buildList {
                                add("due ${DueParsing.format(parsed)}")
                                RepeatRule.fromKey(updated.repeatRule).takeIf { it != RepeatRule.NONE }?.let {
                                    add("repeating ${it.key.lowercase()}")
                                }
                                if (updated.reminderEnabled) add("reminder on")
                            }
                            ToolExecResult("Set \"${match.title}\" to ${bits.joinToString(", ")}.")
                        }
                    }
                }
            }

            "add_subtask" -> {
                val titleQuery = args.firstString("task_title", "title")
                val item = args.firstString("item", "subtask_text", "text")
                val match = matchTask(editableTasks(db), titleQuery)
                when {
                    match == null -> taskNotFound(db, titleQuery)
                    item.isBlank() -> ToolExecResult("No checklist item text was provided.", success = false)
                    else -> {
                        db.taskDao().update(match.copy(subtasks = Checklist.add(match.subtasks, item)))
                        ToolExecResult("Added \"$item\" to the checklist on task \"${match.title}\".")
                    }
                }
            }

            "set_subtask_done" -> {
                val titleQuery = args.firstString("task_title", "title")
                val itemQuery = args.firstString("item", "subtask_text", "text")
                val match = matchTask(editableTasks(db), titleQuery)
                if (match == null) {
                    taskNotFound(db, titleQuery)
                } else {
                    val item = Checklist.findByText(match.subtasks, itemQuery)
                    if (item == null) {
                        val names = Checklist.parse(match.subtasks).joinToString(", ") { it.text }.ifBlank { "none" }
                        ToolExecResult("No checklist item matching \"$itemQuery\" on task \"${match.title}\". It has: $names.", success = false)
                    } else {
                        val done = if (args.has("done")) args.optBoolean("done", true) else !item.done
                        db.taskDao().update(match.copy(subtasks = Checklist.setDone(match.subtasks, item.id, done)))
                        ToolExecResult("${if (done) "Checked" else "Unchecked"} \"${item.text}\" on task \"${match.title}\".")
                    }
                }
            }

            "edit_subtask" -> {
                val titleQuery = args.firstString("task_title", "title")
                val itemQuery = args.firstString("item", "subtask_text", "text")
                val newText = args.firstString("new_text", "new_item")
                val match = matchTask(editableTasks(db), titleQuery)
                if (match == null) {
                    taskNotFound(db, titleQuery)
                } else if (newText.isBlank()) {
                    ToolExecResult("No new text was provided for the item.", success = false)
                } else {
                    val item = Checklist.findByText(match.subtasks, itemQuery)
                    if (item == null) {
                        val names = Checklist.parse(match.subtasks).joinToString(", ") { it.text }.ifBlank { "none" }
                        ToolExecResult("No checklist item matching \"$itemQuery\" on task \"${match.title}\". It has: $names.", success = false)
                    } else {
                        db.taskDao().update(match.copy(subtasks = Checklist.updateText(match.subtasks, item.id, newText)))
                        ToolExecResult("Changed \"${item.text}\" to \"$newText\" on task \"${match.title}\".")
                    }
                }
            }

            "remove_subtask" -> {
                val titleQuery = args.firstString("task_title", "title")
                val itemQuery = args.firstString("item", "subtask_text", "text")
                val match = matchTask(editableTasks(db), titleQuery)
                if (match == null) {
                    taskNotFound(db, titleQuery)
                } else {
                    val item = Checklist.findByText(match.subtasks, itemQuery)
                    if (item == null) {
                        val names = Checklist.parse(match.subtasks).joinToString(", ") { it.text }.ifBlank { "none" }
                        ToolExecResult("No checklist item matching \"$itemQuery\" on task \"${match.title}\". It has: $names.", success = false)
                    } else {
                        db.taskDao().update(match.copy(subtasks = Checklist.remove(match.subtasks, item.id)))
                        ToolExecResult("Removed \"${item.text}\" from the checklist on task \"${match.title}\".")
                    }
                }
            }

            "set_task_attachment" -> {
                val titleQuery = args.firstString("task_title", "title")
                val fileName = args.optString("file_name", "task.txt")
                val content = args.optString("content", "")
                val match = matchTask(editableTasks(db), titleQuery)
                if (match == null) {
                    taskNotFound(db, titleQuery)
                } else {
                    val att = Attachments.textAttachment(appContext, fileName, content)
                    if (att == null) {
                        ToolExecResult("Couldn't write attachment \"$fileName\" (disk error).", success = false)
                    } else {
                        val list = Attachments.upsert(appContext, Attachments.parse(match.attachments), att)
                        db.taskDao().update(match.copy(attachments = Attachments.serialize(list)))
                        ToolExecResult("Saved attachment \"$fileName\" on task \"${match.title}\".")
                    }
                }
            }

            "remove_task_attachment" -> {
                val titleQuery = args.firstString("task_title", "title")
                val fileName = args.optString("file_name", "")
                val match = matchTask(editableTasks(db), titleQuery)
                if (match == null) {
                    taskNotFound(db, titleQuery)
                } else {
                    val existing = Attachments.parse(match.attachments)
                    val resolved = resolveAttachmentName(existing, fileName)
                    if (resolved == null) {
                        val names = existing.joinToString(", ") { it.name }.ifBlank { "none" }
                        ToolExecResult("No attachment named \"$fileName\" on task \"${match.title}\". It currently has: $names.", success = false)
                    } else {
                        val list = Attachments.removeByName(appContext, existing, resolved)
                        db.taskDao().update(match.copy(attachments = Attachments.serialize(list)))
                        val after = db.taskDao().getByIdOnce(match.id)
                        val stillThere = after != null &&
                            Attachments.parse(after.attachments).any { it.name.equals(resolved, ignoreCase = true) }
                        if (stillThere) {
                            ToolExecResult("Tried to remove \"$resolved\" from task \"${match.title}\", but it's still attached — the deletion did not go through.", success = false)
                        } else {
                            ToolExecResult("Removed attachment \"$resolved\" from task \"${match.title}\".")
                        }
                    }
                }
            }

            "attach_upload_to_task" -> {
                val titleQuery = args.firstString("task_title", "title")
                val requestedName = args.optString("file_name", "")
                val match = matchTask(editableTasks(db), titleQuery)
                if (match == null) {
                    taskNotFound(db, titleQuery)
                } else {
                    storeUpload(appContext, db, uploadMime, uploadData, uploadName, requestedName) { att ->
                        val list = Attachments.upsert(appContext, Attachments.parse(match.attachments), att)
                        db.taskDao().update(match.copy(attachments = Attachments.serialize(list)))
                        ToolExecResult("Attached \"${att.name}\" to task \"${match.title}\".")
                    }
                }
            }


            "list_drafts" -> {
                val notes = draftNotes(db)
                val tasks = draftTasks(db)
                if (notes.isEmpty() && tasks.isEmpty()) {
                    ToolExecResult("The draft area is empty.")
                } else {
                    val sb = StringBuilder()
                    if (notes.isNotEmpty()) {
                        sb.append("Draft notes (${notes.size}): ")
                        sb.append(notes.joinToString("; ") { it.title.ifBlank { "Untitled" } + (if (it.isDoodle) " {drawing}" else "") })
                        sb.append(".\n")
                    }
                    if (tasks.isNotEmpty()) {
                        sb.append("Draft tasks (${tasks.size}): ")
                        sb.append(tasks.joinToString("; ") { it.title.ifBlank { "Untitled" } })
                        sb.append(".")
                    }
                    ToolExecResult(sb.toString().trim())
                }
            }

            "delete_draft" -> {
                val kind = args.optString("kind", "").lowercase()
                val titleQuery = args.optString("title", "")
                when (kind) {
                    "note" -> {
                        val candidates = draftNotes(db)
                        val match = matchNote(candidates, titleQuery)
                        if (match == null) noteNotFound(titleQuery, candidates, kind = "draft note") else {
                            db.noteDao().delete(match)
                            ToolExecResult("Deleted draft note \"${match.title.ifBlank { "Untitled" }}\". This was permanent — drafts do not go to the Trash.")
                        }
                    }
                    "task" -> {
                        val candidates = draftTasks(db)
                        val match = matchTask(candidates, titleQuery)
                        if (match == null) taskNotFound(titleQuery, candidates, kind = "draft task") else {
                            db.taskDao().delete(match)
                            ToolExecResult("Deleted draft task \"${match.title.ifBlank { "Untitled" }}\". This was permanent — drafts do not go to the Trash.")
                        }
                    }
                    else -> ToolExecResult("delete_draft needs kind = note or task.", success = false)
                }
            }

            "list_trash" -> {
                val type = args.optString("type", "both").trim().lowercase()
                val wantNotes = type != "tasks"
                val wantTasks = type != "notes"
                val notes = if (wantNotes) trashedNotes(db) else emptyList()
                val tasks = if (wantTasks) trashedTasks(db) else emptyList()
                if (notes.isEmpty() && tasks.isEmpty()) {
                    ToolExecResult("The Trash is empty.")
                } else {
                    val sb = StringBuilder()
                    if (notes.isNotEmpty()) {
                        sb.append("Trashed notes (${notes.size}):\n")
                        notes.forEach { n ->
                            sb.append("- \"").append(n.title.ifBlank { "Untitled" }).append("\"")
                            n.trashedAt?.let { sb.append(" (deleted ").append(DueParsing.format(it)).append(")") }
                            sb.append("\n")
                        }
                    }
                    if (tasks.isNotEmpty()) {
                        if (sb.isNotEmpty()) sb.append("\n")
                        sb.append("Trashed tasks (${tasks.size}):\n")
                        tasks.forEach { t ->
                            sb.append("- \"").append(t.title.ifBlank { "Untitled" }).append("\"")
                            t.trashedAt?.let { sb.append(" (deleted ").append(DueParsing.format(it)).append(")") }
                            sb.append("\n")
                        }
                    }
                    sb.append("\nItems are removed for good after 30 days in the Trash.")
                    ToolExecResult(sb.toString().trim())
                }
            }

            "restore_note_from_trash" -> {
                val titleQuery = args.optString("title", "")
                val candidates = trashedNotes(db)
                val match = matchNote(candidates, titleQuery)
                if (match == null) {
                    noteNotFound(
                        titleQuery,
                        candidates,
                        kind = "trashed note",
                        extra = " Call list_trash to see what is in the Trash."
                    )
                } else {
                    TaskActions.untrashNote(db, match)
                    ToolExecResult(
                        if (match.archived) "Restored note \"${match.title}\" from the Trash — it's back on the Archive screen."
                        else "Restored note \"${match.title}\" from the Trash — it's back on the notes page."
                    )
                }
            }

            "restore_task_from_trash" -> {
                val titleQuery = args.optString("title", "")
                val candidates = trashedTasks(db)
                val match = matchTask(candidates, titleQuery)
                if (match == null) {
                    taskNotFound(
                        titleQuery,
                        candidates,
                        kind = "trashed task",
                        extra = " Call list_trash to see what is in the Trash."
                    )
                } else {
                    TaskActions.untrash(appContext, db, match)
                    ToolExecResult("Restored task \"${match.title}\" from the Trash.")
                }
            }


            "read_attachment" -> {
                val itemType = args.optString("item_type", "").trim().lowercase()
                val titleQuery = args.firstString("title", "note_title", "task_title")
                val fileName = args.firstString("file_name", "name")
                val note = if (itemType != "task") matchNote(editableNotes(db), titleQuery) else null
                val task = if (note == null && itemType != "note") matchTask(editableTasks(db), titleQuery) else null
                if (note == null && task == null) {
                    ToolExecResult("No note or task found matching \"$titleQuery\".", success = false)
                } else {
                    val ownerKind = if (note != null) "note" else "task"
                    val ownerTitle = note?.title ?: task!!.title
                    val attachments = Attachments.parse(note?.attachments ?: task!!.attachments)
                    val resolved = resolveAttachmentName(attachments, fileName)
                    val att = attachments.firstOrNull { it.name.equals(resolved, ignoreCase = true) }
                    if (att == null) {
                        val names = attachments.joinToString(", ") { it.name }.ifBlank { "none" }
                        ToolExecResult("No attachment named \"$fileName\" on $ownerKind \"$ownerTitle\". It currently has: $names.", success = false)
                    } else {
                        ToolExecResult(
                            "Attachment on $ownerKind \"$ownerTitle\":\n" + describeAttachment(appContext, att),
                            imagesFrom(appContext, listOf(att))
                        )
                    }
                }
            }

            "search_items" -> {
                val raw = args.firstString("query", "q", "search")
                val type = args.optString("type", "both").trim().lowercase()
                if (raw.isBlank()) {
                    ToolExecResult("No search query was provided.", success = false)
                } else {
                    val query = SearchQuery.parse(raw)
                    val wantNotes = type != "tasks"
                    val wantTasks = type != "notes"

                    val noteHits = if (wantNotes) {
                        activeNotes(db).filterBySearch(query)
                            .map { it to query.rank(it) }
                            .sortedByDescending { it.second }
                            .take(25).map { it.first }
                    } else {
                        emptyList()
                    }
                    val taskHits = if (wantTasks) {
                        activeTasks(db).filterBySearch(query)
                            .map { it to query.rank(it) }
                            .sortedByDescending { it.second }
                            .take(25).map { it.first }
                    } else {
                        emptyList()
                    }

                    if (noteHits.isEmpty() && taskHits.isEmpty()) {
                        ToolExecResult("Nothing matched \"$raw\".")
                    } else {
                        val sb = StringBuilder()
                        if (noteHits.isNotEmpty()) {
                            sb.append("Notes (${noteHits.size}):\n")
                            noteHits.forEach { sb.append("- ").append(summarize(it)).append("\n") }
                        }
                        if (taskHits.isNotEmpty()) {
                            if (sb.isNotEmpty()) sb.append("\n")
                            sb.append("Tasks (${taskHits.size}):\n")
                            taskHits.forEach { sb.append("- ").append(summarize(it)).append("\n") }
                        }
                        ToolExecResult(sb.toString().trim())
                    }
                }
            }

            "recall_notes" -> {
                val query = args.optString("query", "")
                if (query.isBlank()) {
                    ToolExecResult("No query was provided.", success = false)
                } else {
                    when (val outcome = com.lucent.app.data.EmbeddingProvider.embed(appContext, query)) {
                        is com.lucent.app.data.EmbeddingProvider.Outcome.Unavailable -> ToolExecResult(
                            "${outcome.reason} Try search_items with your best-guess keywords instead.",
                            success = false
                        )
                        is com.lucent.app.data.EmbeddingProvider.Outcome.Failed -> ToolExecResult(
                            "Semantic search failed: ${outcome.error.message ?: outcome.error.toString()}. " +
                                "Try search_items with your best-guess keywords instead.",
                            success = false
                        )
                        is com.lucent.app.data.EmbeddingProvider.Outcome.Success -> {
                            val hits = com.lucent.app.data.EmbeddingStore.search(
                                appContext, outcome.vector, outcome.model, topK = 8
                            )
                            val byId = db.noteDao().getByIds(hits.map { it.noteId })
                                .filter { it.trashedAt == null && !it.hidden && !it.isDraft }
                                .associateBy { it.id }
                            val ranked = hits.mapNotNull { byId[it.noteId] }
                            if (ranked.isEmpty()) {
                                ToolExecResult("Nothing seems related to \"$query\".")
                            } else {
                                val sb = StringBuilder("Notes related to \"$query\" (${ranked.size}):\n")
                                ranked.forEach { sb.append("- ").append(summarize(it)).append("\n") }
                                ToolExecResult(sb.toString().trim())
                            }
                        }
                    }
                }
            }

            "web_search" -> {
                val query = args.firstString("query", "q", "search")
                if (query.isBlank()) {
                    ToolExecResult("No search query was provided.", success = false)
                } else {
                    WebSearchClient.search(query).fold(
                        onSuccess = { ToolExecResult(it) },
                        onFailure = { ToolExecResult("Web search couldn't be completed: ${it.message ?: "network error"}.", success = false) }
                    )
                }
            }

            else -> FormattingTools.execute(db, name, args)
                ?: NotebookTools.execute(db, name, args)
                ?: ToolExecResult("Unknown tool: $name", success = false)
        }
    }
}
