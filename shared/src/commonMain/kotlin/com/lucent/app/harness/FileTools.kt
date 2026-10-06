package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import com.lucent.app.network.ToolImage
import kotlinx.serialization.json.*
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.use
import okio.buffer

object FileTools : HarnessGroupTools {

    override val group = HarnessGroup.FILES

    private const val MAX_LIST = 500
    private const val MAX_SEARCH = 120
    private const val MAX_IMAGE_BYTES = 8L * 1024 * 1024
    private const val MAX_IMAGE_CEILING = 32L * 1024 * 1024
    private val IMAGE_TYPES = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "webp" to "image/webp",
        "gif" to "image/gif"
    )
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
                "default 400). Binary files are refused; use file_info first if you are unsure, and read_image when " +
                "the file is a picture.",
            params = listOf(
                HarnessSchema.text("path", "File to read"),
                HarnessSchema.number("start_line", "First line to return", false),
                HarnessSchema.number("max_lines", "How many lines to return", false)
            )
        ),
        HarnessTool(
            name = "read_image",
            group = group,
            permission = HarnessPermission.READ,
            description = "Look at a picture and see it: PNG, JPEG, WebP or GIF. Arguments: path, plus optional " +
                "max_bytes to lower the size limit. The image comes back as an attachment you can look at, so " +
                "describe what is actually in it rather than guessing from the name.",
            params = listOf(
                HarnessSchema.text("path", "Image file to look at"),
                HarnessSchema.number("max_bytes", "Refuse images larger than this many bytes", false)
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
            ) + HarnessSchema.escalation()
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
            ) + HarnessSchema.escalation()
        ),
        HarnessTool(
            name = "create_directory",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Create a directory, including any missing parents. Arguments: path.",
            params = listOf(HarnessSchema.text("path", "Directory to create")) + HarnessSchema.escalation()
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
            ) + HarnessSchema.escalation()
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
            ) + HarnessSchema.escalation()
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
            ) + HarnessSchema.escalation()
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
            ) + HarnessSchema.escalation()
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
            ) + HarnessSchema.escalation()
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
            ) + HarnessSchema.escalation()
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JsonObject): ToolExecResult? = try {
        FileObservations.track(HarnessRuntime.conversationId)
        when (name) {
            "workspace_info" -> workspaceInfo(ctx)
            "list_directory" -> listDirectory(ctx, args)
            "search_files" -> searchFiles(ctx, args)
            "read_file" -> readFile(ctx, args)
            "read_image" -> readImage(ctx, args)
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
        if (e.blocked) throw e
        ToolExecResult(e.message ?: "That path cannot be used", success = false)
    } catch (e: Exception) {
        ToolExecResult("$name failed: ${e.message ?: e::class.simpleName}", success = false)
    }

    private fun workspaceInfo(ctx: HarnessCtx): ToolExecResult {
        val root = HarnessRuntime.workspacePath().toPath()
        val files = mutableListOf<Path>()
        fun walkSpace(p: Path) {
            if (files.size >= 20000) return
            FileSystem.SYSTEM.listOrNull(p)?.forEach { 
                if (FileSystem.SYSTEM.metadataOrNull(it)?.isDirectory == true) walkSpace(it)
                else if (FileSystem.SYSTEM.metadataOrNull(it)?.isRegularFile == true) files.add(it)
            }
        }
        if (FileSystem.SYSTEM.metadataOrNull(root)?.isDirectory == true) walkSpace(root)
        val bytes = files.sumOf { FileSystem.SYSTEM.metadata(it).size ?: 0L }
        val groups = HarnessGroup.entries.filter { ctx.config.groupEnabled(it) }.joinToString(", ") { it.key }
        val sb = StringBuilder()
        sb.append("Workspace: ${root.path}\n")
        sb.append("Files: ${files.size}${if (files.size >= 20000) "+" else ""}, ${Workspace.humanSize(bytes)}\n")
        sb.append("Free space: ${Workspace.humanSize(0L)}\n")
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

    private fun listDirectory(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val raw = (args["path"]?.jsonPrimitive?.content ?: "").ifBlank { "." }
        val dir = Workspace.forRead(ctx, raw).toPath()
        if (!dir.isDirectory) return ToolExecResult("${Workspace.display(ctx, dir)} is a file, not a directory.", success = false)
        val depth = (args["depth"]?.jsonPrimitive?.intOrNull ?: 1).coerceIn(1, 3)
        val max = args.optInt("max_entries", MAX_LIST).coerceIn(1, 2000)
        val out = StringBuilder()
        var count = 0
        fun walk(current: Path, level: Int, prefix: String) {
            val children = FileSystem.SYSTEM.listOrNull(current)?.sortedWith(compareBy({ FileSystem.SYSTEM.metadataOrNull(it)?.isDirectory != true }, { it.name.lowercase() })) ?: return
            for (child in children) {
                if (count >= max) return
                count++
                val marker = if (FileSystem.SYSTEM.metadataOrNull(child)?.isDirectory == true) "/" else ""
                out.append(prefix).append(child.name).append(marker)
                if (FileSystem.SYSTEM.metadataOrNull(child)?.isRegularFile == true) out.append("  ").append(Workspace.humanSize(FileSystem.SYSTEM.metadata(child).size ?: 0L))
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

    private fun searchFiles(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val query = (args["query"]?.jsonPrimitive?.content ?: "")
        if (query.isBlank()) return ToolExecResult("Give me something to search for.", success = false)
        val root = Workspace.forRead(ctx, (args["path"]?.jsonPrimitive?.content ?: "").toPath().ifBlank { "." })
        val regex = (args["regex"]?.jsonPrimitive?.booleanOrNull ?: false)
        val glob = (args["glob"]?.jsonPrimitive?.content ?: "")
        val max = args.optInt("max_results", MAX_SEARCH).coerceIn(1, 500)
        val pattern = if (regex) Regex(query) else null
        val namePattern = if (glob.isNotBlank()) globToRegex(glob) else null
        val matches = mutableListOf<String>()
        var scanned = 0
        val files = mutableListOf<Path>()
        if (FileSystem.SYSTEM.metadataOrNull(root)?.isRegularFile == true) files.add(root) else {
            fun walk(p: Path) {
                FileSystem.SYSTEM.listOrNull(p)?.forEach {
                    if (FileSystem.SYSTEM.metadataOrNull(it)?.isDirectory == true) walk(it)
                    else if (FileSystem.SYSTEM.metadataOrNull(it)?.isRegularFile == true) files.add(it)
                }
            }
            walk(root)
        }
        for (file in files) {
            if (matches.size >= max) break
            val relativePath = file.toString().removePrefix(root.toString()).removePrefix("/")
            if (namePattern != null && !namePattern.matches(relativePath.replace('\\', '/'))) continue
            val nameHit = file.name.contains(query, ignoreCase = !regex)
            if (nameHit) matches.add("${file.toString()}  (name match)")
            if ((FileSystem.SYSTEM.metadata(file).size ?: 0L) > 2L * 1024 * 1024) continue
            scanned++
            val text = try { FileSystem.SYSTEM.read(file) { readUtf8() } } catch (e: Exception) { continue }
            if (Workspace.looksBinary(text.toByteArray())) continue
            text.lineSequence().forEachIndexed { index, line ->
                if (matches.size >= max) return@forEachIndexed
                val hit = if (pattern != null) pattern.containsMatchIn(line) else line.contains(query, ignoreCase = true)
                if (hit) matches.add("${file.toString()}:${index + 1}  ${line.trim().take(200)}")
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

    private fun readFile(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val file = Workspace.forRead(ctx, (args["path"]?.jsonPrimitive?.content ?: "").toPath())
        if ((FileSystem.SYSTEM.metadataOrNull(file)?.isDirectory == true)) return listDirectory(ctx, buildJsonObject { put("path", (args["path"]?.jsonPrimitive?.content ?: "")) })
        val text = Workspace.readText(file.toString())
        FileObservations.note(file.toString())
        val lines = text.split("\n")
        val start = (args["start_line"]?.jsonPrimitive?.intOrNull ?: 1).coerceAtLeast(1)
        val max = (args["max_lines"]?.jsonPrimitive?.intOrNull ?: 400).coerceIn(1, 5000)
        val slice = lines.drop(start - 1).take(max)
        val header = "${Workspace.display(ctx, file.toString())} — ${lines.size} lines, ${Workspace.humanSize((FileSystem.SYSTEM.metadata(file).size ?: 0L))}" +
            if (start > 1 || start - 1 + slice.size < lines.size) " (showing ${start}-${start + slice.size - 1})" else ""
        return ToolExecResult("$header\n" + slice.joinToString("\n"))
    }

    private fun readImage(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val file = Workspace.forRead(ctx, (args["path"]?.jsonPrimitive?.content ?: "").toPath())
        val shown = Workspace.display(ctx, file.toString())
        if ((FileSystem.SYSTEM.metadataOrNull(file)?.isDirectory == true)) return ToolExecResult("$shown is a directory, not an image.", success = false)
        val extension = file.name.substringAfterLast(".", "").lowercase()
        val claimed = IMAGE_TYPES[extension]
            ?: return ToolExecResult(
                "$shown is not an image. I can look at ${IMAGE_TYPES.keys.joinToString(", ")} files.",
                success = false
            )
        val ceiling = args.optLong("max_bytes", MAX_IMAGE_BYTES).coerceIn(1024L, MAX_IMAGE_CEILING)
        if ((FileSystem.SYSTEM.metadata(file).size ?: 0L) > ceiling) {
            return ToolExecResult(
                "$shown is ${Workspace.humanSize((FileSystem.SYSTEM.metadata(file).size ?: 0L))}, above the ${Workspace.humanSize(ceiling)} limit.",
                success = false
            )
        }
        val head = ByteArray(16)
        val read = try {
            FileSystem.SYSTEM.read(file) { read(head) }
        } catch (e: Exception) {
            return ToolExecResult("$shown could not be read: ${e.message ?: e::class.simpleName}", success = false)
        }
        val sniffed = imageMime(head, read.coerceAtLeast(0))
            ?: return ToolExecResult("$shown is named .$extension but does not contain $claimed data.", success = false)
        if (sniffed != claimed) {
            return ToolExecResult("$shown is named .$extension but the bytes are $sniffed.", success = false)
        }
        val bytes = Workspace.readBytes(file.toString(), ceiling)
        val encoded = java.util.Base64.getEncoder().encodeToString(bytes)
        FileObservations.note(file.toString())
        return ToolExecResult(
            "$shown ($sniffed, ${Workspace.humanSize((FileSystem.SYSTEM.metadata(file).size ?: 0L))}).",
            images = listOf(ToolImage(sniffed, encoded, file.name))
        )
    }

    internal fun imageMime(bytes: ByteArray, length: Int = bytes.size): String? {
        val size = length.coerceAtMost(bytes.size)
        fun at(index: Int): Int = if (index < size) bytes[index].toInt() and 0xFF else -1
        fun tag(offset: Int, text: String): Boolean =
            text.indices.all { at(offset + it) == text[it].code }
        return when {
            size >= 8 && at(0) == 0x89 && tag(1, "PNG") && at(4) == 0x0D && at(5) == 0x0A && at(6) == 0x1A ->
                "image/png"
            size >= 3 && at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "image/jpeg"
            size >= 6 && (tag(0, "GIF87a") || tag(0, "GIF89a")) -> "image/gif"
            size >= 12 && tag(0, "RIFF") && tag(8, "WEBP") -> "image/webp"
            else -> null
        }
    }

    private fun writeFile(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val file = Workspace.forWrite(ctx, (args["path"]?.jsonPrimitive?.content ?: "").toPath())
        val content = (args["content"]?.jsonPrimitive?.content ?: "")
        val append = (args["append"]?.jsonPrimitive?.booleanOrNull ?: false)
        if ((FileSystem.SYSTEM.metadataOrNull(file)?.isDirectory == true)) return ToolExecResult("${Workspace.display(ctx, file.toString())} is a directory.", success = false)
        if (!append && (FileSystem.SYSTEM.metadataOrNull(file)?.isRegularFile == true) && (FileSystem.SYSTEM.metadata(file).size ?: 0L) > 0 && !FileObservations.seen(file.toString())) {
            return ToolExecResult(
                "${Workspace.display(ctx, file.toString())} already exists and has not been read. Read it first, then write.",
                success = false
            )
        }
        if (append) {
            if (ctx.config.snapshots && FileSystem.SYSTEM.exists(file)) Snapshots.capture(ctx, file.toString())
            file.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            file.appendText(if ((FileSystem.SYSTEM.metadata(file).size ?: 0L) > 0 && !FileSystem.SYSTEM.read(file) { readUtf8() }.endsWith("\n")) "\n$content" else content)
        } else {
            Workspace.writeText(ctx, file.toString(), content)
        }
        val verb = if (append) "Appended to" else "Wrote"
        FileObservations.note(file.toString())
        return ToolExecResult("$verb ${Workspace.display(ctx, file.toString())} (${Workspace.humanSize((FileSystem.SYSTEM.metadata(file).size ?: 0L))}, ${Workspace.countLines(file.toString())} lines).")
    }

    private fun editFile(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val file = Workspace.forWrite(ctx, (args["path"]?.jsonPrimitive?.content ?: "").toPath())
        if (!FileSystem.SYSTEM.exists(file)) return ToolExecResult("${Workspace.display(ctx, file.toString())} does not exist.", success = false)
        if (!FileObservations.seen(file.toString())) {
            return ToolExecResult(
                "${Workspace.display(ctx, file.toString())} has not been read. Read it first, then edit it.",
                success = false
            )
        }
        val find = (args["find"]?.jsonPrimitive?.content ?: "")
        if (find.isEmpty()) return ToolExecResult("Give me the exact text to find.", success = false)
        val replace = (args["replace"]?.jsonPrimitive?.content ?: "")
        val all = (args["all"]?.jsonPrimitive?.booleanOrNull ?: false)
        val before = FileSystem.SYSTEM.read(file) { readUtf8() }
        val occurrences = Regex(Regex.escape(find)).findAll(before).count()
        if (occurrences == 0) return ToolExecResult("That text is not in ${Workspace.display(ctx, file.toString())}.", success = false)
        if (occurrences > 1 && !all) {
            return ToolExecResult(
                "That text appears $occurrences times in ${Workspace.display(ctx, file.toString())}. Add more context or set all=true.",
                success = false
            )
        }
        val after = if (all) before.replace(find, replace) else before.replaceFirst(find, replace)
        Workspace.writeText(ctx, file.toString(), after)
        FileObservations.note(file.toString())
        val diff = Diffs.unified(before, after)
        return ToolExecResult(
            "Edited ${Workspace.display(ctx, file.toString())} ($occurrences replacement${if (occurrences == 1) "" else "s"}).\n" +
                diff.take(4000)
        )
    }

    private fun createDirectory(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val dir = Workspace.forWrite(ctx, (args["path"]?.jsonPrimitive?.content ?: "").toPath())
        val ok = try { FileSystem.SYSTEM.createDirectories(dir); true } catch(e:Exception){false}
        return if (ok) ToolExecResult("Directory ${Workspace.display(ctx, dir)} is ready.") else
            ToolExecResult("Could not create ${Workspace.display(ctx, dir)}.", success = false)
    }

    private fun movePath(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val from = Workspace.forWrite(ctx, (args["from"]?.jsonPrimitive?.content ?: "").toPath())
        val to = Workspace.forWrite(ctx, (args["to"]?.jsonPrimitive?.content ?: "").toPath())
        if (!FileSystem.SYSTEM.exists(from)) return ToolExecResult("${Workspace.display(ctx, from.toString())} does not exist.", success = false)
        if (FileSystem.SYSTEM.exists(to) && !(args["overwrite"]?.jsonPrimitive?.booleanOrNull ?: false)) {
            return ToolExecResult("${Workspace.display(ctx, to.toString())} already exists.", success = false)
        }
        if ((FileSystem.SYSTEM.metadataOrNull(from)?.isRegularFile == true) && ctx.config.snapshots) Snapshots.capture(ctx, from.toString())
        to.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
        val ok = if ((args["overwrite"]?.jsonPrimitive?.booleanOrNull ?: false) && FileSystem.SYSTEM.exists(to)) {
            to.deleteRecursively() && from.renameTo(to)
        } else from.renameTo(to)
        return if (ok) {
            ToolExecResult("Moved ${Workspace.display(ctx, from.toString())} to ${Workspace.display(ctx, to.toString())}.")
        } else ToolExecResult("Could not move ${Workspace.display(ctx, from.toString())}.", success = false)
    }

    private fun copyPath(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val from = Workspace.forRead(ctx, (args["from"]?.jsonPrimitive?.content ?: "").toPath())
        val to = Workspace.forWrite(ctx, (args["to"]?.jsonPrimitive?.content ?: "").toPath())
        if (FileSystem.SYSTEM.exists(to) && !(args["overwrite"]?.jsonPrimitive?.booleanOrNull ?: false)) {
            return ToolExecResult("${Workspace.display(ctx, to.toString())} already exists.", success = false)
        }
        if ((FileSystem.SYSTEM.metadataOrNull(from)?.isDirectory == true)) {
            run {
                fun copyWalk(src: Path, dst: Path) {
                    FileSystem.SYSTEM.createDirectories(dst)
                    FileSystem.SYSTEM.list(src).forEach { p ->
                        val childDst = dst / p.name
                        if (FileSystem.SYSTEM.metadata(p).isDirectory == true) {
                            copyWalk(p, childDst)
                        } else {
                            FileSystem.SYSTEM.copy(p, childDst)
                        }
                    }
                }
                if ((args["overwrite"]?.jsonPrimitive?.booleanOrNull ?: false) && FileSystem.SYSTEM.exists(to)) FileSystem.SYSTEM.deleteRecursively(to)
                copyWalk(from, to)
            }
        } else {
            to.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            run { if ((args["overwrite"]?.jsonPrimitive?.booleanOrNull ?: false) && FileSystem.SYSTEM.exists(to)) FileSystem.SYSTEM.delete(to); FileSystem.SYSTEM.copy(from, to) }
        }
        val bytes = writer.close()
        FileSystem.SYSTEM.write(archive) { write(bytes) }
        return ToolExecResult("Copied ${Workspace.display(ctx, from.toString())} to ${Workspace.display(ctx, to.toString())}.")
    }

    private fun deletePath(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val path = Workspace.forWrite(ctx, (args["path"]?.jsonPrimitive?.content ?: "").toPath())
        if (!FileSystem.SYSTEM.exists(path)) return ToolExecResult("${Workspace.display(ctx, path.toString())} does not exist.", success = false)
        if (path.isDirectory && !(args["recursive"]?.jsonPrimitive?.booleanOrNull ?: false)) {
            val children = FileSystem.SYSTEM.listOrNull(path)?.size ?: 0
            if (children > 0) {
                return ToolExecResult(
                    "${Workspace.display(ctx, path.toString())} holds $children entr${if (children == 1) "y" else "ies"}. " +
                        "Set recursive=true to delete it all.",
                    success = false
                )
            }
        }
        if (!path.isDirectory && ctx.config.snapshots) Snapshots.capture(ctx, path.toString())
        val ok = try { FileSystem.SYSTEM.deleteRecursively(path); true } catch(e:Exception){false}
        return if (ok) ToolExecResult("Deleted ${Workspace.display(ctx, path.toString())}.")
        else ToolExecResult("Could not delete ${Workspace.display(ctx, path.toString())}.", success = false)
    }

    private fun fileInfo(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val path = Workspace.resolveFile((args["path"]?.jsonPrimitive?.content ?: "")).toString().toPath()
        if (!FileSystem.SYSTEM.exists(path)) return ToolExecResult("${Workspace.display(ctx, path.toString())} does not exist.", success = false)
        val inside = Workspace.isInside(path.toString(), HarnessRuntime.workspacePath())
        val snapshots = Snapshots.history(ctx, path.toString(), 5)
        val sb = StringBuilder()
        sb.append("${Workspace.display(ctx, path.toString())}\n")
        sb.append("Absolute: ${path.path}\n")
        sb.append("Kind: ${if (path.isDirectory) "directory" else "file"}\n")
        if (path.isFile) {
            sb.append("Size: ${Workspace.humanSize(path.size)} (${path.size} bytes)\n")
            sb.append("Lines: ${Workspace.countLines(path)}\n")
        }
        sb.append("Modified: ${java.util.Date(FileSystem.SYSTEM.metadataOrNull(path)?.lastModifiedAtMillis ?: 0L)}\n")
        sb.append("Inside workspace: $inside\n")
        sb.append("Readable: ${Workspace.readable(ctx, path.toString())}, writable: ${Workspace.writable(ctx, path.toString())}\n")
        sb.append("Snapshots: ${snapshots.size}${if (snapshots.isNotEmpty()) " (latest ${snapshots.first().id})" else ""}")
        return ToolExecResult(sb.toString())
    }

    private fun batchFiles(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val action = (args["action"]?.jsonPrimitive?.content ?: "").lowercase()
        val paths = args["paths"]?.jsonArray ?: JsonArray(emptyList())
        if (paths.size == 0) return ToolExecResult("Give me the paths to work on.", success = false)
        val find = (args["find"]?.jsonPrimitive?.content ?: "")
        val replace = (args["replace"]?.jsonPrimitive?.content ?: "")
        val regex = (args["regex"]?.jsonPrimitive?.booleanOrNull ?: false)
        val target = (args["target_dir"]?.jsonPrimitive?.content ?: "")
        val done = mutableListOf<String>()
        for (i in 0 until paths.size) {
            val raw = paths.optString(i, "")
            if (raw.isBlank()) continue
            try {
                when (action) {
                    "rename" -> {
                        if (find.isEmpty()) return ToolExecResult("Renaming needs find and replace.", success = false)
                        val file = Workspace.forWrite(ctx, raw).toPath()
                        val newName = if (regex) file.name.replace(Regex(find), replace) else file.name.replace(find, replace)
                        val to = (file.parent!! / newName)
                        if ((FileSystem.SYSTEM.metadataOrNull(file)?.isRegularFile == true) && ctx.config.snapshots) Snapshots.capture(ctx, file.toString())
                        if (try { FileSystem.SYSTEM.atomicMove(file, to); true } catch(e:Exception){false}) done.add("${file.name} → $newName") else done.add("${file.name} (failed)")
                    }
                    "copy", "move" -> {
                        if (target.isBlank()) return ToolExecResult("Copy and move need target_dir.", success = false)
                        val file = Workspace.forRead(ctx, raw).toPath()
                        val destination = Workspace.forWrite(ctx, "$target/${file.name}").toPath()
                        destination.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
                        if (action == "copy") {
                            if ((FileSystem.SYSTEM.metadataOrNull(file)?.isDirectory == true)) file.copyRecursively(destination, overwrite = true)
                            else file.copyTo(destination, overwrite = true)
                            done.add("${file.name} copied")
                        } else {
                            if (file.renameTo(destination)) done.add("${file.name} moved")
                            else done.add("${file.name} (failed)")
                        }
                    }
                    "delete" -> {
                        val file = Workspace.forWrite(ctx, raw).toPath()
                        if ((FileSystem.SYSTEM.metadataOrNull(file)?.isRegularFile == true) && ctx.config.snapshots) Snapshots.capture(ctx, file.toString())
                        val ok = if ((FileSystem.SYSTEM.metadataOrNull(file)?.isDirectory == true)) FileSystem.SYSTEM.deleteRecursively(file) else FileSystem.SYSTEM.delete(file)
                        done.add("${file.name} ${if (ok) "deleted" else "(failed)"}")
                    }
                    else -> return ToolExecResult("action must be rename, copy, move or delete.", success = false)
                }
            } catch (e: HarnessError) {
                if (e.blocked) throw e
                done.add("$raw (${e.message})")
            }
        }
        val bytes = writer.close()
        FileSystem.SYSTEM.write(archive) { write(bytes) }
        return ToolExecResult("$action on ${done.size} path(s):\n" + done.joinToString("\n"))
    }

    private fun diffFiles(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val path = Workspace.forRead(ctx, (args["path"]?.jsonPrimitive?.content ?: "").toPath())
        if (path.isDirectory) return ToolExecResult("Point me at a file, not a directory.", success = false)
        val before = FileSystem.SYSTEM.read(path) { readUtf8() }
        val after = when {
            args.containsKey("content") -> (args["content"]?.jsonPrimitive?.content ?: "")
            (args["other"]?.jsonPrimitive?.content ?: "").isNotBlank() -> Workspace.readText(Workspace.forRead(ctx, (args["other"]?.jsonPrimitive?.content ?: "").toPath()))
            else -> return ToolExecResult("Give me either other (a file) or content (text) to compare against.", success = false)
        }
        val diff = Diffs.unified(before, after)
        return if (diff.isBlank()) ToolExecResult("${Workspace.display(ctx, path.toString())} is identical to what you passed.")
        else ToolExecResult("Diff for ${Workspace.display(ctx, path.toString())}:\n" + diff.take(8000))
    }

    private fun fileHistory(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val limit = (args["limit"]?.jsonPrimitive?.intOrNull ?: 20).coerceIn(1, 200)
        val raw = (args["path"]?.jsonPrimitive?.content ?: "")
        val entries = if (raw.isBlank()) {
            Snapshots.all(ctx).takeLast(limit).reversed()
        } else {
            Workspace.resolveFile(raw).let { Snapshots.history(ctx, it.toString(), limit) }
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

    private fun restoreFile(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val path = Workspace.resolveFile((args["path"]?.jsonPrimitive?.content ?: "")).toString().toPath()
        val id = (args["snapshot_id"]?.jsonPrimitive?.content ?: "")
        val entry = if (id.isNotBlank()) {
            Snapshots.all(ctx).firstOrNull { it.id == id }
                ?: return ToolExecResult("No snapshot with id $id.", success = false)
        } else {
            Snapshots.latest(ctx, path.toString())
                ?: return ToolExecResult("No snapshots for ${Workspace.display(ctx, path.toString())}.", success = false)
        }
        val restored = Snapshots.restore(ctx, entry.id)
        return ToolExecResult("Restored ${Workspace.display(ctx, restored)} from snapshot ${entry.id} (${java.util.Date(entry.at)}).")
    }

    private fun zipPaths(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val action = (args["action"]?.jsonPrimitive?.content ?: "zip").lowercase()
        val archive = if (action == "unzip") Workspace.forRead(ctx, (args["path"]?.jsonPrimitive?.content ?: "")).toPath()
        else Workspace.forWrite(ctx, (args["path"]?.jsonPrimitive?.content ?: "")).toPath()
        return if (action == "unzip") unzip(ctx, archive) else zip(ctx, archive, args["paths"]?.jsonArray)
    }

    private fun zip(ctx: HarnessCtx, archive: Path, paths: JsonArray?): ToolExecResult {
        val roots = mutableListOf<Path>()
        if (paths != null) {
            for (i in 0 until paths.size) {
                val raw = paths.optString(i, "")
                if (raw.isNotBlank()) roots.add(Workspace.forRead(ctx, raw).toPath())
            }
        }
        if (roots.isEmpty()) return ToolExecResult("Give me the paths to pack.", success = false)
        archive.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
        if (ctx.config.snapshots && FileSystem.SYSTEM.exists(archive)) Snapshots.capture(ctx, archive.toString())
        var count = 0
        val writer = ZipWriter()
        roots.forEach { root ->
            val base = if (FileSystem.SYSTEM.metadataOrNull(root)?.isDirectory == true) root else root.parent!!
            val files = mutableListOf<Path>()
            fun walk(p: Path) {
                FileSystem.SYSTEM.listOrNull(p)?.forEach {
                    if (FileSystem.SYSTEM.metadataOrNull(it)?.isDirectory == true) walk(it)
                    else if (FileSystem.SYSTEM.metadataOrNull(it)?.isRegularFile == true) files.add(it)
                }
            }
            if (FileSystem.SYSTEM.metadataOrNull(root)?.isDirectory == true) walk(root) else files.add(root)
            
            files.forEach { file ->
                val entryName = file.toString().removePrefix(base.toString()).removePrefix("/").replace('\', '/')
                writer.addEntry(entryName, FileSystem.SYSTEM.read(file) { readByteArray() })
                count++
            }
        }
        val bytes = writer.close()
        FileSystem.SYSTEM.write(archive) { write(bytes) }
        return ToolExecResult("Packed $count file(s) into ${Workspace.display(ctx, archive.toString())} (${Workspace.humanSize(FileSystem.SYSTEM.metadata(archive).size ?: 0L)}).")
    }

    private fun unzip(ctx: HarnessCtx, archive: Path): ToolExecResult {
        val target = archive.parent!! / archive.name.substringBeforeLast('.')
        val root = Workspace.forWrite(ctx, target.toString()).toPath()
        FileSystem.SYSTEM.createDirectories(root)
        var count = 0
        FileSystem.SYSTEM.openZip(archive).use { zipFs ->
            fun walk(dir: Path) {
                zipFs.list(dir).forEach { path ->
                    val out = root / path.toString().removePrefix("/")
                    if (!Workspace.isInside(out.toString(), root.toString())) return@forEach
                    if (zipFs.metadata(path).isDirectory == true) {
                        FileSystem.SYSTEM.createDirectories(out)
                        walk(path)
                    } else {
                        out.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
                        FileSystem.SYSTEM.write(out) { write(zipFs.read(path) { readByteArray() }) }
                        count++
                    }
                }
            }
            walk("/".toPath())
        }
        return ToolExecResult("Unpacked $count file(s) into ${Workspace.display(ctx, root.toString())}.")
    }
}

internal object FileObservations {

    private val seen = LinkedHashSet<String>()
    private var conversation: Long = -1L

    fun track(conversationId: Long) {
        if (conversationId == conversation) return
        conversation = conversationId
        seen.clear()
    }

    fun note(path: String) {
        seen.add(key(path))
    }

    fun seen(path: String): Boolean = seen.contains(key(path))

    private fun key(path: String): String = path
}
