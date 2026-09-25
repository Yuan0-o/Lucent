package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import org.json.JSONObject
import java.io.File

internal fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

internal fun shellSyntax(value: String): String? {
    val tokens = listOf(";", "&&", "||", "|", "`", "\$(", ">", "<")
    for (token in tokens) {
        if (value.contains(token)) {
            return "Blocked: git arguments must be plain git arguments, never shell syntax (found $token). Nothing was run."
        }
    }
    return null
}

internal fun blocked(arguments: String): String? {
    val text = arguments.trim()
    if (text.isEmpty()) return "No git arguments were given, so nothing was run."
    shellSyntax(text)?.let { return it }
    val tokens = text.split(Regex("\\s+"))
    val command = tokens.first()
    if (command == "filter-branch") {
        return "Blocked: git filter-branch rewrites history. Nothing was run."
    }
    if (tokens.any { it == "-f" || it.startsWith("--force") }) {
        return "Blocked: force is never allowed here. Nothing was run; ask the user if it is really needed."
    }
    if (tokens.any { it == "--hard" }) {
        return "Blocked: --hard throws work away. Nothing was run; use git_restore or ask the user first."
    }
    if (command == "clean" && tokens.drop(1).any { it.startsWith("-") && it.contains('f') }) {
        return "Blocked: git clean -f deletes untracked files for good. Nothing was run."
    }
    if (command == "update-ref" && tokens.any { it == "-d" || it == "--delete" }) {
        return "Blocked: git update-ref -d removes refs. Nothing was run."
    }
    if (command == "push" && tokens.any { it == "-d" || it == "--delete" }) {
        return "Blocked: push --delete removes a remote branch. Nothing was run; ask the user first."
    }
    if (command == "reflog" && tokens.getOrNull(1) == "expire") {
        return "Blocked: git reflog expire destroys the recovery history. Nothing was run."
    }
    if (command == "gc" && tokens.any { it.startsWith("--prune") }) {
        return "Blocked: git gc --prune=now drops unreachable objects for good. Nothing was run."
    }
    return null
}

internal fun relativePathProblem(value: String): String? {
    val clean = value.trim()
    if (clean.isEmpty()) return "A path is required."
    if (clean.startsWith("/") || clean.startsWith("~") || (clean.length > 1 && clean[1] == ':')) {
        return "$clean must be a relative path inside the repository."
    }
    if (clean.split("/").any { it == ".." }) return "$clean must stay inside the repository."
    if (clean.startsWith("-")) return "$clean is not a path."
    return shellSyntax(clean)
}

internal fun revisionProblem(value: String, label: String): String? {
    val clean = value.trim()
    if (clean.isEmpty()) return "A $label is required."
    if (clean.startsWith("-")) return "A $label cannot start with a dash."
    if (clean.any { it.isWhitespace() }) return "A $label cannot contain spaces."
    return shellSyntax(clean)
}

internal fun urlProblem(value: String): String? {
    val clean = value.trim()
    if (clean.isEmpty()) return "A url is required."
    if (clean.startsWith("-")) return "A url cannot start with a dash."
    if (clean.any { it.isWhitespace() }) return "A url cannot contain spaces."
    return shellSyntax(clean)
}

internal fun splitPathList(raw: String): List<String> = raw.split(Regex("\\s+")).filter { it.isNotBlank() }

internal fun pathListProblem(paths: List<String>): String? {
    if (paths.isEmpty()) return "At least one relative path is required."
    for (path in paths) {
        relativePathProblem(path)?.let { return it }
    }
    return null
}

internal fun quotedPaths(paths: List<String>): String = paths.joinToString(" ") { quote(it) }

private val READ_ONLY_COMMANDS = setOf("status", "diff", "log", "show", "blame")

internal fun readOnlyArguments(arguments: String): Boolean {
    val command = arguments.trim().split(Regex("\\s+")).firstOrNull() ?: return false
    return command in READ_ONLY_COMMANDS
}

object GitTools : HarnessGroupTools {

    override val group = HarnessGroup.GIT

    private const val SHELL_MESSAGE =
        "Git needs a shell. Install the git plugin or enable the shell backend in Settings."
    private const val NO_OUTPUT = "(git returned no output)"
    private const val QUICK_TIMEOUT = 120
    private const val NETWORK_TIMEOUT = 300
    private const val CLONE_TIMEOUT = 600
    private const val BLAME_LINES = 200

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "git_status",
            group = group,
            permission = HarnessPermission.READ,
            description = "Show the state of the repository: git status --porcelain=v1 -b plus a count of staged, " +
                "modified, untracked and conflicted files. Arguments: repo (optional repository path, default the " +
                "workspace root).",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false)
            )
        ),
        HarnessTool(
            name = "git_diff",
            group = group,
            permission = HarnessPermission.READ,
            description = "Show uncommitted changes. Arguments: repo (optional), path (optional file or directory " +
                "inside the repository), staged (true to compare the index with HEAD), stat (true for a summary " +
                "instead of the full patch).",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("path", "Limit the diff to this relative path", false),
                HarnessSchema.flag("staged", "Diff the staged index instead of the working tree", false),
                HarnessSchema.flag("stat", "Show a diffstat instead of the full patch", false)
            )
        ),
        HarnessTool(
            name = "git_log",
            group = group,
            permission = HarnessPermission.READ,
            description = "List recent commits, one line each with decorations. Arguments: repo (optional), limit " +
                "(how many commits, default 20), path (optional relative file or directory to follow).",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.number("limit", "How many commits to show, default 20", false),
                HarnessSchema.text("path", "Only commits touching this relative path", false)
            )
        ),
        HarnessTool(
            name = "git_show",
            group = group,
            permission = HarnessPermission.READ,
            description = "Show one commit or object with its diffstat and patch. Arguments: repo (optional), ref " +
                "(required: HEAD, HEAD~2, a commit hash, a tag or a branch name).",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("ref", "Commit, tag or branch to show")
            )
        ),
        HarnessTool(
            name = "git_branch",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Work with branches. Arguments: repo (optional), action (list, create, switch or delete), " +
                "name (branch name for create, switch and delete), force (delete only: true allows deleting an " +
                "unmerged branch and needs Delete set to Allow in Settings).",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("action", "One of list, create, switch, delete"),
                HarnessSchema.text("name", "Branch name", false),
                HarnessSchema.flag("force", "Delete an unmerged branch (needs Delete set to Allow)", false)
            )
        ),
        HarnessTool(
            name = "git_checkout",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Switch to a ref, or bring files back from it. Arguments: repo (optional), ref (required), " +
                "paths (optional space-separated relative paths; with paths only those files are taken from ref, " +
                "without them the whole working tree moves to ref).",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("ref", "Commit, tag or branch to check out"),
                HarnessSchema.text("paths", "Relative paths to restore from ref", false)
            )
        ),
        HarnessTool(
            name = "git_add",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Stage changes for the next commit. Arguments: repo (optional), paths (required string with " +
                "one or more space-separated relative paths inside the repository, for example " +
                "\"src/main/kotlin app/build.gradle.kts\").",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("paths", "One or more relative paths, space separated")
            )
        ),
        HarnessTool(
            name = "git_commit",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Create a commit from what is staged. Arguments: repo (optional), message (required commit " +
                "message), all (true to also stage tracked changes with -a).",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("message", "Commit message"),
                HarnessSchema.flag("all", "Also stage tracked modifications with -a", false)
            )
        ),
        HarnessTool(
            name = "git_restore",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Throw away working-tree changes or unstage files. Arguments: repo (optional), paths " +
                "(optional space-separated relative paths, default everything), staged (true to restore the index " +
                "with git restore --staged).",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("paths", "Relative paths to restore, default all of them", false),
                HarnessSchema.flag("staged", "Restore the staged index instead of the working tree", false)
            )
        ),
        HarnessTool(
            name = "git_stash",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Shelve or bring back work in progress. Arguments: repo (optional), action (push, pop, list " +
                "or drop), message (optional label used by push).",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("action", "One of push, pop, list, drop"),
                HarnessSchema.text("message", "Label for the stash entry", false)
            )
        ),
        HarnessTool(
            name = "git_merge",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Merge a branch or ref into the current branch. Arguments: repo (optional), ref (required), " +
                "no_ff (true to force a merge commit). Refuses to start when the working tree has uncommitted changes.",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("ref", "Branch, tag or commit to merge in"),
                HarnessSchema.flag("no_ff", "Always create a merge commit", false)
            )
        ),
        HarnessTool(
            name = "git_rebase",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Rebase the current branch. Arguments: repo (optional), action (start, continue, abort or " +
                "skip), onto (optional ref to rebase onto). start only runs when the Git permission is set to Allow " +
                "in Settings.",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("action", "One of start, continue, abort, skip"),
                HarnessSchema.text("onto", "Ref to rebase onto, default the tracked upstream", false)
            )
        ),
        HarnessTool(
            name = "git_remote",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Manage remotes. Arguments: repo (optional), action (list, add, remove or set-url), name " +
                "(remote name, needed by add, remove and set-url), url (needed by add and set-url).",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("action", "One of list, add, remove, set-url"),
                HarnessSchema.text("name", "Remote name", false),
                HarnessSchema.text("url", "Remote url", false)
            )
        ),
        HarnessTool(
            name = "git_clone",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Clone a repository into the workspace. Arguments: url (required https, ssh or git url; " +
                "urls containing shell metacharacters are refused), directory (optional target folder inside the " +
                "workspace, default a folder named after the repository).",
            params = listOf(
                HarnessSchema.text("url", "Repository url to clone"),
                HarnessSchema.text("directory", "Target folder inside the workspace", false)
            )
        ),
        HarnessTool(
            name = "git_fetch",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Download refs and objects without touching the working tree. Arguments: repo (optional), " +
                "remote (optional remote name; without it the tool runs git fetch --all --prune).",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("remote", "Only fetch this remote", false)
            )
        ),
        HarnessTool(
            name = "git_pull",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Fetch and integrate the upstream branch. Arguments: repo (optional), remote (optional " +
                "remote name), rebase (true to rebase instead of the default --ff-only).",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("remote", "Remote to pull from", false),
                HarnessSchema.flag("rebase", "Rebase local commits instead of fast-forward only", false)
            )
        ),
        HarnessTool(
            name = "git_push",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Upload commits to a remote. Arguments: repo (optional), remote (optional), branch " +
                "(optional), set_upstream (true to add -u when publishing a new branch). Force is never allowed.",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("remote", "Remote to push to", false),
                HarnessSchema.text("branch", "Branch to push", false),
                HarnessSchema.flag("set_upstream", "Add -u and track the pushed branch", false)
            )
        ),
        HarnessTool(
            name = "git_blame",
            group = group,
            permission = HarnessPermission.READ,
            description = "Show which commit last changed each line of a file. Arguments: repo (optional), path " +
                "(required relative path inside the repository), lines (optional range such as 10,40 or 10,+30; " +
                "without it blame output stops after 200 lines).",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("path", "Relative path of the file to blame"),
                HarnessSchema.text("lines", "Line range such as 10,40 or 10,+30", false)
            )
        ),
        HarnessTool(
            name = "git_reset",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Move the branch pointer without touching files. Arguments: repo (optional), mode (soft or " +
                "mixed only), ref (optional, default HEAD). --hard is refused: use git_restore for working-tree " +
                "changes or ask the user.",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("mode", "soft or mixed"),
                HarnessSchema.text("ref", "Commit to reset to, default HEAD", false)
            )
        ),
        HarnessTool(
            name = "git_init",
            group = group,
            permission = HarnessPermission.GIT,
            description = "Create a new repository with git init when the directory is not one already. Arguments: " +
                "repo (optional directory, default the workspace root; the folder is created when missing).",
            params = listOf(
                HarnessSchema.text("repo", "Directory to initialise, default the workspace root", false)
            )
        ),
        HarnessTool(
            name = "git_apply_patch",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Apply a unified diff to the working tree. Arguments: repo (optional), patch (required " +
                "patch text), reverse (true to undo a patch with git apply -R). The patch is written to a temporary " +
                "file and removed afterwards.",
            params = listOf(
                HarnessSchema.text("repo", "Repository directory, default the workspace root", false),
                HarnessSchema.text("patch", "Unified diff text to apply"),
                HarnessSchema.flag("reverse", "Undo the patch with -R", false)
            )
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = when (name) {
        "git_status" -> status(ctx, args)
        "git_diff" -> diff(ctx, args)
        "git_log" -> log(ctx, args)
        "git_show" -> show(ctx, args)
        "git_branch" -> branch(ctx, args)
        "git_checkout" -> checkout(ctx, args)
        "git_add" -> add(ctx, args)
        "git_commit" -> commit(ctx, args)
        "git_restore" -> restore(ctx, args)
        "git_stash" -> stash(ctx, args)
        "git_merge" -> merge(ctx, args)
        "git_rebase" -> rebase(ctx, args)
        "git_remote" -> remote(ctx, args)
        "git_clone" -> cloneRepo(ctx, args)
        "git_fetch" -> fetch(ctx, args)
        "git_pull" -> pull(ctx, args)
        "git_push" -> push(ctx, args)
        "git_blame" -> blame(ctx, args)
        "git_reset" -> reset(ctx, args)
        "git_init" -> initRepo(ctx, args)
        "git_apply_patch" -> applyPatch(ctx, args)
        else -> null
    }

    private suspend fun git(
        ctx: HarnessCtx,
        repo: String,
        arguments: String,
        timeoutSeconds: Int = QUICK_TIMEOUT
    ): ToolExecResult {
        blocked(arguments)?.let { return ToolExecResult(it, success = false) }
        if (!HarnessRuntime.shellReady()) return ToolExecResult(SHELL_MESSAGE, success = false)
        val directory = try {
            repository(ctx, repo, arguments)
        } catch (e: HarnessError) {
            return ToolExecResult(e.message ?: "That repository is not reachable.", success = false)
        }
        val command = "git -C ${quote(directory.path)} $arguments"
        val outcome = try {
            HarnessRuntime.runShell(command, directory, timeoutSeconds, emptyMap())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            return ToolExecResult(
                "git could not start: ${t.message ?: t::class.simpleName ?: "unknown error"}",
                success = false
            )
        }
        val body = buildString {
            append(outcome.text.ifBlank { NO_OUTPUT })
            if (outcome.timedOut) append("\ngit timed out after $timeoutSeconds seconds.")
            if (outcome.exitCode != 0) append("\nexit code ${outcome.exitCode}")
        }
        return ToolExecResult(ctx.limit(body), success = outcome.ok && !outcome.timedOut)
    }

    private fun repository(ctx: HarnessCtx, repo: String, arguments: String): File {
        val clean = repo.trim()
        if (clean.isEmpty()) return HarnessRuntime.workspace()
        return if (readOnlyArguments(arguments)) Workspace.forRead(ctx, clean) else Workspace.forWrite(ctx, clean)
    }

    private fun repoOf(args: JSONObject): String = args.optString("repo", "").trim()

    private fun textOf(args: JSONObject, key: String): String = args.optString(key, "").trim()

    private suspend fun status(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val result = git(ctx, repoOf(args), "status --porcelain=v1 -b")
        if (!result.success) return result
        return ToolExecResult(statusSummary(result.summary) + "\n\n" + result.summary)
    }

    private fun statusSummary(text: String): String {
        var branch = ""
        var staged = 0
        var modified = 0
        var untracked = 0
        var conflicts = 0
        text.lines().forEach { line ->
            if (line.startsWith("##")) {
                branch = line.removePrefix("##").trim()
                return@forEach
            }
            if (line.length < 2) return@forEach
            val index = line[0]
            val work = line[1]
            when {
                index == '?' -> untracked++
                index == 'U' || work == 'U' -> conflicts++
                index == 'A' && work == 'A' -> conflicts++
                index == 'D' && work == 'D' -> conflicts++
                else -> {
                    if (index != ' ') staged++
                    if (work != ' ') modified++
                }
            }
        }
        val head = if (branch.isEmpty()) "No branch information." else "Branch: $branch"
        return "$head\nStaged: $staged  Modified: $modified  Untracked: $untracked  Conflicts: $conflicts"
    }

    private suspend fun diff(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val paths = splitPathList(textOf(args, "path"))
        val command = StringBuilder("diff")
        if (args.optBoolean("stat", false)) command.append(" --stat")
        if (args.optBoolean("staged", false)) command.append(" --staged")
        if (paths.isNotEmpty()) {
            pathListProblem(paths)?.let { return ToolExecResult(it, success = false) }
            command.append(" -- ").append(quotedPaths(paths))
        }
        return git(ctx, repoOf(args), command.toString())
    }

    private suspend fun log(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val limit = args.optInt("limit", 20).coerceIn(1, 500)
        val paths = splitPathList(textOf(args, "path"))
        val command = StringBuilder("log --oneline --decorate -n $limit")
        if (paths.isNotEmpty()) {
            pathListProblem(paths)?.let { return ToolExecResult(it, success = false) }
            command.append(" -- ").append(quotedPaths(paths))
        }
        return git(ctx, repoOf(args), command.toString())
    }

    private suspend fun show(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val ref = textOf(args, "ref")
        revisionProblem(ref, "ref")?.let { return ToolExecResult(it, success = false) }
        return git(ctx, repoOf(args), "show --stat --patch ${quote(ref)}")
    }

    private suspend fun branch(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val action = textOf(args, "action").lowercase().ifEmpty { "list" }
        val name = textOf(args, "name")
        return when (action) {
            "list" -> git(ctx, repoOf(args), "branch --list")
            "create" -> {
                revisionProblem(name, "branch name")?.let { return ToolExecResult(it, success = false) }
                git(ctx, repoOf(args), "switch -c ${quote(name)}")
            }
            "switch" -> {
                revisionProblem(name, "branch name")?.let { return ToolExecResult(it, success = false) }
                git(ctx, repoOf(args), "switch ${quote(name)}")
            }
            "delete" -> deleteBranch(ctx, args, name)
            else -> ToolExecResult("action must be list, create, switch or delete.", success = false)
        }
    }

    private suspend fun deleteBranch(ctx: HarnessCtx, args: JSONObject, name: String): ToolExecResult {
        revisionProblem(name, "branch name")?.let { return ToolExecResult(it, success = false) }
        val force = args.optBoolean("force", false)
        if (!force) return git(ctx, repoOf(args), "branch -d ${quote(name)}")
        if (ctx.config.approvalFor(HarnessPermission.DELETE) != Approval.ALLOW) {
            return ToolExecResult(
                "Force-deleting $name needs the Delete permission policy set to Allow in Settings. Nothing was run.",
                success = false
            )
        }
        return git(ctx, repoOf(args), "branch -D ${quote(name)}")
    }

    private suspend fun checkout(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val ref = textOf(args, "ref")
        revisionProblem(ref, "ref")?.let { return ToolExecResult(it, success = false) }
        val paths = splitPathList(textOf(args, "paths"))
        if (paths.isEmpty()) return git(ctx, repoOf(args), "checkout ${quote(ref)}")
        pathListProblem(paths)?.let { return ToolExecResult(it, success = false) }
        return git(ctx, repoOf(args), "checkout ${quote(ref)} -- " + quotedPaths(paths))
    }

    private suspend fun add(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val paths = splitPathList(textOf(args, "paths"))
        pathListProblem(paths)?.let { return ToolExecResult(it, success = false) }
        return git(ctx, repoOf(args), "add -- " + quotedPaths(paths))
    }

    private suspend fun commit(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val message = args.optString("message", "").trim()
        if (message.isEmpty()) return ToolExecResult("A commit message is required.", success = false)
        val all = if (args.optBoolean("all", false)) "-a " else ""
        return git(ctx, repoOf(args), "commit $all-m ${quote(message)}")
    }

    private suspend fun restore(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val paths = splitPathList(textOf(args, "paths"))
        val staged = args.optBoolean("staged", false)
        val target = if (paths.isEmpty()) "." else quotedPaths(paths)
        if (paths.isNotEmpty()) pathListProblem(paths)?.let { return ToolExecResult(it, success = false) }
        val prefix = if (staged) "restore --staged -- " else "restore -- "
        return git(ctx, repoOf(args), prefix + target)
    }

    private suspend fun stash(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val action = textOf(args, "action").lowercase().ifEmpty { "push" }
        val message = args.optString("message", "").trim()
        return when (action) {
            "push" -> {
                val label = if (message.isEmpty()) "" else " -m ${quote(message)}"
                git(ctx, repoOf(args), "stash push$label")
            }
            "pop" -> git(ctx, repoOf(args), "stash pop")
            "list" -> git(ctx, repoOf(args), "stash list")
            "drop" -> git(ctx, repoOf(args), "stash drop")
            else -> ToolExecResult("action must be push, pop, list or drop.", success = false)
        }
    }

    private suspend fun merge(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val ref = textOf(args, "ref")
        revisionProblem(ref, "ref")?.let { return ToolExecResult(it, success = false) }
        val repo = repoOf(args)
        val dirty = git(ctx, repo, "status --porcelain")
        if (!dirty.success) return dirty
        val changes = dirty.summary.lines().filter { it.isNotBlank() && it != NO_OUTPUT }
        if (changes.isNotEmpty()) {
            return ToolExecResult(
                "The working tree has ${changes.size} uncommitted change(s). Commit or stash them before merging.",
                success = false
            )
        }
        val noFf = if (args.optBoolean("no_ff", false)) " --no-ff" else ""
        val result = git(ctx, repo, "merge$noFf ${quote(ref)}")
        if (result.success) return result
        val conflict = result.summary.contains("CONFLICT") || result.summary.contains("Automatic merge failed")
        val head = if (conflict) {
            "Merge conflict in $ref. Resolve the files listed above, stage them with git_add and finish with git_commit."
        } else {
            "Merge of $ref failed."
        }
        return ToolExecResult("$head\n\n${result.summary}", success = false)
    }

    private suspend fun rebase(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val action = textOf(args, "action").lowercase().ifEmpty { "start" }
        val onto = textOf(args, "onto")
        return when (action) {
            "start" -> {
                if (ctx.config.approvalFor(HarnessPermission.GIT) != Approval.ALLOW) {
                    return ToolExecResult(
                        "High-risk git operations are blocked by the permission policy: set Git to Allow in " +
                            "Settings before rebasing. Nothing was run.",
                        success = false
                    )
                }
                if (onto.isEmpty()) return git(ctx, repoOf(args), "rebase")
                revisionProblem(onto, "ref")?.let { return ToolExecResult(it, success = false) }
                git(ctx, repoOf(args), "rebase ${quote(onto)}")
            }
            "continue" -> git(ctx, repoOf(args), "rebase --continue")
            "abort" -> git(ctx, repoOf(args), "rebase --abort")
            "skip" -> git(ctx, repoOf(args), "rebase --skip")
            else -> ToolExecResult("action must be start, continue, abort or skip.", success = false)
        }
    }

    private suspend fun remote(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val action = textOf(args, "action").lowercase().ifEmpty { "list" }
        val name = textOf(args, "name")
        val url = textOf(args, "url")
        return when (action) {
            "list" -> git(ctx, repoOf(args), "remote -v")
            "add" -> {
                revisionProblem(name, "remote name")?.let { return ToolExecResult(it, success = false) }
                urlProblem(url)?.let { return ToolExecResult(it, success = false) }
                git(ctx, repoOf(args), "remote add ${quote(name)} ${quote(url)}")
            }
            "remove" -> {
                revisionProblem(name, "remote name")?.let { return ToolExecResult(it, success = false) }
                git(ctx, repoOf(args), "remote remove ${quote(name)}")
            }
            "set-url" -> {
                revisionProblem(name, "remote name")?.let { return ToolExecResult(it, success = false) }
                urlProblem(url)?.let { return ToolExecResult(it, success = false) }
                git(ctx, repoOf(args), "remote set-url ${quote(name)} ${quote(url)}")
            }
            else -> ToolExecResult("action must be list, add, remove or set-url.", success = false)
        }
    }

    private suspend fun cloneRepo(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val url = textOf(args, "url")
        urlProblem(url)?.let { return ToolExecResult(it, success = false) }
        val raw = textOf(args, "directory")
        val directory = try {
            if (raw.isEmpty()) "" else Workspace.forWrite(ctx, raw).path
        } catch (e: HarnessError) {
            return ToolExecResult(e.message ?: "That directory is outside the workspace.", success = false)
        }
        val target = if (directory.isEmpty()) "" else " ${quote(directory)}"
        return git(ctx, "", "clone ${quote(url)}$target", CLONE_TIMEOUT)
    }

    private suspend fun fetch(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val remote = textOf(args, "remote")
        if (remote.isEmpty()) return git(ctx, repoOf(args), "fetch --all --prune", NETWORK_TIMEOUT)
        revisionProblem(remote, "remote name")?.let { return ToolExecResult(it, success = false) }
        return git(ctx, repoOf(args), "fetch --prune ${quote(remote)}", NETWORK_TIMEOUT)
    }

    private suspend fun pull(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val remote = textOf(args, "remote")
        val mode = if (args.optBoolean("rebase", false)) "--rebase" else "--ff-only"
        if (remote.isEmpty()) return git(ctx, repoOf(args), "pull $mode", NETWORK_TIMEOUT)
        revisionProblem(remote, "remote name")?.let { return ToolExecResult(it, success = false) }
        return git(ctx, repoOf(args), "pull $mode ${quote(remote)}", NETWORK_TIMEOUT)
    }

    private suspend fun push(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val remote = textOf(args, "remote")
        val branch = textOf(args, "branch")
        val upstream = args.optBoolean("set_upstream", false)
        if (remote.isEmpty() && branch.isEmpty()) return git(ctx, repoOf(args), "push", NETWORK_TIMEOUT)
        if (remote.isEmpty()) return ToolExecResult("A remote is required when a branch is given.", success = false)
        revisionProblem(remote, "remote name")?.let { return ToolExecResult(it, success = false) }
        if (branch.isEmpty()) {
            if (upstream) return ToolExecResult("set_upstream needs both remote and branch.", success = false)
            return git(ctx, repoOf(args), "push ${quote(remote)}", NETWORK_TIMEOUT)
        }
        revisionProblem(branch, "branch name")?.let { return ToolExecResult(it, success = false) }
        val flag = if (upstream) "-u " else ""
        return git(ctx, repoOf(args), "push $flag${quote(remote)} ${quote(branch)}", NETWORK_TIMEOUT)
    }

    private suspend fun blame(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val path = textOf(args, "path")
        relativePathProblem(path)?.let { return ToolExecResult(it, success = false) }
        val lines = textOf(args, "lines")
        if (lines.isEmpty()) {
            val result = git(ctx, repoOf(args), "blame -- ${quote(path)}")
            if (!result.success) return result
            val all = result.summary.lines()
            if (all.size <= BLAME_LINES) return result
            val kept = all.take(BLAME_LINES).joinToString("\n")
            return ToolExecResult("$kept\n… blame output stopped at $BLAME_LINES lines.")
        }
        if (!lines.matches(Regex("\\d+(,(\\+)?\\d+)?"))) {
            return ToolExecResult("lines must look like 10,40 or 10,+30.", success = false)
        }
        return git(ctx, repoOf(args), "blame -L $lines -- ${quote(path)}")
    }

    private suspend fun reset(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val mode = textOf(args, "mode").lowercase().ifEmpty { "mixed" }
        if (mode == "hard" || mode == "--hard") {
            return ToolExecResult(
                "git_reset never runs --hard because it throws work away. Use git_restore to discard working-tree " +
                    "changes, or ask the user first. Nothing was run.",
                success = false
            )
        }
        if (mode != "soft" && mode != "mixed") {
            return ToolExecResult("mode must be soft or mixed.", success = false)
        }
        val ref = textOf(args, "ref")
        if (ref.isEmpty()) return git(ctx, repoOf(args), "reset --$mode")
        revisionProblem(ref, "ref")?.let { return ToolExecResult(it, success = false) }
        return git(ctx, repoOf(args), "reset --$mode ${quote(ref)}")
    }

    private suspend fun initRepo(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val raw = repoOf(args)
        val directory = try {
            if (raw.isEmpty()) HarnessRuntime.workspace() else Workspace.forWrite(ctx, raw)
        } catch (e: HarnessError) {
            return ToolExecResult(e.message ?: "That directory is outside the workspace.", success = false)
        }
        if (!directory.exists()) directory.mkdirs()
        if (File(directory, ".git").exists()) {
            return ToolExecResult("${directory.path} is already a git repository.")
        }
        return git(ctx, directory.path, "init")
    }

    private suspend fun applyPatch(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val patch = args.optString("patch", "")
        if (patch.isBlank()) return ToolExecResult("There is no patch text to apply.", success = false)
        if (!HarnessRuntime.shellReady()) return ToolExecResult(SHELL_MESSAGE, success = false)
        val file = File(HarnessRuntime.subDir("tmp"), "lucent-patch-${System.currentTimeMillis()}.patch")
        return try {
            file.writeText(patch)
            val reverse = if (args.optBoolean("reverse", false)) "-R " else ""
            git(ctx, repoOf(args), "apply $reverse${quote(file.path)}")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            ToolExecResult("The patch could not be applied: ${t.message ?: t::class.simpleName}", success = false)
        } finally {
            file.delete()
        }
    }
}
