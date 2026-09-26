package com.lucent.app.harness

import com.lucent.app.harness.plugins.PluginCatalog
import com.lucent.app.harness.plugins.PluginDownload
import com.lucent.app.harness.plugins.PluginManager
import com.lucent.app.harness.plugins.PluginSource
import com.lucent.app.harness.plugins.PluginSpec
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class PipelineHost(private val root: File) : HarnessHost {
    override val android: Boolean = false
    override fun defaultWorkspace(): File = File(root, "workspace").apply { mkdirs() }
    override fun filesDir(): File = File(root, "files").apply { mkdirs() }
    override fun cacheDir(): File = File(root, "cache").apply { mkdirs() }
}

private class ScriptedShell(private val respond: (String) -> ShellOutcome) : HarnessShell {
    override val id: String = "scripted"
    override fun isReady(): Boolean = true
    override fun describe(): String = "the scripted test shell"
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

private fun serve(body: ByteArray): Pair<HttpServer, String> {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/file.bin") { exchange ->
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }
    server.start()
    return server to "http://127.0.0.1:${server.address.port}/file.bin"
}

private fun sandbox(block: (File) -> Unit) {
    val root = java.nio.file.Files.createTempDirectory("lucent-plugin").toFile()
    val previousHost = HarnessRuntime.host
    val previousShell = HarnessRuntime.shell
    val previousConfig = HarnessRuntime.config()
    HarnessRuntime.host = PipelineHost(root)
    HarnessRuntime.android = false
    HarnessRuntime.update(HarnessConfig(enabled = true))
    try {
        block(root)
    } finally {
        HarnessRuntime.host = previousHost
        HarnessRuntime.shell = previousShell
        HarnessRuntime.update(previousConfig)
        root.deleteRecursively()
    }
}

private fun scriptedPlugin() = PluginSpec(
    id = "scripted",
    name = "Scripted",
    summary = "runs an install script",
    android = false,
    desktop = true,
    bytes = 0L,
    sources = emptyList(),
    detectCommand = "test -d /tmp/installed",
    installScript = "unpack thing",
    licence = "MIT",
    homepage = "https://example.invalid",
    windowsDetect = "if exist thing (exit 0) else (exit 1)",
    windowsInstall = "unpack thing",
    windowsRemove = "erase thing"
)

private fun payloadPlugin(url: String, bytes: Long, checksum: String = "") = PluginSpec(
    id = "payload",
    name = "Payload",
    summary = "downloads something",
    android = false,
    desktop = true,
    bytes = bytes,
    sources = listOf(PluginSource("test", "Test mirror", url, official = true, sha256 = checksum, bytes = bytes)),
    detectCommand = "test -d /tmp/installed",
    installScript = "unpack {file}",
    licence = "MIT",
    homepage = "https://example.invalid",
    windowsDetect = "if exist payload (exit 0) else (exit 1)",
    windowsInstall = "unpack \"{file}\"",
    windowsRemove = "erase payload"
)

class PluginPipelineTest {

    @Test
    fun theCatalogueProbesAndInstallsOnEveryPlatformItShipsTo() {
        PluginCatalog.all().forEach { plugin ->
            if (plugin.needsShell) {
                assertTrue(plugin.probeFor(false).isNotBlank(), "${plugin.id} has no desktop probe")
                assertTrue(plugin.installFor(false).isNotBlank(), "${plugin.id} has no desktop install")
                assertTrue(plugin.removeFor(false).isNotBlank(), "${plugin.id} has no desktop remove")
            }
            if (plugin.android && plugin.id != "termux") {
                assertTrue(plugin.detectCommand.isNotBlank(), "${plugin.id} has no android probe")
            }
        }
        assertEquals("command -v yt-dlp", PluginCatalog.find("ytdlp-termux")?.detectCommand)
        assertEquals("where git", PluginCatalog.find("git")?.probeFor(false))
    }

    @Test
    fun windowsProbesAreNotPosixCommands() {
        PluginCatalog.forPlatform(false).forEach { plugin ->
            assertFalse(
                plugin.probeFor(false).startsWith("command -v"),
                "${plugin.id} would probe Windows with a POSIX builtin"
            )
        }
    }

    @Test
    fun scriptSuccessWithAFailingProbeIsReportedAsACheckFailure() = sandbox { root ->
        HarnessRuntime.shell = ScriptedShell { command ->
            if (command.contains("unpack")) ShellOutcome(true, "unpacked everything", "", 0)
            else ShellOutcome(false, "", "the plugin is still missing", 1)
        }
        val manager = PluginManager.desktop()
        val outcome = runBlocking { manager.install(scriptedPlugin(), PluginSource("", "", "")) { _, _ -> } }
        assertFalse(outcome.ok)
        assertEquals(PluginFailure.DETECT, outcome.failure)
        assertTrue(outcome.detail.contains("still missing"), outcome.detail)
        assertTrue(outcome.message.contains(scriptedPlugin().probeFor(false)), outcome.message)
    }

    @Test
    fun aFailedInstallCommandKeepsItsExitCodeAndOutput() = sandbox { root ->
        HarnessRuntime.shell = ScriptedShell { ShellOutcome(false, "boom: no space left", "", 3) }
        val manager = PluginManager.desktop()
        val outcome = runBlocking { manager.install(scriptedPlugin(), PluginSource("", "", "")) { _, _ -> } }
        assertFalse(outcome.ok)
        assertEquals(PluginFailure.INSTALL, outcome.failure)
        assertTrue(outcome.message.contains("exited 3"), outcome.message)
        assertTrue(outcome.detail.contains("no space left"), outcome.detail)
    }

    @Test
    fun theDownloadIsKeptWhenNoShellCanInstallIt() = sandbox { root ->
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        val (server, url) = serve(bytes)
        try {
            HarnessRuntime.shell = null
            val manager = PluginManager.desktop()
            val outcome = runBlocking {
                manager.install(payloadPlugin(url, bytes.size.toLong()), PluginSource("", "", "")) { _, _ -> }
            }
            assertFalse(outcome.ok)
            assertEquals(PluginFailure.NO_SHELL, outcome.failure)
            val staged = File(outcome.installedPath)
            assertTrue(staged.isFile, "the payload must survive for a later install")
            assertEquals(bytes.size.toLong(), staged.length())
            assertTrue(staged.path.startsWith(HarnessRuntime.workspace().path), staged.path)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aShortDownloadIsRejected() = sandbox { root ->
        val bytes = ByteArray(64) { 7 }
        val (server, url) = serve(bytes)
        try {
            val manager = PluginManager.desktop()
            val outcome = runBlocking {
                manager.install(payloadPlugin(url, bytes.size.toLong()), PluginSource("", "", "")) { _, _ -> }
            }
            assertFalse(outcome.ok)
            assertEquals(PluginFailure.DOWNLOAD, outcome.failure)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aWrongLengthIsRejectedAndTheFileIsDeleted() = sandbox { _ ->
        val bytes = ByteArray(4096) { 3 }
        val (server, url) = serve(bytes)
        try {
            val source = PluginSource("test", "Test mirror", url, official = true, bytes = 9999L)
            val target = File(HarnessRuntime.downloadsDir(), "short.bin")
            val outcome = runBlocking { PluginDownload.fetch(source, target) { _, _ -> } }
            assertFalse(outcome.ok)
            assertFalse(target.exists())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aChecksumMismatchIsRejected() = sandbox { _ ->
        val bytes = ByteArray(4096) { 5 }
        val (server, url) = serve(bytes)
        try {
            val source = PluginSource(
                "test",
                "Test mirror",
                url,
                official = true,
                sha256 = "00",
                bytes = bytes.size.toLong()
            )
            val target = File(HarnessRuntime.downloadsDir(), "checksum.bin")
            val outcome = runBlocking { PluginDownload.fetch(source, target) { _, _ -> } }
            assertFalse(outcome.ok)
            assertFalse(target.exists())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aPluginIsRecordedOnlyWhenTheProbeAgrees() = sandbox { root ->
        var unpacked = false
        HarnessRuntime.shell = ScriptedShell { command ->
            if (command.contains("unpack")) {
                unpacked = true
                ShellOutcome(true, "done", "", 0)
            } else {
                ShellOutcome(unpacked, "", "", if (unpacked) 0 else 1)
            }
        }
        val manager = PluginManager.desktop()
        val outcome = runBlocking { manager.install(scriptedPlugin(), PluginSource("", "", "")) { _, _ -> } }
        assertTrue(outcome.ok, outcome.message)
        assertTrue(HarnessRuntime.config().pluginInstalled("scripted"))
        val probe = runBlocking { manager.probe(scriptedPlugin()) }
        assertEquals(0, probe.exitCode)
    }
}
