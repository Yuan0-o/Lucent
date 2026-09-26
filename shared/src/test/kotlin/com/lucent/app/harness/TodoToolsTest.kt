package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

private class TodoHost(private val root: File) : HarnessHost {
    override val android: Boolean = false
    override fun defaultWorkspace(): File = File(root, "workspace").apply { mkdirs() }
    override fun filesDir(): File = File(root, "files").apply { mkdirs() }
    override fun cacheDir(): File = File(root, "cache").apply { mkdirs() }
}

class TodoToolsTest {

    private fun sandbox(block: (HarnessCtx) -> Unit) {
        val root = java.nio.file.Files.createTempDirectory("lucent-todos").toFile()
        val previousHost = HarnessRuntime.host
        val previousConfig = HarnessRuntime.config()
        val previousConversation = HarnessRuntime.conversationId
        HarnessRuntime.host = TodoHost(root)
        HarnessRuntime.android = false
        HarnessRuntime.conversationId = 42L
        HarnessRuntime.update(HarnessConfig(enabled = true))
        TodoBoard.clear()
        try {
            HarnessRuntime.workspace().mkdirs()
            val ctx = HarnessCtx(
                harnessTestContext(),
                null,
                HarnessRuntime.config(),
                emptySet(),
                false,
                HarnessRuntime.workspace()
            )
            block(ctx)
        } finally {
            TodoBoard.clear()
            HarnessRuntime.conversationId = previousConversation
            HarnessRuntime.host = previousHost
            HarnessRuntime.update(previousConfig)
            root.deleteRecursively()
        }
    }

    @Test
    fun statusesAreNormalisedToTheThreeTheHarnessUses() {
        assertEquals("active", todoStatus("in_progress"))
        assertEquals("active", todoStatus("Running"))
        assertEquals("done", todoStatus("COMPLETED"))
        assertEquals("pending", todoStatus("waiting"))
        assertEquals("pending", todoStatus(""))
    }

    @Test
    fun theBoardRendersEveryStatusDistinctly() {
        val rendered = TodoBoard.render(
            listOf(
                TodoItem("write the parser", "done"),
                TodoItem("wire the panel", "active"),
                TodoItem("publish", "pending")
            )
        )
        assertEquals("[x] write the parser\n[~] wire the panel\n[ ] publish", rendered)
        TodoBoard.save(harnessTestContext(), 42L, listOf(TodoItem("only", "done")))
        assertTrue(TodoBoard.summary().startsWith("1/1"), TodoBoard.summary())
    }

    @Test
    fun todoWriteReplacesTheWholeListAndTodoReadHandsItBack() = sandbox { ctx ->
        val first = JSONObject().put(
            "todos",
            JSONArray().put(JSONObject().put("title", "draft the release notes").put("status", "active"))
                .put("check the build")
        )
        val written = runBlocking { assertNotNull(TodoTools.execute(ctx, "todo_write", first)) as ToolExecResult }
        assertTrue(written.summary.contains("draft the release notes"), written.summary)
        assertTrue(written.summary.contains("check the build"), written.summary)
        assertEquals(2, TodoBoard.todos().size)

        val replacement = JSONObject().put("todos", JSONArray().put("only this one"))
        val second = runBlocking { assertNotNull(TodoTools.execute(ctx, "todo_write", replacement)) as ToolExecResult }
        assertTrue(second.summary.contains("only this one"), second.summary)
        assertEquals(listOf("only this one"), TodoBoard.todos().map { it.title })

        val read = runBlocking { assertNotNull(TodoTools.execute(ctx, "todo_read", JSONObject())) as ToolExecResult }
        assertTrue(read.summary.contains("only this one"), read.summary)
    }

    @Test
    fun anEmptyTodoListIsRefusedRatherThanStored() = sandbox { ctx ->
        val empty = JSONObject().put("todos", JSONArray())
        val result = runBlocking { assertNotNull(TodoTools.execute(ctx, "todo_write", empty)) as ToolExecResult }
        assertTrue(!result.success, result.summary)
        assertTrue(TodoBoard.todos().isEmpty())
    }
}

class SubAgentInboxTest {

    private fun subAgent(id: String): SubAgent {
        val constructor = SubAgent::class.java.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            Set::class.java,
            Long::class.javaPrimitiveType
        )
        constructor.isAccessible = true
        return constructor.newInstance(id, "read the spec", emptySet<String>(), 1L) as SubAgent
    }

    @Test
    fun instructionsWaitInTheInboxUntilTheRoundBoundary() {
        val agent = subAgent("sub-inbox")
        agent.instruct("also check the changelog")
        agent.instruct("  ")
        assertEquals(listOf("also check the changelog"), agent.inbox)
        assertEquals(listOf("also check the changelog"), agent.drainInbox())
        assertTrue(agent.inbox.isEmpty())
        assertTrue(agent.drainInbox().isEmpty())
    }

    @Test
    fun theInboxIsOnlyForRunningHelpers() {
        assertTrue(SubAgents.list().isEmpty())
        assertTrue(!SubAgents.instruct("sub-missing", "keep going"))
        assertTrue(!SubAgents.instruct("sub-inbox", "   "))
        assertEquals(
            listOf("spawn_agent", "agent_status", "agent_result", "agent_stop", "list_agents", "send_message", "interrupt"),
            AgentTools.tools.map { it.name }
        )
    }
}
