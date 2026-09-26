package com.lucent.app.harness

import android.content.Context
import com.lucent.app.data.AppDatabase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private class EscalationHost(private val root: File) : HarnessHost {
    override val android: Boolean = false
    override fun defaultWorkspace(): File = File(root, "workspace").apply { mkdirs() }
    override fun filesDir(): File = File(root, "files").apply { mkdirs() }
    override fun cacheDir(): File = File(root, "cache").apply { mkdirs() }
}

class HarnessEscalationTest {

    private fun withEscalationSandbox(block: (File, Context, AppDatabase) -> Unit) {
        val root = java.nio.file.Files.createTempDirectory("lucent-escalation").toFile()
        val previousHost = HarnessRuntime.host
        val previousConfig = HarnessRuntime.config()
        val previousConversation = HarnessRuntime.conversationId
        HarnessRuntime.host = EscalationHost(root)
        HarnessRuntime.android = false
        HarnessRuntime.conversationId = 7L
        HarnessRuntime.update(HarnessConfig(enabled = true))
        HarnessGate.clearEscalations()
        try {
            HarnessRuntime.workspace().mkdirs()
            val context = harnessTestContext()
            val db = allocate(AppDatabase::class.java) as AppDatabase
            block(root, context, db)
        } finally {
            HarnessGate.clearEscalations()
            HarnessRuntime.conversationId = previousConversation
            HarnessRuntime.host = previousHost
            HarnessRuntime.update(previousConfig)
            root.deleteRecursively()
        }
    }

    @Test
    fun theEscalationParametersAreReadFromTheCall() {
        val args = JSONObject()
            .put("path", "notes/report.txt")
            .put("sandbox_permissions", "workspace-write")
            .put("justification", "  the report has to sit beside the other project files  ")
        val escalation = assertNotNull(HarnessEscalation.of(args))
        assertEquals("workspace-write", escalation.sandboxPermissions)
        assertEquals("the report has to sit beside the other project files", escalation.justification)
        assertTrue(escalation.usable)
        assertFalse(escalation.fullAccess)
        val full = assertNotNull(HarnessEscalation.of(JSONObject().put("sandbox_permissions", "danger-full-access")))
        assertTrue(full.fullAccess)
        assertNull(HarnessEscalation.of(JSONObject().put("path", "notes/report.txt")))
    }

    @Test
    fun onlyMutatingToolsThatDeclareThePairMayEscalate() {
        val writer = assertNotNull(HarnessGate.find("write_file"))
        val reader = assertNotNull(HarnessGate.find("read_file"))
        assertTrue(writer.escalatable)
        assertFalse(reader.escalatable)
        assertTrue(writer.params.any { it.name == HarnessEscalation.PARAM_PERMISSIONS })
        assertFalse(reader.params.any { it.name == HarnessEscalation.PARAM_PERMISSIONS })
        assertNull(HarnessGate.escalationProblem(writer, HarnessEscalation("workspace-write", "because")))
        assertNotNull(HarnessGate.escalationProblem(writer, HarnessEscalation("workspace-write", " ")))
        assertNotNull(HarnessGate.escalationProblem(writer, HarnessEscalation("everything", "because")))
        assertNotNull(HarnessGate.escalationProblem(reader, HarnessEscalation("workspace-write", "because")))
    }

    @Test
    fun theWiderSandboxOnlyCoversThePathsTheCallNames() = withEscalationSandbox { root, _, _ ->
        val config = HarnessConfig(enabled = true)
        File(root, "outside").mkdirs()
        val target = JSONObject().put("path", File(root, "outside/report.txt").path).toString()
        val wide = HarnessGate.widenedConfig(config, target, HarnessEscalation("workspace-write", "because"))
        assertTrue(wide.writeRoots.contains(File(root, "outside").canonicalPath), wide.writeRoots.toString())
        assertFalse(wide.writeRoots.contains(root.parentFile?.path ?: ""))
        val full = HarnessGate.widenedConfig(config, target, HarnessEscalation("danger-full-access", "because"))
        assertTrue(full.writeRoots.isNotEmpty())
        assertEquals(config, HarnessGate.widenedConfig(config, JSONObject().put("path", "/proc/1/status").toString(),
            HarnessEscalation("danger-full-access", "because")))
    }

    @Test
    fun aBlockedWriteIsRetriedOnceAndTheAuditTrailSaysEscalated() = withEscalationSandbox { root, context, db ->
        val target = File(root, "outside/report.txt")
        val plain = JSONObject().put("path", target.path).put("content", "hello").toString()
        val refused = runBlocking { HarnessGate.execute(context, db, "write_file", plain) }
        assertFalse(refused.success, refused.summary)
        assertFalse(target.exists(), "the blocked write must not have happened")

        val escalated = JSONObject()
            .put("path", target.path)
            .put("content", "hello")
            .put("sandbox_permissions", "workspace-write")
            .put("justification", "the report has to sit beside the other project files")
            .toString()
        val retried = runBlocking { HarnessGate.execute(context, db, "write_file", escalated) }
        assertTrue(retried.success, retried.summary)
        assertEquals("hello", target.readText())
        assertTrue(HarnessGate.escalatedInThisConversation("write_file"))

        val again = runBlocking { HarnessGate.execute(context, db, "write_file", escalated) }
        assertFalse(again.success, again.summary)
        assertTrue(again.summary.contains("already"), again.summary)

        assertTrue(awaitEscalatedAudit(context), "the retry should be recorded as an escalated attempt")
    }

    @Test
    fun aRetryWithoutAJustificationIsRefused() = withEscalationSandbox { root, context, db ->
        val target = File(root, "outside/report.txt")
        val args = JSONObject()
            .put("path", target.path)
            .put("content", "hello")
            .put("sandbox_permissions", "workspace-write")
            .put("justification", "   ")
            .toString()
        val result = runBlocking { HarnessGate.execute(context, db, "write_file", args) }
        assertFalse(result.success, result.summary)
        assertTrue(result.summary.contains("justification"), result.summary)
        assertFalse(target.exists())
        assertFalse(HarnessGate.escalatedInThisConversation("write_file"))
    }

    @Test
    fun escalationNeverLiftsTheProtectedSystemPaths() = withEscalationSandbox { _, context, db ->
        val args = JSONObject()
            .put("path", "/proc/1/status")
            .put("content", "hello")
            .put("sandbox_permissions", "danger-full-access")
            .put("justification", "I want to see whether this works")
            .toString()
        val result = runBlocking { HarnessGate.execute(context, db, "write_file", args) }
        assertFalse(result.success, result.summary)
        assertFalse(HarnessGate.escalatedInThisConversation("write_file"))
    }

    private fun awaitEscalatedAudit(context: Context): Boolean {
        val deadline = System.currentTimeMillis() + 4000
        while (System.currentTimeMillis() < deadline) {
            if (AuditTrail.entries(context, 40).any { it.approval == "escalated" }) return true
            Thread.sleep(25)
        }
        return false
    }

    private fun allocate(type: Class<*>): Any? = try {
        val unsafe = Class.forName("sun.misc.Unsafe")
        val field = unsafe.getDeclaredField("theUnsafe")
        field.isAccessible = true
        unsafe.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), type)
    } catch (t: Throwable) {
        null
    }
}
