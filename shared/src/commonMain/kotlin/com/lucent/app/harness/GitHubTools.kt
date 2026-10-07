package com.lucent.app.harness

import kotlin.io.encoding.Base64
import com.lucent.app.network.ToolExecResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

object GitHubTools : HarnessGroupTools {

    override val group = HarnessGroup.GITHUB

    const val NO_TOKEN = "No GitHub token is set. Add one in Settings → Agent → GitHub."

    private const val DEFAULT_API = "https://api.github.com"
    private const val API_VERSION = "2022-11-28"
    private const val JSON_ACCEPT = "application/vnd.github+json"
    private const val DIFF_ACCEPT = "application/vnd.github.diff"
    private const val MATCH_ACCEPT = "application/vnd.github.text-match+json"
    private const val LOG_TAIL = 8000
    private const val MAX_TEXT_FILE = 256 * 1024

    private val ISSUE_ACTIONS = listOf("list", "get", "create", "update", "comment", "close")
    private val PULL_ACTIONS = listOf("list", "get", "create", "update", "comment", "diff", "files", "merge")
    private val BRANCH_ACTIONS = listOf("list", "get", "create", "delete")
    private val SEARCH_KINDS = listOf("code", "repos", "issues", "commits")
    private val RUN_ACTIONS = listOf("runs", "run", "jobs", "logs", "dispatch", "cancel")
    private val RELEASE_ACTIONS = listOf("list", "get", "create")

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "github_repo",
            group = group,
            permission = HarnessPermission.READ,
            description = "Read one GitHub repository's overview. Arguments: owner, repo (for example owner " +
                "\"octocat\", repo \"hello-world\"). Returns the description, stars, forks, open issues, language, " +
                "default branch, size and last push time. Call it before working on a repository so you use the real " +
                "default branch.",
            params = listOf(
                HarnessSchema.text("owner", "The account or organisation that owns the repository"),
                HarnessSchema.text("repo", "The repository name")
            )
        ),
        HarnessTool(
            name = "github_contents",
            group = group,
            permission = HarnessPermission.READ,
            description = "List a folder or read one text file from a GitHub repository. Arguments: owner, repo, path " +
                "(folder or file path, leave out for the root), ref (branch, tag or commit sha). A folder comes back " +
                "as a listing; a text file under 256 KiB is decoded for you, and anything larger or binary gives you " +
                "its download URL instead.",
            params = listOf(
                HarnessSchema.text("owner", "The repository owner"),
                HarnessSchema.text("repo", "The repository name"),
                HarnessSchema.text("path", "Folder or file path inside the repository", false),
                HarnessSchema.text("ref", "Branch, tag or commit sha", false)
            )
        ),
        HarnessTool(
            name = "github_issues",
            group = group,
            permission = HarnessPermission.GITHUB,
            description = "Work with GitHub issues. Arguments: owner, repo, action (list, get, create, update, " +
                "comment, close), number (the issue number for get, update, comment and close), state (open, closed " +
                "or all for list), labels (comma separated), title, body, comment (the comment text). get also pulls " +
                "the latest comments so you can read the discussion.",
            params = listOf(
                HarnessSchema.text("owner", "The repository owner"),
                HarnessSchema.text("repo", "The repository name"),
                HarnessSchema.text("action", "One of: list, get, create, update, comment, close"),
                HarnessSchema.number("number", "The issue number", false),
                HarnessSchema.text("state", "open, closed or all", false),
                HarnessSchema.text("labels", "Comma separated labels", false),
                HarnessSchema.text("title", "Issue title", false),
                HarnessSchema.text("body", "Issue body text", false),
                HarnessSchema.text("comment", "Comment text for the comment action", false)
            )
        ),
        HarnessTool(
            name = "github_pulls",
            group = group,
            permission = HarnessPermission.GITHUB,
            description = "Work with GitHub pull requests. Arguments: owner, repo, action (list, get, create, update, " +
                "comment, diff, files, merge), number, state, title, body, head (source branch), base (target branch), " +
                "comment. diff returns the unified diff so you can review the change, files lists what changed, and " +
                "merge merges the pull request.",
            params = listOf(
                HarnessSchema.text("owner", "The repository owner"),
                HarnessSchema.text("repo", "The repository name"),
                HarnessSchema.text("action", "One of: list, get, create, update, comment, diff, files, merge"),
                HarnessSchema.number("number", "The pull request number", false),
                HarnessSchema.text("state", "open, closed or all", false),
                HarnessSchema.text("title", "Pull request title", false),
                HarnessSchema.text("body", "Pull request body text", false),
                HarnessSchema.text("head", "Source branch, for create", false),
                HarnessSchema.text("base", "Target branch, for create", false),
                HarnessSchema.text("comment", "Comment text for the comment action", false)
            )
        ),
        HarnessTool(
            name = "github_branches",
            group = group,
            permission = HarnessPermission.GITHUB,
            description = "Work with a GitHub repository's branches. Arguments: owner, repo, action (list, get, " +
                "create, delete), name (the branch name), from (the sha, tag or branch the new branch starts at). " +
                "Deleting a branch only runs when the GitHub approval policy is set to Allow; otherwise it refuses " +
                "and changes nothing.",
            params = listOf(
                HarnessSchema.text("owner", "The repository owner"),
                HarnessSchema.text("repo", "The repository name"),
                HarnessSchema.text("action", "One of: list, get, create, delete"),
                HarnessSchema.text("name", "The branch name", false),
                HarnessSchema.text("from", "The sha, tag or branch to start a new branch at", false)
            )
        ),
        HarnessTool(
            name = "github_commits",
            group = group,
            permission = HarnessPermission.READ,
            description = "List commits on a GitHub repository. Arguments: owner, repo, path (only commits touching " +
                "this file or folder), branch (branch name or sha), limit (how many, default 20, maximum 100). Each " +
                "line shows the short sha, author, date and the first line of the commit message.",
            params = listOf(
                HarnessSchema.text("owner", "The repository owner"),
                HarnessSchema.text("repo", "The repository name"),
                HarnessSchema.text("path", "Only commits touching this path", false),
                HarnessSchema.text("branch", "Branch name or sha", false),
                HarnessSchema.number("limit", "How many commits to list, default 20", false)
            )
        ),
        HarnessTool(
            name = "github_search",
            group = group,
            permission = HarnessPermission.READ,
            description = "Search GitHub. Arguments: query (GitHub search syntax, for example " +
                "\"repo:owner/name language:kotlin retry\"), kind (code, repos, issues or commits; default code). " +
                "Use kind repos to find a repository, code to find real usage of an API, issues to find discussions, " +
                "and commits to find a change.",
            params = listOf(
                HarnessSchema.text("query", "The GitHub search query"),
                HarnessSchema.text("kind", "One of: code, repos, issues, commits", false)
            )
        ),
        HarnessTool(
            name = "github_actions",
            group = group,
            permission = HarnessPermission.GITHUB,
            description = "Work with GitHub Actions runs. Arguments: owner, repo, action (runs, run, jobs, logs, " +
                "dispatch, cancel), run_id, workflow (file name or id, for dispatch), ref (branch, for dispatch), " +
                "job_id (for logs). logs returns the tail of one job's log so you can read why CI failed; find the " +
                "job_id with the jobs action first.",
            params = listOf(
                HarnessSchema.text("owner", "The repository owner"),
                HarnessSchema.text("repo", "The repository name"),
                HarnessSchema.text("action", "One of: runs, run, jobs, logs, dispatch, cancel"),
                HarnessSchema.number("run_id", "The workflow run id", false),
                HarnessSchema.text("workflow", "Workflow file name or id, for dispatch", false),
                HarnessSchema.text("ref", "Branch to run the workflow on", false),
                HarnessSchema.number("job_id", "The job id, for logs", false)
            )
        ),
        HarnessTool(
            name = "github_releases",
            group = group,
            permission = HarnessPermission.GITHUB,
            description = "Work with GitHub releases. Arguments: owner, repo, action (list, get, create), tag (the " +
                "tag name, for get and create), name (the release title), body (release notes), draft (true to keep " +
                "it a draft). get and create need tag; create also uses name and body when you give them.",
            params = listOf(
                HarnessSchema.text("owner", "The repository owner"),
                HarnessSchema.text("repo", "The repository name"),
                HarnessSchema.text("action", "One of: list, get, create"),
                HarnessSchema.text("tag", "The tag name", false),
                HarnessSchema.text("name", "The release title", false),
                HarnessSchema.text("body", "The release notes", false),
                HarnessSchema.flag("draft", "true to keep the release a draft")
            )
        ),
        HarnessTool(
            name = "github_user",
            group = group,
            permission = HarnessPermission.READ,
            description = "Show the GitHub account this app is signed in as, with its name, profile link and the " +
                "remaining API rate limit. No arguments. Use it to check the token still works or to see whether " +
                "searches are running out of quota before a long job."
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JsonObject): ToolExecResult? {
        if (tools.none { it.name == name }) return null
        if (token().isEmpty()) return ToolExecResult(NO_TOKEN, success = false)
        return try {
            when (name) {
                "github_repo" -> repo(args)
                "github_contents" -> contents(args)
                "github_issues" -> issues(args)
                "github_pulls" -> pulls(args)
                "github_branches" -> branches(args)
                "github_commits" -> commits(args)
                "github_search" -> search(args)
                "github_actions" -> actions(args)
                "github_releases" -> releases(args)
                "github_user" -> user()
                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            ToolExecResult("$name failed: ${t.message ?: t::class.java.simpleName}", success = false)
        }
    }

    private fun token(): String = HarnessRuntime.config().githubToken.trim()

    private fun base(): String =
        HarnessRuntime.config().githubApi.trim().trimEnd('/').ifBlank { DEFAULT_API }

    private fun headers(accept: String = JSON_ACCEPT): Map<String, String> = mapOf(
        "Authorization" to "Bearer ${token()}",
        "Accept" to accept,
        "X-GitHub-Api-Version" to API_VERSION,
        "User-Agent" to "Lucent",
        "Content-Type" to "application/json; charset=utf-8"
    )

    private suspend fun call(
        method: String,
        path: String,
        body: JsonObject? = null,
        accept: String = JSON_ACCEPT,
        follow: Boolean = false,
        timeoutSeconds: Int = 60
    ): HttpReply = HttpJson.request(method, base() + path, headers(accept), body?.toString(), timeoutSeconds, follow)

    private fun problem(reply: HttpReply): ToolExecResult? {
        if (reply.code == 0) return ToolExecResult("GitHub could not be reached: ${reply.error}", success = false)
        if (reply.ok) return null
        val detail = HttpJson.explain(reply.body)
        val head = "GitHub returned ${HttpJson.describe(reply.code)}."
        return ToolExecResult(if (detail.isBlank()) head else "$head\n$detail", success = false)
    }

    private fun notJson(reply: HttpReply): ToolExecResult = ToolExecResult(
        "GitHub answered ${HttpJson.describe(reply.code)} with something that is not JSON:\n" +
            HttpJson.cut(reply.body, 1200),
        success = false
    )

    private fun repoBase(args: JsonObject): String? {
        val owner = (args["owner"]?.jsonPrimitive?.content ?: "").trim()
        val repo = (args["repo"]?.jsonPrimitive?.content ?: "").trim()
        if (owner.isEmpty() || repo.isEmpty()) return null
        return "/repos/${HttpJson.enc(owner)}/${HttpJson.enc(repo)}"
    }

    private fun repoNeeded(): ToolExecResult = ToolExecResult(
        "Give owner and repo, for example owner \"octocat\" and repo \"hello-world\".",
        success = false
    )

    private fun numberNeeded(kind: String): ToolExecResult =
        ToolExecResult("Give the $kind number in number.", success = false)

    private fun unknown(name: String, raw: String, allowed: List<String>): ToolExecResult {
        val asked = raw.trim()
        val head = if (asked.isEmpty()) "$name needs an action." else "$name has no action called \"$asked\"."
        return ToolExecResult("$head Use one of: ${allowed.joinToString(", ")}.", success = false)
    }

    private fun splitList(raw: String): JsonArray {
        return kotlinx.serialization.json.buildJsonArray {
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
    }
    }

    private fun labelNames(array: JsonArray?): String {
        if (array == null || array.size == 0) return "none"
        val out = mutableListOf<String>()
        for (i in 0 until array.size) {
            val name = (array[i] as? JsonObject)?.get("name")?.jsonPrimitive?.content ?: "".orEmpty()
            if (name.isNotBlank()) out.add(name)
        }
        return if (out.isEmpty()) "none" else out.joinToString(", ")
    }

    private fun decode(content: String): String? = try {
        val clean = content.replace(Regex("\\s+"), "")
        val bytes = Base64.Mime.decode(clean)
        String(bytes, Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }

    private suspend fun repo(args: JsonObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val reply = call("GET", root)
        problem(reply)?.let { return it }
        val o = HttpJson.objectOf(reply.body) ?: return notJson(reply)
        val sb = StringBuilder()
        sb.append((o["full_name"]?.jsonPrimitive?.content ?: "")).append(" — ")
        sb.append(HttpJson.oneLine((o["description"]?.jsonPrimitive?.content ?: "").ifBlank { "no description" }, 200)).append('\n')
        sb.append("stars: ").append((o["stargazers_count"]?.jsonPrimitive?.intOrNull ?: 0))
        sb.append(" | forks: ").append((o["forks_count"]?.jsonPrimitive?.intOrNull ?: 0))
        sb.append(" | open issues: ").append((o["open_issues_count"]?.jsonPrimitive?.intOrNull ?: 0)).append('\n')
        sb.append("language: ").append((o["language"]?.jsonPrimitive?.content ?: "").ifBlank { "unknown" })
        sb.append(" | default branch: ").append((o["default_branch"]?.jsonPrimitive?.content ?: ""))
        sb.append(" | size: ").append(Workspace.humanSize((o["size"]?.jsonPrimitive?.longOrNull ?: 0L) * 1024L)).append('\n')
        sb.append("pushed: ").append((o["pushed_at"]?.jsonPrimitive?.content ?: ""))
        sb.append(" | private: ").append(if ((o["private"]?.jsonPrimitive?.booleanOrNull ?: false)) "yes" else "no").append('\n')
        sb.append((o["html_url"]?.jsonPrimitive?.content ?: ""))
        return ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
    }

    private suspend fun contents(args: JsonObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val path = (args["path"]?.jsonPrimitive?.content ?: "").trim().trim('/')
        val ref = (args["ref"]?.jsonPrimitive?.content ?: "").trim()
        val url = StringBuilder("$root/contents")
        if (path.isNotEmpty()) url.append('/').append(HttpJson.encPath(path))
        if (ref.isNotEmpty()) url.append("?ref=").append(HttpJson.enc(ref))
        val reply = call("GET", url.toString())
        problem(reply)?.let { return it }
        val body = reply.body.trim()
        if (body.startsWith("[")) {
            val array = HttpJson.arrayOf(body) ?: return notJson(reply)
            return ToolExecResult(
                HttpJson.rows(array, 50) { item ->
                    val type = (item["type"]?.jsonPrimitive?.content ?: "file")
                    val size = if (type == "file") " | ${(item["size"]?.jsonPrimitive?.longOrNull ?: 0L)} bytes" else ""
                    "$type ${(item["name"]?.jsonPrimitive?.content ?: "")}$size"
                }
            )
        }
        val file = HttpJson.objectOf(body) ?: return notJson(reply)
        val type = (file["type"]?.jsonPrimitive?.content ?: "file")
        val name = file["name"]?.jsonPrimitive?.content ?: path
        val download = (file["download_url"]?.jsonPrimitive?.content ?: "")
        if (type == "dir") {
            return ToolExecResult("$name is a folder. Call github_contents again with path \"$name\" to list it.")
        }
        if (type == "symlink") {
            return ToolExecResult("$name is a symlink to ${(file["target"]?.jsonPrimitive?.content ?: "")}.")
        }
        val size = (file["size"]?.jsonPrimitive?.longOrNull ?: 0L)
        if (size > MAX_TEXT_FILE) {
            return ToolExecResult(
                "$name is ${Workspace.humanSize(size)}, too large to decode here. Its download URL is " +
                    "$download — fetch it with http_request if you need the contents."
            )
        }
        val content = (file["content"]?.jsonPrimitive?.content ?: "")
        if ((file["encoding"]?.jsonPrimitive?.content ?: "") != "base64" || content.isEmpty()) {
            return ToolExecResult("$name has no inline text here. Download URL: $download")
        }
        val text = decode(content) ?: return ToolExecResult("$name could not be decoded. Download URL: $download")
        if (Workspace.looksBinary(text.toByteArray(Charsets.UTF_8))) {
            return ToolExecResult("$name looks binary (${Workspace.humanSize(size)}). Download URL: $download")
        }
        return ToolExecResult("----- $name (${Workspace.humanSize(size)}) -----\n${HttpJson.cut(text, HttpJson.REPLY_BUDGET)}")
    }

    private suspend fun issues(args: JsonObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val raw = (args["action"]?.jsonPrimitive?.content ?: "")
        val action = HttpJson.action(raw, ISSUE_ACTIONS)
        if (action.isEmpty()) return unknown("Issues", raw, ISSUE_ACTIONS)
        val number = (args["number"]?.jsonPrimitive?.intOrNull ?: 0)
        return when (action) {
            "list" -> {
                val state = (args["state"]?.jsonPrimitive?.content ?: "open").trim().ifBlank { "open" }
                val labels = (args["labels"]?.jsonPrimitive?.content ?: "").trim()
                val url = StringBuilder("$root/issues?state=").append(HttpJson.enc(state)).append("&per_page=25")
                if (labels.isNotEmpty()) url.append("&labels=").append(HttpJson.enc(labels))
                val reply = call("GET", url.toString())
                problem(reply)?.let { return it }
                val array = HttpJson.arrayOf(reply.body) ?: return notJson(reply)
                val plain = mutableListOf<kotlinx.serialization.json.JsonObject>()
                for (i in 0 until array.size) {
                    val item = (array[i] as? JsonObject) ?: continue
                    if (item.containsKey("pull_request")) continue
                    plain.add(item)
                }
                ToolExecResult(
                    HttpJson.rows(kotlinx.serialization.json.JsonArray(plain), 25) { item ->
                        "#${(item["number"]?.jsonPrimitive?.intOrNull ?: 0)} | ${(item["state"]?.jsonPrimitive?.content ?: "")} | " +
                            "${HttpJson.oneLine((item["title"]?.jsonPrimitive?.content ?: ""), 110)} | " +
                            HttpJson.field(item, "user.login")
                    }
                )
            }
            "get" -> {
                if (number <= 0) return numberNeeded("issue")
                val reply = call("GET", "$root/issues/$number")
                problem(reply)?.let { return it }
                val issue = HttpJson.objectOf(reply.body) ?: return notJson(reply)
                val sb = StringBuilder()
                sb.append('#').append(issue["number"]?.jsonPrimitive?.intOrNull ?: number).append(' ')
                sb.append((issue["title"]?.jsonPrimitive?.content ?: "")).append('\n')
                sb.append("state: ").append((issue["state"]?.jsonPrimitive?.content ?: ""))
                sb.append(" | by: ").append(HttpJson.field(issue, "user.login"))
                sb.append(" | comments: ").append((issue["comments"]?.jsonPrimitive?.intOrNull ?: 0))
                sb.append(" | labels: ").append(labelNames(issue["labels"]?.jsonArray)).append('\n')
                sb.append((issue["html_url"]?.jsonPrimitive?.content ?: "")).append("\n\n")
                sb.append((issue["body"]?.jsonPrimitive?.content ?: "").ifBlank { "(no body)" })
                val comments = call("GET", "$root/issues/$number/comments?per_page=20")
                if (comments.ok) {
                    val array = HttpJson.arrayOf(comments.body)
                    if (array != null && array.size > 0) {
                        sb.append("\n\nComments:\n")
                        sb.append(
                            HttpJson.rows(array, 20) { comment ->
                                "${HttpJson.field(comment, "user.login")}: " +
                                    HttpJson.oneLine((comment["body"]?.jsonPrimitive?.content ?: ""), 300)
                            }
                        )
                    }
                }
                ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
            }
            "create" -> {
                val title = (args["title"]?.jsonPrimitive?.content ?: "").trim()
                if (title.isEmpty()) return ToolExecResult("create needs a title.", success = false)
                val payload = mutableMapOf<String, kotlinx.serialization.json.JsonElement>("title" to kotlinx.serialization.json.JsonPrimitive(title))
                val body = (args["body"]?.jsonPrimitive?.content ?: "")
                if (body.isNotEmpty()) payload["body"] = kotlinx.serialization.json.JsonPrimitive(body)
                val labels = splitList((args["labels"]?.jsonPrimitive?.content ?: ""))
                if (labels.size > 0) payload["labels"] = kotlinx.serialization.json.JsonArray(labels.map { kotlinx.serialization.json.JsonPrimitive(it) })
                val reply = call("POST", "$root/issues", kotlinx.serialization.json.JsonObject(payload))
                problem(reply)?.let { return it }
                val issue = HttpJson.objectOf(reply.body)
                val created = (issue?.get("html_url")?.jsonPrimitive?.content ?: "").orEmpty()
                val createdNumber = (issue?.get("number")?.jsonPrimitive?.intOrNull ?: 0) ?: 0
                if (createdNumber > 0) {
                    ToolExecResult("Created #$createdNumber $created".trim())
                } else {
                    ToolExecResult("Created the issue. $created".trim())
                }
            }
            "update" -> {
                if (number <= 0) return numberNeeded("issue")
                val payload = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                val title = (args["title"]?.jsonPrimitive?.content ?: "").trim()
                if (title.isNotEmpty()) payload["title"] = kotlinx.serialization.json.JsonPrimitive(title)
                if (args.containsKey("body")) payload["body"] = kotlinx.serialization.json.JsonPrimitive((args["body"]?.jsonPrimitive?.content ?: ""))
                val state = (args["state"]?.jsonPrimitive?.content ?: "").trim()
                if (state.isNotEmpty()) payload["state"] = kotlinx.serialization.json.JsonPrimitive(state)
                val labels = splitList((args["labels"]?.jsonPrimitive?.content ?: ""))
                if (labels.size > 0) payload["labels"] = kotlinx.serialization.json.JsonArray(labels.map { kotlinx.serialization.json.JsonPrimitive(it) })
                if (payload.size == 0) {
                    return ToolExecResult("update needs title, body, state or labels to change.", success = false)
                }
                val reply = call("PATCH", "$root/issues/$number", kotlinx.serialization.json.JsonObject(payload))
                problem(reply)?.let { return it }
                ToolExecResult("Updated #$number.")
            }
            "comment" -> {
                if (number <= 0) return numberNeeded("issue")
                val body = (args["comment"]?.jsonPrimitive?.content ?: "").trim().ifBlank { (args["body"]?.jsonPrimitive?.content ?: "").trim() }
                if (body.isEmpty()) return ToolExecResult("comment needs the text in comment.", success = false)
                val reply = call("POST", "$root/issues/$number/comments", kotlinx.serialization.json.buildJsonObject { put("body", body) })
                problem(reply)?.let { return it }
                ToolExecResult("Commented on #$number.")
            }
            else -> {
                if (number <= 0) return numberNeeded("issue")
                val reply = call("PATCH", "$root/issues/$number", kotlinx.serialization.json.buildJsonObject { put("state", "closed") })
                problem(reply)?.let { return it }
                ToolExecResult("Closed #$number.")
            }
        }
    }

    private suspend fun pulls(args: JsonObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val raw = (args["action"]?.jsonPrimitive?.content ?: "")
        val action = HttpJson.action(raw, PULL_ACTIONS)
        if (action.isEmpty()) return unknown("Pull requests", raw, PULL_ACTIONS)
        val number = (args["number"]?.jsonPrimitive?.intOrNull ?: 0)
        return when (action) {
            "list" -> {
                val state = (args["state"]?.jsonPrimitive?.content ?: "open").trim().ifBlank { "open" }
                val reply = call("GET", "$root/pulls?state=${HttpJson.enc(state)}&per_page=25")
                problem(reply)?.let { return it }
                val array = HttpJson.arrayOf(reply.body) ?: return notJson(reply)
                ToolExecResult(
                    HttpJson.rows(array, 25) { item ->
                        "#${(item["number"]?.jsonPrimitive?.intOrNull ?: 0)} | ${(item["state"]?.jsonPrimitive?.content ?: "")} | " +
                            "${HttpJson.oneLine((item["title"]?.jsonPrimitive?.content ?: ""), 100)} | " +
                            "${HttpJson.field(item, "head.ref")} -> ${HttpJson.field(item, "base.ref")}"
                    }
                )
            }
            "get" -> {
                if (number <= 0) return numberNeeded("pull request")
                val reply = call("GET", "$root/pulls/$number")
                problem(reply)?.let { return it }
                val pull = HttpJson.objectOf(reply.body) ?: return notJson(reply)
                val sb = StringBuilder()
                sb.append('#').append(pull["number"]?.jsonPrimitive?.intOrNull ?: number).append(' ')
                sb.append((pull["title"]?.jsonPrimitive?.content ?: "")).append('\n')
                sb.append("state: ").append((pull["state"]?.jsonPrimitive?.content ?: ""))
                sb.append(" | merged: ").append(if ((pull["merged"]?.jsonPrimitive?.booleanOrNull ?: false)) "yes" else "no")
                sb.append(" | by: ").append(HttpJson.field(pull, "user.login")).append('\n')
                sb.append(HttpJson.field(pull, "head.ref")).append(" -> ").append(HttpJson.field(pull, "base.ref"))
                sb.append(" | files: ").append((pull["changed_files"]?.jsonPrimitive?.intOrNull ?: 0))
                sb.append(" | +").append((pull["additions"]?.jsonPrimitive?.intOrNull ?: 0))
                sb.append(" -").append((pull["deletions"]?.jsonPrimitive?.intOrNull ?: 0)).append('\n')
                sb.append((pull["html_url"]?.jsonPrimitive?.content ?: "")).append("\n\n")
                sb.append((pull["body"]?.jsonPrimitive?.content ?: "").ifBlank { "(no body)" })
                ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
            }
            "create" -> {
                val title = (args["title"]?.jsonPrimitive?.content ?: "").trim()
                val head = (args["head"]?.jsonPrimitive?.content ?: "").trim()
                val target = (args["base"]?.jsonPrimitive?.content ?: "").trim()
                if (title.isEmpty() || head.isEmpty() || target.isEmpty()) {
                    return ToolExecResult("create needs title, head and base.", success = false)
                }
                val payload = mutableMapOf<String, kotlinx.serialization.json.JsonElement>("title" to kotlinx.serialization.json.JsonPrimitive(title), "head" to kotlinx.serialization.json.JsonPrimitive(head), "base" to kotlinx.serialization.json.JsonPrimitive(target))
                val body = (args["body"]?.jsonPrimitive?.content ?: "")
                if (body.isNotEmpty()) payload["body"] = kotlinx.serialization.json.JsonPrimitive(body)
                val reply = call("POST", "$root/pulls", kotlinx.serialization.json.JsonObject(payload))
                problem(reply)?.let { return it }
                val pull = HttpJson.objectOf(reply.body)
                val created = (pull?.get("html_url")?.jsonPrimitive?.content ?: "").orEmpty()
                val createdNumber = (pull?.get("number")?.jsonPrimitive?.intOrNull ?: 0) ?: 0
                if (createdNumber > 0) {
                    ToolExecResult("Created #$createdNumber $created".trim())
                } else {
                    ToolExecResult("Created the pull request. $created".trim())
                }
            }
            "update" -> {
                if (number <= 0) return numberNeeded("pull request")
                val payload = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                val title = (args["title"]?.jsonPrimitive?.content ?: "").trim()
                if (title.isNotEmpty()) payload["title"] = kotlinx.serialization.json.JsonPrimitive(title)
                if (args.containsKey("body")) payload["body"] = kotlinx.serialization.json.JsonPrimitive((args["body"]?.jsonPrimitive?.content ?: ""))
                val state = (args["state"]?.jsonPrimitive?.content ?: "").trim()
                if (state.isNotEmpty()) payload["state"] = kotlinx.serialization.json.JsonPrimitive(state)
                if (payload.size == 0) {
                    return ToolExecResult("update needs title, body or state to change.", success = false)
                }
                val reply = call("PATCH", "$root/pulls/$number", kotlinx.serialization.json.JsonObject(payload))
                problem(reply)?.let { return it }
                ToolExecResult("Updated #$number.")
            }
            "comment" -> {
                if (number <= 0) return numberNeeded("pull request")
                val body = (args["comment"]?.jsonPrimitive?.content ?: "").trim().ifBlank { (args["body"]?.jsonPrimitive?.content ?: "").trim() }
                if (body.isEmpty()) return ToolExecResult("comment needs the text in comment.", success = false)
                val reply = call("POST", "$root/issues/$number/comments", kotlinx.serialization.json.buildJsonObject { put("body", body) })
                problem(reply)?.let { return it }
                ToolExecResult("Commented on #$number.")
            }
            "diff" -> {
                if (number <= 0) return numberNeeded("pull request")
                val reply = call("GET", "$root/pulls/$number", null, DIFF_ACCEPT)
                problem(reply)?.let { return it }
                val diff = reply.body.trim()
                ToolExecResult(
                    if (diff.isEmpty()) "That pull request has an empty diff." else HttpJson.cut(diff, HttpJson.REPLY_BUDGET)
                )
            }
            "files" -> {
                if (number <= 0) return numberNeeded("pull request")
                val reply = call("GET", "$root/pulls/$number/files?per_page=50")
                problem(reply)?.let { return it }
                val array = HttpJson.arrayOf(reply.body) ?: return notJson(reply)
                ToolExecResult(
                    HttpJson.rows(array, 50) { item ->
                        "${(item["status"]?.jsonPrimitive?.content ?: "")} ${(item["filename"]?.jsonPrimitive?.content ?: "")} | " +
                            "+${(item["additions"]?.jsonPrimitive?.intOrNull ?: 0)} -${(item["deletions"]?.jsonPrimitive?.intOrNull ?: 0)}"
                    }
                )
            }
            else -> {
                if (number <= 0) return numberNeeded("pull request")
                val reply = call("PUT", "$root/pulls/$number/merge", kotlinx.serialization.json.buildJsonObject { put("merge_method", "merge") })
                problem(reply)?.let { return it }
                val outcome = HttpJson.objectOf(reply.body)
                val message = (outcome?.get("message")?.jsonPrimitive?.content ?: "").orEmpty()
                if ((outcome?.get("merged")?.jsonPrimitive?.booleanOrNull ?: false) == true) {
                    ToolExecResult("Merged #$number. $message".trim())
                } else {
                    ToolExecResult("GitHub did not merge #$number: $message", success = false)
                }
            }
        }
    }

    private suspend fun branches(args: JsonObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val raw = (args["action"]?.jsonPrimitive?.content ?: "")
        val action = HttpJson.action(raw, BRANCH_ACTIONS)
        if (action.isEmpty()) return unknown("Branches", raw, BRANCH_ACTIONS)
        val name = (args["name"]?.jsonPrimitive?.content ?: "").trim()
        return when (action) {
            "list" -> {
                val reply = call("GET", "$root/branches?per_page=50")
                problem(reply)?.let { return it }
                val array = HttpJson.arrayOf(reply.body) ?: return notJson(reply)
                ToolExecResult(
                    HttpJson.rows(array, 50) { item ->
                        val sha = HttpJson.field(item, "commit.sha")
                        "${(item["name"]?.jsonPrimitive?.content ?: "")} | ${sha.take(9)} | " +
                            (if ((item["protected"]?.jsonPrimitive?.booleanOrNull ?: false)) "protected" else "open")
                    }
                )
            }
            "get" -> {
                if (name.isEmpty()) return ToolExecResult("get needs the branch name in name.", success = false)
                val reply = call("GET", "$root/branches/${HttpJson.enc(name)}")
                problem(reply)?.let { return it }
                val branch = HttpJson.objectOf(reply.body) ?: return notJson(reply)
                ToolExecResult(
                    "branch: ${branch["name"]?.jsonPrimitive?.content ?: name}\n" +
                        "sha: ${HttpJson.field(branch, "commit.sha")}\n" +
                        "protected: ${if ((branch["protected"]?.jsonPrimitive?.booleanOrNull ?: false)) "yes" else "no"}"
                )
            }
            "create" -> {
                val from = (args["from"]?.jsonPrimitive?.content ?: "").trim()
                if (name.isEmpty() || from.isEmpty()) {
                    return ToolExecResult(
                        "create needs name and from (the sha, tag or branch to start from).",
                        success = false
                    )
                }
                val payload = mutableMapOf<String, kotlinx.serialization.json.JsonElement>("ref" to kotlinx.serialization.json.JsonPrimitive("refs/heads/$name"), "sha" to kotlinx.serialization.json.JsonPrimitive(from))
                val reply = call("POST", "$root/git/refs", kotlinx.serialization.json.JsonObject(payload))
                problem(reply)?.let { return it }
                ToolExecResult("Created branch $name from $from.")
            }
            else -> {
                if (HarnessRuntime.config().approvalFor(HarnessPermission.GITHUB) != Approval.ALLOW) {
                    return ToolExecResult(
                        "Deleting a branch only runs when the GitHub approval policy is set to Allow in " +
                            "Settings → Agent → Permissions. Nothing was deleted.",
                        success = false
                    )
                }
                if (name.isEmpty()) return ToolExecResult("delete needs the branch name in name.", success = false)
                val reply = call("DELETE", "$root/git/refs/heads/${HttpJson.enc(name)}")
                problem(reply)?.let { return it }
                ToolExecResult("Deleted branch $name.")
            }
        }
    }

    private suspend fun commits(args: JsonObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val limit = (args["limit"]?.jsonPrimitive?.intOrNull ?: 20).coerceIn(1, 100)
        val path = (args["path"]?.jsonPrimitive?.content ?: "").trim()
        val branch = (args["branch"]?.jsonPrimitive?.content ?: "").trim()
        val url = StringBuilder("$root/commits?per_page=$limit")
        if (path.isNotEmpty()) url.append("&path=").append(HttpJson.enc(path))
        if (branch.isNotEmpty()) url.append("&sha=").append(HttpJson.enc(branch))
        val reply = call("GET", url.toString())
        problem(reply)?.let { return it }
        val array = HttpJson.arrayOf(reply.body) ?: return notJson(reply)
        return ToolExecResult(
            HttpJson.rows(array, limit) { item ->
                val sha = (item["sha"]?.jsonPrimitive?.content ?: "").take(9)
                val message = HttpJson.firstLine(HttpJson.field(item, "commit.message"), 120)
                val author = HttpJson.field(item, "commit.author.name")
                val date = HttpJson.field(item, "commit.author.date")
                "$sha | $author | $date | $message"
            }
        )
    }

    private suspend fun search(args: JsonObject): ToolExecResult {
        val kind = HttpJson.action((args["kind"]?.jsonPrimitive?.content ?: "code"), SEARCH_KINDS).ifBlank { "code" }
        val query = (args["query"]?.jsonPrimitive?.content ?: "").trim()
        if (query.isEmpty()) return ToolExecResult("search needs query.", success = false)
        val endpoint = when (kind) {
            "repos" -> "repositories"
            "issues" -> "issues"
            "commits" -> "commits"
            else -> "code"
        }
        val accept = if (kind == "code") MATCH_ACCEPT else JSON_ACCEPT
        val reply = call("GET", "/search/$endpoint?q=${HttpJson.enc(query)}&per_page=20", null, accept)
        problem(reply)?.let { return it }
        val payload = HttpJson.objectOf(reply.body) ?: return notJson(reply)
        val items = payload["items"]?.jsonArray
        val total = "total_count: ${(payload["total_count"]?.jsonPrimitive?.intOrNull ?: 0)}"
        val text = when (kind) {
            "repos" -> HttpJson.rows(items, 20) { item ->
                "${(item["full_name"]?.jsonPrimitive?.content ?: "")} | ${(item["stargazers_count"]?.jsonPrimitive?.intOrNull ?: 0)} stars | " +
                    HttpJson.oneLine((item["description"]?.jsonPrimitive?.content ?: ""), 90)
            }
            "issues" -> HttpJson.rows(items, 20) { item ->
                "${(item["state"]?.jsonPrimitive?.content ?: "")} | ${HttpJson.oneLine((item["title"]?.jsonPrimitive?.content ?: ""), 110)} | " +
                    (item["html_url"]?.jsonPrimitive?.content ?: "")
            }
            "commits" -> HttpJson.rows(items, 20) { item ->
                "${(item["sha"]?.jsonPrimitive?.content ?: "").take(9)} | ${HttpJson.field(item, "repository.full_name")} | " +
                    HttpJson.firstLine(HttpJson.field(item, "commit.message"), 100)
            }
            else -> HttpJson.rows(items, 20) { item ->
                "${HttpJson.field(item, "repository.full_name")} | ${(item["path"]?.jsonPrimitive?.content ?: "")} | " +
                    (item["html_url"]?.jsonPrimitive?.content ?: "")
            }
        }
        return ToolExecResult("$total\n$text")
    }

    private suspend fun actions(args: JsonObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val raw = (args["action"]?.jsonPrimitive?.content ?: "")
        val action = HttpJson.action(raw, RUN_ACTIONS)
        if (action.isEmpty()) return unknown("Actions", raw, RUN_ACTIONS)
        val runId = (args["run_id"]?.jsonPrimitive?.longOrNull ?: 0L)
        val jobId = (args["job_id"]?.jsonPrimitive?.longOrNull ?: 0L)
        return when (action) {
            "runs" -> {
                val reply = call("GET", "$root/actions/runs?per_page=15")
                problem(reply)?.let { return it }
                val runs = HttpJson.objectOf(reply.body)?.get("workflow_runs")?.jsonArray
                ToolExecResult(
                    HttpJson.rows(runs, 15) { run ->
                        "${(run["id"]?.jsonPrimitive?.longOrNull ?: 0L)} | ${(run["name"]?.jsonPrimitive?.content ?: "")} | " +
                            "${(run["status"]?.jsonPrimitive?.content ?: "")}/${(run["conclusion"]?.jsonPrimitive?.content ?: "")} | " +
                            "${(run["head_branch"]?.jsonPrimitive?.content ?: "")} | ${(run["created_at"]?.jsonPrimitive?.content ?: "")}"
                    }
                )
            }
            "run" -> {
                if (runId <= 0) return ToolExecResult("run needs run_id.", success = false)
                val reply = call("GET", "$root/actions/runs/$runId")
                problem(reply)?.let { return it }
                val run = HttpJson.objectOf(reply.body) ?: return notJson(reply)
                val sb = StringBuilder()
                sb.append((run["name"]?.jsonPrimitive?.content ?: "")).append(" #").append((run["run_number"]?.jsonPrimitive?.intOrNull ?: 0)).append('\n')
                sb.append("status: ").append((run["status"]?.jsonPrimitive?.content ?: ""))
                sb.append(" | conclusion: ").append((run["conclusion"]?.jsonPrimitive?.content ?: "").ifBlank { "pending" }).append('\n')
                sb.append("branch: ").append((run["head_branch"]?.jsonPrimitive?.content ?: ""))
                sb.append(" | event: ").append((run["event"]?.jsonPrimitive?.content ?: ""))
                sb.append(" | started: ").append((run["run_started_at"]?.jsonPrimitive?.content ?: "")).append('\n')
                sb.append((run["html_url"]?.jsonPrimitive?.content ?: ""))
                ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
            }
            "jobs" -> {
                if (runId <= 0) return ToolExecResult("jobs needs run_id.", success = false)
                val reply = call("GET", "$root/actions/runs/$runId/jobs?per_page=30")
                problem(reply)?.let { return it }
                val jobs = HttpJson.objectOf(reply.body)?.get("jobs")?.jsonArray
                ToolExecResult(
                    HttpJson.rows(jobs, 30) { job ->
                        "${(job["id"]?.jsonPrimitive?.longOrNull ?: 0L)} | ${(job["name"]?.jsonPrimitive?.content ?: "")} | " +
                            "${(job["status"]?.jsonPrimitive?.content ?: "")}/${(job["conclusion"]?.jsonPrimitive?.content ?: "")}"
                    }
                )
            }
            "logs" -> {
                if (jobId <= 0) return ToolExecResult("logs needs job_id; find it with the jobs action.", success = false)
                val reply = call("GET", "$root/actions/jobs/$jobId/logs", null, JSON_ACCEPT, true, 120)
                problem(reply)?.let { return it }
                val text = reply.body.trim()
                if (text.isEmpty()) return ToolExecResult("That job has no log text yet.")
                if (text.length <= LOG_TAIL) return ToolExecResult(text)
                ToolExecResult(
                    "… showing the last $LOG_TAIL of ${text.length} log characters\n" + text.takeLast(LOG_TAIL)
                )
            }
            "dispatch" -> {
                val workflow = (args["workflow"]?.jsonPrimitive?.content ?: "").trim()
                val ref = (args["ref"]?.jsonPrimitive?.content ?: "").trim()
                if (workflow.isEmpty() || ref.isEmpty()) {
                    return ToolExecResult("dispatch needs workflow (file name or id) and ref (branch).", success = false)
                }
                val reply = call(
                    "POST",
                    "$root/actions/workflows/${HttpJson.enc(workflow)}/dispatches",
                    kotlinx.serialization.json.buildJsonObject { put("ref", ref) }
                )
                problem(reply)?.let { return it }
                ToolExecResult("Dispatched $workflow on $ref.")
            }
            else -> {
                if (runId <= 0) return ToolExecResult("cancel needs run_id.", success = false)
                val reply = call("POST", "$root/actions/runs/$runId/cancel")
                problem(reply)?.let { return it }
                ToolExecResult("Asked GitHub to cancel run $runId.")
            }
        }
    }

    private suspend fun releases(args: JsonObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val raw = (args["action"]?.jsonPrimitive?.content ?: "")
        val action = HttpJson.action(raw, RELEASE_ACTIONS)
        if (action.isEmpty()) return unknown("Releases", raw, RELEASE_ACTIONS)
        val tag = (args["tag"]?.jsonPrimitive?.content ?: "").trim()
        return when (action) {
            "list" -> {
                val reply = call("GET", "$root/releases?per_page=20")
                problem(reply)?.let { return it }
                val array = HttpJson.arrayOf(reply.body) ?: return notJson(reply)
                ToolExecResult(
                    HttpJson.rows(array, 20) { item ->
                        "${(item["tag_name"]?.jsonPrimitive?.content ?: "")} | ${(item["name"]?.jsonPrimitive?.content ?: "")} | " +
                            "${if ((item["draft"]?.jsonPrimitive?.booleanOrNull ?: false)) "draft" else "published"} | " +
                            (item["published_at"]?.jsonPrimitive?.content ?: "")
                    }
                )
            }
            "get" -> {
                if (tag.isEmpty()) return ToolExecResult("get needs tag.", success = false)
                val reply = call("GET", "$root/releases/tags/${HttpJson.enc(tag)}")
                problem(reply)?.let { return it }
                val release = HttpJson.objectOf(reply.body)
                val sb = StringBuilder()
                sb.append(release?.get("name")?.jsonPrimitive?.content ?: tag.orEmpty()).append(" (").append(tag).append(")\n")
                sb.append("draft: ").append(if ((release?.get("draft")?.jsonPrimitive?.booleanOrNull ?: false) == true) "yes" else "no")
                sb.append(" | published: ").append((release?.get("published_at")?.jsonPrimitive?.content ?: "").orEmpty()).append('\n')
                sb.append((release?.get("html_url")?.jsonPrimitive?.content ?: "").orEmpty()).append("\n\n")
                sb.append((release?.get("body")?.jsonPrimitive?.content ?: "").orEmpty().ifBlank { "(no notes)" })
                ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
            }
            else -> {
                if (tag.isEmpty()) return ToolExecResult("create needs tag.", success = false)
                val payload = mutableMapOf<String, kotlinx.serialization.json.JsonElement>("tag_name" to kotlinx.serialization.json.JsonPrimitive(tag))
                val name = (args["name"]?.jsonPrimitive?.content ?: "").trim()
                if (name.isNotEmpty()) payload["name"] = kotlinx.serialization.json.JsonPrimitive(name)
                if (args.containsKey("body")) payload["body"] = kotlinx.serialization.json.JsonPrimitive((args["body"]?.jsonPrimitive?.content ?: ""))
                payload["draft"] = kotlinx.serialization.json.JsonPrimitive((args["draft"]?.jsonPrimitive?.booleanOrNull ?: false))
                val reply = call("POST", "$root/releases", kotlinx.serialization.json.JsonObject(payload))
                problem(reply)?.let { return it }
                val release = HttpJson.objectOf(reply.body)
                ToolExecResult(
                    "Created release ${release?.get("tag_name")?.jsonPrimitive?.content ?: tag.orEmpty()} " +
                        (release?.get("html_url")?.jsonPrimitive?.content ?: "").orEmpty()
                )
            }
        }
    }

    private suspend fun user(): ToolExecResult {
        val reply = call("GET", "/user")
        problem(reply)?.let { return it }
        val account = HttpJson.objectOf(reply.body) ?: return notJson(reply)
        val sb = StringBuilder()
        sb.append("login: ").append((account["login"]?.jsonPrimitive?.content ?: "")).append('\n')
        sb.append("name: ").append((account["name"]?.jsonPrimitive?.content ?: "").ifBlank { "(none)" })
        sb.append(" | public repos: ").append((account["public_repos"]?.jsonPrimitive?.intOrNull ?: 0))
        sb.append(" | followers: ").append((account["followers"]?.jsonPrimitive?.intOrNull ?: 0)).append('\n')
        sb.append("profile: ").append((account["html_url"]?.jsonPrimitive?.content ?: "")).append('\n')
        val limits = call("GET", "/rate_limit")
        if (limits.ok) {
            val resources = HttpJson.objectOf(limits.body)?.get("resources")?.jsonObject
            val core = resources?.get("core")?.jsonObject
            val search = resources?.get("search")?.jsonObject
            if (core != null) {
                sb.append("core requests left: ").append((core["remaining"]?.jsonPrimitive?.intOrNull ?: 0))
                sb.append(" of ").append((core["limit"]?.jsonPrimitive?.intOrNull ?: 0)).append('\n')
            }
            if (search != null) {
                sb.append("search requests left: ").append((search["remaining"]?.jsonPrimitive?.intOrNull ?: 0))
                sb.append(" of ").append((search["limit"]?.jsonPrimitive?.intOrNull ?: 0)).append('\n')
            }
        } else {
            sb.append("rate limit: could not be read (${HttpJson.describe(limits.code)})\n")
        }
        return ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
    }
}
