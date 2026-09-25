package com.lucent.app.tools

import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Note
import com.lucent.app.data.Notebook
import com.lucent.app.data.NotebookItem
import com.lucent.app.data.SearchQuery
import com.lucent.app.data.Task
import com.lucent.app.data.filterBySearch
import com.lucent.app.i18n.S
import com.lucent.app.network.ToolDefinition
import com.lucent.app.network.ToolExecResult
import com.lucent.app.network.ToolParam
import org.json.JSONObject

object NotebookTools {

    fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "list_notebooks",
            description = "List the user's NOTEBOOKS, with how many notes and tasks each one groups. A notebook is a folder over items that already exist — it never copies, moves, or deletes them. Call this first so you use a notebook's real name.",
            params = emptyList()
        ),
        ToolDefinition(
            name = "create_notebook",
            description = "Create a new empty NOTEBOOK with the given name, shown on the shelf as a book with a coloured cover. Notebooks group the notes and tasks the user already has, so put items in afterwards with add_to_notebook.",
            params = listOf(
                ToolParam("title", "string", "The name for the new notebook"),
                ToolParam("cover", "string", "Optional cover colour: crimson, ocean, forest, amber, or plum. Defaults to crimson.", required = false)
            )
        ),
        ToolDefinition(
            name = "read_notebook",
            description = "Show one NOTEBOOK in full, matched by its name: its cover colour, whether it is pinned, when it was created and last changed, how many notes and tasks it groups, and every note and task inside it with the same summaries list_notes and list_tasks give. It also says which other notebooks each item also sits in, so you can see the whole structure before you change anything. Read-only.",
            params = listOf(ToolParam("notebook", "string", "The name (or part of it) of the notebook"))
        ),
        ToolDefinition(
            name = "update_notebook",
            description = "Change a NOTEBOOK itself in one go, matched by its current name: give it a new name, a new cover colour, and/or pin or unpin it. The notes and tasks inside are untouched. Use add_to_notebook, remove_from_notebook and move_to_notebook to change what it holds instead.",
            params = listOf(
                ToolParam("notebook", "string", "The current name (or part of it) of the notebook"),
                ToolParam("new_title", "string", "A new name for the notebook. Leave out to keep the current one.", required = false),
                ToolParam("cover", "string", "A new cover colour: crimson, ocean, forest, amber, or plum. Leave out to keep the current one.", required = false),
                ToolParam("pinned", "boolean", "true to pin it to the front of the shelf, false to unpin it. Leave out to leave it as it is.", required = false)
            )
        ),
        ToolDefinition(
            name = "rename_notebook",
            description = "Rename a NOTEBOOK, matched by its current name (a close partial match is accepted). The notes and tasks inside it are untouched.",
            params = listOf(
                ToolParam("notebook", "string", "The current name (or part of it) of the notebook"),
                ToolParam("new_title", "string", "The new name for the notebook")
            )
        ),
        ToolDefinition(
            name = "pin_notebook",
            description = "Pin or unpin a NOTEBOOK, matched by its name. Pinned notebooks stay at the front of the shelf, whichever sort order is chosen.",
            params = listOf(
                ToolParam("notebook", "string", "The name (or part of it) of the notebook"),
                ToolParam("pinned", "boolean", "true to pin it, false to unpin it")
            )
        ),
        ToolDefinition(
            name = "set_notebook_cover",
            description = "Change the colour of a NOTEBOOK's cover, matched by its name. This is only the colour of the book on the shelf: crimson, ocean, forest, amber, or plum.",
            params = listOf(
                ToolParam("notebook", "string", "The name (or part of it) of the notebook"),
                ToolParam("cover", "string", "One of: crimson, ocean, forest, amber, plum")
            )
        ),
        ToolDefinition(
            name = "move_notebook",
            description = "Move a NOTEBOOK to another position on the shelf, matched by its name — the same order the person gets by dragging books around when the sort is set to Custom. Use it when they ask for a notebook to go first, last, or before/after another one.",
            params = listOf(
                ToolParam("notebook", "string", "The name (or part of it) of the notebook to move"),
                ToolParam("position", "string", "One of: first, last, before, after"),
                ToolParam("relative_to", "string", "For before/after: the name (or part of it) of the notebook to sit next to", required = false)
            )
        ),
        ToolDefinition(
            name = "delete_notebook",
            description = "Delete a NOTEBOOK, matched by its name. Only the notebook itself goes to the Trash — the notes and tasks grouped in it are NOT deleted, they stay in the notes and tasks lists, and the person can restore the notebook within 30 days. Call list_notebooks first so you use its real name.",
            params = listOf(ToolParam("notebook", "string", "The name (or part of it) of the notebook to delete"))
        ),
        ToolDefinition(
            name = "list_notebook_trash",
            description = "List the notebooks currently in the Trash, with when each was deleted. Notebooks stay there for 30 days. Use this when the person deleted a notebook and wants it back, then restore it with restore_notebook_from_trash.",
            params = emptyList()
        ),
        ToolDefinition(
            name = "restore_notebook_from_trash",
            description = "Bring a deleted NOTEBOOK back out of the Trash onto the shelf, matched by its name. The notes and tasks it grouped are put back with it. Call list_notebook_trash first to see what is there.",
            params = listOf(ToolParam("notebook", "string", "The name (or part of it) of the trashed notebook to restore"))
        ),
        ToolDefinition(
            name = "list_notebook_items",
            description = "List what one NOTEBOOK holds, matched by its name: the notes and tasks grouped in it, with the same summaries list_notes and list_tasks give. Use it to see a notebook's contents, or to get exact item titles before removing or moving them.",
            params = listOf(
                ToolParam("notebook", "string", "The name (or part of it) of the notebook"),
                ToolParam("item_type", "string", "Optional: note, task, or both. Defaults to both.", required = false)
            )
        ),
        ToolDefinition(
            name = "add_to_notebook",
            description = "Put a note or task that already exists into a NOTEBOOK, matched by the notebook's name and the item's title. This only groups the item — nothing is copied, moved, or changed, and an item can be in several notebooks at once. If it is already there, nothing happens.",
            params = listOf(
                ToolParam("notebook", "string", "The name (or part of it) of the notebook to add it to"),
                ToolParam("title", "string", "The title (or part of it) of the note or task to add"),
                ToolParam("item_type", "string", "Optional: note or task. Leave out to look in both.", required = false)
            )
        ),
        ToolDefinition(
            name = "remove_from_notebook",
            description = "Take a note or task out of a NOTEBOOK, matched by the notebook's name and the item's title. The item itself is not deleted or changed — it only stops being grouped in that notebook. To delete the item instead, use delete_note or delete_task.",
            params = listOf(
                ToolParam("notebook", "string", "The name (or part of it) of the notebook to take it out of"),
                ToolParam("title", "string", "The title (or part of it) of the note or task"),
                ToolParam("item_type", "string", "Optional: note or task. Leave out to look in both.", required = false)
            )
        ),
        ToolDefinition(
            name = "move_to_notebook",
            description = "Move a note or task from one notebook to another, matched by the item's title and the destination notebook's name. The item is not copied and not deleted. Pass from_notebook when you know which notebook it is in; leave it out when the item sits in only one notebook and that one will be used.",
            params = listOf(
                ToolParam("title", "string", "The title (or part of it) of the note or task"),
                ToolParam("to_notebook", "string", "The name (or part of it) of the notebook to move it into"),
                ToolParam("from_notebook", "string", "Optional: the notebook it is currently in", required = false),
                ToolParam("item_type", "string", "Optional: note or task. Leave out to look in both.", required = false)
            )
        ),
        ToolDefinition(
            name = "search_notebook",
            description = "Search INSIDE one notebook only, matched by the notebook's name, and get back just the notes and tasks grouped in it that match. It takes the same words and filters as search_items (tag:work, is:pinned, has:attachment, priority:high, due:today, ...). Use search_items instead to search all notes and tasks.",
            params = listOf(
                ToolParam("notebook", "string", "The name (or part of it) of the notebook"),
                ToolParam("query", "string", "The search query, e.g. \"hotel tag:travel\" or \"budget\""),
                ToolParam("item_type", "string", "Optional: note, task, or both. Defaults to both.", required = false)
            )
        )
    )

    fun describeToolCall(name: String, a: JSONObject): String? {
        fun s(vararg keys: String): String {
            for (k in keys) {
                val v = a.optString(k, "")
                if (v.isNotBlank()) return v
            }
            return ""
        }
        return when (name) {
            "create_notebook" -> S.ccCreateNotebook(s("title", "name"))
            "set_notebook_cover" -> S.ccSetNotebookCover(s("notebook"), s("cover", "colour", "color"))
            "pin_notebook" ->
                if (a.optBoolean("pinned", true)) S.ccPinNotebook(s("notebook", "title", "name"))
                else S.ccUnpinNotebook(s("notebook", "title", "name"))
            "move_notebook" -> S.ccMoveNotebook(s("notebook"), s("position"))
            "read_notebook" -> S.ccReadNotebook(s("notebook", "title", "name"))
            "update_notebook" -> S.ccUpdateNotebook(s("notebook", "title", "name"))
            "restore_notebook_from_trash" -> S.ccRestoreNotebook(s("notebook", "title", "name"))
            "rename_notebook" -> S.ccRenameNotebook(s("notebook"), s("new_title", "new_name"))
            "delete_notebook" -> S.ccTrashNotebook(s("notebook", "title", "name"))
            "add_to_notebook" -> S.ccAddToNotebook(s("title"), s("notebook"))
            "remove_from_notebook" -> S.ccRemoveFromNotebook(s("title"), s("notebook"))
            "move_to_notebook" -> S.ccMoveToNotebook(s("title"), s("to_notebook", "notebook"))
            else -> null
        }
    }

    fun editableArguments(name: String, a: JSONObject): List<AppTools.EditableArgument> = when (name) {
        "create_notebook" -> listOfNotNull(argOf(a, "title", S.confirmEditTitleLabel))
        "set_notebook_cover" -> listOfNotNull(argOf(a, "cover", S.notebookCoverTitle))
        "move_notebook" -> listOfNotNull(
            argOf(a, "position", S.confirmEditItemLabel),
            argOf(a, "relative_to", S.confirmEditTitleLabel)
        )
        "rename_notebook", "update_notebook" -> listOfNotNull(argOf(a, "new_title", S.confirmEditNewTitleLabel))
        else -> emptyList()
    }

    private fun argOf(a: JSONObject, key: String, label: String): AppTools.EditableArgument? =
        a.optString(key, "").takeIf { it.isNotBlank() }?.let { AppTools.EditableArgument(key, label, it) }

    suspend fun execute(db: AppDatabase, name: String, args: JSONObject): ToolExecResult? = when (name) {

        "list_notebooks" -> {
            val all = db.notebookDao().getAllOnce()
            if (all.isEmpty()) {
                ToolExecResult("There are no notebooks yet. Create one with create_notebook.")
            } else {
                val counts = itemCounts(db)
                val sb = StringBuilder("Notebooks (${all.size}):\n")
                all.forEach { notebook ->
                    val (notes, tasks) = counts[notebook.id] ?: (0 to 0)
                    sb.append("- \"").append(displayName(notebook)).append("\" (")
                    sb.append(itemCountLabel(notes, tasks)).append(")\n")
                }
                ToolExecResult(sb.toString().trim())
            }
        }

        "create_notebook" -> {
            val title = args.firstString("title", "name").trim()
            if (title.isBlank()) {
                ToolExecResult("No notebook name was provided.", success = false)
            } else {
                val cover = args.firstString("cover", "colour", "color").trim().lowercase()
                val colorKey = com.lucent.app.ui.NotebookColor.entries
                    .firstOrNull { it.key == cover }?.key
                    .orEmpty()
                db.notebookDao().insert(Notebook(title = title, color = colorKey))
                val coverNote = if (colorKey.isBlank()) "" else " Its cover is $cover."
                ToolExecResult("Created notebook \"$title\".$coverNote Add notes or tasks to it with add_to_notebook.")
            }
        }

        "pin_notebook" -> {
            val all = db.notebookDao().getAllOnce()
            val query = args.firstString("notebook", "title", "name")
            val notebook = matchNotebook(all, query)
            if (notebook == null) {
                notFoundNotebook(query, all)
            } else {
                val pinned = args.optBoolean("pinned", true)
                db.notebookDao().update(
                    notebook.copy(pinned = pinned, updatedAt = System.currentTimeMillis())
                )
                ToolExecResult(
                    if (pinned) "Pinned the notebook \"${displayName(notebook)}\" to the front of the shelf."
                    else "Unpinned the notebook \"${displayName(notebook)}\"."
                )
            }
        }

        "set_notebook_cover" -> {
            val all = db.notebookDao().getAllOnce()
            val query = args.firstString("notebook", "title", "name")
            val notebook = matchNotebook(all, query)
            val raw = args.firstString("cover", "colour", "color").trim().lowercase()
            val color = com.lucent.app.ui.NotebookColor.entries.firstOrNull { it.key == raw }
            if (notebook == null) {
                notFoundNotebook(query, all)
            } else if (color == null) {
                ToolExecResult(
                    "\"$raw\" isn't a cover colour this app has. Use one of: " +
                        com.lucent.app.ui.NotebookColor.entries.joinToString(", ") { it.key } + ".",
                    success = false
                )
            } else {
                db.notebookDao().update(
                    notebook.copy(color = color.key, updatedAt = System.currentTimeMillis())
                )
                ToolExecResult("The notebook \"${displayName(notebook)}\" now has a ${color.key} cover.")
            }
        }

        "move_notebook" -> {
            val all = db.notebookDao().getAllOnce()
            val query = args.firstString("notebook", "title", "name")
            val notebook = matchNotebook(all, query)
            val position = args.firstString("position", "where").trim().lowercase()
            if (notebook == null) {
                notFoundNotebook(query, all)
            } else {
                val ordered = all.sortedBy { it.manualOrder }.map { it.id }
                val without = ordered.filter { it != notebook.id }
                val relative = if (position == "before" || position == "after") {
                    val relQuery = args.firstString("relative_to", "relative")
                    if (relQuery.isBlank()) {
                        ToolExecResult(
                            "\"$position\" needs relative_to — the name of the notebook it should sit next to.",
                            success = false
                        )
                    } else {
                        val target = matchNotebook(all.filter { it.id != notebook.id }, relQuery)
                        if (target == null) {
                            notFoundNotebook(relQuery, all.filter { it.id != notebook.id })
                        } else {
                            target.id
                        }
                    }
                } else null
                if (relative is ToolExecResult) {
                    relative
                } else {
                    val index = when (position) {
                        "first", "top" -> 0
                        "last", "bottom" -> without.size
                        "before" -> without.indexOf(relative as Long?).takeIf { it >= 0 }
                        "after" -> without.indexOf(relative as Long?).takeIf { it >= 0 }?.plus(1)
                        else -> null
                    }
                    if (index == null) {
                        ToolExecResult(
                            "\"$position\" isn't a position this app has. Use one of: first, last, before, after.",
                            success = false
                        )
                    } else {
                        val finalOrder = without.toMutableList().also { it.add(index, notebook.id) }
                        val byId = all.associateBy { it.id }
                        finalOrder.forEachIndexed { i, id ->
                            byId[id]?.let { db.notebookDao().update(it.copy(manualOrder = i * 1000)) }
                        }
                        val where = when (position) {
                            "first", "top" -> "to the front of the shelf"
                            "last", "bottom" -> "to the end of the shelf"
                            "before" -> "just before \"${args.firstString("relative_to")}\""
                            else -> "just after \"${args.firstString("relative_to")}\""
                        }
                        ToolExecResult(
                            "Moved the notebook \"${displayName(notebook)}\" $where. The order is used " +
                                "when the person sorts their notebooks by Custom.",
                        )
                    }
                }
            }
        }

        "rename_notebook" -> {
            val all = db.notebookDao().getAllOnce()
            val query = args.firstString("notebook", "title", "name")
            val notebook = matchNotebook(all, query)
            val newTitle = args.firstString("new_title", "new_name").trim()
            if (notebook == null) {
                notFoundNotebook(query, all)
            } else if (newTitle.isBlank()) {
                ToolExecResult("No new notebook name was provided.", success = false)
            } else if (notebook.title == newTitle) {
                ToolExecResult("Notebook \"${displayName(notebook)}\" is already called \"$newTitle\".")
            } else {
                db.notebookDao().update(notebook.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
                val clash = all.any { it.id != notebook.id && it.title.equals(newTitle, ignoreCase = true) }
                val suffix = if (clash) " Another notebook already uses that name, so tell them apart by what is inside." else ""
                ToolExecResult("Renamed notebook \"${displayName(notebook)}\" to \"$newTitle\".$suffix")
            }
        }

        "delete_notebook" -> {
            val all = db.notebookDao().getAllOnce()
            val query = args.firstString("notebook", "title", "name")
            val notebook = matchNotebook(all, query)
            if (notebook == null) {
                notFoundNotebook(query, all)
            } else {
                val held = db.notebookDao().getItemsOnce(notebook.id).size
                db.notebookDao().update(
                    notebook.copy(trashedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
                )
                val kept = if (held == 0) {
                    "It was empty."
                } else {
                    "Its $held item${if (held == 1) "" else "s"} ${if (held == 1) "was" else "were"} not deleted — " +
                        "${if (held == 1) "it stays" else "they stay"} in the notes and tasks lists."
                }
                ToolExecResult(
                    "Moved the notebook \"${displayName(notebook)}\" to the Trash; it can be restored for 30 " +
                        "days. $kept"
                )
            }
        }

        "list_notebook_trash" -> {
            val trashed = db.notebookDao().getTrashedOnce()
            if (trashed.isEmpty()) {
                ToolExecResult("The notebook Trash is empty.")
            } else {
                val sb = StringBuilder("Notebooks in the Trash (${trashed.size}):\n")
                trashed.forEach { notebook ->
                    val held = db.notebookDao().getItemsOnce(notebook.id).size
                    sb.append("- \"").append(displayName(notebook)).append("\" (")
                    sb.append(held).append(if (held == 1) " item)\n" else " items)\n")
                }
                ToolExecResult(sb.toString().trim())
            }
        }

        "restore_notebook_from_trash" -> {
            val trashed = db.notebookDao().getTrashedOnce()
            val query = args.firstString("notebook", "title", "name")
            val notebook = matchNotebook(trashed, query)
            if (notebook == null) {
                val names = trashed.joinToString(", ") { "\"" + displayName(it) + "\"" }
                if (trashed.isEmpty()) {
                    ToolExecResult("The notebook Trash is empty, so there is nothing to restore.", success = false)
                } else {
                    ToolExecResult(
                        "No trashed notebook matches \"$query\". In the Trash: $names.",
                        success = false
                    )
                }
            } else {
                val held = db.notebookDao().getItemsOnce(notebook.id).size
                db.notebookDao().update(
                    notebook.copy(trashedAt = null, updatedAt = System.currentTimeMillis())
                )
                ToolExecResult(
                    "Restored the notebook \"${displayName(notebook)}\" to the shelf with its $held " +
                        "grouped item${if (held == 1) "" else "s"}."
                )
            }
        }

        "read_notebook" -> {
            val all = db.notebookDao().getAllOnce()
            val query = args.firstString("notebook", "title", "name")
            val notebook = matchNotebook(all, query)
            if (notebook == null) {
                notFoundNotebook(query, all)
            } else {
                val members = db.notebookDao().getItemsOnce(notebook.id)
                val notes = notesIn(db, members)
                val tasks = tasksIn(db, members)
                val shown = notes.size + tasks.size
                val elsewhere = membershipLabels(db, all, notebook.id)
                val sb = StringBuilder()
                sb.append("Notebook \"").append(displayName(notebook)).append("\"\n")
                sb.append("- cover: ").append(notebook.color.ifBlank { "crimson" })
                sb.append(", ").append(if (notebook.pinned) "pinned to the front" else "not pinned").append("\n")
                sb.append("- created ").append(stamp(notebook.createdAt))
                sb.append(", last changed ").append(stamp(notebook.updatedAt)).append("\n")
                sb.append("- holds ").append(itemCountLabel(notes.size, tasks.size)).append("\n")
                if (members.size > shown) {
                    sb.append("- ").append(members.size - shown)
                    sb.append(" grouped item(s) are in the Trash or hidden, so they are not listed here\n")
                }
                if (notes.isNotEmpty()) {
                    sb.append("Notes:\n")
                    notes.forEach { entry ->
                        sb.append("- ").append(AppTools.summarize(entry.second))
                        elsewhere[NotebookItem.KIND_NOTE to entry.second.id]?.let { sb.append(" ").append(it) }
                        sb.append("\n")
                    }
                }
                if (tasks.isNotEmpty()) {
                    sb.append("Tasks:\n")
                    tasks.forEach { entry ->
                        sb.append("- ").append(AppTools.summarize(entry.second))
                        elsewhere[NotebookItem.KIND_TASK to entry.second.id]?.let { sb.append(" ").append(it) }
                        sb.append("\n")
                    }
                }
                if (shown == 0) {
                    sb.append("Nothing is grouped in this notebook yet. Use add_to_notebook to put a note ")
                    sb.append("or task in it.")
                }
                ToolExecResult(sb.toString().trim())
            }
        }

        "update_notebook" -> {
            val all = db.notebookDao().getAllOnce()
            val query = args.firstString("notebook", "title", "name")
            val notebook = matchNotebook(all, query)
            val newTitle = args.firstString("new_title", "new_name").trim()
            val coverRaw = args.firstString("cover", "colour", "color").trim().lowercase()
            val pinned = if (args.has("pinned")) args.optBoolean("pinned") else null
            val cover = if (coverRaw.isBlank()) {
                null
            } else {
                com.lucent.app.ui.NotebookColor.entries.firstOrNull { it.key == coverRaw }
            }
            if (notebook == null) {
                notFoundNotebook(query, all)
            } else if (newTitle.isBlank() && coverRaw.isBlank() && pinned == null) {
                ToolExecResult(
                    "Nothing was given to change. Pass new_title, cover, or pinned.",
                    success = false
                )
            } else if (coverRaw.isNotBlank() && cover == null) {
                ToolExecResult(
                    "\"$coverRaw\" isn't a cover colour this app has. Use one of: " +
                        com.lucent.app.ui.NotebookColor.entries.joinToString(", ") { it.key } + ".",
                    success = false
                )
            } else {
                val updated = notebook.copy(
                    title = newTitle.ifBlank { notebook.title },
                    color = cover?.key ?: notebook.color,
                    pinned = pinned ?: notebook.pinned,
                    updatedAt = System.currentTimeMillis()
                )
                db.notebookDao().update(updated)
                val changes = ArrayList<String>()
                if (newTitle.isNotBlank() && newTitle != notebook.title) {
                    changes.add("renamed to \"$newTitle\"")
                }
                if (cover != null && cover.key != notebook.color) {
                    changes.add("given a ${cover.key} cover")
                }
                if (pinned != null && pinned != notebook.pinned) {
                    changes.add(if (pinned) "pinned to the front of the shelf" else "unpinned")
                }
                val kept = db.notebookDao().getItemsOnce(notebook.id).size
                val holding = if (kept == 0) {
                    "It is empty."
                } else {
                    "Its $kept grouped item${if (kept == 1) "" else "s"} ${if (kept == 1) "is" else "are"} unchanged."
                }
                val what = if (changes.isEmpty()) {
                    "\"${displayName(notebook)}\" was already like that"
                } else {
                    "Updated the notebook \"${displayName(notebook)}\": " + changes.joinToString(", ")
                }
                ToolExecResult("$what. $holding")
            }
        }

        "list_notebook_items" -> {
            val all = db.notebookDao().getAllOnce()
            val query = args.firstString("notebook", "title", "name")
            val notebook = matchNotebook(all, query)
            if (notebook == null) {
                notFoundNotebook(query, all)
            } else {
                listItems(db, notebook, itemFilter(args.firstString("item_type", "kind", "type")))
            }
        }

        "add_to_notebook" -> {
            val all = db.notebookDao().getAllOnce()
            val query = args.firstString("notebook", "name")
            val notebook = matchNotebook(all, query)
            if (notebook == null) {
                notFoundNotebook(query, all)
            } else {
                when (val found = lookupTarget(db, args.firstString("item_type", "kind", "type"), itemQuery(args))) {
                    is TargetLookup.Failed -> found.result
                    is TargetLookup.Found -> addTarget(db, notebook, found.target)
                }
            }
        }

        "remove_from_notebook" -> {
            val all = db.notebookDao().getAllOnce()
            val query = args.firstString("notebook", "name")
            val notebook = matchNotebook(all, query)
            if (notebook == null) {
                notFoundNotebook(query, all)
            } else {
                when (val found = lookupTarget(db, args.firstString("item_type", "kind", "type"), itemQuery(args))) {
                    is TargetLookup.Failed -> found.result
                    is TargetLookup.Found -> removeTarget(db, notebook, found.target)
                }
            }
        }

        "move_to_notebook" -> {
            val all = db.notebookDao().getAllOnce()
            val toQuery = args.firstString("to_notebook", "notebook", "to")
            val to = matchNotebook(all, toQuery)
            if (to == null) {
                notFoundNotebook(toQuery, all)
            } else {
                when (val found = lookupTarget(db, args.firstString("item_type", "kind", "type"), itemQuery(args))) {
                    is TargetLookup.Failed -> found.result
                    is TargetLookup.Found -> moveTarget(db, all, to, found.target, args.firstString("from_notebook", "from"))
                }
            }
        }

        "search_notebook" -> {
            val all = db.notebookDao().getAllOnce()
            val query = args.firstString("notebook", "name")
            val raw = args.firstString("query", "q", "search")
            val notebook = matchNotebook(all, query)
            if (notebook == null) {
                notFoundNotebook(query, all)
            } else if (raw.isBlank()) {
                ToolExecResult("No search query was provided.", success = false)
            } else {
                searchNotebook(db, notebook, raw, itemFilter(args.firstString("item_type", "kind", "type")))
            }
        }

        else -> null
    }

    private data class Target(val kind: String, val itemId: Long, val title: String) {
        val label: String get() = if (kind == NotebookItem.KIND_NOTE) "note" else "task"
        val labelTitle: String get() = if (kind == NotebookItem.KIND_NOTE) "Note" else "Task"
    }

    private sealed class TargetLookup {
        data class Found(val target: Target) : TargetLookup()
        data class Failed(val result: ToolExecResult) : TargetLookup()
    }

    private enum class ItemFilter { NOTES, TASKS, BOTH }

    private fun itemFilter(raw: String): ItemFilter = when (raw.trim().lowercase()) {
        "note", "notes" -> ItemFilter.NOTES
        "task", "tasks" -> ItemFilter.TASKS
        else -> ItemFilter.BOTH
    }

    private fun itemQuery(args: JSONObject): String =
        args.firstString("title", "item", "note_title", "task_title")

    private fun JSONObject.firstString(vararg keys: String): String {
        for (key in keys) {
            val value = optString(key, "")
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun displayName(notebook: Notebook): String = notebook.title.ifBlank { "Untitled" }

    private fun matchNotebook(all: List<Notebook>, query: String): Notebook? {
        if (query.isBlank()) return null
        return all.firstOrNull { it.title.contains(query, ignoreCase = true) }
    }

    private fun notFoundNotebook(query: String, all: List<Notebook>): ToolExecResult {
        if (all.isEmpty()) {
            return ToolExecResult("There are no notebooks yet. Create one with create_notebook.", success = false)
        }
        val names = all.joinToString(", ") { "\"" + displayName(it) + "\"" }
        val asked = if (query.isBlank()) "No notebook name was given." else "No notebook found matching \"$query\"."
        return ToolExecResult("$asked The notebooks are: $names.", success = false)
    }

    private fun stamp(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    private suspend fun membershipLabels(
        db: AppDatabase,
        all: List<Notebook>,
        current: Long
    ): Map<Pair<String, Long>, String> {
        val names = all.associate { it.id to displayName(it) }
        val out = HashMap<Pair<String, Long>, String>()
        val kinds = listOf(NotebookItem.KIND_NOTE, NotebookItem.KIND_TASK)
        kinds.forEach { kind ->
            val grouped = db.notebookDao().getItemsByKindOnce(kind)
                .filter { it.notebookId != current }
                .groupBy { it.itemId }
            grouped.forEach { (itemId, members) ->
                val labels = members.mapNotNull { names[it.notebookId] }.distinct()
                if (labels.isNotEmpty()) {
                    out[kind to itemId] = "(also in " + labels.joinToString(", ") { "\"$it\"" } + ")"
                }
            }
        }
        return out
    }

    private suspend fun itemCounts(db: AppDatabase): Map<Long, Pair<Int, Int>> {
        val notes = db.notebookDao().getItemsByKindOnce(NotebookItem.KIND_NOTE)
            .groupingBy { it.notebookId }.eachCount()
        val tasks = db.notebookDao().getItemsByKindOnce(NotebookItem.KIND_TASK)
            .groupingBy { it.notebookId }.eachCount()
        return (notes.keys + tasks.keys).associateWith { (notes[it] ?: 0) to (tasks[it] ?: 0) }
    }

    private fun itemCountLabel(notes: Int, tasks: Int): String {
        val total = notes + tasks
        if (total == 0) return "empty"
        val parts = buildList {
            if (notes > 0) add("$notes note${if (notes == 1) "" else "s"}")
            if (tasks > 0) add("$tasks task${if (tasks == 1) "" else "s"}")
        }
        return "$total item${if (total == 1) "" else "s"}: ${parts.joinToString(", ")}"
    }

    private suspend fun notesIn(db: AppDatabase, members: List<NotebookItem>): List<Pair<NotebookItem, Note>> {
        val mine = members.filter { it.itemKind == NotebookItem.KIND_NOTE }
        if (mine.isEmpty()) return emptyList()
        val byId = db.noteDao().getByIds(mine.map { it.itemId }).associateBy { it.id }
        return mine.mapNotNull { member ->
            byId[member.itemId]?.takeIf { it.trashedAt == null && !it.hidden && !it.isDraft }?.let { member to it }
        }
    }

    private suspend fun tasksIn(db: AppDatabase, members: List<NotebookItem>): List<Pair<NotebookItem, Task>> {
        val mine = members.filter { it.itemKind == NotebookItem.KIND_TASK }
        if (mine.isEmpty()) return emptyList()
        val byId = db.taskDao().getByIds(mine.map { it.itemId }).associateBy { it.id }
        return mine.mapNotNull { member ->
            byId[member.itemId]?.takeIf { it.trashedAt == null && !it.hidden && !it.isDraft }?.let { member to it }
        }
    }

    private suspend fun lookupTarget(db: AppDatabase, itemType: String, titleQuery: String): TargetLookup {
        if (titleQuery.isBlank()) {
            return TargetLookup.Failed(ToolExecResult("No note or task title was provided.", success = false))
        }
        val type = itemType.trim().lowercase()
        val note = if (type != "task" && type != "tasks") {
            AppTools.activeNotes(db).firstOrNull { it.title.contains(titleQuery, ignoreCase = true) }
        } else {
            null
        }
        val task = if (type != "note" && type != "notes") {
            AppTools.activeTasks(db).firstOrNull { it.title.contains(titleQuery, ignoreCase = true) }
        } else {
            null
        }
        return when {
            note != null && task != null -> TargetLookup.Failed(
                ToolExecResult(
                    "Both a note (\"${note.title}\") and a task (\"${task.title}\") match \"$titleQuery\". " +
                        "Pass item_type = note or item_type = task to say which one.",
                    success = false
                )
            )
            note != null -> TargetLookup.Found(Target(NotebookItem.KIND_NOTE, note.id, note.title))
            task != null -> TargetLookup.Found(Target(NotebookItem.KIND_TASK, task.id, task.title))
            else -> TargetLookup.Failed(ToolExecResult("No note or task found matching \"$titleQuery\".", success = false))
        }
    }

    private suspend fun listItems(db: AppDatabase, notebook: Notebook, filter: ItemFilter): ToolExecResult {
        val members = db.notebookDao().getItemsOnce(notebook.id)
        val notes = if (filter != ItemFilter.TASKS) notesIn(db, members) else emptyList()
        val tasks = if (filter != ItemFilter.NOTES) tasksIn(db, members) else emptyList()
        val total = notes.size + tasks.size
        if (total == 0) {
            val what = when (filter) {
                ItemFilter.NOTES -> "notes"
                ItemFilter.TASKS -> "tasks"
                ItemFilter.BOTH -> "items"
            }
            return ToolExecResult("Notebook \"${displayName(notebook)}\" has no $what.")
        }
        val sb = StringBuilder("Notebook \"${displayName(notebook)}\" ($total item${if (total == 1) "" else "s"}):\n")
        if (notes.isNotEmpty()) {
            sb.append("Notes (${notes.size}):\n")
            notes.forEach { sb.append("- ").append(AppTools.summarize(it.second)).append("\n") }
        }
        if (tasks.isNotEmpty()) {
            if (notes.isNotEmpty()) sb.append("\n")
            sb.append("Tasks (${tasks.size}):\n")
            tasks.forEach { sb.append("- ").append(AppTools.summarize(it.second)).append("\n") }
        }
        return ToolExecResult(sb.toString().trim())
    }

    private suspend fun searchNotebook(
        db: AppDatabase,
        notebook: Notebook,
        raw: String,
        filter: ItemFilter
    ): ToolExecResult {
        val members = db.notebookDao().getItemsOnce(notebook.id)
        val query = SearchQuery.parse(raw)
        val noteHits = if (filter != ItemFilter.TASKS) {
            notesIn(db, members).map { it.second }.filterBySearch(query)
                .map { it to query.rank(it) }.sortedByDescending { it.second }.take(25).map { it.first }
        } else {
            emptyList()
        }
        val taskHits = if (filter != ItemFilter.NOTES) {
            tasksIn(db, members).map { it.second }.filterBySearch(query)
                .map { it to query.rank(it) }.sortedByDescending { it.second }.take(25).map { it.first }
        } else {
            emptyList()
        }
        val total = noteHits.size + taskHits.size
        if (total == 0) {
            return ToolExecResult("Nothing in notebook \"${displayName(notebook)}\" matched \"$raw\".")
        }
        val sb = StringBuilder(
            "In notebook \"${displayName(notebook)}\", $total match${if (total == 1) "" else "es"} for \"$raw\":\n"
        )
        if (noteHits.isNotEmpty()) {
            sb.append("Notes (${noteHits.size}):\n")
            noteHits.forEach { sb.append("- ").append(AppTools.summarize(it)).append("\n") }
        }
        if (taskHits.isNotEmpty()) {
            if (noteHits.isNotEmpty()) sb.append("\n")
            sb.append("Tasks (${taskHits.size}):\n")
            taskHits.forEach { sb.append("- ").append(AppTools.summarize(it)).append("\n") }
        }
        return ToolExecResult(sb.toString().trim())
    }

    private suspend fun addTarget(db: AppDatabase, notebook: Notebook, target: Target): ToolExecResult {
        if (db.notebookDao().membershipExistsOnce(notebook.id, target.kind, target.itemId) > 0) {
            return ToolExecResult(
                "${target.labelTitle} \"${target.title}\" is already in notebook \"${displayName(notebook)}\" — nothing to do."
            )
        }
        db.notebookDao().insertItem(
            NotebookItem(notebookId = notebook.id, itemKind = target.kind, itemId = target.itemId)
        )
        db.notebookDao().update(notebook.copy(updatedAt = System.currentTimeMillis()))
        return ToolExecResult("Added ${target.label} \"${target.title}\" to notebook \"${displayName(notebook)}\".")
    }

    private suspend fun removeTarget(db: AppDatabase, notebook: Notebook, target: Target): ToolExecResult {
        val member = db.notebookDao().getItemsOnce(notebook.id)
            .firstOrNull { it.itemKind == target.kind && it.itemId == target.itemId }
        if (member == null) {
            return ToolExecResult(
                "${target.labelTitle} \"${target.title}\" isn't in notebook \"${displayName(notebook)}\" — nothing was removed."
            )
        }
        db.notebookDao().deleteItemById(member.id)
        return ToolExecResult(
            "Took ${target.label} \"${target.title}\" out of notebook \"${displayName(notebook)}\". " +
                "The ${target.label} itself was not deleted."
        )
    }

    private suspend fun moveTarget(
        db: AppDatabase,
        all: List<Notebook>,
        to: Notebook,
        target: Target,
        fromQuery: String
    ): ToolExecResult {
        val memberships = db.notebookDao().getItemsByKindOnce(target.kind).filter { it.itemId == target.itemId }
        val explicitFrom = if (fromQuery.isNotBlank()) matchNotebook(all, fromQuery) else null
        if (fromQuery.isNotBlank() && explicitFrom == null) {
            return notFoundNotebook(fromQuery, all)
        }
        if (explicitFrom == null && memberships.size > 1) {
            val names = memberships.mapNotNull { member ->
                all.firstOrNull { it.id == member.notebookId }?.let { displayName(it) }
            }
            return ToolExecResult(
                "${target.labelTitle} \"${target.title}\" is in several notebooks (${names.joinToString(", ")}). " +
                    "Say which one to move it from.",
                success = false
            )
        }
        val sourceMember = if (explicitFrom != null) {
            memberships.firstOrNull { it.notebookId == explicitFrom.id }
        } else {
            memberships.firstOrNull()
        }
        if (sourceMember == null) {
            return if (explicitFrom != null) {
                ToolExecResult(
                    "${target.labelTitle} \"${target.title}\" isn't in notebook \"${displayName(explicitFrom)}\" — nothing was moved.",
                    success = false
                )
            } else {
                ToolExecResult(
                    "${target.labelTitle} \"${target.title}\" isn't in any notebook yet. Put it in one with add_to_notebook.",
                    success = false
                )
            }
        }
        val source = all.firstOrNull { it.id == sourceMember.notebookId }
        if (source?.id == to.id) {
            return ToolExecResult(
                "${target.labelTitle} \"${target.title}\" is already in notebook \"${displayName(to)}\" — nothing to move."
            )
        }
        db.notebookDao().deleteItemById(sourceMember.id)
        if (db.notebookDao().membershipExistsOnce(to.id, target.kind, target.itemId) == 0) {
            db.notebookDao().insertItem(
                NotebookItem(notebookId = to.id, itemKind = target.kind, itemId = target.itemId)
            )
        }
        db.notebookDao().update(to.copy(updatedAt = System.currentTimeMillis()))
        val sourceName = source?.let { displayName(it) } ?: "its notebook"
        return ToolExecResult(
            "Moved ${target.label} \"${target.title}\" from notebook \"$sourceName\" to notebook \"${displayName(to)}\"."
        )
    }
}
