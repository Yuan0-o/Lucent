package com.lucent.app.harness

import java.io.File
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.put

class TerminalToolsTest {

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
        fill(ctx, "capabilities", setOf(HarnessRuntime.CAP_SHELL))
        fill(ctx, "android", false)
        fill(ctx, "workspacePath", dir.path)
        return ctx
    }

    @Test
    fun testJsonNullEnvArgDoesNotCrash() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "lucent-term-${System.nanoTime()}")
        dir.mkdirs()
        val ctx = ctxFor(HarnessConfig(workspace = dir.path), dir)
        
        val args = buildJsonObject {
            put("command", "ls")
            put("env", JsonNull)
        }
        
        try {
            TerminalTools.execute(ctx, "terminal_run_command", args)
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
