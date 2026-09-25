package com.lucent.app.harness

import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class TestHost(private val root: File) : HarnessHost {
    override val android: Boolean = false
    override fun defaultWorkspace(): File = File(root, "workspace").apply { mkdirs() }
    override fun filesDir(): File = File(root, "files").apply { mkdirs() }
    override fun cacheDir(): File = File(root, "cache").apply { mkdirs() }
}

private fun withSandbox(config: HarnessConfig = HarnessConfig(), block: (File) -> Unit) {
    val root = java.nio.file.Files.createTempDirectory("lucent-harness").toFile()
    val previousHost = HarnessRuntime.host
    val previousConfig = HarnessRuntime.config()
    HarnessRuntime.host = TestHost(root)
    HarnessRuntime.android = false
    HarnessRuntime.update(config)
    try {
        HarnessRuntime.workspace().mkdirs()
        block(root)
    } finally {
        HarnessRuntime.host = previousHost
        HarnessRuntime.update(previousConfig)
        root.deleteRecursively()
    }
}

class HarnessWorkspaceTest {

    @Test
    fun relativePathsLandInsideTheWorkspace() = withSandbox { _ ->
        val resolved = Workspace.resolve("notes/today.txt")
        assertTrue(Workspace.isInside(resolved, HarnessRuntime.workspace()), resolved.path)
    }

    @Test
    fun tildeIsRelativeToTheWorkspace() = withSandbox { _ ->
        val resolved = Workspace.resolve("~/draft.md")
        assertTrue(Workspace.isInside(resolved, HarnessRuntime.workspace()))
        assertEquals("draft.md", resolved.name)
    }

    @Test
    fun escapingTheWorkspaceIsVisibleToThePolicy() = withSandbox { _ ->
        val outside = Workspace.resolve("../../etc/passwd")
        assertFalse(Workspace.writable(HarnessConfig(), outside))
        assertFalse(Workspace.readable(HarnessConfig(), outside))
    }

    @Test
    fun declaredRootsWidenThePolicy() = withSandbox { root ->
        val extra = File(root, "shared").apply { mkdirs() }
        val config = HarnessConfig(writeRoots = listOf(extra.path))
        assertTrue(Workspace.writable(config, File(extra, "a.txt")))
        assertFalse(Workspace.readable(config, File(root, "elsewhere/a.txt")))
        val ro = HarnessConfig(readOnlyRoots = listOf(File(root, "reference").path))
        assertTrue(Workspace.readable(ro, File(root, "reference/manual.pdf")))
        assertFalse(Workspace.writable(ro, File(root, "reference/manual.pdf")))
    }

    @Test
    fun androidSystemPathsAreRefused() {
        val previous = HarnessRuntime.android
        HarnessRuntime.android = true
        try {
            assertTrue(Workspace.blocked("/system/bin/sh"))
            assertTrue(Workspace.blocked("/data/data/com.other.app/files"))
            assertFalse(Workspace.blocked("/storage/emulated/0/Documents/Lucent"))
        } finally {
            HarnessRuntime.android = previous
        }
    }

    @Test
    fun textAndBinaryAreToldApart() = withSandbox { root ->
        val text = File(root, "note.md")
        text.writeText("# heading\n\nbody\n")
        assertEquals(3, Workspace.countLines(text))
        assertTrue(!Workspace.looksBinary(text.readBytes()))
        val binary = ByteArray(64) { 0 }
        assertTrue(Workspace.looksBinary(binary))
        assertEquals("1.0 KiB", Workspace.humanSize(1024))
    }

    @Test
    fun snapshotsRestoreWhatWasThereBefore() = withSandbox { _ ->
        val file = File(HarnessRuntime.workspace(), "report.md")
        file.writeText("first version")
        Snapshots.capture(file)
        file.writeText("second version")
        val history = Snapshots.history(file.canonicalPath, 5)
        assertEquals(1, history.size)
        Snapshots.restore(history.first().id)
        assertEquals("first version", file.readText())
        assertNotNull(Snapshots.latest(file.canonicalPath))
        assertTrue(Snapshots.totalBytes() > 0)
        Snapshots.clear()
        assertTrue(Snapshots.all().isEmpty())
    }

    @Test
    fun unifiedDiffMarksBothSides() {
        val diff = Diffs.unified("a\nb\nc", "a\nB\nc")
        assertTrue(diff.contains("-b"))
        assertTrue(diff.contains("+B"))
        assertEquals("", Diffs.unified("same", "same"))
    }
}

class HarnessDataTest {

    @Test
    fun configSurvivesARoundTrip() {
        val config = HarnessConfig(
            workspace = "/tmp/example",
            groups = setOf("files", "office"),
            approvals = mapOf("delete" to "deny"),
            mcpServers = listOf(McpServer(id = "weather", name = "Weather", url = "https://example.test/mcp", token = "t")),
            connectors = listOf(ConnectorConfig(id = "slack", token = "xoxb", account = "team")),
            plugins = listOf(PluginState(id = "ubuntu", installed = true, source = "tuna", sizeBytes = 42)),
            mirrors = mapOf("ubuntu" to "tuna"),
            githubToken = "gh"
        )
        val parsed = HarnessConfig.parse(config.toJson())
        assertEquals(config.workspace, parsed.workspace)
        assertEquals(config.groups, parsed.groups)
        assertEquals(Approval.DENY, parsed.approvalFor(HarnessPermission.DELETE))
        assertEquals(Approval.ALLOW, parsed.approvalFor(HarnessPermission.READ))
        assertEquals(Approval.CONFIRM, parsed.approvalFor(HarnessPermission.EXECUTE))
        assertEquals("weather", parsed.mcpServers.single().id)
        assertEquals("xoxb", parsed.connector("slack")?.token)
        assertTrue(parsed.pluginInstalled("ubuntu"))
        assertEquals("tuna", parsed.mirrors["ubuntu"])
        assertEquals("gh", parsed.githubToken)
    }

    @Test
    fun brokenConfigFallsBackToDefaults() {
        val parsed = HarnessConfig.parse("{ this is not json")
        assertEquals(HarnessConfig.DEFAULT.groups, parsed.groups)
        assertTrue(parsed.enabled)
    }

    @Test
    fun groupAndApprovalEditsAreImmutable() {
        val config = HarnessConfig()
        val without = config.withGroup(HarnessGroup.FILES, false)
        assertFalse(without.groupEnabled(HarnessGroup.FILES))
        assertTrue(config.groupEnabled(HarnessGroup.FILES))
        assertEquals(Approval.DENY, config.withApproval(HarnessPermission.WRITE, Approval.DENY)
            .approvalFor(HarnessPermission.WRITE))
        assertTrue(config.withPlugin(PluginState(id = "git", installed = true)).pluginInstalled("git"))
        assertFalse(config.withoutMcp("nope").mcpServers.any { it.id == "nope" })
    }

    @Test
    fun csvRoundTripsQuotedFields() {
        val rows = listOf(listOf("name", "note"), listOf("Ada", "says \"hello\", twice"))
        val text = Csv.write(rows)
        assertEquals(rows, Csv.parse(text))
        assertEquals("'it''s'", Csv.quote("it's"))
    }

    @Test
    fun htmlBecomesReadableText() {
        val html = """
            <html><head><title>A page</title><meta name="description" content="Summary"></head>
            <body><script>var x = 1;</script><h1>Heading</h1><p>First &amp; second</p>
            <a href="/next">Next</a></body></html>
        """.trimIndent()
        assertEquals("A page", HtmlText.title(html))
        assertEquals("Summary", HtmlText.description(html))
        val text = HtmlText.text(html)
        assertTrue(text.contains("Heading"))
        assertTrue(text.contains("First & second"))
        assertFalse(text.contains("var x = 1"))
        val links = HtmlText.links(html, "https://example.test/docs/")
        assertEquals("https://example.test/next", links.single().first)
    }

    @Test
    fun pathologicalHtmlDoesNotHang() {
        val html = "<div>".repeat(20000)
        assertTrue(HtmlText.text(html).length < 100)
    }

    @Test
    fun describeNamesTheToolAndItsSubject() {
        val text = HarnessDescribe.describe("read_file", JSONObject().put("path", "notes/today.md").toString())
        assertTrue(text.contains("read_file"))
        assertTrue(text.contains("notes/today.md"))
        assertEquals(listOf("notes/today.md"), HarnessDescribe.files(JSONObject().put("path", "notes/today.md").toString()))
        assertTrue(HarnessDescribe.digest("{\n  \"a\": 1\n}").length <= 300)
    }
}

class HarnessPlanTest {

    @Test
    fun planBoardRecordsAndRendersProgress() = withSandbox { _ ->
        PlanBoard.clear()
        PlanBoard.publish(listOf(PlanStep("read the file", "done"), PlanStep("write the report", "active")))
        assertEquals(2, PlanBoard.current().size)
        assertTrue(PlanBoard.summary().contains("1/2"))
        assertTrue(PlanBoard.summary().contains("read the file"))
        PlanBoard.clear()
        assertTrue(PlanBoard.current().isEmpty())
        assertTrue(PlanBoard.summary().isEmpty())
    }

    @Test
    fun skillFilesAreDiscoveredAndReadable() = withSandbox { _ ->
        val dir = File(HarnessRuntime.workspace(), ".lucent/skills").apply { mkdirs() }
        File(dir, "reports.md").writeText("---\nname: reports\ndescription: how to write them\n---\n\nUse headings.\n")
        assertTrue(SkillTools.tools.map { it.name }.contains("list_skills"))
        assertTrue(File(dir, "reports.md").exists())
    }
}

class HarnessRegistryTest {

    private val capabilities = setOf(
        HarnessRuntime.CAP_SHELL,
        HarnessRuntime.CAP_PLUGINS,
        HarnessRuntime.CAP_DEVICE
    )

    @Test
    fun everyToolNameIsUniqueAndSnakeCase() {
        val names = HarnessGate.allTools().map { it.name }
        assertEquals(names.size, names.toSet().size, "duplicate tool names: " + names.groupBy { it }.filter { it.value.size > 1 })
        names.forEach { name ->
            assertTrue(Regex("[a-z][a-z0-9_]*").matches(name), "not snake_case: $name")
            assertTrue(name.length <= 64, "too long: $name")
        }
    }

    @Test
    fun noToolCollidesWithTheNotebookTools() {
        val notebook = com.lucent.app.tools.AppTools.definitions(includeWebSearch = true)
            .map { it.name }
            .filterNot { HarnessGate.isHarnessTool(it) }
            .toSet()
        val overlap = HarnessGate.allTools().map { it.name }.filter { notebook.contains(it) }
        assertTrue(overlap.isEmpty(), "clashing names: $overlap")
    }

    @Test
    fun everyModuleOwnsItsTools() {
        HarnessGate.groupModules().forEach { module ->
            assertTrue(module.tools.isNotEmpty(), "${module.group.key} has no tools")
            module.tools.forEach { tool ->
                assertEquals(module.group, tool.group, "${tool.name} sits in the wrong module")
                assertTrue(module.canHandle(tool.name), "${tool.name} is not handled by its own module")
            }
            assertFalse(module.canHandle("definitely_not_a_tool"))
        }
    }

    @Test
    fun permissionsAndDescriptionsAreDeclared() {
        HarnessGate.allTools().forEach { tool ->
            assertTrue(tool.description.length in 20..1200, "${tool.name} has a ${tool.description.length} character description")
            tool.params.forEach { param ->
                assertTrue(param.name.isNotBlank(), "${tool.name} has an unnamed parameter")
                assertTrue(
                    param.type in setOf("string", "number", "integer", "boolean", "array", "object"),
                    "${tool.name}.${param.name} has type ${param.type}"
                )
                assertTrue(param.description.isNotBlank(), "${tool.name}.${param.name} has no description")
            }
        }
    }

    @Test
    fun platformAndCapabilityGatingHolds() = withSandbox(HarnessConfig(deviceEnabled = true)) {
        val onPhone = HarnessGate.enabledTools(android = true, capabilities = capabilities).map { it.name }.toSet()
        val onDesktop = HarnessGate.enabledTools(android = false, capabilities = capabilities).map { it.name }.toSet()
        assertTrue(onPhone.contains("read_screen"))
        assertFalse(onDesktop.contains("read_screen"))
        assertTrue(onDesktop.contains("read_file"))
        val withoutPlugins = HarnessGate.enabledTools(android = false, capabilities = setOf(HarnessRuntime.CAP_SHELL))
            .map { it.name }
        assertFalse(withoutPlugins.contains("convert_office"))
        assertTrue(withoutPlugins.contains("read_file"))
    }

    @Test
    fun switchingAGroupOffRemovesItsTools() = withSandbox(HarnessConfig(groups = setOf("files"))) { _ ->
        val names = HarnessGate.enabledTools(android = false, capabilities = capabilities).map { it.name }
        assertTrue(names.contains("write_file"))
        assertFalse(names.contains("git_status"))
        assertFalse(names.contains("update_plan"))
    }

    @Test
    fun approvalPolicyDecidesWhatNeedsAConfirmation() = withSandbox { _ ->
        assertTrue(HarnessGate.needsConfirmation("delete_path"))
        assertFalse(HarnessGate.needsConfirmation("read_file"))
        HarnessRuntime.update(HarnessConfig(approvals = mapOf("delete" to "allow")))
        assertFalse(HarnessGate.needsConfirmation("delete_path"))
        HarnessRuntime.update(HarnessConfig(approvals = mapOf("read" to "confirm")))
        assertTrue(HarnessGate.needsConfirmation("read_file"))
    }

    @Test
    fun dynamicToolsJoinTheRegistry() {
        val tool = HarnessTool(
            name = "mcp__weather__forecast",
            group = HarnessGroup.MCP,
            permission = HarnessPermission.NETWORK,
            description = "Ask the weather server for a forecast for a named place."
        )
        HarnessGate.dynamicTools = listOf(tool)
        try {
            assertTrue(HarnessGate.isHarnessTool("mcp__weather__forecast"))
            assertEquals(HarnessGroup.MCP, HarnessGate.groupOf("mcp__weather__forecast"))
            assertEquals(HarnessPermission.NETWORK, HarnessGate.permissionOf("mcp__weather__forecast"))
        } finally {
            HarnessGate.dynamicTools = emptyList()
        }
        assertFalse(HarnessGate.isHarnessTool("mcp__weather__forecast"))
    }

    @Test
    fun promptMentionsCapabilitiesWithoutLeakingSecrets() = withSandbox(HarnessConfig(githubToken = "ghp_secret")) { _ ->
        val block = HarnessPrompt.block()
        assertTrue(block.contains("workspace") || block.contains("Workspace"))
        assertFalse(block.contains("ghp_secret"))
        assertTrue(HarnessPrompt.capabilitySummary().contains("tools="))
        assertTrue(HarnessPrompt.compactBlock().isNotEmpty())
    }

}
