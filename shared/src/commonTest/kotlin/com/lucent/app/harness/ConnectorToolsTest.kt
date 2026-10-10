package com.lucent.app.harness

import java.io.File
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.put

class ConnectorToolsTest {

    private fun allocateCtx(): HarnessCtx {
        val type = Class.forName("sun.misc.Unsafe")
        val field = type.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val allocate = type.getDeclaredMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, HarnessCtx::class.java) as HarnessCtx
    }

    private fun fill(obj: Any, name: String, value: Any?) {
        val field = obj.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(obj, value)
    }

    private fun ctxFor(config: HarnessConfig, dir: File): HarnessCtx {
        val ctx = allocateCtx()
        fill(ctx, "config", config)
        fill(ctx, "capabilities", emptySet<String>())
        fill(ctx, "android", false)
        fill(ctx, "workspacePath", dir.path)
        return ctx
    }

    @Test
    fun testJsonNullArgsDoNotCrash() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "lucent-conn-${System.nanoTime()}")
        dir.mkdirs()
        val ctx = ctxFor(HarnessConfig(workspace = dir.path), dir)
        
        val args = buildJsonObject {
            put("properties", JsonNull)
            put("headers", JsonNull)
            put("select", JsonNull)
            put("status", JsonNull)
            put("formula", JsonNull)
            put("fields", JsonNull)
            put("data", JsonNull)
            put("connector", "test")
            put("action", "test")
        }
        
        try {
            ConnectorTools.execute(ctx, "notion_api", args)
            ConnectorTools.execute(ctx, "jira_api", args)
            ConnectorTools.execute(ctx, "http_request", args)
        } catch (e: IllegalArgumentException) {
            if (e.message?.contains("is not a JsonObject") == true) {
                fail("Crashed with JsonObject exception: ${e.message}")
            }
        } catch (e: Exception) {
        } finally {
            dir.deleteRecursively()
        }
    }
}
