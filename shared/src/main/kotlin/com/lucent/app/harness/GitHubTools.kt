package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

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

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? {
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
        body: JSONObject? = null,
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

    private fun repoBase(args: JSONObject): String? {
        val owner = args.optString("owner", "").trim()
        val repo = args.optString("repo", "").trim()
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

    private fun splitList(raw: String): JSONArray {
        val out = JSONArray()
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { out.put(it) }
        return out
    }

    private fun labelNames(array: JSONArray?): String {
        if (array == null || array.length() == 0) return "none"
        val out = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val name = array.optJSONObject(i)?.optString("name", "").orEmpty()
            if (name.isNotBlank()) out.add(name)
        }
        return if (out.isEmpty()) "none" else out.joinToString(", ")
    }

    private fun decode(content: String): String? = try {
        val clean = content.replace(Regex("\\s+"), "")
        val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
        String(bytes, StandardCharsets.UTF_8)
    } catch (t: Throwable) {
        null
    }

    private suspend fun repo(args: JSONObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val reply = call("GET", root)
        problem(reply)?.let { return it }
        val o = HttpJson.objectOf(reply.body) ?: return notJson(reply)
        val sb = StringBuilder()
        sb.append(o.optString("full_name", "")).append(" — ")
        sb.append(HttpJson.oneLine(o.optString("description", "").ifBlank { "no description" }, 200)).append('\n')
        sb.append("stars: ").append(o.optInt("stargazers_count", 0))
        sb.append(" | forks: ").append(o.optInt("forks_count", 0))
        sb.append(" | open issues: ").append(o.optInt("open_issues_count", 0)).append('\n')
        sb.append("language: ").append(o.optString("language", "").ifBlank { "unknown" })
        sb.append(" | default branch: ").append(o.optString("default_branch", ""))
        sb.append(" | size: ").append(Workspace.humanSize(o.optLong("size", 0L) * 1024L)).append('\n')
        sb.append("pushed: ").append(o.optString("pushed_at", ""))
        sb.append(" | private: ").append(if (o.optBoolean("private", false)) "yes" else "no").append('\n')
        sb.append(o.optString("html_url", ""))
        return ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
    }

    private suspend fun contents(args: JSONObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val path = args.optString("path", "").trim().trim('/')
        val ref = args.optString("ref", "").trim()
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
                    val type = item.optString("type", "file")
                    val size = if (type == "file") " | ${item.optLong("size", 0L)} bytes" else ""
                    "$type ${item.optString("name", "")}$size"
                }
            )
        }
        val file = HttpJson.objectOf(body) ?: return notJson(reply)
        val type = file.optString("type", "file")
        val name = file.optString("name", path)
        val download = file.optString("download_url", "")
        if (type == "dir") {
            return ToolExecResult("$name is a folder. Call github_contents again with path \"$name\" to list it.")
        }
        if (type == "symlink") {
            return ToolExecResult("$name is a symlink to ${file.optString("target", "")}.")
        }
        val size = file.optLong("size", 0L)
        if (size > MAX_TEXT_FILE) {
            return ToolExecResult(
                "$name is ${Workspace.humanSize(size)}, too large to decode here. Its download URL is " +
                    "$download — fetch it with http_request if you need the contents."
            )
        }
        val content = file.optString("content", "")
        if (file.optString("encoding", "") != "base64" || content.isEmpty()) {
            return ToolExecResult("$name has no inline text here. Download URL: $download")
        }
        val text = decode(content) ?: return ToolExecResult("$name could not be decoded. Download URL: $download")
        if (Workspace.looksBinary(text.toByteArray(StandardCharsets.UTF_8))) {
            return ToolExecResult("$name looks binary (${Workspace.humanSize(size)}). Download URL: $download")
        }
        return ToolExecResult("----- $name (${Workspace.humanSize(size)}) -----\n${HttpJson.cut(text, HttpJson.REPLY_BUDGET)}")
    }

    private suspend fun issues(args: JSONObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val raw = args.optString("action", "")
        val action = HttpJson.action(raw, ISSUE_ACTIONS)
        if (action.isEmpty()) return unknown("Issues", raw, ISSUE_ACTIONS)
        val number = args.optInt("number", 0)
        return when (action) {
            "list" -> {
                val state = args.optString("state", "open").trim().ifBlank { "open" }
                val labels = args.optString("labels", "").trim()
                val url = StringBuilder("$root/issues?state=").append(HttpJson.enc(state)).append("&per_page=25")
                if (labels.isNotEmpty()) url.append("&labels=").append(HttpJson.enc(labels))
                val reply = call("GET", url.toString())
                problem(reply)?.let { return it }
                val array = HttpJson.arrayOf(reply.body) ?: return notJson(reply)
                val plain = JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    if (item.has("pull_request")) continue
                    plain.put(item)
                }
                ToolExecResult(
                    HttpJson.rows(plain, 25) { item ->
                        "#${item.optInt("number", 0)} | ${item.optString("state", "")} | " +
                            "${HttpJson.oneLine(item.optString("title", ""), 110)} | " +
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
                sb.append('#').append(issue.optInt("number", number)).append(' ')
                sb.append(issue.optString("title", "")).append('\n')
                sb.append("state: ").append(issue.optString("state", ""))
                sb.append(" | by: ").append(HttpJson.field(issue, "user.login"))
                sb.append(" | comments: ").append(issue.optInt("comments", 0))
                sb.append(" | labels: ").append(labelNames(issue.optJSONArray("labels"))).append('\n')
                sb.append(issue.optString("html_url", "")).append("\n\n")
                sb.append(issue.optString("body", "").ifBlank { "(no body)" })
                val comments = call("GET", "$root/issues/$number/comments?per_page=20")
                if (comments.ok) {
                    val array = HttpJson.arrayOf(comments.body)
                    if (array != null && array.length() > 0) {
                        sb.append("\n\nComments:\n")
                        sb.append(
                            HttpJson.rows(array, 20) { comment ->
                                "${HttpJson.field(comment, "user.login")}: " +
                                    HttpJson.oneLine(comment.optString("body", ""), 300)
                            }
                        )
                    }
                }
                ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
            }
            "create" -> {
                val title = args.optString("title", "").trim()
                if (title.isEmpty()) return ToolExecResult("create needs a title.", success = false)
                val payload = JSONObject().put("title", title)
                val body = args.optString("body", "")
                if (body.isNotEmpty()) payload.put("body", body)
                val labels = splitList(args.optString("labels", ""))
                if (labels.length() > 0) payload.put("labels", labels)
                val reply = call("POST", "$root/issues", payload)
                problem(reply)?.let { return it }
                val issue = HttpJson.objectOf(reply.body)
                ToolExecResult(
                    "Created #${issue?.optInt("number", 0)} ${issue?.optString("html_url", "").orEmpty()}".trim()
                )
            }
            "update" -> {
                if (number <= 0) return numberNeeded("issue")
                val payload = JSONObject()
                val title = args.optString("title", "").trim()
                if (title.isNotEmpty()) payload.put("title", title)
                if (args.has("body")) payload.put("body", args.optString("body", ""))
                val state = args.optString("state", "").trim()
                if (state.isNotEmpty()) payload.put("state", state)
                val labels = splitList(args.optString("labels", ""))
                if (labels.length() > 0) payload.put("labels", labels)
                if (payload.length() == 0) {
                    return ToolExecResult("update needs title, body, state or labels to change.", success = false)
                }
                val reply = call("PATCH", "$root/issues/$number", payload)
                problem(reply)?.let { return it }
                ToolExecResult("Updated #$number.")
            }
            "comment" -> {
                if (number <= 0) return numberNeeded("issue")
                val body = args.optString("comment", "").trim().ifBlank { args.optString("body", "").trim() }
                if (body.isEmpty()) return ToolExecResult("comment needs the text in comment.", success = false)
                val reply = call("POST", "$root/issues/$number/comments", JSONObject().put("body", body))
                problem(reply)?.let { return it }
                ToolExecResult("Commented on #$number.")
            }
            else -> {
                if (number <= 0) return numberNeeded("issue")
                val reply = call("PATCH", "$root/issues/$number", JSONObject().put("state", "closed"))
                problem(reply)?.let { return it }
                ToolExecResult("Closed #$number.")
            }
        }
    }

    private suspend fun pulls(args: JSONObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val raw = args.optString("action", "")
        val action = HttpJson.action(raw, PULL_ACTIONS)
        if (action.isEmpty()) return unknown("Pull requests", raw, PULL_ACTIONS)
        val number = args.optInt("number", 0)
        return when (action) {
            "list" -> {
                val state = args.optString("state", "open").trim().ifBlank { "open" }
                val reply = call("GET", "$root/pulls?state=${HttpJson.enc(state)}&per_page=25")
                problem(reply)?.let { return it }
                val array = HttpJson.arrayOf(reply.body) ?: return notJson(reply)
                ToolExecResult(
                    HttpJson.rows(array, 25) { item ->
                        "#${item.optInt("number", 0)} | ${item.optString("state", "")} | " +
                            "${HttpJson.oneLine(item.optString("title", ""), 100)} | " +
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
                sb.append('#').append(pull.optInt("number", number)).append(' ')
                sb.append(pull.optString("title", "")).append('\n')
                sb.append("state: ").append(pull.optString("state", ""))
                sb.append(" | merged: ").append(if (pull.optBoolean("merged", false)) "yes" else "no")
                sb.append(" | by: ").append(HttpJson.field(pull, "user.login")).append('\n')
                sb.append(HttpJson.field(pull, "head.ref")).append(" -> ").append(HttpJson.field(pull, "base.ref"))
                sb.append(" | files: ").append(pull.optInt("changed_files", 0))
                sb.append(" | +").append(pull.optInt("additions", 0))
                sb.append(" -").append(pull.optInt("deletions", 0)).append('\n')
                sb.append(pull.optString("html_url", "")).append("\n\n")
                sb.append(pull.optString("body", "").ifBlank { "(no body)" })
                ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
            }
            "create" -> {
                val title = args.optString("title", "").trim()
                val head = args.optString("head", "").trim()
                val target = args.optString("base", "").trim()
                if (title.isEmpty() || head.isEmpty() || target.isEmpty()) {
                    return ToolExecResult("create needs title, head and base.", success = false)
                }
                val payload = JSONObject().put("title", title).put("head", head).put("base", target)
                val body = args.optString("body", "")
                if (body.isNotEmpty()) payload.put("body", body)
                val reply = call("POST", "$root/pulls", payload)
                problem(reply)?.let { return it }
                val pull = HttpJson.objectOf(reply.body)
                ToolExecResult(
                    "Created #${pull?.optInt("number", 0)} ${pull?.optString("html_url", "").orEmpty()}".trim()
                )
            }
            "update" -> {
                if (number <= 0) return numberNeeded("pull request")
                val payload = JSONObject()
                val title = args.optString("title", "").trim()
                if (title.isNotEmpty()) payload.put("title", title)
                if (args.has("body")) payload.put("body", args.optString("body", ""))
                val state = args.optString("state", "").trim()
                if (state.isNotEmpty()) payload.put("state", state)
                if (payload.length() == 0) {
                    return ToolExecResult("update needs title, body or state to change.", success = false)
                }
                val reply = call("PATCH", "$root/pulls/$number", payload)
                problem(reply)?.let { return it }
                ToolExecResult("Updated #$number.")
            }
            "comment" -> {
                if (number <= 0) return numberNeeded("pull request")
                val body = args.optString("comment", "").trim().ifBlank { args.optString("body", "").trim() }
                if (body.isEmpty()) return ToolExecResult("comment needs the text in comment.", success = false)
                val reply = call("POST", "$root/issues/$number/comments", JSONObject().put("body", body))
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
                        "${item.optString("status", "")} ${item.optString("filename", "")} | " +
                            "+${item.optInt("additions", 0)} -${item.optInt("deletions", 0)}"
                    }
                )
            }
            else -> {
                if (number <= 0) return numberNeeded("pull request")
                val reply = call("PUT", "$root/pulls/$number/merge", JSONObject().put("merge_method", "merge"))
                problem(reply)?.let { return it }
                val outcome = HttpJson.objectOf(reply.body)
                val message = outcome?.optString("message", "").orEmpty()
                if (outcome?.optBoolean("merged", false) == true) {
                    ToolExecResult("Merged #$number. $message".trim())
                } else {
                    ToolExecResult("GitHub did not merge #$number: $message", success = false)
                }
            }
        }
    }

    private suspend fun branches(args: JSONObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val raw = args.optString("action", "")
        val action = HttpJson.action(raw, BRANCH_ACTIONS)
        if (action.isEmpty()) return unknown("Branches", raw, BRANCH_ACTIONS)
        val name = args.optString("name", "").trim()
        return when (action) {
            "list" -> {
                val reply = call("GET", "$root/branches?per_page=50")
                problem(reply)?.let { return it }
                val array = HttpJson.arrayOf(reply.body) ?: return notJson(reply)
                ToolExecResult(
                    HttpJson.rows(array, 50) { item ->
                        val sha = HttpJson.field(item, "commit.sha")
                        "${item.optString("name", "")} | ${sha.take(9)} | " +
                            (if (item.optBoolean("protected", false)) "protected" else "open")
                    }
                )
            }
            "get" -> {
                if (name.isEmpty()) return ToolExecResult("get needs the branch name in name.", success = false)
                val reply = call("GET", "$root/branches/${HttpJson.enc(name)}")
                problem(reply)?.let { return it }
                val branch = HttpJson.objectOf(reply.body) ?: return notJson(reply)
                ToolExecResult(
                    "branch: ${branch.optString("name", name)}\n" +
                        "sha: ${HttpJson.field(branch, "commit.sha")}\n" +
                        "protected: ${if (branch.optBoolean("protected", false)) "yes" else "no"}"
                )
            }
            "create" -> {
                val from = args.optString("from", "").trim()
                if (name.isEmpty() || from.isEmpty()) {
                    return ToolExecResult(
                        "create needs name and from (the sha, tag or branch to start from).",
                        success = false
                    )
                }
                val payload = JSONObject().put("ref", "refs/heads/$name").put("sha", from)
                val reply = call("POST", "$root/git/refs", payload)
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

    private suspend fun commits(args: JSONObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val limit = args.optInt("limit", 20).coerceIn(1, 100)
        val path = args.optString("path", "").trim()
        val branch = args.optString("branch", "").trim()
        val url = StringBuilder("$root/commits?per_page=$limit")
        if (path.isNotEmpty()) url.append("&path=").append(HttpJson.enc(path))
        if (branch.isNotEmpty()) url.append("&sha=").append(HttpJson.enc(branch))
        val reply = call("GET", url.toString())
        problem(reply)?.let { return it }
        val array = HttpJson.arrayOf(reply.body) ?: return notJson(reply)
        return ToolExecResult(
            HttpJson.rows(array, limit) { item ->
                val sha = item.optString("sha", "").take(9)
                val message = HttpJson.firstLine(HttpJson.field(item, "commit.message"), 120)
                val author = HttpJson.field(item, "commit.author.name")
                val date = HttpJson.field(item, "commit.author.date")
                "$sha | $author | $date | $message"
            }
        )
    }

    private suspend fun search(args: JSONObject): ToolExecResult {
        val kind = HttpJson.action(args.optString("kind", "code"), SEARCH_KINDS).ifBlank { "code" }
        val query = args.optString("query", "").trim()
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
        val items = payload.optJSONArray("items")
        val total = "total_count: ${payload.optInt("total_count", 0)}"
        val text = when (kind) {
            "repos" -> HttpJson.rows(items, 20) { item ->
                "${item.optString("full_name", "")} | ${item.optInt("stargazers_count", 0)} stars | " +
                    HttpJson.oneLine(item.optString("description", ""), 90)
            }
            "issues" -> HttpJson.rows(items, 20) { item ->
                "${item.optString("state", "")} | ${HttpJson.oneLine(item.optString("title", ""), 110)} | " +
                    item.optString("html_url", "")
            }
            "commits" -> HttpJson.rows(items, 20) { item ->
                "${item.optString("sha", "").take(9)} | ${HttpJson.field(item, "repository.full_name")} | " +
                    HttpJson.firstLine(HttpJson.field(item, "commit.message"), 100)
            }
            else -> HttpJson.rows(items, 20) { item ->
                "${HttpJson.field(item, "repository.full_name")} | ${item.optString("path", "")} | " +
                    item.optString("html_url", "")
            }
        }
        return ToolExecResult("$total\n$text")
    }

    private suspend fun actions(args: JSONObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val raw = args.optString("action", "")
        val action = HttpJson.action(raw, RUN_ACTIONS)
        if (action.isEmpty()) return unknown("Actions", raw, RUN_ACTIONS)
        val runId = args.optLong("run_id", 0L)
        val jobId = args.optLong("job_id", 0L)
        return when (action) {
            "runs" -> {
                val reply = call("GET", "$root/actions/runs?per_page=15")
                problem(reply)?.let { return it }
                val runs = HttpJson.objectOf(reply.body)?.optJSONArray("workflow_runs")
                ToolExecResult(
                    HttpJson.rows(runs, 15) { run ->
                        "${run.optLong("id", 0L)} | ${run.optString("name", "")} | " +
                            "${run.optString("status", "")}/${run.optString("conclusion", "")} | " +
                            "${run.optString("head_branch", "")} | ${run.optString("created_at", "")}"
                    }
                )
            }
            "run" -> {
                if (runId <= 0) return ToolExecResult("run needs run_id.", success = false)
                val reply = call("GET", "$root/actions/runs/$runId")
                problem(reply)?.let { return it }
                val run = HttpJson.objectOf(reply.body) ?: return notJson(reply)
                val sb = StringBuilder()
                sb.append(run.optString("name", "")).append(" #").append(run.optInt("run_number", 0)).append('\n')
                sb.append("status: ").append(run.optString("status", ""))
                sb.append(" | conclusion: ").append(run.optString("conclusion", "").ifBlank { "pending" }).append('\n')
                sb.append("branch: ").append(run.optString("head_branch", ""))
                sb.append(" | event: ").append(run.optString("event", ""))
                sb.append(" | started: ").append(run.optString("run_started_at", "")).append('\n')
                sb.append(run.optString("html_url", ""))
                ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
            }
            "jobs" -> {
                if (runId <= 0) return ToolExecResult("jobs needs run_id.", success = false)
                val reply = call("GET", "$root/actions/runs/$runId/jobs?per_page=30")
                problem(reply)?.let { return it }
                val jobs = HttpJson.objectOf(reply.body)?.optJSONArray("jobs")
                ToolExecResult(
                    HttpJson.rows(jobs, 30) { job ->
                        "${job.optLong("id", 0L)} | ${job.optString("name", "")} | " +
                            "${job.optString("status", "")}/${job.optString("conclusion", "")}"
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
                val workflow = args.optString("workflow", "").trim()
                val ref = args.optString("ref", "").trim()
                if (workflow.isEmpty() || ref.isEmpty()) {
                    return ToolExecResult("dispatch needs workflow (file name or id) and ref (branch).", success = false)
                }
                val reply = call(
                    "POST",
                    "$root/actions/workflows/${HttpJson.enc(workflow)}/dispatches",
                    JSONObject().put("ref", ref)
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

    private suspend fun releases(args: JSONObject): ToolExecResult {
        val root = repoBase(args) ?: return repoNeeded()
        val raw = args.optString("action", "")
        val action = HttpJson.action(raw, RELEASE_ACTIONS)
        if (action.isEmpty()) return unknown("Releases", raw, RELEASE_ACTIONS)
        val tag = args.optString("tag", "").trim()
        return when (action) {
            "list" -> {
                val reply = call("GET", "$root/releases?per_page=20")
                problem(reply)?.let { return it }
                val array = HttpJson.arrayOf(reply.body) ?: return notJson(reply)
                ToolExecResult(
                    HttpJson.rows(array, 20) { item ->
                        "${item.optString("tag_name", "")} | ${item.optString("name", "")} | " +
                            "${if (item.optBoolean("draft", false)) "draft" else "published"} | " +
                            item.optString("published_at", "")
                    }
                )
            }
            "get" -> {
                if (tag.isEmpty()) return ToolExecResult("get needs tag.", success = false)
                val reply = call("GET", "$root/releases/tags/${HttpJson.enc(tag)}")
                problem(reply)?.let { return it }
                val release = HttpJson.objectOf(reply.body)
                val sb = StringBuilder()
                sb.append(release?.optString("name", tag).orEmpty()).append(" (").append(tag).append(")\n")
                sb.append("draft: ").append(if (release?.optBoolean("draft", false) == true) "yes" else "no")
                sb.append(" | published: ").append(release?.optString("published_at", "").orEmpty()).append('\n')
                sb.append(release?.optString("html_url", "").orEmpty()).append("\n\n")
                sb.append(release?.optString("body", "").orEmpty().ifBlank { "(no notes)" })
                ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
            }
            else -> {
                if (tag.isEmpty()) return ToolExecResult("create needs tag.", success = false)
                val payload = JSONObject().put("tag_name", tag)
                val name = args.optString("name", "").trim()
                if (name.isNotEmpty()) payload.put("name", name)
                if (args.has("body")) payload.put("body", args.optString("body", ""))
                payload.put("draft", args.optBoolean("draft", false))
                val reply = call("POST", "$root/releases", payload)
                problem(reply)?.let { return it }
                val release = HttpJson.objectOf(reply.body)
                ToolExecResult(
                    "Created release ${release?.optString("tag_name", tag).orEmpty()} " +
                        release?.optString("html_url", "").orEmpty()
                )
            }
        }
    }

    private suspend fun user(): ToolExecResult {
        val reply = call("GET", "/user")
        problem(reply)?.let { return it }
        val account = HttpJson.objectOf(reply.body) ?: return notJson(reply)
        val sb = StringBuilder()
        sb.append("login: ").append(account.optString("login", "")).append('\n')
        sb.append("name: ").append(account.optString("name", "").ifBlank { "(none)" })
        sb.append(" | public repos: ").append(account.optInt("public_repos", 0))
        sb.append(" | followers: ").append(account.optInt("followers", 0)).append('\n')
        sb.append("profile: ").append(account.optString("html_url", "")).append('\n')
        val limits = call("GET", "/rate_limit")
        if (limits.ok) {
            val resources = HttpJson.objectOf(limits.body)?.optJSONObject("resources")
            val core = resources?.optJSONObject("core")
            val search = resources?.optJSONObject("search")
            if (core != null) {
                sb.append("core requests left: ").append(core.optInt("remaining", 0))
                sb.append(" of ").append(core.optInt("limit", 0)).append('\n')
            }
            if (search != null) {
                sb.append("search requests left: ").append(search.optInt("remaining", 0))
                sb.append(" of ").append(search.optInt("limit", 0)).append('\n')
            }
        } else {
            sb.append("rate limit: could not be read (${HttpJson.describe(limits.code)})\n")
        }
        return ToolExecResult(HttpJson.cut(sb.toString().trimEnd()))
    }
}
