package com.lucent.app.harness

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class HttpToolsTest {

    private val githubPermissions = mapOf(
        "github_repo" to HarnessPermission.READ,
        "github_contents" to HarnessPermission.READ,
        "github_issues" to HarnessPermission.GITHUB,
        "github_pulls" to HarnessPermission.GITHUB,
        "github_branches" to HarnessPermission.GITHUB,
        "github_commits" to HarnessPermission.READ,
        "github_search" to HarnessPermission.READ,
        "github_actions" to HarnessPermission.GITHUB,
        "github_releases" to HarnessPermission.GITHUB,
        "github_user" to HarnessPermission.READ
    )

    private val connectorPermissions = mapOf(
        "notion_api" to HarnessPermission.NETWORK,
        "slack_api" to HarnessPermission.NETWORK,
        "gdrive_api" to HarnessPermission.NETWORK,
        "onedrive_api" to HarnessPermission.NETWORK,
        "gitlab_api" to HarnessPermission.NETWORK,
        "jira_api" to HarnessPermission.NETWORK,
        "linear_api" to HarnessPermission.NETWORK,
        "webdav_request" to HarnessPermission.NETWORK,
        "http_request" to HarnessPermission.NETWORK,
        "connector_status" to HarnessPermission.READ
    )

    @Test
    fun prettyReindentsJsonWithTwoSpaces() {
        val pretty = HttpJson.pretty("{\"name\":\"lucent\",\"items\":[1,2]}")
        assertTrue(pretty.startsWith("{\n"), pretty)
        assertTrue(pretty.contains("\n  \"name\":"), pretty)
        assertTrue(pretty.contains("\"name\": \"lucent\""), pretty)
        assertTrue(pretty.contains("\n  \"items\": ["), pretty)
        assertTrue(pretty.lines().size >= 5, pretty)
        assertFalse(pretty.contains("{\"name\""), pretty)
    }

    @Test
    fun prettyKeepsPlainTextAndArrayJson() {
        assertEquals("not json at all", HttpJson.pretty("not json at all"))
        assertEquals("", HttpJson.pretty("   "))
        val pretty = HttpJson.pretty("[{\"a\":1},{\"b\":2}]")
        assertTrue(pretty.contains("\"a\": 1"), pretty)
        assertTrue(pretty.contains("\"b\": 2"), pretty)
        assertTrue(pretty.contains("\n"), pretty)
    }

    @Test
    fun prettyTruncatesToTheCharacterBudget() {
        val huge = "{\"body\":\"" + "x".repeat(9000) + "\"}"
        val pretty = HttpJson.pretty(huge)
        assertTrue(pretty.length <= HttpJson.REPLY_BUDGET + 80, "${pretty.length}")
        assertTrue(pretty.contains("output cut at"), pretty.takeLast(120))
        assertTrue(HttpJson.cut("short", 100) == "short")
        val inline = HttpJson.pretty(huge, HttpJson.INLINE_BUDGET)
        assertTrue(inline.length > HttpJson.REPLY_BUDGET, "${inline.length}")
    }

    @Test
    fun compactListPullsMappedFields() {
        val array = Json.parseToJsonElement(
            "[{\"number\":12,\"title\":\"Fix the bug\",\"user\":{\"login\":\"ada\"}}," +
                "{\"number\":13,\"title\":\"Ship it\"}]"
        ).jsonArray
        val text = HttpJson.compactList(
            array,
            listOf("number" to "#", "title" to "title", "user.login" to "by"),
            10
        )
        assertTrue(text.contains("- #: 12 | title: Fix the bug | by: ada"), text)
        assertTrue(text.contains("- #: 13 | title: Ship it"), text)
        assertFalse(text.contains("by: \n"), text)
        assertEquals("No items came back.", HttpJson.compactList(null, listOf("a" to "a")))
        assertEquals("No items came back.", HttpJson.compactList(buildJsonArray { }, listOf("a" to "a")))
    }

    @Test
    fun compactListReportsWhatItLeftOut() {
        val array = buildJsonArray {
            for (i in 1..30) add(buildJsonObject { put("n", i) })
        }
        val text = HttpJson.compactList(array, listOf("n" to "n"), 5)
        assertEquals(6, text.lines().size, text)
        assertTrue(text.endsWith("… and 25 more"), text)
        assertEquals("12", HttpJson.field(Json.parseToJsonElement("{\"a\":{\"b\":12}}").jsonObject, "a.b"))
        assertEquals("", HttpJson.field(Json.parseToJsonElement("{\"a\":1}").jsonObject, "a.b"))
        assertEquals("", HttpJson.field(Json.parseToJsonElement("{}").jsonObject, "missing"))
    }

    @Test
    fun actionParsingAcceptsKnownSpellings() {
        val allowed = listOf("list", "get", "page_create", "pipeline_jobs")
        assertEquals("list", HttpJson.action(" List ", allowed))
        assertEquals("get", HttpJson.action("GET", allowed))
        assertEquals("page_create", HttpJson.action("page-create", allowed))
        assertEquals("page_create", HttpJson.action("pageCreate", allowed))
        assertEquals("pipeline_jobs", HttpJson.action("pipelineJobs", allowed))
        assertEquals("", HttpJson.action("burn", allowed))
        assertEquals("", HttpJson.action("", allowed))
    }

    @Test
    fun urlValidationOnlyAllowsHttpAndHttps() {
        assertNull(HttpJson.urlProblem("https://api.github.com/repos/octocat/hello-world"))
        assertNull(HttpJson.urlProblem("http://example.com/hook"))
        assertNotNull(HttpJson.urlProblem("file:///etc/passwd"))
        assertNotNull(HttpJson.urlProblem("ftp://example.com/data"))
        assertNotNull(HttpJson.urlProblem("example.com/path"))
        assertNotNull(HttpJson.urlProblem("https:///nohost"))
        assertNotNull(HttpJson.urlProblem(""))
    }

    @Test
    fun httpRequestRefusesNonHttpUrlsBeforeAnyRequest() = runBlocking {
        withConfig(HarnessConfig()) { ctx ->
            val local = assertNotNull(
                ConnectorTools.execute(
                    ctx,
                    "http_request",
                    buildJsonObject { put("method", "GET"); put("url", "file:///etc/passwd") }
                )
            )
            assertFalse(local.success)
            assertTrue(local.summary.contains("http:// and https://"), local.summary)
            val ftp = assertNotNull(
                ConnectorTools.execute(ctx, "http_request", buildJsonObject { put("url", "ftp://example.com/data") })
            )
            assertFalse(ftp.success)
            val empty = assertNotNull(ConnectorTools.execute(ctx, "http_request", buildJsonObject { }))
            assertFalse(empty.success)
            assertTrue(empty.summary.contains("Give the URL"), empty.summary)
            val escape = assertNotNull(
                ConnectorTools.execute(
                    ctx,
                    "http_request",
                    buildJsonObject { put("url", "https://example.com"); put("save_to", "../escape.txt") }
                )
            )
            assertFalse(escape.success)
        }
    }

    @Test
    fun toolNamesAreUniqueSnakeCaseAndCarryTheirGroup() {
        val names = GitHubTools.tools.map { it.name } + ConnectorTools.tools.map { it.name }
        assertEquals(names.size, names.toSet().size, names.toString())
        assertEquals(githubPermissions.keys + connectorPermissions.keys, names.toSet())
        for (tool in GitHubTools.tools) {
            assertTrue(tool.name.matches(Regex("[a-z][a-z0-9_]*")), "${tool.name} is not snake_case")
            assertEquals(HarnessGroup.GITHUB, tool.group, tool.name)
            assertEquals(githubPermissions[tool.name], tool.permission, tool.name)
            assertEquals(HarnessPermission.READ == tool.permission, tool.readOnly, tool.name)
            assertTrue(tool.description.isNotBlank(), tool.name)
            assertTrue(tool.description.length <= 450, "${tool.name} has ${tool.description.length} characters")
            assertTrue(tool.params.all { it.name.matches(Regex("[a-z][a-z0-9_]*")) }, tool.name)
            assertTrue(tool.params.all { it.description.isNotBlank() }, tool.name)
        }
        for (tool in ConnectorTools.tools) {
            assertTrue(tool.name.matches(Regex("[a-z][a-z0-9_]*")), "${tool.name} is not snake_case")
            assertEquals(HarnessGroup.CONNECTORS, tool.group, tool.name)
            assertEquals(connectorPermissions[tool.name], tool.permission, tool.name)
            assertEquals(HarnessPermission.READ == tool.permission, tool.readOnly, tool.name)
            assertTrue(tool.description.isNotBlank(), tool.name)
            assertTrue(tool.description.length <= 450, "${tool.name} has ${tool.description.length} characters")
            assertTrue(tool.params.all { it.name.matches(Regex("[a-z][a-z0-9_]*")) }, tool.name)
        }
    }

    @Test
    fun githubReportsTheMissingTokenWithoutRequesting() = runBlocking {
        withConfig(HarnessConfig(githubToken = "", githubApi = "")) { ctx ->
            for (name in githubPermissions.keys) {
                val result = assertNotNull(
                    GitHubTools.execute(
                        ctx,
                        name,
                        buildJsonObject { put("owner", "octocat"); put("repo", "hello-world") }
                    ),
                    name
                )
                assertFalse(result.success, name)
                assertEquals(GitHubTools.NO_TOKEN, result.summary, name)
            }
        }
    }

    @Test
    fun githubValidatesArgumentsBeforeItReachesTheNetwork() = runBlocking {
        withConfig(HarnessConfig(githubToken = "test-token")) { ctx ->
            val noRepo = assertNotNull(GitHubTools.execute(ctx, "github_repo", buildJsonObject { }))
            assertFalse(noRepo.success)
            assertTrue(noRepo.summary.contains("owner and repo"), noRepo.summary)
            val badAction = assertNotNull(
                GitHubTools.execute(
                    ctx,
                    "github_issues",
                    buildJsonObject { put("owner", "a"); put("repo", "b"); put("action", "burn") }
                )
            )
            assertFalse(badAction.success)
            assertTrue(badAction.summary.contains("no action called"), badAction.summary)
            assertTrue(badAction.summary.contains("comment"), badAction.summary)
            val noNumber = assertNotNull(
                GitHubTools.execute(
                    ctx,
                    "github_pulls",
                    buildJsonObject { put("owner", "a"); put("repo", "b"); put("action", "diff") }
                )
            )
            assertFalse(noNumber.success)
            val noHead = assertNotNull(
                GitHubTools.execute(
                    ctx,
                    "github_pulls",
                    buildJsonObject { put("owner", "a"); put("repo", "b"); put("action", "create"); put("title", "t") }
                )
            )
            assertFalse(noHead.success)
            assertTrue(noHead.summary.contains("head"), noHead.summary)
            val noWorkflow = assertNotNull(
                GitHubTools.execute(
                    ctx,
                    "github_actions",
                    buildJsonObject { put("owner", "a"); put("repo", "b"); put("action", "dispatch") }
                )
            )
            assertFalse(noWorkflow.success)
            val noUpdate = assertNotNull(
                GitHubTools.execute(
                    ctx,
                    "github_releases",
                    buildJsonObject { put("owner", "a"); put("repo", "b"); put("action", "create") }
                )
            )
            assertFalse(noUpdate.success)
            assertTrue(noUpdate.summary.contains("tag"), noUpdate.summary)
        }
    }

    @Test
    fun branchDeleteNeedsTheAllowPolicy() = runBlocking {
        val approvals = mapOf(HarnessPermission.GITHUB.key to Approval.CONFIRM.key)
        withConfig(HarnessConfig(githubToken = "test-token", approvals = approvals)) { ctx ->
            val refused = assertNotNull(
                GitHubTools.execute(
                    ctx,
                    "github_branches",
                    buildJsonObject { put("owner", "a"); put("repo", "b"); put("action", "delete"); put("name", "old") }
                )
            )
            assertFalse(refused.success)
            assertTrue(refused.summary.contains("approval policy"), refused.summary)
            assertTrue(refused.summary.contains("Nothing was deleted"), refused.summary)
        }
    }

    @Test
    fun connectorsReportMissingConfiguration() = runBlocking {
        withConfig(HarnessConfig()) { ctx ->
            val notion = assertNotNull(
                ConnectorTools.execute(ctx, "notion_api", buildJsonObject { put("action", "search") })
            )
            assertFalse(notion.success)
            assertTrue(notion.summary.contains("The Notion connector is not configured."), notion.summary)
            assertTrue(notion.summary.contains("Settings → Agent → Connectors"), notion.summary)
            val slack = assertNotNull(ConnectorTools.execute(ctx, "slack_api", buildJsonObject { put("action", "history") }))
            assertTrue(slack.summary.contains("The Slack connector is not configured."), slack.summary)
            val drive = assertNotNull(ConnectorTools.execute(ctx, "gdrive_api", buildJsonObject { put("action", "list") }))
            assertTrue(drive.summary.contains("Google Drive connector is not configured"), drive.summary)
            val one = assertNotNull(ConnectorTools.execute(ctx, "onedrive_api", buildJsonObject { put("action", "list") }))
            assertTrue(one.summary.contains("OneDrive connector is not configured"), one.summary)
            val gitlab = assertNotNull(ConnectorTools.execute(ctx, "gitlab_api", buildJsonObject { put("action", "projects") }))
            assertTrue(gitlab.summary.contains("GitLab connector is not configured"), gitlab.summary)
            val jira = assertNotNull(ConnectorTools.execute(ctx, "jira_api", buildJsonObject { put("action", "issue_get") }))
            assertTrue(jira.summary.contains("Jira connector is not configured"), jira.summary)
            val linear = assertNotNull(ConnectorTools.execute(ctx, "linear_api", buildJsonObject { put("action", "teams") }))
            assertTrue(linear.summary.contains("Linear connector is not configured"), linear.summary)
            val webdav = assertNotNull(
                ConnectorTools.execute(ctx, "webdav_request", buildJsonObject { put("action", "list") })
            )
            assertTrue(webdav.summary.contains("WebDAV connector is not configured"), webdav.summary)
        }
    }

    @Test
    fun connectorsWithoutTokensRefuseBeforeAnyRequest() = runBlocking {
        val connectors = listOf(
            ConnectorConfig(id = "notion"),
            ConnectorConfig(id = "slack"),
            ConnectorConfig(id = "gdrive"),
            ConnectorConfig(id = "onedrive"),
            ConnectorConfig(id = "gitlab"),
            ConnectorConfig(id = "linear"),
            ConnectorConfig(id = "jira", baseUrl = "https://example.atlassian.net"),
            ConnectorConfig(id = "webdav", baseUrl = "https://dav.example.com")
        )
        withConfig(HarnessConfig(connectors = connectors)) { ctx ->
            val calls = mapOf(
                "notion_api" to buildJsonObject { put("action", "search") },
                "slack_api" to buildJsonObject { put("action", "post_message") },
                "gdrive_api" to buildJsonObject { put("action", "list") },
                "onedrive_api" to buildJsonObject { put("action", "list") },
                "gitlab_api" to buildJsonObject { put("action", "projects") },
                "linear_api" to buildJsonObject { put("action", "teams") },
                "jira_api" to buildJsonObject { put("action", "issue_get") },
                "webdav_request" to buildJsonObject { put("action", "list") }
            )
            for ((name, args) in calls) {
                val result = assertNotNull(ConnectorTools.execute(ctx, name, args), name)
                assertFalse(result.success, name)
                assertTrue(result.summary.contains("without a token"), "$name: ${result.summary}")
            }
        }
    }

    @Test
    fun connectorAliasesAndConfiguredIdsAreFound() = runBlocking {
        val connectors = listOf(
            ConnectorConfig(id = "google_drive", token = "drive-token"),
            ConnectorConfig(id = "jira", token = "jira-token", baseUrl = "", account = "me@example.com")
        )
        withConfig(HarnessConfig(connectors = connectors)) { ctx ->
            val drive = assertNotNull(ConnectorTools.execute(ctx, "gdrive_api", buildJsonObject { put("action", "get") }))
            assertFalse(drive.success)
            assertTrue(drive.summary.contains("file_id"), drive.summary)
            val jira = assertNotNull(ConnectorTools.execute(ctx, "jira_api", buildJsonObject { put("action", "issue_get") }))
            assertFalse(jira.success)
            assertTrue(jira.summary.contains("no site URL"), jira.summary)
        }
    }

    @Test
    fun connectorStatusListsConfigurationWithoutSecrets() = runBlocking {
        val config = HarnessConfig(
            githubToken = "ghp-secret-value",
            githubApi = "https://api.github.com",
            connectors = listOf(ConnectorConfig(id = "slack", token = "xoxb-secret-value", account = "lucent"))
        )
        withConfig(config) { ctx ->
            val status = assertNotNull(ConnectorTools.execute(ctx, "connector_status", buildJsonObject { }))
            assertTrue(status.success)
            assertTrue(status.summary.contains("GitHub: token set"), status.summary)
            assertTrue(status.summary.contains("https://api.github.com"), status.summary)
            assertTrue(status.summary.contains("Slack (slack): configured, token saved (account: lucent)"), status.summary)
            assertTrue(status.summary.contains("Notion (notion): not configured"), status.summary)
            assertFalse(status.summary.contains("ghp-secret-value"), status.summary)
            assertFalse(status.summary.contains("xoxb-secret-value"), status.summary)
        }
    }

    @Test
    fun executeReturnsNullForForeignNames() = runBlocking {
        withConfig(HarnessConfig(githubToken = "test-token")) { ctx ->
            assertNull(GitHubTools.execute(ctx, "read_file", buildJsonObject { }))
            assertNull(GitHubTools.execute(ctx, "connector_status", buildJsonObject { }))
            assertNull(GitHubTools.execute(ctx, "", buildJsonObject { }))
            assertNull(ConnectorTools.execute(ctx, "github_repo", buildJsonObject { }))
            assertNull(ConnectorTools.execute(ctx, "mcp_call", buildJsonObject { }))
            assertNull(ConnectorTools.execute(ctx, "", buildJsonObject { }))
        }
    }

    @Test
    fun jsonParsingHelpersAreForgiving() {
        assertNotNull(HttpJson.objectOf("{\"a\":1}"))
        assertNull(HttpJson.objectOf("nope"))
        assertNull(HttpJson.objectOf("[1,2]"))
        assertNotNull(HttpJson.arrayOf("[1,2]"))
        assertNull(HttpJson.arrayOf("{}"))
        assertEquals("plain", HttpJson.text("plain"))
        assertEquals("", HttpJson.text(null))
        assertEquals("7", HttpJson.text(7))
        assertEquals("a b", HttpJson.oneLine("  a\n b ", 40))
        assertEquals("first", HttpJson.firstLine("\n  first\nsecond", 40))
        assertTrue(HttpJson.describe(429).contains("429"))
        assertTrue(HttpJson.describe(0).contains("no response"))
        assertTrue(HttpJson.explain("{\"message\":\"Bad credentials\"}").contains("Bad credentials"))
    }

    private suspend fun withConfig(config: HarnessConfig, body: suspend (HarnessCtx) -> Unit) {
        val dir = File(System.getProperty("java.io.tmpdir"), "lucent-http-tools-${System.nanoTime()}")
        dir.mkdirs()
        val savedConfig = HarnessRuntime.config()
        val savedAndroid = HarnessRuntime.android
        val active = config.copy(workspace = dir.path)
        HarnessRuntime.update(active)
        HarnessRuntime.android = false
        try {
            body(ctxFor(active, dir))
        } finally {
            HarnessRuntime.install(savedConfig)
            HarnessRuntime.android = savedAndroid
            dir.deleteRecursively()
        }
    }

    private fun ctxFor(config: HarnessConfig, dir: File): HarnessCtx {
        val ctx = allocateCtx()
        fill(ctx, "config", config)
        fill(ctx, "capabilities", emptySet<String>())
        fill(ctx, "android", false)
        fill(ctx, "workspacePath", dir.path)
        return ctx
    }

    private fun allocateCtx(): HarnessCtx {
        val type = Class.forName("sun.misc.Unsafe")
        val field = type.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val allocate = type.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, HarnessCtx::class.java) as HarnessCtx
    }

    private fun fill(target: HarnessCtx, name: String, value: Any) {
        val field = HarnessCtx::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }
}
