package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import com.lucent.app.network.ToolImage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileTools : HarnessGroupTools {

    override val group = HarnessGroup.FILES

    private const val MAX_LIST = 500
    private const val MAX_SEARCH = 120

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "workspace_info",
            group = group,
            permission = HarnessPermission.READ,
            description = "Show the agent workspace: its absolute path, how much room is left, how many files it holds, " +
                "the permission policy in force and which capability groups are switched on. Call this first when you " +
                "are unsure where you are allowed to work."
        ),
        HarnessTool(
            name = "list_directory",
            group = group,
            permission = HarnessPermission.READ,
            description = "List a directory inside the workspace. Returns names, sizes, modification times and a marker " +
                "for directories. Arguments: path (default the workspace root), depth (1-3, default 1), max_entries.",
            params = listOf(
                HarnessSchema.text("path", "Directory to list, relative to the workspace or absolute", false),
                HarnessSchema.number("depth", "How many levels to walk, 1 to 3", false),
                HarnessSchema.number("max_entries", "Stop after this many entries", false)
            )
        ),
        HarnessTool(
            name = "search_files",
            group = group,
            permission = HarnessPermission.READ,
            description = "Search file names and file contents under a directory. Set regex true to treat query as a " +
                "regular expression, or glob to filter names (for example **/*.kt). Returns path, line number and the " +
                "matching line.",
            params = listOf(
                HarnessSchema.text("query", "Text or pattern to look for"),
                HarnessSchema.text("path", "Directory to search, default the workspace root", false),
                HarnessSchema.flag("regex", "Treat query as a regular expression", false),
                HarnessSchema.text("glob", "Only consider names matching this glob", false),
                HarnessSchema.number("max_results", "Stop after this many matches", false)
            )
        ),
        HarnessTool(
            name = "read_file",
            group = group,
            permission = HarnessPermission.READ,
            description = "Read a text file. Arguments: path, start_line (1-based, optional), max_lines (optional, " +
                "default 400). Binary files are refused; use file_info first if you are unsure.",
            params = listOf(
                HarnessSchema.text("path", "File to read"),
                HarnessSchema.number("start_line", "First line to return", false),
                HarnessSchema.number("max_lines", "How many lines to return", false)
            )
        ),
        HarnessTool(
            name = "write_file",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Create or replace a text file inside the workspace. The previous contents are snapshotted so " +
                "they can be restored. Set append true to add to the end instead of replacing.",
            params = listOf(
                HarnessSchema.text("path", "File to write"),
                HarnessSchema.text("content", "Full text to write"),
                HarnessSchema.flag("append", "Append instead of replacing", false)
            )
        ),
        HarnessTool(
            name = "edit_file",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Replace exact text inside a file, the precise way to change a few lines of a large file. " +
                "Returns a unified diff of what changed and fails when the search text is missing or ambiguous.",
            params = listOf(
                HarnessSchema.text("path", "File to edit"),
                HarnessSchema.text("find", "Exact text to find"),
                HarnessSchema.text("replace", "Replacement text"),
                HarnessSchema.flag("all", "Replace every occurrence", false)
            )
        ),
        HarnessTool(
            name = "create_directory",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Create a directory, including any missing parents. Arguments: path.",
            params = listOf(HarnessSchema.text("path", "Directory to create"))
        ),
        HarnessTool(
            name = "move_path",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Move or rename a file or directory inside the workspace. Arguments: from, to. Refuses to " +
                "overwrite an existing destination unless overwrite is true.",
            params = listOf(
                HarnessSchema.text("from", "Existing path"),
                HarnessSchema.text("to", "New path"),
                HarnessSchema.flag("overwrite", "Replace the destination when it exists", false)
            )
        ),
        HarnessTool(
            name = "copy_path",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Copy a file or directory inside the workspace. Arguments: from, to, overwrite.",
            params = listOf(
                HarnessSchema.text("from", "Source path"),
                HarnessSchema.text("to", "Destination path"),
                HarnessSchema.flag("overwrite", "Replace the destination when it exists", false)
            )
        ),
        HarnessTool(
            name = "delete_path",
            group = group,
            permission = HarnessPermission.DELETE,
            description = "Delete a file or directory. Directories need recursive true. The contents are snapshotted " +
                "first when the file fits the snapshot policy, so a mistake can be undone with restore_file.",
            params = listOf(
                HarnessSchema.text("path", "Path to delete"),
                HarnessSchema.flag("recursive", "Delete a directory and everything inside it", false)
            )
        ),
        HarnessTool(
            name = "file_info",
            group = group,
            permission = HarnessPermission.READ,
            description = "Report one path in detail: kind, byte size, line count for text, modification time, whether " +
                "it is inside the workspace, and how many snapshots exist.",
            params = listOf(HarnessSchema.text("path", "Path to inspect"))
        ),
        HarnessTool(
            name = "batch_files",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Work on many files at once. action is rename, copy, move or delete; paths is the list to act " +
                "on; for rename give find and replace (plain text or a regular expression when regex is true); for copy " +
                "and move give target_dir.",
            params = listOf(
                HarnessSchema.text("action", "rename, copy, move or delete"),
                HarnessSchema.list("paths", "Paths to act on"),
                HarnessSchema.text("find", "Text to find when renaming", false),
                HarnessSchema.text("replace", "Replacement text when renaming", false),
                HarnessSchema.flag("regex", "Treat find as a regular expression", false),
                HarnessSchema.text("target_dir", "Destination directory for copy and move", false)
            )
        ),
        HarnessTool(
            name = "diff_files",
            group = group,
            permission = HarnessPermission.READ,
            description = "Show a unified diff between two files, or between a file and the text you pass in content. " +
                "Use it after editing to confirm exactly what changed.",
            params = listOf(
                HarnessSchema.text("path", "The file to compare"),
                HarnessSchema.text("other", "The other file", false),
                HarnessSchema.text("content", "Text to compare against instead of a second file", false)
            )
        ),
        HarnessTool(
            name = "file_history",
            group = group,
            permission = HarnessPermission.READ,
            description = "List the snapshots kept for a path, newest first, with their ids and times. Pass no path to " +
                "list the most recent snapshots overall.",
            params = listOf(
                HarnessSchema.text("path", "Path to look up", false),
                HarnessSchema.number("limit", "How many entries", false)
            )
        ),
        HarnessTool(
            name = "restore_file",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Roll a file back to an earlier snapshot. Arguments: path, snapshot_id (optional; the newest " +
                "snapshot for that path is used when it is omitted).",
            params = listOf(
                HarnessSchema.text("path", "Path to restore"),
                HarnessSchema.text("snapshot_id", "Snapshot to restore", false)
            )
        ),
        HarnessTool(
            name = "zip_paths",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Pack files and directories into a .zip inside the workspace, or unpack one when action is " +
                "unzip. Arguments: action (zip or unzip), path (the archive), paths (what to pack when zipping).",
            params = listOf(
                HarnessSchema.text("action", "zip or unzip"),
                HarnessSchema.text("path", "Archive path"),
                HarnessSchema.list("paths", "Paths to pack when zipping", false)
            )
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = try {
        when (name) {
            "workspace_info" -> workspaceInfo(ctx)
            "list_directory" -> listDirectory(ctx, args)
            "search_files" -> searchFiles(ctx, args)
            "read_file" -> readFile(ctx, args)
            "write_file" -> writeFile(ctx, args)
            "edit_file" -> editFile(ctx, args)
            "create_directory" -> createDirectory(ctx, args)
            "move_path" -> movePath(ctx, args)
            "copy_path" -> copyPath(ctx, args)
            "delete_path" -> deletePath(ctx, args)
            "file_info" -> fileInfo(ctx, args)
            "batch_files" -> batchFiles(ctx, args)
            "diff_files" -> diffFiles(ctx, args)
            "file_history" -> fileHistory(ctx, args)
            "restore_file" -> restoreFile(ctx, args)
            "zip_paths" -> zipPaths(ctx, args)
            else -> null
        }
    } catch (e: HarnessError) {
        ToolExecResult(e.message ?: "That path cannot be used", success = false)
    } catch (e: Exception) {
        ToolExecResult("$name failed: ${e.message ?: e::class.simpleName}", success = false)
    }

    private fun workspaceInfo(ctx: HarnessCtx): ToolExecResult {
        val root = HarnessRuntime.workspace()
        val files = root.walkTopDown().filter { it.isFile }.take(20000).toList()
        val bytes = files.sumOf { it.length() }
        val groups = HarnessGroup.entries.filter { ctx.config.groupEnabled(it) }.joinToString(", ") { it.key }
        val sb = StringBuilder()
        sb.append("Workspace: ${root.path}\n")
        sb.append("Files: ${files.size}${if (files.size >= 20000) "+" else ""}, ${Workspace.humanSize(bytes)}\n")
        sb.append("Free space: ${Workspace.humanSize(root.usableSpace)}\n")
        sb.append("Platform: ${if (ctx.android) "Android" else "Windows"}\n")
        sb.append("Shell: ${pluginShellLabel()}\n")
        sb.append("Tool groups on: $groups\n")
        sb.append("Snapshots: ${if (ctx.config.snapshots) "on (${Snapshots.all(ctx).size} kept)" else "off"}\n")
        sb.append("Delete approval: ${ctx.config.approvalFor(HarnessPermission.DELETE).key}, ")
        sb.append("command approval: ${ctx.config.approvalFor(HarnessPermission.EXECUTE).key}\n")
        val extra = ctx.config.writeRoots.filter { it.isNotBlank() }
        if (extra.isNotEmpty()) sb.append("Extra writable roots: ${extra.joinToString(", ")}\n")
        val readOnly = ctx.config.readOnlyRoots.filter { it.isNotBlank() }
        if (readOnly.isNotEmpty()) sb.append("Read-only roots: ${readOnly.joinToString(", ")}\n")
        return ToolExecResult(sb.toString().trimEnd())
    }

    private fun pluginShellLabel(): String {
        val shell = HarnessRuntime.shell
        return when {
            shell == null -> "none"
            !shell.isReady() -> "${shell.id} (not ready)"
            else -> "${shell.id} (${shell.describe()})"
        }
    }

    private fun listDirectory(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val raw = args.optString("path", "").ifBlank { "." }
        val dir = Workspace.forRead(ctx, raw)
        if (!dir.isDirectory) return ToolExecResult("${Workspace.display(ctx, dir)} is a file, not a directory.", success = false)
        val depth = args.optInt("depth", 1).coerceIn(1, 3)
        val max = args.optInt("max_entries", MAX_LIST).coerceIn(1, 2000)
        val out = StringBuilder()
        var count = 0
        fun walk(current: File, level: Int, prefix: String) {
            val children = current.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return
            for (child in children) {
                if (count >= max) return
                count++
                val marker = if (child.isDirectory) "/" else ""
                out.append(prefix).append(child.name).append(marker)
                if (child.isFile) out.append("  ").append(Workspace.humanSize(child.length()))
                out.append('\n')
                if (child.isDirectory && level < depth) walk(child, level + 1, "$prefix  ")
            }
        }
        walk(dir, 1, "")
        if (count == 0) return ToolExecResult("${Workspace.display(ctx, dir)} is empty.")
        val head = "${Workspace.display(ctx, dir)} — $count entr${if (count == 1) "y" else "ies"}" +
            if (count >= max) " (stopped at $max)" else ""
        return ToolExecResult("$head\n${out.toString().trimEnd()}")
    }

    private fun searchFiles(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val query = args.optString("query", "")
        if (query.isBlank()) return ToolExecResult("Give me something to search for.", success = false)
        val root = Workspace.forRead(ctx, args.optString("path", "").ifBlank { "." })
        val regex = args.optBoolean("regex", false)
        val glob = args.optString("glob", "")
        val max = args.optInt("max_results", MAX_SEARCH).coerceIn(1, 500)
        val pattern = if (regex) Regex(query) else null
        val namePattern = if (glob.isNotBlank()) globToRegex(glob) else null
        val matches = mutableListOf<String>()
        var scanned = 0
        val files = if (root.isFile) listOf(root) else root.walkTopDown().filter { it.isFile }.toList()
        for (file in files) {
            if (matches.size >= max) break
            val relative = file.relativeToOrSelf(root)
            if (namePattern != null && !namePattern.matches(relative.path.replace('\\', '/'))) continue
            val nameHit = file.name.contains(query, ignoreCase = !regex)
            if (nameHit) matches.add("${file.path}  (name match)")
            if (file.length() > 2L * 1024 * 1024) continue
            scanned++
            val text = try { file.readText() } catch (e: Exception) { continue }
            if (Workspace.looksBinary(text.toByteArray())) continue
            text.lineSequence().forEachIndexed { index, line ->
                if (matches.size >= max) return@forEachIndexed
                val hit = if (pattern != null) pattern.containsMatchIn(line) else line.contains(query, ignoreCase = true)
                if (hit) matches.add("${file.path}:${index + 1}  ${line.trim().take(200)}")
            }
        }
        return if (matches.isEmpty()) {
            ToolExecResult("No matches for \"$query\" under ${Workspace.display(ctx, root)} ($scanned files read).")
        } else {
            ToolExecResult(
                "${matches.size} match${if (matches.size == 1) "" else "es"} for \"$query\" " +
                    "($scanned files read):\n" + matches.joinToString("\n")
            )
        }
    }

    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder()
        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            when {
                c == '*' && i + 1 < glob.length && glob[i + 1] == '*' -> {
                    sb.append(".*")
                    i++
                }
                c == '*' -> sb.append("[^/]*")
                c == '?' -> sb.append("[^/]")
                c == '.' || c == '(' || c == ')' || c == '+' || c == '|' || c == '^' || c == '$' -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
            i++
        }
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }

    private fun readFile(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forRead(ctx, args.optString("path", ""))
        if (file.isDirectory) return listDirectory(ctx, JSONObject().put("path", args.optString("path", "")))
        val text = Workspace.readText(file)
        val lines = text.split("\n")
        val start = args.optInt("start_line", 1).coerceAtLeast(1)
        val max = args.optInt("max_lines", 400).coerceIn(1, 5000)
        val slice = lines.drop(start - 1).take(max)
        val header = "${Workspace.display(ctx, file)} — ${lines.size} lines, ${Workspace.humanSize(file.length())}" +
            if (start > 1 || start - 1 + slice.size < lines.size) " (showing ${start}-${start + slice.size - 1})" else ""
        return ToolExecResult("$header\n" + slice.joinToString("\n"))
    }

    private fun writeFile(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forWrite(ctx, args.optString("path", ""))
        val content = args.optString("content", "")
        val append = args.optBoolean("append", false)
        if (file.isDirectory) return ToolExecResult("${Workspace.display(ctx, file)} is a directory.", success = false)
        if (append) {
            if (ctx.config.snapshots && file.exists()) Snapshots.capture(ctx, file)
            file.parentFile?.mkdirs()
            file.appendText(if (file.length() > 0 && !file.readText().endsWith("\n")) "\n$content" else content)
        } else {
            Workspace.writeText(ctx, file, content)
        }
        val verb = if (append) "Appended to" else "Wrote"
        return ToolExecResult("$verb ${Workspace.display(ctx, file)} (${Workspace.humanSize(file.length())}, ${Workspace.countLines(file)} lines).")
    }

    private fun editFile(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = Workspace.forWrite(ctx, args.optString("path", ""))
        if (!file.exists()) return ToolExecResult("${Workspace.display(ctx, file)} does not exist.", success = false)
        val find = args.optString("find", "")
        if (find.isEmpty()) return ToolExecResult("Give me the exact text to find.", success = false)
        val replace = args.optString("replace", "")
        val all = args.optBoolean("all", false)
        val before = file.readText()
        val occurrences = Regex(Regex.escape(find)).findAll(before).count()
        if (occurrences == 0) return ToolExecResult("That text is not in ${Workspace.display(ctx, file)}.", success = false)
        if (occurrences > 1 && !all) {
            return ToolExecResult(
                "That text appears $occurrences times in ${Workspace.display(ctx, file)}. Add more context or set all=true.",
                success = false
            )
        }
        val after = if (all) before.replace(find, replace) else before.replaceFirst(find, replace)
        Workspace.writeText(ctx, file, after)
        val diff = Diffs.unified(before, after)
        return ToolExecResult(
            "Edited ${Workspace.display(ctx, file)} ($occurrences replacement${if (occurrences == 1) "" else "s"}).\n" +
                diff.take(4000)
        )
    }

    private fun createDirectory(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val dir = Workspace.forWrite(ctx, args.optString("path", ""))
        val ok = dir.exists() || dir.mkdirs()
        return if (ok) ToolExecResult("Directory ${Workspace.display(ctx, dir)} is ready.") else
            ToolExecResult("Could not create ${Workspace.display(ctx, dir)}.", success = false)
    }

    private fun movePath(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val from = Workspace.forWrite(ctx, args.optString("from", ""))
        val to = Workspace.forWrite(ctx, args.optString("to", ""))
        if (!from.exists()) return ToolExecResult("${Workspace.display(ctx, from)} does not exist.", success = false)
        if (to.exists() && !args.optBoolean("overwrite", false)) {
            return ToolExecResult("${Workspace.display(ctx, to)} already exists.", success = false)
        }
        if (from.isFile && ctx.config.snapshots) Snapshots.capture(ctx, from)
        to.parentFile?.mkdirs()
        val ok = if (args.optBoolean("overwrite", false) && to.exists()) {
            to.deleteRecursively() && from.renameTo(to)
        } else from.renameTo(to)
        return if (ok) {
            ToolExecResult("Moved ${Workspace.display(ctx, from)} to ${Workspace.display(ctx, to)}.")
        } else ToolExecResult("Could not move ${Workspace.display(ctx, from)}.", success = false)
    }

    private fun copyPath(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val from = Workspace.forRead(ctx, args.optString("from", ""))
        val to = Workspace.forWrite(ctx, args.optString("to", ""))
        if (to.exists() && !args.optBoolean("overwrite", false)) {
            return ToolExecResult("${Workspace.display(ctx, to)} already exists.", success = false)
        }
        if (from.isDirectory) {
            from.copyRecursively(to, overwrite = args.optBoolean("overwrite", false))
        } else {
            to.parentFile?.mkdirs()
            from.copyTo(to, overwrite = args.optBoolean("overwrite", false))
        }
        return ToolExecResult("Copied ${Workspace.display(ctx, from)} to ${Workspace.display(ctx, to)}.")
    }

    private fun deletePath(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val path = Workspace.forWrite(ctx, args.optString("path", ""))
        if (!path.exists()) return ToolExecResult("${Workspace.display(ctx, path)} does not exist.", success = false)
        if (path.isDirectory && !args.optBoolean("recursive", false)) {
            val children = path.listFiles()?.size ?: 0
            if (children > 0) {
                return ToolExecResult(
                    "${Workspace.display(ctx, path)} holds $children entr${if (children == 1) "y" else "ies"}. " +
                        "Set recursive=true to delete it all.",
                    success = false
                )
            }
        }
        if (!path.isDirectory && ctx.config.snapshots) Snapshots.capture(ctx, path)
        val ok = if (path.isDirectory) path.deleteRecursively() else path.delete()
        return if (ok) ToolExecResult("Deleted ${Workspace.display(ctx, path)}.")
        else ToolExecResult("Could not delete ${Workspace.display(ctx, path)}.", success = false)
    }

    private fun fileInfo(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val path = Workspace.resolve(ctx, args.optString("path", ""))
        if (!path.exists()) return ToolExecResult("${Workspace.display(ctx, path)} does not exist.", success = false)
        val inside = Workspace.isInside(path, HarnessRuntime.workspace())
        val snapshots = Snapshots.history(ctx, path.canonicalPath, 5)
        val sb = StringBuilder()
        sb.append("${Workspace.display(ctx, path)}\n")
        sb.append("Absolute: ${path.path}\n")
        sb.append("Kind: ${if (path.isDirectory) "directory" else "file"}\n")
        if (path.isFile) {
            sb.append("Size: ${Workspace.humanSize(path.length())} (${path.length()} bytes)\n")
            sb.append("Lines: ${Workspace.countLines(path)}\n")
        }
        sb.append("Modified: ${java.util.Date(path.lastModified())}\n")
        sb.append("Inside workspace: $inside\n")
        sb.append("Readable: ${Workspace.readable(ctx, path)}, writable: ${Workspace.writable(ctx, path)}\n")
        sb.append("Snapshots: ${snapshots.size}${if (snapshots.isNotEmpty()) " (latest ${snapshots.first().id})" else ""}")
        return ToolExecResult(sb.toString())
    }

    private fun batchFiles(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val action = args.optString("action", "").lowercase()
        val paths = args.optJSONArray("paths") ?: JSONArray()
        if (paths.length() == 0) return ToolExecResult("Give me the paths to work on.", success = false)
        val find = args.optString("find", "")
        val replace = args.optString("replace", "")
        val regex = args.optBoolean("regex", false)
        val target = args.optString("target_dir", "")
        val done = mutableListOf<String>()
        for (i in 0 until paths.length()) {
            val raw = paths.optString(i, "")
            if (raw.isBlank()) continue
            try {
                when (action) {
                    "rename" -> {
                        if (find.isEmpty()) return ToolExecResult("Renaming needs find and replace.", success = false)
                        val file = Workspace.forWrite(ctx, raw)
                        val newName = if (regex) file.name.replace(Regex(find), replace) else file.name.replace(find, replace)
                        val to = File(file.parentFile, newName)
                        if (file.isFile && ctx.config.snapshots) Snapshots.capture(ctx, file)
                        if (file.renameTo(to)) done.add("${file.name} → $newName") else done.add("${file.name} (failed)")
                    }
                    "copy", "move" -> {
                        if (target.isBlank()) return ToolExecResult("Copy and move need target_dir.", success = false)
                        val file = Workspace.forRead(ctx, raw)
                        val destination = Workspace.forWrite(ctx, "$target/${file.name}")
                        destination.parentFile?.mkdirs()
                        if (action == "copy") {
                            if (file.isDirectory) file.copyRecursively(destination, overwrite = true)
                            else file.copyTo(destination, overwrite = true)
                            done.add("${file.name} copied")
                        } else {
                            if (file.renameTo(destination)) done.add("${file.name} moved")
                            else done.add("${file.name} (failed)")
                        }
                    }
                    "delete" -> {
                        val file = Workspace.forWrite(ctx, raw)
                        if (file.isFile && ctx.config.snapshots) Snapshots.capture(ctx, file)
                        val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                        done.add("${file.name} ${if (ok) "deleted" else "(failed)"}")
                    }
                    else -> return ToolExecResult("action must be rename, copy, move or delete.", success = false)
                }
            } catch (e: HarnessError) {
                done.add("$raw (${e.message})")
            }
        }
        return ToolExecResult("$action on ${done.size} path(s):\n" + done.joinToString("\n"))
    }

    private fun diffFiles(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val path = Workspace.forRead(ctx, args.optString("path", ""))
        if (path.isDirectory) return ToolExecResult("Point me at a file, not a directory.", success = false)
        val before = path.readText()
        val after = when {
            args.has("content") -> args.optString("content", "")
            args.optString("other", "").isNotBlank() -> Workspace.readText(Workspace.forRead(ctx, args.optString("other", "")))
            else -> return ToolExecResult("Give me either other (a file) or content (text) to compare against.", success = false)
        }
        val diff = Diffs.unified(before, after)
        return if (diff.isBlank()) ToolExecResult("${Workspace.display(ctx, path)} is identical to what you passed.")
        else ToolExecResult("Diff for ${Workspace.display(ctx, path)}:\n" + diff.take(8000))
    }

    private fun fileHistory(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val limit = args.optInt("limit", 20).coerceIn(1, 200)
        val raw = args.optString("path", "")
        val entries = if (raw.isBlank()) {
            Snapshots.all(ctx).takeLast(limit).reversed()
        } else {
            Workspace.resolve(ctx, raw).let { Snapshots.history(ctx, it.canonicalPath, limit) }
        }
        if (entries.isEmpty()) return ToolExecResult("No snapshots yet.")
        val sb = StringBuilder("Snapshots (${entries.size}):\n")
        entries.forEach { entry ->
            sb.append(entry.id).append("  ").append(java.util.Date(entry.at)).append("  ")
                .append(Workspace.humanSize(entry.bytes)).append("  ").append(entry.path).append('\n')
        }
        sb.append("Total kept: ${Workspace.humanSize(Snapshots.totalBytes(ctx))}")
        return ToolExecResult(sb.toString())
    }

    private fun restoreFile(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val path = Workspace.resolve(ctx, args.optString("path", ""))
        val id = args.optString("snapshot_id", "")
        val entry = if (id.isNotBlank()) {
            Snapshots.all(ctx).firstOrNull { it.id == id }
                ?: return ToolExecResult("No snapshot with id $id.", success = false)
        } else {
            Snapshots.latest(ctx, path.canonicalPath)
                ?: return ToolExecResult("No snapshots for ${Workspace.display(ctx, path)}.", success = false)
        }
        val restored = Snapshots.restore(ctx, entry.id)
        return ToolExecResult("Restored ${Workspace.display(ctx, restored)} from snapshot ${entry.id} (${java.util.Date(entry.at)}).")
    }

    private fun zipPaths(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val action = args.optString("action", "zip").lowercase()
        val archive = if (action == "unzip") Workspace.forRead(ctx, args.optString("path", ""))
        else Workspace.forWrite(ctx, args.optString("path", ""))
        return if (action == "unzip") unzip(ctx, archive) else zip(ctx, archive, args.optJSONArray("paths"))
    }

    private fun zip(ctx: HarnessCtx, archive: File, paths: JSONArray?): ToolExecResult {
        val roots = mutableListOf<File>()
        if (paths != null) {
            for (i in 0 until paths.length()) {
                val raw = paths.optString(i, "")
                if (raw.isNotBlank()) roots.add(Workspace.forRead(ctx, raw))
            }
        }
        if (roots.isEmpty()) return ToolExecResult("Give me the paths to pack.", success = false)
        archive.parentFile?.mkdirs()
        if (ctx.config.snapshots && archive.exists()) Snapshots.capture(ctx, archive)
        var count = 0
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            roots.forEach { root ->
                val base = if (root.isDirectory) root else root.parentFile
                val files = if (root.isDirectory) root.walkTopDown().filter { it.isFile }.toList() else listOf(root)
                files.forEach { file ->
                    val entryName = base.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            }
        }
        return ToolExecResult("Packed $count file(s) into ${Workspace.display(ctx, archive)} (${Workspace.humanSize(archive.length())}).")
    }

    private fun unzip(ctx: HarnessCtx, archive: File): ToolExecResult {
        val target = File(archive.parentFile, archive.nameWithoutExtension)
        val root = Workspace.forWrite(ctx, target.path)
        root.mkdirs()
        var count = 0
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val out = File(root, entry.name)
                if (!Workspace.isInside(out, root)) return ToolExecResult("That archive tries to escape the folder.", success = false)
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                    count++
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return ToolExecResult("Unpacked $count file(s) into ${Workspace.display(ctx, root)}.")
    }
}
