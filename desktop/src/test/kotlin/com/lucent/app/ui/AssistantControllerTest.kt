package com.lucent.app.ui

import android.content.Context
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.DataKeys
import com.lucent.app.data.LocalSecrets
import com.lucent.app.data.MemoryTier
import com.lucent.app.network.ApiSpec
import com.lucent.app.network.ChatTurn
import com.lucent.app.network.RawModelReply
import com.lucent.app.network.ToolCallRequest
import com.lucent.app.network.ToolDefinition
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class AssistantControllerTest {

    private class TestContext(private val dir: File) : Context() {
        override val filesDir: File get() = dir
    }

    private fun freshDir(): File =
        File(System.getProperty("java.io.tmpdir"), "lucent-assistant-controller-test-${System.nanoTime()}")
            .apply { mkdirs() }

    private suspend fun use(dir: File, block: suspend () -> Unit) {
        LocalSecrets.filesDirOverride = dir
        LocalSecrets.resetForTesting()
        DataKeys.resetCacheForTesting()
        try {
            block()
        } finally {
            LocalSecrets.filesDirOverride = null
            LocalSecrets.resetForTesting()
            DataKeys.resetCacheForTesting()
        }
    }

    private class ScriptedLlmClient(private val scripted: List<Result<RawModelReply>>) : AssistantLlmClient {
        private val index = AtomicInteger(0)
        val callCount: Int get() = index.get()
        override suspend fun streamChat(
            baseUrl: String,
            spec: ApiSpec,
            apiKey: String,
            model: String,
            history: List<ChatTurn>,
            systemPrompt: String,
            tools: List<ToolDefinition>,
            onDelta: (String) -> Unit
        ): Result<RawModelReply> {
            val i = index.getAndIncrement()
            return scripted.getOrElse(i) { scripted.last() }
        }
    }

    private fun newController(context: Context, db: AppDatabase, llmClient: AssistantLlmClient) =
        AssistantControllerImpl(
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            db = db,
            llmClient = llmClient,
            context = context
        )

    private suspend fun awaitState(
        controller: AssistantControllerImpl,
        timeoutMs: Long = 5_000,
        description: String,
        condition: (AssistantUiState) -> Boolean
    ) {
        try {
            withTimeout(timeoutMs) {
                while (!condition(controller.state.value)) delay(10)
            }
        } catch (e: TimeoutCancellationException) {
            fail("Timed out waiting for: $description (last state: ${controller.state.value})")
        }
    }

    private fun createTaskReply(callId: String = "call_1", title: String = "Buy milk", notes: String = "2%") =
        Result.success(
            RawModelReply(
                text = null,
                toolCalls = listOf(
                    ToolCallRequest(
                        id = callId,
                        name = "create_task",
                        argumentsJson = "{\"title\":\"$title\",\"notes\":\"$notes\"}"
                    )
                )
            )
        )

    private fun finalTextReply(text: String = "Done.") =
        Result.success(RawModelReply(text = text, toolCalls = emptyList()))

    private fun AssistantControllerImpl.sendFixture(context: Context, text: String = "add a task to buy milk") {
        send(
            appContext = context,
            text = text,
            attachmentMime = null,
            attachmentData = null,
            attachmentName = null,
            url = "https://example.invalid",
            spec = ApiSpec.OPENAI,
            key = "test-key",
            model = "test-model",
            name = "Assistant",
            style = "friendly",
            memoryTier = MemoryTier.LOW,
            webSearchEnabled = false
        )
    }

    @Test
    fun toolCallNeedingConfirmationSetsPendingConfirmationWithEditableCorrectArgs() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            val llm = ScriptedLlmClient(listOf(createTaskReply(title = "Buy milk", notes = "2%")))
            val controller = newController(context, db, llm)

            controller.sendFixture(context)

            awaitState(controller, description = "pendingConfirmation to be set") {
                it.pendingConfirmation != null
            }

            val confirm = controller.state.value.pendingConfirmation!!
            assertEquals("create_task", confirm.toolName)
            val byKey = confirm.edits.associate { it.key to it.value }
            assertEquals("Buy milk", byKey["title"])
            assertEquals("2%", byKey["notes"])

            assertTrue(db.taskDao().getAllOnce().isEmpty())
        }
    }

    @Test
    fun decliningConfirmationWritesNothing() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            val llm = ScriptedLlmClient(listOf(createTaskReply()))
            val controller = newController(context, db, llm)

            controller.sendFixture(context)
            awaitState(controller, description = "pendingConfirmation to be set") { it.pendingConfirmation != null }

            controller.resolveConfirmation(approved = false)

            awaitState(controller, description = "turn to finish after declining") { !it.sending }
            assertTrue(db.taskDao().getAllOnce().isEmpty(), "declining must not create the task")
        }
    }

    @Test
    fun approvingConfirmationTwiceWritesExactlyOnce() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            val llm = ScriptedLlmClient(listOf(createTaskReply(), finalTextReply()))
            val controller = newController(context, db, llm)

            controller.sendFixture(context)
            awaitState(controller, description = "pendingConfirmation to be set") { it.pendingConfirmation != null }

            controller.resolveConfirmation(approved = true)
            controller.resolveConfirmation(approved = true)

            awaitState(controller, description = "turn to finish after approving") { !it.sending }

            val tasks = db.taskDao().getAllOnce()
            assertEquals(1, tasks.size, "approving twice must still write exactly one task")
            assertEquals("Buy milk", tasks.first().title)
        }
    }
}
