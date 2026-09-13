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

/**
 * P1-2 step 4: the send → tool-call → confirm → persist chain, exercised on a plain JVM
 * [AssistantControllerImpl] instance — no Android environment, no real network, no Compose UI.
 *
 * This is exactly what P1-2's steps 1-2 were for: [AssistantControllerImpl] takes its
 * [AppDatabase] and its [AssistantLlmClient] as constructor parameters instead of reaching for
 * `AppDatabase.getInstance(...)` / the real `LlmClient` object itself, so a test can hand it a
 * real (but disposable) test database — [AppDatabase.createForTesting], the same seam
 * `com.lucent.app.data.BackupRoundTripTest` and `com.lucent.app.data.DbEncryptionTest` already
 * use for exactly this reason — and a fake, scripted [AssistantLlmClient] instead of a live model.
 * Everything else [AssistantControllerImpl] touches (AppTools, the desktop Context shim,
 * GenerationService, Haptics, ReminderScheduler) already has a safe desktop/no-op path with a
 * disabled reminder and no due date, which is what [createTaskReply] deliberately sends.
 *
 * There is no `kotlinx-coroutines-test` dependency in this module — P1-2's own brief asks not to
 * add one — so generation genuinely runs on a background dispatcher here exactly as it does in the
 * real app. [awaitState] polls [AssistantControllerImpl.state] with a timeout rather than assuming
 * any particular interleaving, which is what makes these tests correct regardless of that.
 */
class AssistantControllerTest {

    private class TestContext(private val dir: File) : Context() {
        override val filesDir: File get() = dir
    }

    private fun freshDir(): File =
        File(System.getProperty("java.io.tmpdir"), "lucent-assistant-controller-test-${System.nanoTime()}")
            .apply { mkdirs() }

    // Same key-material setup as BackupRoundTripTest/DbEncryptionTest: the store is encrypted at
    // rest keyed off the files directory, so any test that touches AppDatabase needs this wrapper
    // around it — including, incidentally, AssistantDraftBridge.clear's own
    // AppDatabase.getInstance(...) call inside resolveConfirmation, which this also keeps safe.
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

    /** Returns scripted replies in order, repeating the last one if asked for more than were given. */
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

    /** Poll [AssistantControllerImpl.state] until [condition] holds, or fail with [description]. */
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

    // No "due"/"reminder" keys, so AppTools.execute's create_task branch builds a task with no due
    // date and reminders off — ReminderScheduler.sync's first check (shouldFire) then returns
    // immediately without touching any OS-level scheduling, which is what keeps this safe to run
    // as a plain JVM test.
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
            // confirmTools defaults to true, useLocalModel defaults to false — exactly the cloud,
            // confirm-before-write path this test exists to exercise.
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

            // Nothing runs until the user answers — the whole point of confirm-before-write.
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

            // The double-click this test is named for: two approvals back to back, exactly as two
            // fast taps on the same button would arrive.
            controller.resolveConfirmation(approved = true)
            controller.resolveConfirmation(approved = true)

            awaitState(controller, description = "turn to finish after approving") { !it.sending }

            val tasks = db.taskDao().getAllOnce()
            assertEquals(1, tasks.size, "approving twice must still write exactly one task")
            assertEquals("Buy milk", tasks.first().title)
        }
    }
}
