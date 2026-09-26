package com.lucent.app.harness

import android.content.Context
import com.lucent.app.harness.mcp.mcpCommandLine
import com.lucent.app.harness.ooxml.documentText
import com.lucent.app.harness.ooxml.escapeXml
import com.lucent.app.harness.ooxml.xml
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class SupportContext(private val root: File) : Context() {
    override val applicationContext: Context get() = this
    override val filesDir: File get() = File(root, "files").apply { mkdirs() }
}

class HarnessSupportTest {

    @Test
    fun auditEntriesAreFormattedForTheActivityLog() {
        val entry = AuditEntry(
            at = 1_700_000_000_000L,
            tool = "plugin:ubuntu",
            group = "plugins",
            permission = "execute",
            approval = "allow",
            arguments = "install",
            outcome = "ok",
            detail = "unpacked",
            millis = 12L,
            files = emptyList()
        )
        val line = AuditTrail.format(entry)
        assertTrue(line.contains("plugin:ubuntu"), line)
        assertTrue(line.contains("unpacked"), line)
    }

    @Test
    fun subAgentReportsLandInsideTheWorkspace() {
        val root = java.nio.file.Files.createTempDirectory("lucent-reports").toFile()
        val previousHost = HarnessRuntime.host
        HarnessRuntime.host = object : HarnessHost {
            override val android: Boolean = false
            override fun defaultWorkspace(): File = File(root, "workspace").apply { mkdirs() }
            override fun filesDir(): File = File(root, "files").apply { mkdirs() }
            override fun cacheDir(): File = File(root, "cache").apply { mkdirs() }
        }
        try {
            val folder = SubAgentReports.folder()
            assertTrue(Workspace.isInside(folder, HarnessRuntime.workspace()), folder.path)
        } finally {
            HarnessRuntime.host = previousHost
            root.deleteRecursively()
        }
    }

    @Test
    fun ooxmlHelpersEscapeAndBuildDocuments() {
        assertEquals("a &amp; b", escapeXml("a & b"))
        val body = xml("root") {
            attr("count", 2)
            child("item").text("hello & bye")
        }
        val rendered = documentText(body)
        assertTrue(rendered.contains("count=\"2\""), rendered)
        assertTrue(rendered.contains("hello &amp; bye"), rendered)
    }

    @Test
    fun mcpCommandsAreSplitForTheShell() {
        val server = McpServer(
            id = "files",
            name = "Files",
            command = "npx",
            arguments = "-y @modelcontextprotocol/server-filesystem /tmp"
        )
        val line = mcpCommandLine(server)
        assertEquals("npx", line.first())
        assertTrue(line.contains("@modelcontextprotocol/server-filesystem"), line.toString())
        assertTrue(line.contains("/tmp"), line.toString())
    }

    @Test
    fun specificationsNameEveryGroupAndPermission() {
        HarnessGroup.entries.forEach { group ->
            assertTrue(group.key.isNotBlank(), "a group has no key")
            assertTrue(group.title.isNotBlank(), "${group.key} has no title")
        }
        HarnessPermission.entries.forEach { permission ->
            assertTrue(permission.key.isNotBlank(), "a permission has no key")
            assertTrue(permission.title.isNotBlank(), "${permission.key} has no title")
        }
    }
}
