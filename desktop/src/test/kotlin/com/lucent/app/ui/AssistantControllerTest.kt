package com.lucent.app.ui

import android.content.Context
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.ChatConversation
import com.lucent.app.data.ChatMessage
import com.lucent.app.data.DataKeys
import com.lucent.app.data.LocalSecrets
import com.lucent.app.data.MemoryTier
import com.lucent.app.data.TokenEstimator
import com.lucent.app.local.LocalLlm
import com.lucent.app.local.LocalModelStore
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
            onDelta: (String) -> Unit,
            onReasoning: (String) -> Unit,
            onRetry: (Int) -> Unit,
            reasoning: String
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

    private class ScriptedLocalEngine(private val replies: List<String>) {
        val transcripts = mutableListOf<List<Pair<String, String>>>()
        private var index = 0

        suspend fun generate(
            messages: List<Pair<String, String>>,
            images: List<ByteArray>,
            onDelta: (String) -> Unit
        ): Int {
            transcripts.add(messages.toList())
            val text = replies.getOrElse(index) { replies.last() }
            index++
            onDelta(text)
            return 0
        }
    }

    private fun installTestModel(context: Context, dir: File) {
        val modelDir = File(dir, "local_model").apply { mkdirs() }
        File(modelDir, "model.gguf").writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x01))
        LocalLlm.ensureLoadedOverride = { _ -> true }
        assertTrue(LocalModelStore.hasModel(context), "the test model must be registered")
    }

    private fun AssistantControllerImpl.sendLocalFixture(
        context: Context,
        text: String = "add a task to buy milk",
        memoryTier: MemoryTier = MemoryTier.LOW,
        targetConversationId: Long? = null
    ) {
        send(
            appContext = context,
            text = text,
            attachmentMime = null,
            attachmentData = null,
            attachmentName = null,
            url = "https://example.invalid",
            spec = ApiSpec.OPENAI,
            key = "test-key",
            model = "local-model",
            name = "Assistant",
            style = "friendly",
            memoryTier = memoryTier,
            webSearchEnabled = false,
            useLocalModel = true,
            useLocalTools = true,
            targetConversationId = targetConversationId
        )
    }

    @Test
    fun localTurnExecutesAToolCallAndFeedsTheResultBack() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            installTestModel(context, dir)
            val engine = ScriptedLocalEngine(
                listOf(
                    """{"tool": "create_task", "arguments": {"title": "Buy milk"}}""",
                    "Added it."
                )
            )
            LocalLlm.generateOverride = { messages, images, onDelta -> engine.generate(messages, images, onDelta) }
            try {
                val controller = newController(context, db, ScriptedLlmClient(emptyList()))
                controller.sendLocalFixture(context)

                awaitState(controller, description = "the local turn's confirmation") { it.pendingConfirmation != null }
                assertEquals("create_task", controller.state.value.pendingConfirmation!!.toolName)
                assertTrue(db.taskDao().getAllOnce().isEmpty())

                controller.resolveConfirmation(approved = true)
                awaitState(controller, description = "the local turn to finish") { !it.sending }

                assertEquals(1, db.taskDao().getAllOnce().size)
                assertEquals("Buy milk", db.taskDao().getAllOnce().first().title)
                assertEquals("Added it.", db.chatDao().getAllOnce().last { it.role == "assistant" }.content)

                val prompt = engine.transcripts.first().first { it.first == "system" }.second
                assertTrue(prompt.contains("""{"tool": "<tool_name>", "arguments": { ... }}"""))
                assertTrue(prompt.contains("- create_task("))

                val secondRound = engine.transcripts[1]
                assertTrue(
                    secondRound.none { it.first == "tool" },
                    "the local transcript must not re-use the cloud API's tool role"
                )
                assertTrue(
                    secondRound.any { it.first == "user" && it.second.startsWith("Result of create_task:") },
                    "the tool result must come back as a message every chat template renders"
                )
            } finally {
                LocalLlm.resetOverridesForTesting()
            }
        }
    }

    @Test
    fun localTurnRunsASecondToolCallAfterSeeingTheFirstResult() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            installTestModel(context, dir)
            val engine = ScriptedLocalEngine(
                listOf(
                    """<tool_call>{"name": "create_task", "arguments": {"title": "Buy milk"}}</tool_call>""",
                    """{"tool": "list_tasks", "arguments": {}}""",
                    "You have one task: Buy milk."
                )
            )
            LocalLlm.generateOverride = { messages, images, onDelta -> engine.generate(messages, images, onDelta) }
            try {
                val controller = newController(context, db, ScriptedLlmClient(emptyList()))
                controller.sendLocalFixture(context)

                awaitState(controller, description = "the local turn's confirmation") { it.pendingConfirmation != null }
                controller.resolveConfirmation(approved = true)
                awaitState(controller, description = "the multi-round local turn to finish") { !it.sending }

                assertEquals(1, db.taskDao().getAllOnce().size)
                assertEquals(
                    "You have one task: Buy milk.",
                    db.chatDao().getAllOnce().last { it.role == "assistant" }.content
                )
                assertEquals(3, engine.transcripts.size, "the result must feed another round of generation")
                val thirdRound = engine.transcripts[2]
                assertTrue(thirdRound.any { it.first == "user" && it.second.startsWith("Result of create_task:") })
                assertTrue(thirdRound.any { it.first == "user" && it.second.startsWith("Result of list_tasks:") })
            } finally {
                LocalLlm.resetOverridesForTesting()
            }
        }
    }

    @Test
    fun localTurnExecutesALlamaPythonTagCall() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            installTestModel(context, dir)
            val engine = ScriptedLocalEngine(
                listOf(
                    """<|python_tag|>{"name": "list_tasks", "parameters": {}}<|eom_id|>""",
                    "Nothing on your list yet."
                )
            )
            LocalLlm.generateOverride = { messages, images, onDelta -> engine.generate(messages, images, onDelta) }
            try {
                val controller = newController(context, db, ScriptedLlmClient(emptyList()))
                controller.sendLocalFixture(context, text = "what is on my list?")
                awaitState(controller, description = "the python-tag local turn to finish") { !it.sending }

                assertEquals(
                    "Nothing on your list yet.",
                    db.chatDao().getAllOnce().last { it.role == "assistant" }.content
                )
                assertTrue(
                    engine.transcripts[1].any { it.first == "user" && it.second.startsWith("Result of list_tasks:") },
                    "a python-tag call must run and its result must come back"
                )
            } finally {
                LocalLlm.resetOverridesForTesting()
            }
        }
    }

    @Test
    fun localTurnExecutesAMistralToolCallsShape() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            installTestModel(context, dir)
            val engine = ScriptedLocalEngine(
                listOf(
                    """[TOOL_CALLS] [{"name": "list_tasks", "arguments": {}}]""",
                    "Your list is empty."
                )
            )
            LocalLlm.generateOverride = { messages, images, onDelta -> engine.generate(messages, images, onDelta) }
            try {
                val controller = newController(context, db, ScriptedLlmClient(emptyList()))
                controller.sendLocalFixture(context, text = "what is on my list?")
                awaitState(controller, description = "the [TOOL_CALLS] local turn to finish") { !it.sending }

                assertEquals(
                    "Your list is empty.",
                    db.chatDao().getAllOnce().last { it.role == "assistant" }.content
                )
                assertTrue(
                    engine.transcripts[1].any { it.first == "user" && it.second.startsWith("Result of list_tasks:") },
                    "a [TOOL_CALLS] call must run and its result must come back"
                )
            } finally {
                LocalLlm.resetOverridesForTesting()
            }
        }
    }

    @Test
    fun localTurnThatCannotResolveAToolCallTellsTheUserInsteadOfShowingJson() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            installTestModel(context, dir)
            val engine = ScriptedLocalEngine(
                List(6) { """{"tool": "delete_everything", "arguments": {"title": "x"}}""" }
            )
            LocalLlm.generateOverride = { messages, images, onDelta -> engine.generate(messages, images, onDelta) }
            try {
                val controller = newController(context, db, ScriptedLlmClient(emptyList()))
                controller.sendLocalFixture(context, text = "delete everything")
                awaitState(controller, description = "the unusable local turn to finish") { !it.sending }

                val assistant = db.chatDao().getAllOnce().last { it.role == "assistant" }
                assertTrue(
                    !assistant.content.contains("delete_everything"),
                    "raw tool JSON must never reach the user"
                )
                assertTrue(
                    controller.state.value.errorText.isNotBlank(),
                    "a local model that could not call a tool must say so instead of staying silent"
                )
                assertTrue(
                    engine.transcripts[1].any { it.first == "user" && it.second.contains("no tool named") },
                    "the retry must tell the model which tool names exist"
                )
            } finally {
                LocalLlm.resetOverridesForTesting()
            }
        }
    }

    @Test
    fun localTranscriptIsTrimmedToTheEngineContext() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            installTestModel(context, dir)
            val conversationId = db.chatConversationDao().insert(ChatConversation())
            val filler = "x".repeat(1200)
            repeat(12) { i ->
                db.chatDao().insert(
                    ChatMessage(
                        role = if (i % 2 == 0) "user" else "assistant",
                        content = "$filler $i",
                        conversationId = conversationId
                    )
                )
            }
            val engine = ScriptedLocalEngine(listOf("Just chatting."))
            LocalLlm.generateOverride = { messages, images, onDelta -> engine.generate(messages, images, onDelta) }
            try {
                val controller = newController(context, db, ScriptedLlmClient(emptyList()))
                controller.sendLocalFixture(
                    context,
                    text = "add a task to buy milk",
                    memoryTier = MemoryTier.MEDIUM,
                    targetConversationId = conversationId
                )
                awaitState(controller, description = "the trimmed local turn to finish") { !it.sending }

                val sent = engine.transcripts.first()
                val budget = LocalLlm.N_CTX - LocalLlm.MAX_NEW_TOKENS
                val used = sent.sumOf { TokenEstimator.estimate(it.second) }
                assertTrue(used < budget, "the local prompt must fit the engine context: $used >= $budget")
                assertTrue(
                    sent.any { it.second == "add a task to buy milk" },
                    "the message being answered must survive the trim"
                )
                assertTrue(sent.size < 14, "older history must be dropped to make room")
            } finally {
                LocalLlm.resetOverridesForTesting()
            }
        }
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

    @Test
    fun agentTraceRecordsTheToolStepAndIsPersistedWithTheReply() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val context = TestContext(dir)
            val db = AppDatabase.createForTesting(context)
            val llm = ScriptedLlmClient(listOf(createTaskReply(title = "Buy milk"), finalTextReply("Added it.")))
            val controller = newController(context, db, llm)

            controller.sendFixture(context)
            awaitState(controller, description = "pendingConfirmation to be set") { it.pendingConfirmation != null }

            val running = controller.agentTraceFor(controller.currentConversationId)
            assertTrue(running != null, "a live trace must exist while the turn runs")
            val pendingStep = running.steps.first { it.kind == AgentStepKind.TOOL }
            assertEquals("create_task", pendingStep.toolName)
            assertEquals("Buy milk", pendingStep.detail)
            assertEquals(AgentStepStatus.RUNNING, pendingStep.status)

            controller.resolveConfirmation(approved = true)
            awaitState(controller, description = "turn to finish after approving") { !it.sending }

            val assistant = db.chatDao().getAllOnce().last { it.role == "assistant" }
            val trace = AgentTraceCodec.decode(assistant.agentTrace)
            assertTrue(trace != null, "the trace must be persisted on the reply")
            assertEquals(AgentStepStatus.DONE, trace.status)
            val step = trace.steps.first { it.kind == AgentStepKind.TOOL }
            assertEquals("create_task", step.toolName)
            assertEquals("Buy milk", step.detail)
            assertEquals(AgentStepStatus.DONE, step.status)
        }
    }

    @Test
    fun declinedConfirmationLeavesTheTraceCancelledWithNothingWritten() = runBlocking {
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
            val assistant = db.chatDao().getAllOnce().last { it.role == "assistant" }
            val trace = AgentTraceCodec.decode(assistant.agentTrace)
            assertTrue(trace != null, "the cancelled trace must still be persisted")
            assertEquals(AgentStepStatus.CANCELLED, trace.status)
            val step = trace.steps.first { it.kind == AgentStepKind.TOOL }
            assertEquals(AgentStepStatus.CANCELLED, step.status)
        }
    }
}
