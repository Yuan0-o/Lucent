package com.lucent.app.harness

import com.lucent.app.harness.plugins.PluginCatalog
import com.lucent.app.harness.plugins.PluginDownload
import com.lucent.app.harness.plugins.PluginManager
import com.lucent.app.harness.plugins.PluginPreflight
import com.lucent.app.harness.plugins.PluginSource
import com.lucent.app.harness.plugins.PluginSpec
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class PreflightHost(private val root: File) : HarnessHost {
    override val android: Boolean = false
    override fun defaultWorkspace(): File = File(root, "workspace").apply { mkdirs() }
    override fun filesDir(): File = File(root, "files").apply { mkdirs() }
    override fun cacheDir(): File = File(root, "cache").apply { mkdirs() }
}

private class PreflightShell(private val respond: (String) -> ShellOutcome) : HarnessShell {
    override val id: String = "preflight-scripted"
    override fun isReady(): Boolean = true
    override fun describe(): String = "the preflight test shell"
    val seen = mutableListOf<String>()
    override suspend fun run(
        command: String,
        workdir: File?,
        timeoutSeconds: Int,
        env: Map<String, String>
    ): ShellOutcome {
        seen.add(command)
        return respond(command)
    }
}

private fun preflightSandbox(android: Boolean = false, block: (File) -> Unit) {
    val root = java.nio.file.Files.createTempDirectory("lucent-preflight").toFile()
    val previousHost = HarnessRuntime.host
    val previousShell = HarnessRuntime.shell
    val previousPluginHost = HarnessRuntime.pluginHost
    val previousAndroid = HarnessRuntime.android
    val previousConfig = HarnessRuntime.config()
    HarnessRuntime.host = PreflightHost(root)
    HarnessRuntime.android = android
    HarnessRuntime.update(HarnessConfig(enabled = true))
    try {
        block(root)
    } finally {
        HarnessRuntime.host = previousHost
        HarnessRuntime.shell = previousShell
        HarnessRuntime.pluginHost = previousPluginHost
        HarnessRuntime.android = previousAndroid
        HarnessRuntime.update(previousConfig)
        root.deleteRecursively()
    }
}

private fun serveBytes(body: ByteArray): Pair<HttpServer, String> {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/file.bin") { exchange ->
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }
    server.start()
    return server to "http://127.0.0.1:${server.address.port}/file.bin"
}

private fun noScriptPlugin() = PluginSpec(
    id = "noscript",
    name = "NoScript",
    summary = "has no install script here",
    android = false,
    desktop = true,
    bytes = 0L,
    sources = emptyList(),
    detectCommand = "command -v noscript",
    installScript = "",
    licence = "MIT",
    homepage = "https://example.invalid",
    needsShell = false,
    windowsInstall = ""
)

private fun noSourcePlugin() = PluginSpec(
    id = "nosource",
    name = "NoSource",
    summary = "wants a file nobody ships",
    android = false,
    desktop = true,
    bytes = 0L,
    sources = emptyList(),
    detectCommand = "command -v nosource",
    installScript = "unpack {file}",
    licence = "MIT",
    homepage = "https://example.invalid",
    needsShell = false,
    windowsInstall = "unpack {file}"
)

private fun deadSourcePlugin() = PluginSpec(
    id = "deadd",
    name = "DeadSource",
    summary = "points at a closed port",
    android = false,
    desktop = true,
    bytes = 0L,
    sources = listOf(PluginSource("dead", "Dead mirror", "http://127.0.0.1:1/file.bin")),
    detectCommand = "command -v deadd",
    installScript = "unpack {file}",
    licence = "MIT",
    homepage = "https://example.invalid",
    needsShell = false,
    windowsInstall = "unpack {file}"
)

private fun termuxStylePlugin() = PluginSpec(
    id = "git-termux-test",
    name = "GitTermuxTest",
    summary = "termux package install",
    android = true,
    desktop = false,
    bytes = 0L,
    sources = emptyList(),
    detectCommand = "command -v git",
    installScript = "pkg install -y git",
    licence = "MIT",
    homepage = "https://example.invalid",
    needsShell = true
)

class PluginPreflightTest {

    @Test
    fun aPluginWithNoScriptIsBlockedBeforeAnythingRuns() = preflightSandbox { _ ->
        val report = runBlocking {
            PluginPreflight.inspect(noScriptPlugin(), PluginSource("", "", ""), false)
        }
        assertTrue(report.blocked)
        assertEquals("no_script", report.problems.single().code)
        assertEquals(PluginFailure.NO_SCRIPT, PluginPreflight.failureOf(report))
    }

    @Test
    fun aPluginWithNoDownloadSourceIsBlocked() = preflightSandbox { _ ->
        val report = runBlocking {
            PluginPreflight.inspect(noSourcePlugin(), PluginSource("", "", ""), false)
        }
        assertTrue(report.blocked)
        assertTrue(report.problems.any { it.code == "no_source" })
        assertEquals(PluginFailure.DOWNLOAD, PluginPreflight.failureOf(report))
    }

    @Test
    fun unreachableMirrorsAreReportedWithSpeeds() = preflightSandbox { _ ->
        val report = runBlocking {
            PluginPreflight.inspect(deadSourcePlugin(), PluginSource("", "", ""), false)
        }
        assertTrue(report.blocked)
        assertTrue(report.problems.any { it.code == "source_unreachable" })
        assertEquals(1, report.mirrorResults.size)
        assertEquals(0L, report.mirrorResults.single().second)
        val text = PluginPreflight.render(report)
        assertTrue(text.contains("source_unreachable"))
        assertTrue(text.contains("no answer"))
    }

    @Test
    fun aStagedFileIsReusedInsteadOfDownloadedAgain() = preflightSandbox { _ ->
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        val (server, url) = serveBytes(bytes)
        try {
            val plugin = PluginSpec(
                id = "payload",
                name = "Payload",
                summary = "downloads something",
                android = false,
                desktop = true,
                bytes = bytes.size.toLong(),
                sources = listOf(PluginSource("test", "Test mirror", url, official = true, bytes = bytes.size.toLong())),
                detectCommand = "command -v payload",
                installScript = "unpack {file}",
                licence = "MIT",
                homepage = "https://example.invalid",
                needsShell = false,
                windowsInstall = "unpack {file}"
            )
            val source = plugin.sources.single()
            val target = PluginPreflight.targetFile(plugin, source)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            assertNotNull(PluginPreflight.reusableStaged(plugin, source))
            val report = runBlocking { PluginPreflight.inspect(plugin, source, false) }
            assertFalse(report.blocked)
            assertTrue(report.notes.any { it.contains(target.name) })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun thePackageListIsRefreshedOncePerProcess() = preflightSandbox(android = true) { _ ->
        val shell = PreflightShell { command ->
            when {
                command == "pkg update" -> ShellOutcome(true, "updated", "", 0)
                command.contains("pkg install") -> ShellOutcome(true, "installed", "", 0)
                else -> ShellOutcome(true, "", "", 0)
            }
        }
        HarnessRuntime.shell = shell
        val manager = PluginManager.testInstance(true)
        HarnessRuntime.pluginHost = manager
        runBlocking { manager.install(termuxStylePlugin(), PluginSource("", "", "")) { _, _ -> } }
        runBlocking { manager.install(termuxStylePlugin(), PluginSource("", "", "")) { _, _ -> } }
        assertEquals(1, shell.seen.count { it == "pkg update" })
        assertEquals(2, shell.seen.count { it.contains("pkg install") })
    }

    @Test
    fun aRememberedMirrorIsUsedWithoutReprobing() = preflightSandbox { _ ->
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        val (server, url) = serveBytes(bytes)
        try {
            val plugin = PluginSpec(
                id = "payload",
                name = "Payload",
                summary = "downloads something",
                android = false,
                desktop = true,
                bytes = bytes.size.toLong(),
                sources = listOf(
                    PluginSource("slow", "Slow mirror", "http://127.0.0.1:1/file.bin"),
                    PluginSource("tuna", "Fast mirror", url, bytes = bytes.size.toLong())
                ),
                detectCommand = "command -v payload",
                installScript = "unpack {file}",
                licence = "MIT",
                homepage = "https://example.invalid",
                needsShell = false,
                windowsInstall = "unpack {file}"
            )
            HarnessRuntime.shell = PreflightShell { ShellOutcome(true, "", "", 0) }
            HarnessRuntime.update(HarnessRuntime.config().withMirror("payload", "tuna"))
            val notes = mutableListOf<String>()
            val manager = PluginManager.desktop()
            val outcome = runBlocking {
                manager.install(plugin, PluginSource("", "", "")) { _, note -> notes.add(note) }
            }
            assertTrue(outcome.ok, outcome.message)
            assertTrue(notes.any { it.contains("remembered source") }, notes.joinToString())
            assertEquals("tuna", HarnessRuntime.config().mirrors["payload"])
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun theNoShellMessageDoesNotClaimADownloadThatNeverHappened() = preflightSandbox { _ ->
        HarnessRuntime.shell = null
        val manager = PluginManager.desktop()
        val scripted = PluginSpec(
            id = "scripted",
            name = "Scripted",
            summary = "runs an install script",
            android = false,
            desktop = true,
            bytes = 0L,
            sources = emptyList(),
            detectCommand = "command -v scripted",
            installScript = "do the thing",
            licence = "MIT",
            homepage = "https://example.invalid",
            windowsInstall = "do the thing"
        )
        val outcome = runBlocking { manager.install(scripted, PluginSource("", "", "")) { _, _ -> } }
        assertFalse(outcome.ok)
        assertEquals(PluginFailure.NO_SHELL, outcome.failure)
        assertFalse(outcome.message.contains("was downloaded"), outcome.message)
    }

    @Test
    fun theInspectToolIsRegistered() {
        assertTrue(PluginTools.tools.any { it.name == "plugin_inspect" })
    }

    @Test
    fun theAuditTrailRedactsSecrets() {
        assertEquals("token=***", AuditTrail.redact("token=abc123"))
        assertEquals("https://***@example.com/x", AuditTrail.redact("https://user:s3cret@example.com/x"))
        assertEquals("nothing secret here", AuditTrail.redact("nothing secret here"))
    }

    @Test
    fun theCatalogueStillProbesEveryPlugin() {
        PluginCatalog.all().forEach { plugin ->
            if (plugin.needsShell && plugin.desktop) {
                assertTrue(plugin.probeFor(false).isNotBlank(), "${plugin.id} has no desktop probe")
            }
        }
    }
}
