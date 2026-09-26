package com.lucent.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucent.app.AppScope
import com.lucent.app.GenerationService
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.ChatConversation
import com.lucent.app.data.ChatMessage
import com.lucent.app.data.DEFAULT_ASSISTANT_STYLE
import com.lucent.app.data.MemoryTier
import com.lucent.app.data.TokenEstimator
import com.lucent.app.network.ApiSpec
import com.lucent.app.network.ChatTurn
import com.lucent.app.network.LlmClient
import com.lucent.app.network.RawModelReply
import com.lucent.app.network.ToolDefinition
import com.lucent.app.network.ToolExecResult
import com.lucent.app.network.ToolImage
import com.lucent.app.network.ToolResultTurn
import com.lucent.app.assistant.tools.LocalToolCallParser
import com.lucent.app.assistant.text.ReplyPolish
import com.lucent.app.assistant.prompts.SystemPrompts
import com.lucent.app.tools.AppTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PendingConfirmation(
    val actionTitle: String,
    val details: String,
    val toolName: String,
    val edits: List<AppTools.EditableArgument> = emptyList(),
    val editorKind: EditorKind? = null
)

enum class EditorKind { NOTE, TASK }

data class AssistantUiState(
    val sending: Boolean = false,
    val errorText: String = "",
    val errorConversationId: Long? = null,
    val errorTrace: AgentTrace? = null,
    val networkErrorMessage: String? = null,
    val pendingConfirmation: PendingConfirmation? = null,
    val messages: List<ChatMessage> = emptyList(),
    val currentConversationId: Long? = null,
    val conversations: List<ChatConversation> = emptyList(),
    val localTurnInFlight: Boolean = false,
    val variantSelection: Map<Long, Int> = emptyMap()
)

private object ActiveConversationStore {

    private const val FILE_NAME = "assistant_active_conversation.dat"

    @Volatile
    private var cached: Long? = null

    @Volatile
    private var cachedFor: Context? = null

    private fun file(context: Context): java.io.File =
        java.io.File(context.applicationContext.filesDir, FILE_NAME)

    fun load(context: Context): Long? {
        val app = context.applicationContext
        if (cachedFor === app) return cached
        val stored = runCatching {
            com.lucent.app.harness.HarnessVault.read(app, file(app)).trim().toLongOrNull()
        }.getOrNull()
        cached = stored
        cachedFor = app
        return stored
    }

    fun save(context: Context, id: Long?) {
        val app = context.applicationContext
        cached = id
        cachedFor = app
        AppScope.io.launch {
            runCatching {
                com.lucent.app.harness.HarnessVault.write(app, file(app), id?.toString().orEmpty())
            }
        }
    }
}

class AssistantControllerImpl(
    private val appScope: CoroutineScope,
    private val db: AppDatabase,
    private val llmClient: AssistantLlmClient,
    private val context: Context
) {

    private val scope: CoroutineScope = appScope

    private val genScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val REVEAL_STEP_MS = 22L
    private val MAX_TOOL_ROUNDS = 12

    private val RECENT_ITEMS_MAX = 6

    private val RECENT_CONVERSATIONS_MAX = 24


    val sending: Boolean get() = turns.isNotEmpty()

    fun isGenerating(conversationId: Long?): Boolean = turnFor(conversationId) != null

    fun streamingTextFor(conversationId: Long?): String? = turnFor(conversationId)?.streamingText

    fun thinkingFor(conversationId: Long?): Boolean = turnFor(conversationId)?.thinking == true

    fun loadingModelFor(conversationId: Long?): Boolean = turnFor(conversationId)?.loadingModel == true

    fun agentTraceFor(conversationId: Long?): AgentTrace? = turnFor(conversationId)?.recorder?.snapshot()

    var errorText by SyncedState("")
        private set
    var errorConversationId by SyncedState<Long?>(null)
        private set
    var errorTrace by SyncedState<AgentTrace?>(null)
        private set
    var errorTraceConversationId by SyncedState<Long?>(null)
        private set
    var networkErrorMessage by SyncedState<String?>(null)
        private set
    var pendingConfirmation by SyncedState<PendingConfirmation?>(null)
    private var refinementContext: String? = null
        private set

    private data class ConfirmationOutcome(
        val approved: Boolean,
        val edits: Map<String, String> = emptyMap(),
        val refine: Boolean = false
    )

    private data class ConfirmedCall(
        val approved: Boolean,
        val argumentsJson: String,
        val refine: Boolean = false
    )

    var messages by SyncedState<List<ChatMessage>>(emptyList())
        private set
    private var messagesJob: Job? = null

    private val turns = mutableStateListOf<Turn>()
    private val turnsGuard = Any()

    private fun turnFor(conversationId: Long?): Turn? =
        turns.firstOrNull { it.conversationId == conversationId }

    private fun localTurnOrNull(): Turn? = turns.firstOrNull { it.isLocal }

    private fun registerTurn(turn: Turn, assistantName: String) {
        synchronized(turnsGuard) {
            val first = turns.isEmpty()
            turns.add(turn)
            if (first) startGenerationService(assistantName)
        }
        publishState()
    }

    private fun unregisterTurn(turn: Turn) {
        synchronized(turnsGuard) {
            turns.remove(turn)
            if (turns.isEmpty()) stopGenerationService()
        }
        publishState()
    }

    private var appContextRef: Context? = context

    private val confirmationMutex = Mutex()
    @Volatile private var confirmingTurn: Turn? = null

    var currentConversationId by SyncedState<Long?>(null)
        private set


    var conversations by SyncedState<List<ChatConversation>>(emptyList())
        private set
    private var conversationsJob: Job? = null

    fun ensureMessagesLoaded(appContext: Context) {
        appContextRef = appContext.applicationContext
        if (conversationsJob == null) {
            conversationsJob = scope.launch {
                db.chatConversationDao().getAll().collect { conversations = it }
            }
        }
        if (messagesJob != null) return
        messagesJob = scope.launch {
            db.chatConversationDao().getAllOnce().forEach { conv ->
                if (db.chatDao().countInConversation(conv.id) == 0) {
                    db.chatConversationDao().delete(conv)
                }
            }
            val ctx = appContextRef
            val stored = if (ctx != null && currentConversationId == null) {
                withContext(Dispatchers.IO) { ActiveConversationStore.load(ctx) }
            } else {
                null
            }
            if (ctx != null && stored != null) {
                if (db.chatConversationDao().getById(stored) != null) {
                    currentConversationId = stored
                } else {
                    ActiveConversationStore.save(ctx, null)
                }
            }
            observeCurrentConversation(db)
        }
    }

    private fun setActiveConversation(id: Long?) {
        currentConversationId = id
        appContextRef?.let { ActiveConversationStore.save(it, id) }
    }

    fun onAllChatsCleared(appContext: Context) {
        stopAllGeneration(silent = true)
        setActiveConversation(null)
        clearError()
        networkErrorMessage = null
        lastSend = null
        lastSendConversationId = null
        observeCurrentConversation(db)
    }

    private var observeJob: Job? = null
    private fun observeCurrentConversation(db: AppDatabase) {
        observeJob?.cancel()
        val id = currentConversationId
        if (id == null) {
            messages = emptyList()
            observeJob = null
            return
        }
        val job = scope.launch {
            val snapshot = db.chatDao().getForConversationOnce(id)
            if (currentConversationId == id) messages = snapshot
            db.chatDao().getForConversation(id).collect {
                if (currentConversationId == id) messages = it
            }
        }
        observeJob = job
    }

    fun startNewConversation(appContext: Context) {
        localTurnOrNull()?.let { stopTurn(it, silent = true) }
        setActiveConversation(null)
        if (errorConversationId == null) clearError()
        observeCurrentConversation(db)
    }

    fun switchConversation(appContext: Context, id: Long) {
        val localTurn = localTurnOrNull()
        if (localTurn != null) {
            if (localTurn.conversationId == id) return
            stopTurn(localTurn)
        }
        setActiveConversation(id)
        observeCurrentConversation(db)
    }

    fun deleteConversation(appContext: Context, id: Long) {
        turnFor(id)?.let { stopTurn(it, silent = true) }
        if (errorConversationId == id) clearError()
        scope.launch {
            db.chatDao().clearConversation(id)
            db.chatConversationDao().getById(id)?.let { db.chatConversationDao().delete(it) }
            if (currentConversationId == id) {
                setActiveConversation(null)
                observeCurrentConversation(db)
            }
        }
    }

    fun renameConversation(appContext: Context, id: Long, newTitle: String) {
        scope.launch {
            db.chatConversationDao().getById(id)?.let { conv ->
                val title = newTitle.trim().ifBlank { conv.title }
                db.chatConversationDao().update(conv.copy(title = title))
            }
        }
    }

    fun clearError() {
        errorText = ""
        errorConversationId = null
        errorTrace = null
        errorTraceConversationId = null
    }

    fun clearNetworkError() { networkErrorMessage = null }

    fun deleteMessages(appContext: Context, ids: Set<Long>) {
        if (ids.isEmpty()) return
        AppScope.io.launch {
            try {
                db.chatDao().deleteByIds(ids.toList())
            } catch (t: Throwable) {
            }
        }
    }

    fun resolveConfirmation(
        approved: Boolean,
        edits: Map<String, String> = emptyMap(),
        refine: Boolean = false
    ) {
        pendingConfirmation = null
        val turn = confirmingTurn
        confirmingTurn = null
        appContextRef?.let { ctx ->
            scope.launch { com.lucent.app.data.AssistantDraftBridge.clear(ctx) }
        }
        turn?.confirmationDeferred?.complete(ConfirmationOutcome(approved, edits, refine))
        turn?.confirmationDeferred = null
    }

    fun stopGeneration(reason: String? = null, silent: Boolean = false) {
        turnFor(currentConversationId)?.let { stopTurn(it, reason, silent) }
    }

    fun stopAllGeneration(reason: String? = null, silent: Boolean = false) {
        turns.toList().forEach { stopTurn(it, reason, silent) }
    }

    private fun stopHarnessWork() {
        runCatching {
            com.lucent.app.harness.HarnessJobs.running().forEach { handle ->
                com.lucent.app.harness.HarnessJobs.kill(handle.job.id)
            }
        }
        runCatching {
            com.lucent.app.harness.SubAgents.list()
                .filter { it.status == "running" }
                .forEach { agent -> com.lucent.app.harness.SubAgents.stop(agent.id) }
        }
    }

    private fun stopTurn(turn: Turn, reason: String? = null, silent: Boolean = false) {
        turn.confirmationDeferred?.complete(ConfirmationOutcome(approved = false))
        turn.confirmationDeferred = null
        if (confirmingTurn === turn) {
            confirmingTurn = null
            pendingConfirmation = null
        }

        if (turn.isLocal) com.lucent.app.local.LocalLlm.stop()
        stopHarnessWork()

        val convId = turn.conversationId
        val ctx = appContextRef
        val cleaned = ReplyPolish.deRobotify(turn.snapshotBuffer()).trim()

        turn.job?.cancel()
        turn.job = null
        turn.finishStream()
        turn.thinking = false
        turn.loadingModel = false
        turn.recorder.endReasoningBlock()
        turn.recorder.finish(AgentStepStatus.CANCELLED)
        val traceJson = AgentTraceCodec.encode(turn.recorder.snapshot())
        unregisterTurn(turn)

        if (silent) {
            turn.turnPersisted = true
            return
        }
        val marker = reason ?: com.lucent.app.i18n.S.replyStopped
        if (!turn.turnPersisted && convId != null && ctx != null) {
            turn.turnPersisted = true
            val body = if (cleaned.isBlank()) marker else "$cleaned\n\n$marker"
            val tokens = TokenEstimator.estimate(body)
            AppScope.io.launch { insertAssistant(db, convId, body, null, null, tokens, traceJson = traceJson) }
        }
    }

    fun onAppBackgrounded(backgroundRepliesEnabled: Boolean) {
        if (backgroundRepliesEnabled) return
        localTurnOrNull()?.let { stopTurn(it, reason = com.lucent.app.i18n.S.replyStoppedBackground) }
    }

    val localTurnInFlight: Boolean get() = turns.any { it.isLocal }

    private data class LastSend(
        val text: String, val attachmentMime: String?, val attachmentData: String?,
        val attachmentName: String?, val attachmentListJson: String?,
        val url: String, val spec: ApiSpec, val key: String,
        val model: String, val name: String, val style: String,
        val memoryTier: MemoryTier, val webSearchEnabled: Boolean, val typingHaptics: Boolean,
        val useLocalModel: Boolean = false,
        val useLocalTools: Boolean = false,
        val useLocalGpu: Boolean = false,
        val confirmTools: Boolean = true,
        val smallModelMode: Boolean = false,
        val agentMode: Boolean = true,
        val localWebSearch: Boolean = false,
        val reasoning: String = com.lucent.app.data.ReasoningEffort.DEFAULT.key,
        val answersMessageId: Long = 0
    )
    private var lastSend: LastSend? = null
    private var lastSendConversationId: Long? = null

    @Volatile private var typingHapticsOn = true

    fun retryLast() {
        val ctx = appContextRef ?: return
        val p = lastSend ?: return
        val target = lastSendConversationId
        if (turnFor(target) != null) return
        networkErrorMessage = null
        send(
            ctx, p.text, p.attachmentMime, p.attachmentData, p.attachmentName, p.url, p.spec,
            p.key, p.model, p.name, p.style, p.memoryTier, p.webSearchEnabled, p.typingHaptics,
            attachmentListJson = p.attachmentListJson,
            insertUserMessage = false, useLocalModel = p.useLocalModel,
            useLocalTools = p.useLocalTools, useLocalGpu = p.useLocalGpu,
            confirmTools = p.confirmTools,
            smallModelMode = p.smallModelMode,
            agentMode = p.agentMode,
            localWebSearch = p.localWebSearch,
            reasoning = p.reasoning,
            answersMessageId = p.answersMessageId,
            targetConversationId = target
        )
    }

    private inner class Turn(
        initialConversationId: Long?,
        val isLocal: Boolean
    ) {
        var conversationId by mutableStateOf(initialConversationId)
        var job: Job? = null
        @Volatile var turnPersisted = false
        var params: LastSend? = null

        var thinking by mutableStateOf(false)
        var loadingModel by mutableStateOf(false)
        var streamingText by mutableStateOf<String?>(null)
        val recorder = AgentTraceRecorder()

        var confirmationDeferred: CompletableDeferred<ConfirmationOutcome>? = null

        val lock = Any()
        val buffer = StringBuilder()
        var shown = 0
        var typewriterJob: Job? = null
        @Volatile var streamEpoch = 0L

        fun currentLen(): Int = synchronized(lock) { buffer.length }
        fun snapshotBuffer(): String = synchronized(lock) { buffer.toString() }

        fun onDelta(epoch: Long, delta: String) {
            synchronized(lock) {
                if (epoch != streamEpoch) return
                buffer.append(delta)
            }
        }

        fun resetStream(reveal: Boolean) {
            typewriterJob?.cancel()
            synchronized(lock) {
                streamEpoch++
                buffer.setLength(0)
            }
            shown = 0
            streamingText = null
            recorder.beginBlock()
            if (!reveal) {
                typewriterJob = null
                return
            }
            val ctx = appContextRef
            typewriterJob = genScope.launch {
                var snapshot = ""
                while (isActive) {
                    val bufLen = synchronized(lock) { buffer.length }
                    if (shown >= bufLen) {
                        delay(16); continue
                    }
                    if (snapshot.length < bufLen) {
                        snapshot = synchronized(lock) { buffer.toString() }
                    }
                    val len = snapshot.length
                    val step = stepFor(len - shown)
                    var moved = 0
                    while (moved < step && shown < len) {
                        val cp = snapshot.codePointAt(shown)
                        shown += Character.charCount(cp)
                        moved++
                    }
                    streamingText = snapshot.substring(0, shown.coerceAtMost(len))
                    if (ctx != null &&
                        typingHapticsOn &&
                        conversationId == currentConversationId
                    ) {
                        Haptics.typingTick(ctx)
                    }
                    delay(REVEAL_STEP_MS)
                }
            }
        }

        suspend fun finishTyping(finalText: String) {
            synchronized(lock) {
                if (buffer.length < finalText.length) {
                    buffer.setLength(0)
                    buffer.append(finalText)
                }
            }
            while (shown < currentLen() && (typewriterJob?.isActive == true)) {
                delay(16)
            }
        }

        fun finishStream() {
            typewriterJob?.cancel()
            typewriterJob = null
            streamingText = null
            synchronized(lock) {
                streamEpoch++
                buffer.setLength(0)
            }
            shown = 0
        }

        fun completionBuzz() {
            typewriterJob?.cancel()
            typewriterJob = null
            if (!typingHapticsOn) return
            if (conversationId != currentConversationId) return
            appContextRef?.let { Haptics.finishBuzz(it) }
        }
    }

    fun resend(
        appContext: Context,
        message: ChatMessage,
        url: String,
        spec: ApiSpec,
        key: String,
        model: String,
        name: String,
        style: String,
        memoryTier: MemoryTier,
        webSearchEnabled: Boolean,
        typingHapticsEnabled: Boolean,
        useLocalModel: Boolean,
        useLocalTools: Boolean,
        useLocalGpu: Boolean,
        confirmTools: Boolean,
        smallModelMode: Boolean,
        agentMode: Boolean = true,
        localWebSearch: Boolean = false,
        reasoning: String = com.lucent.app.data.ReasoningEffort.DEFAULT.key
    ) {
        if (message.role != "user") return
        send(
            appContext = appContext,
            text = message.content,
            attachmentMime = message.attachmentMime,
            attachmentData = message.attachmentData,
            attachmentName = message.attachmentName,
            attachmentListJson = message.attachmentList,
            url = url, spec = spec, key = key, model = model,
            name = name, style = style,
            memoryTier = memoryTier,
            webSearchEnabled = webSearchEnabled,
            typingHapticsEnabled = typingHapticsEnabled,
            insertUserMessage = false,
            useLocalModel = useLocalModel,
            useLocalTools = useLocalTools,
            useLocalGpu = useLocalGpu,
            confirmTools = confirmTools,
            smallModelMode = smallModelMode,
            agentMode = agentMode,
            localWebSearch = localWebSearch,
            reasoning = reasoning,
            answersMessageId = message.id,
            targetConversationId = message.conversationId
        )
        variantSelection.remove(message.id)
        publishState()
    }

    val variantSelection = androidx.compose.runtime.mutableStateMapOf<Long, Int>()

    fun selectVariant(replyToId: Long, index: Int) {
        variantSelection[replyToId] = index
        publishState()
    }

    fun send(
        appContext: Context,
        text: String,
        attachmentMime: String?,
        attachmentData: String?,
        attachmentName: String?,
        url: String,
        spec: ApiSpec,
        key: String,
        model: String,
        name: String,
        style: String,
        memoryTier: MemoryTier,
        webSearchEnabled: Boolean,
        typingHapticsEnabled: Boolean = true,
        insertUserMessage: Boolean = true,
        useLocalModel: Boolean = false,
        useLocalTools: Boolean = false,
        useLocalGpu: Boolean = false,
        confirmTools: Boolean = true,
        smallModelMode: Boolean = false,
        agentMode: Boolean = true,
        localWebSearch: Boolean = false,
        reasoning: String = com.lucent.app.data.ReasoningEffort.DEFAULT.key,
        answersMessageId: Long = 0,
        targetConversationId: Long? = null,
        attachmentListJson: String? = null,
        attachments: List<com.lucent.app.data.Attachment> = emptyList()
    ) {
        val targetKey = targetConversationId ?: currentConversationId
        if (turnFor(targetKey) != null) return
        appContextRef = appContext.applicationContext
        typingHapticsOn = typingHapticsEnabled
        val turn = Turn(initialConversationId = targetKey, isLocal = useLocalModel)
        turn.params = LastSend(
            text, attachmentMime, attachmentData, attachmentName,
            if (attachments.size > 1) com.lucent.app.data.Attachments.serialize(attachments)
            else attachmentListJson,
            url, spec, key, model,
            name, style, memoryTier, webSearchEnabled, typingHapticsEnabled, useLocalModel,
            useLocalTools, useLocalGpu, confirmTools, smallModelMode, agentMode, localWebSearch,
            reasoning, answersMessageId
        )
        turn.thinking = true
        if (errorConversationId == targetKey) {
            errorText = ""
            errorConversationId = null
            errorTrace = null
            errorTraceConversationId = null
        }
        networkErrorMessage = null
        registerTurn(turn, name)

        turn.job = genScope.launch {
            try {
                var convId = targetConversationId ?: currentConversationId
                if (convId == null) {
                    convId = db.chatConversationDao().insert(ChatConversation())
                    setActiveConversation(convId)
                    turn.conversationId = convId
                    observeCurrentConversation(db)
                }
                val conversationId = convId
                turn.conversationId = conversationId

                var answeredId = answersMessageId
                if (insertUserMessage) {
                    answeredId = db.chatDao().insert(
                        ChatMessage(
                            role = "user", content = text,
                            attachmentMime = attachmentMime,
                            attachmentData = attachmentData,
                            attachmentName = attachmentName,
                            attachmentList =
                                if (attachments.size > 1) com.lucent.app.data.Attachments.serialize(attachments)
                                else attachmentListJson,
                            conversationId = conversationId
                        )
                    )
                    db.chatConversationDao().getById(conversationId)?.let { conv ->
                        val newTitle = if (conv.title.isBlank() || conv.title == "New conversation") {
                            text.trim().take(40).ifBlank { conv.title }
                        } else conv.title
                        db.chatConversationDao().update(
                            conv.copy(title = newTitle, updatedAt = System.currentTimeMillis())
                        )
                    }
                }

                if (useLocalModel) {
                    runLocalTurn(
                        turn, db, conversationId, useLocalTools, useLocalGpu, confirmTools,
                        memoryTier, smallModelMode, answeredId, agentMode, localWebSearch
                    )
                    return@launch
                }

                var history = buildHistory(db, conversationId, memoryTier)
                val crossMemory = crossConversationMemory(db, conversationId, memoryTier)
                val compactCrossMemory =
                    if (smallModelMode) crossConversationMemory(db, conversationId, memoryTier, cap = SMALL_MODEL_CROSS_BUDGET)
                    else ""
                val parkedRefine = refinementContext
                refinementContext = null
                val recentItems = recentItemsContext(db, conversationId)
                val fastMode = !agentMode
                val useReasoning = if (fastMode) com.lucent.app.data.ReasoningEffort.DEFAULT.key else reasoning
                val compactMemory = if (smallModelMode || fastMode) {
                    crossConversationMemory(db, conversationId, memoryTier, cap = SMALL_MODEL_CROSS_BUDGET)
                } else {
                    compactCrossMemory
                }
                val basePrompt =
                    if (smallModelMode || fastMode) SystemPrompts.compact(name, style, tier = memoryTier, webSearchEnabled = webSearchEnabled, userText = text)
                    else SystemPrompts.full(name, style, memoryTier, webSearchEnabled)
                val promptContext = SystemPrompts.context(
                    userText = text,
                    crossMemory = if (smallModelMode || fastMode) compactMemory else crossMemory,
                    recentItems = recentItems
                )
                val conversationCacheKey = "lucent-$conversationId"
                val directPrompt = if (fastMode) {
                    basePrompt +
                        "\n\nAGENT MODE IS OFF in this conversation. Call no tools at all — no JSON, no " +
                        "function calls, no \"Result of\" lines. Answer the person directly, in one short " +
                        "reply, as quickly as you can. If they asked for something that would need a tool, " +
                        "say briefly that agent mode is off in Settings and answer in words instead."
                } else basePrompt
                val systemPrompt = if (parkedRefine != null) {
                    basePrompt +
                        "\n\nIMPORTANT (the user is refining an earlier proposal of yours): you proposed this before, " +
                        "the user paused it to ask for changes, and it was NOT executed. Proposal: " + parkedRefine +
                        "\nThe user has now told you what to change. Re-propose the action with their adjustments by calling " +
                        "the matching tool again. The app will show them a confirmation dialog - only call the tool; never " +
                        "claim anything was done, because it only happens after they approve it."
                } else directPrompt
                com.lucent.app.harness.HarnessRuntime.conversationId = conversationId
                com.lucent.app.harness.HarnessRuntime.noteSink = { line -> turn.recorder.addPlanning(line) }
                com.lucent.app.harness.HarnessRuntime.llm = SubAgentBridge(url, spec, key, model)
                if (com.lucent.app.harness.HarnessRuntime.config().enabled) {
                    runCatching {
                        com.lucent.app.harness.HarnessGate.dynamicTools = com.lucent.app.harness.McpTools.dynamicTools()
                    }
                }
                val tools = if (fastMode) emptyList() else AppTools.definitions(includeWebSearch = webSearchEnabled)

                val (uploadMime, uploadData, uploadName) =
                    resolveUpload(db, conversationId, attachmentMime, attachmentData, attachmentName)

                var finalReply: RawModelReply? = null
                var lastToolResults: List<ToolExecResult> = emptyList()
                var errored = false
                val executed = HashMap<String, ToolExecResult>()

                var declinedDetails: String? = null

                var round = 0
                while (round < MAX_TOOL_ROUNDS) {
                    turn.thinking = true
                    turn.resetStream(reveal = false)
                    val roundEpoch = turn.streamEpoch
                    val result = llmClient.streamChat(
                        url, spec, key, model, history, systemPrompt, tools,
                        { delta -> turn.onDelta(roundEpoch, delta) },
                        { piece -> turn.recorder.appendReasoning(piece) },
                        { attempt -> turn.recorder.addNote(com.lucent.app.i18n.S.agentTraceRetrying(attempt)) },
                        reasoning = useReasoning,
                        cacheKey = conversationCacheKey,
                        context = promptContext
                    )

                    if (result.isFailure) {
                        fail(turn, result.exceptionOrNull() ?: Exception("Unknown error"))
                        errored = true
                        break
                    }
                    val reply = result.getOrThrow()
                    turn.recorder.endReasoningBlock()
                    reportCacheUse(turn, reply.usage)

                    if (reply.toolCalls.isEmpty()) {
                        finalReply = reply
                        break
                    }

                    if (!reply.text.isNullOrBlank()) turn.recorder.addPlanning(reply.text)

                    val results = mutableListOf<ToolExecResult>()
                    for (call in reply.toolCalls) {
                        val sig = signatureOf(call.name, call.argumentsJson)
                        val cached = executed[sig]
                        if (cached != null) {
                            val reused = turn.recorder.startTool(call.name, call.argumentsJson)
                            turn.recorder.finishTool(reused, cached)
                            results.add(cached)
                            continue
                        }

                        val stepIndex = turn.recorder.startTool(call.name, call.argumentsJson)
                        val confirmed = if (confirmTools && AppTools.isMutating(call.name)) {
                            confirmToolCall(turn, call.name, call.argumentsJson)
                        } else ConfirmedCall(approved = true, argumentsJson = call.argumentsJson)

                        if (confirmed.refine) {
                            val proposal = AppTools.describeToolCall(call.name, confirmed.argumentsJson)
                            refinementContext = proposal
                            declinedDetails = "refine\u0001" + proposal
                            turn.recorder.cancelTool(stepIndex, com.lucent.app.i18n.S.agentTraceRefining)
                            break
                        }

                        if (!confirmed.approved) {
                            declinedDetails = AppTools.describeToolCall(call.name, call.argumentsJson)
                            turn.recorder.cancelTool(stepIndex, com.lucent.app.i18n.S.agentTraceCancelled)
                            break
                        }

                        turn.recorder.beginToolRun(stepIndex)
                        turn.thinking = true
                        val r = try {
                            AppTools.execute(
                                appContext, db, call.name, confirmed.argumentsJson,
                                uploadMime, uploadData, uploadName
                            )
                        } catch (t: Throwable) {
                            turn.recorder.failTool(stepIndex, toolFailureDetail(t))
                            logToolFailure(call.name, t)
                            throw t
                        }
                        turn.recorder.finishTool(stepIndex, r)
                        if (!r.success) logToolFailure(call.name, null)
                        rememberTouched(db, conversationId, call.name, confirmed.argumentsJson, r)
                        executed[sig] = r
                        results.add(r)
                    }
                    if (declinedDetails != null) break

                    val toolImages = mutableListOf<ToolImage>()
                    for (r in results) toolImages.addAll(r.images)
                    lastToolResults = results

                    history = history + ChatTurn(
                        role = "assistant",
                        content = reply.text?.trim().orEmpty(),
                        toolCalls = reply.toolCalls,
                        thinkingBlocksJson = reply.thinkingBlocksJson,
                        reasoningContent = reply.reasoningContent
                    )
                    val resultTurns = reply.toolCalls.zip(results).map { (call, r) ->
                        ToolResultTurn(id = call.id, name = call.name, content = r.summary)
                    }
                    val primaryImage = toolImages.firstOrNull()
                    val resultContent = if (primaryImage != null && toolImages.size > 1) {
                        resultTurns.mapIndexed { i, t ->
                            if (i == resultTurns.lastIndex)
                                t.copy(content = t.content + " (Attached: image \"${primaryImage.name}\"; ${toolImages.size - 1} more attachment(s) exist but aren't shown.)")
                            else t
                        }
                    } else if (primaryImage != null) {
                        resultTurns.mapIndexed { i, t ->
                            if (i == resultTurns.lastIndex) t.copy(content = t.content + " (Attached: image \"${primaryImage.name}\".)")
                            else t
                        }
                    } else resultTurns
                    history = history + ChatTurn(
                        role = "tool",
                        content = "",
                        attachmentMime = primaryImage?.mime,
                        attachmentData = primaryImage?.data,
                        toolResults = resultContent
                    )
                    round++
                }

                if (declinedDetails != null) {
                    val content = if (declinedDetails.startsWith("refine\u0001")) {
                        com.lucent.app.i18n.S.assistantRefineQuestion(declinedDetails.substringAfter("\u0001"))
                    } else com.lucent.app.i18n.S.assistantDeclinedReply(declinedDetails)
                    turn.thinking = false
                    turn.resetStream(reveal = true)
                    turn.finishTyping(content)
                    turn.recorder.finish(AgentStepStatus.CANCELLED)
                    insertAssistant(
                        db, conversationId, content, null, null,
                        TokenEstimator.estimate(content), answeredId,
                        traceJson = AgentTraceCodec.encode(turn.recorder.snapshot())
                    )
                    turn.turnPersisted = true
                    turn.completionBuzz()
                } else if (!errored) {
                    if (finalReply == null) {
                        turn.thinking = true
                        turn.resetStream(reveal = false)
                        val forcedEpoch = turn.streamEpoch
                        val forced = llmClient.streamChat(
                            url, spec, key, model, history, systemPrompt, emptyList(),
                            { delta -> turn.onDelta(forcedEpoch, delta) },
                            { piece -> turn.recorder.appendReasoning(piece) },
                            { attempt -> turn.recorder.addNote(com.lucent.app.i18n.S.agentTraceRetrying(attempt)) },
                            reasoning = useReasoning,
                            cacheKey = conversationCacheKey,
                            context = promptContext
                        )
                        if (forced.isFailure) {
                            fail(turn, forced.exceptionOrNull() ?: Exception("Unknown error"))
                            errored = true
                        } else {
                            finalReply = forced.getOrThrow()
                            turn.recorder.endReasoningBlock()
                            reportCacheUse(turn, finalReply?.usage ?: com.lucent.app.network.TokenUsage.NONE)
                        }
                    }
                    if (!errored) finalReply?.let { reply ->
                        val img = reply.imageData?.takeIf { it.isNotBlank() }
                        val content = ReplyPolish.replyContent(reply.text, img != null, lastToolResults, userText = text)
                        turn.thinking = false
                        turn.resetStream(reveal = true)
                        turn.finishTyping(content)
                        val tokens = TokenEstimator.estimate(systemPrompt) +
                            TokenEstimator.estimateAll(history.map { it.content }) +
                            TokenEstimator.estimate(content)
                        turn.recorder.finish(AgentStepStatus.DONE)
                        insertAssistant(
                            db, conversationId, content, reply.imageMime, img, tokens, answeredId,
                            traceJson = AgentTraceCodec.encode(turn.recorder.snapshot()),
                            thinkingBlocks = reply.thinkingBlocksJson,
                            reasoningText = reply.reasoningContent
                        )
                        turn.turnPersisted = true
                        turn.completionBuzz()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(turn, e)
            } finally {
                turn.finishStream()
                turn.thinking = false
                turn.loadingModel = false
                if (confirmingTurn === turn) {
                    confirmingTurn = null
                    pendingConfirmation = null
                }
                unregisterTurn(turn)
            }
        }
    }

    private suspend fun runLocalTurn(
        turn: Turn,
        db: AppDatabase,
        conversationId: Long,
        useTools: Boolean,
        useGpu: Boolean,
        confirmTools: Boolean,
        memoryTier: MemoryTier,
        smallModelMode: Boolean,
        answeredId: Long,
        agentMode: Boolean,
        webSearchEnabled: Boolean
    ) {
        val ctx = appContextRef ?: return

        if (!com.lucent.app.local.LocalModelStore.hasModel(ctx)) {
            turn.thinking = false
            postError(turn, com.lucent.app.i18n.S.localModelMissing)
            return
        }

        com.lucent.app.local.LocalLlm.setGpuEnabled(useGpu)

        turn.loadingModel = true
        val loaded = try {
            com.lucent.app.local.LocalLlm.ensureLoaded(ctx)
        } finally {
            turn.loadingModel = false
        }
        com.lucent.app.data.StartupLog.event(
            ctx,
            "local turn: model=${com.lucent.app.local.LocalModelStore.displayName(ctx) ?: "?"}, gpu=$useGpu, loaded=$loaded"
        )
        if (!loaded) {
            turn.thinking = false
            postError(turn, com.lucent.app.i18n.S.localModelLoadFailed(com.lucent.app.i18n.S.localModelLoadFailedDetail))
            return
        }

        if (!useTools || !agentMode) {
            runLocalChatOnly(
                turn, db, conversationId, memoryTier, smallModelMode, answeredId,
                toolsAllowed = useTools, agentMode = agentMode
            )
            return
        }

        val tools = AppTools.definitions(includeWebSearch = webSearchEnabled)
            .filterNot { com.lucent.app.harness.HarnessGate.isHarnessTool(it.name) }
        val validToolNames = tools.map { it.name }.toHashSet()

        val turns = buildHistory(db, conversationId, localTier(memoryTier))
            .filter { it.role == "user" || it.role == "assistant" }
            .map { it.role to it.content }
            .filter { it.second.isNotBlank() }
            .takeLast(com.lucent.app.local.LocalLlm.HISTORY_TURNS * 2)
        val lastUserText = turns.lastOrNull { it.first == "user" }?.second ?: ""

        val localPrompt = SystemPrompts.local(
            tools,
            lastUserText,
            smallModelMode,
            recentItems = recentItemsContext(db, conversationId),
            webSearchEnabled = webSearchEnabled
        )
        val messages = mutableListOf<Pair<String, String>>()
        messages.add("system" to localPrompt)
        messages.addAll(localTranscriptWithin(localPrompt, turns))

        val (uploadMime, uploadData, uploadName) = resolveUpload(db, conversationId, null, null, null)
        if (!uploadData.isNullOrBlank()) {
            messages.add(
                "system" to ("The user has an uploaded file in this conversation: \"" +
                    (uploadName ?: "file") + "\". You cannot see inside it, but you CAN save it: " +
                    "call attach_upload_to_note or attach_upload_to_task to attach that exact " +
                    "file to a note or task when asked.")
            )
        }

        val executed = HashMap<String, ToolExecResult>()
        val toolResults = mutableListOf<ToolExecResult>()
        var finalText: String? = null

        var round = 0
        while (round < MAX_LOCAL_TOOL_ROUNDS) {
            turn.resetStream(reveal = false)
            turn.thinking = true
            val roundEpoch = turn.streamEpoch
            val rc = com.lucent.app.local.LocalLlm.generate(messages) { piece -> turn.onDelta(roundEpoch, piece) }
            val raw = turn.snapshotBuffer()

            if (rc == 1) return
            if (rc != 0) { turn.thinking = false; postError(turn, com.lucent.app.i18n.S.localModelGenerateFailed + " [" + rc + "]"); return }

            val call = LocalToolCallParser.parseLocalToolCall(raw, validToolNames)
            if (call == null) {
                val attempted = LocalToolCallParser.attemptedToolCallName(raw)
                if (attempted != null && round < MAX_LOCAL_TOOL_ROUNDS - 1) {
                    turn.recorder.addNote(
                        com.lucent.app.i18n.S.ccRunGeneric(attempted),
                        com.lucent.app.i18n.S.agentTraceUnknownTool,
                        AgentStepStatus.FAILED
                    )
                    messages.add("assistant" to raw.trim().take(600))
                    messages.add(
                        "user" to ("Result of " + attempted + ": ERROR — no tool named \"" + attempted +
                            "\" exists. Use EXACTLY one tool name from the list in the system " +
                            "message, or answer the user in plain text without any JSON.")
                    )
                    round++
                    continue
                }
                finalText = ReplyPolish.deRobotify(raw).trim()
                break
            }

            turn.resetStream(reveal = false)

            val sig = signatureOf(call.name, call.argsJson)
            val cached = executed[sig]
            val result = if (cached != null) cached else {
                val stepIndex = turn.recorder.startTool(call.name, call.argsJson)
                val confirmed = if (confirmTools && AppTools.isMutating(call.name)) {
                    confirmToolCall(turn, call.name, call.argsJson)
                } else ConfirmedCall(approved = true, argumentsJson = call.argsJson)

                if (confirmed.refine) {
                    val proposal = AppTools.describeToolCall(call.name, confirmed.argumentsJson)
                    refinementContext = proposal
                    val content = com.lucent.app.i18n.S.assistantRefineQuestion(proposal)
                    turn.thinking = false
                    turn.resetStream(reveal = true)
                    turn.finishTyping(content)
                    turn.recorder.cancelTool(stepIndex, com.lucent.app.i18n.S.agentTraceRefining)
                    turn.recorder.finish(AgentStepStatus.CANCELLED)
                    insertAssistant(
                        db, conversationId, content, null, null,
                        TokenEstimator.estimate(content), answeredId,
                        traceJson = AgentTraceCodec.encode(turn.recorder.snapshot())
                    )
                    turn.turnPersisted = true
                    turn.completionBuzz()
                    return
                } else if (!confirmed.approved) {
                    val content = com.lucent.app.i18n.S.assistantDeclinedReply(
                        AppTools.describeToolCall(call.name, call.argsJson)
                    )
                    turn.thinking = false
                    turn.resetStream(reveal = true)
                    turn.finishTyping(content)
                    turn.recorder.cancelTool(stepIndex, com.lucent.app.i18n.S.agentTraceCancelled)
                    turn.recorder.finish(AgentStepStatus.CANCELLED)
                    insertAssistant(
                        db, conversationId, content, null, null,
                        TokenEstimator.estimate(content), answeredId,
                        traceJson = AgentTraceCodec.encode(turn.recorder.snapshot())
                    )
                    turn.turnPersisted = true
                    turn.completionBuzz()
                    return
                } else {
                    turn.recorder.beginToolRun(stepIndex)
                    turn.thinking = true
                    val r = try {
                        AppTools.execute(
                            ctx, db, call.name, confirmed.argumentsJson,
                            uploadMime, uploadData, uploadName
                        )
                    } catch (t: Throwable) {
                        turn.recorder.failTool(stepIndex, toolFailureDetail(t))
                        logToolFailure(call.name, t)
                        throw t
                    }
                    turn.recorder.finishTool(stepIndex, r)
                    if (!r.success) logToolFailure(call.name, null)
                    rememberTouched(db, conversationId, call.name, confirmed.argumentsJson, r)
                    executed[sig] = r
                    r
                }
            }
            toolResults.add(result)

            messages.add("assistant" to LocalToolCallParser.renderLocalToolCall(call))
            messages.add("user" to "Result of ${call.name}: ${result.summary}")
            round++
        }

        if (finalText == null) {
            turn.resetStream(reveal = false)
            turn.thinking = true
            messages.add("system" to "Stop calling tools now and write your final answer to the user, in their language, as plain text. Do not output any JSON.")
            val finalEpoch = turn.streamEpoch
            val rc = com.lucent.app.local.LocalLlm.generate(messages) { piece -> turn.onDelta(finalEpoch, piece) }
            if (rc == 1) return
            finalText = ReplyPolish.deRobotify(turn.snapshotBuffer()).trim()
        }

        val unusableToolCall = finalText?.let { ft ->
            LocalToolCallParser.attemptedToolCallName(ft) != null && ft.trim().let { shape ->
                shape.startsWith("{") || shape.startsWith("<tool_call") || shape.startsWith("```")
            }
        } == true
        if (unusableToolCall) finalText = ""
        if (unusableToolCall && toolResults.none { it.success }) {
            postError(turn, com.lucent.app.i18n.S.localModelGenerateFailed)
        }

        val content = ReplyPolish.replyContent(finalText, hasImage = false, toolResults = toolResults, userText = lastUserText)
        turn.thinking = false
        turn.resetStream(reveal = true)
        turn.finishTyping(content)
        val tokens = TokenEstimator.estimateAll(messages.map { it.second }) + TokenEstimator.estimate(content)
        turn.recorder.finish(AgentStepStatus.DONE)
        insertAssistant(
            db, conversationId, content, null, null, tokens, answeredId,
            traceJson = AgentTraceCodec.encode(turn.recorder.snapshot())
        )
        turn.turnPersisted = true
        turn.completionBuzz()
    }

    private suspend fun runLocalChatOnly(
        turn: Turn,
        db: AppDatabase,
        conversationId: Long,
        memoryTier: MemoryTier,
        smallModelMode: Boolean,
        answeredId: Long,
        toolsAllowed: Boolean,
        agentMode: Boolean
    ) {
        val historyTurns =
            if (smallModelMode) com.lucent.app.local.LocalLlm.HISTORY_TURNS
            else com.lucent.app.local.LocalLlm.HISTORY_TURNS * 2
        val turns = buildHistory(db, conversationId, localTier(memoryTier))
            .filter { it.role == "user" || it.role == "assistant" }
            .map { it.role to it.content }
            .filter { it.second.isNotBlank() }
            .takeLast(historyTurns)
        val lastUserText = turns.lastOrNull { it.first == "user" }?.second ?: ""

        val turnImages: List<ByteArray> = if (com.lucent.app.local.LocalLlm.supportsVision() && answeredId > 0) {
            try {
                db.chatDao().getAll().first().firstOrNull { it.id == answeredId }
                    ?.takeIf { it.attachmentMime?.startsWith("image/") == true }
                    ?.attachmentData
                    ?.let { listOf(android.util.Base64.decode(it, android.util.Base64.DEFAULT)) }
                    ?: emptyList()
            } catch (t: Throwable) {
                emptyList()
            }
        } else emptyList()

        val offReason = if (!agentMode) {
            "Agent mode is OFF — the \"Agent mode\" switch under Settings > Assistant > Local " +
                "model — so this turn is a plain, quick reply with no tools and no step-by-step " +
                "working."
        } else {
            "tool permission has not been granted — the \"Allow tools\" switch under Settings > " +
                "Assistant > Local model is OFF."
        }
        val messages = buildList {
            add(
                "system" to (
                    "You are a helpful assistant living inside Lucent, a personal notes and tasks app. " +
                    "Always reply in the same language the user writes in. Be concise, warm, and clear. " +
                    "Write plain conversational text only — never markdown, asterisks, bullet points, or headings.\n\n" +
                    "IMPORTANT — WHAT YOU CANNOT DO RIGHT NOW, AND WHY. You have NO tools and NO " +
                    "ability to change anything in this app in this conversation. You cannot " +
                    "create, read, edit, complete, or delete notes or tasks, you cannot attach " +
                    "files, and you cannot see the user's existing notes or tasks at all. You also " +
                    "have NO internet access, so you cannot look anything up or fetch anything " +
                    "current. The ONLY reason for all of this is a setting: $offReason " +
                    "It is a setting, not a flaw in their request; their requests are not impossible.\n\n" +
                    "Because of that, you must NEVER say or imply that you have created, added, " +
                    "saved, changed, completed, deleted, or found anything. Never say \"done\", " +
                    "\"added it\", \"I've made that note\", or anything of that shape. That would be " +
                    "false, and the user would go looking for something that does not exist.\n\n" +
                    "If they ask you to create, change, find, or do anything with a note or task " +
                    "(for example \"add a task for tomorrow morning\"), you MUST give them the real " +
                    "reason, in their own language: you can't act right now because " + offReason + " " +
                    "They can change that under Settings > Assistant > Local model (or add the item " +
                    "themselves on the Notes or Tasks tab). NEVER answer with only a bare refusal " +
                    "like \"I can't do that\" or \"your task cannot be completed\" — a refusal that " +
                    "hides the reason reads as a malfunction and leaves them stuck, when one " +
                    "sentence about the setting fixes it. If they ask WHY you can't, that setting " +
                    "IS the answer. You can still help fully in words — draft the wording, think " +
                    "it through, talk it over — and offering that is far more useful than an apology."
                )
            )
            com.lucent.app.i18n.ReplyLanguage.instructionFor(lastUserText)?.let { add("system" to it) }
            if (turnImages.isNotEmpty()) {
                add("system" to "The user's current message includes an image, and you CAN see it. Describe or use it directly; do not claim you cannot see images.")
            }
            addAll(turns)
        }

        turn.resetStream(reveal = true)
        var first = true
        val chatEpoch = turn.streamEpoch
        val rc = com.lucent.app.local.LocalLlm.generate(messages, images = turnImages) { piece ->
            if (first) { first = false; turn.thinking = false }
            turn.onDelta(chatEpoch, piece)
        }
        appContextRef?.let { com.lucent.app.data.StartupLog.event(it, "local chat: generate rc=$rc") }
        if (rc == 1) return
        if (rc != 0) { turn.thinking = false; postError(turn, com.lucent.app.i18n.S.localModelGenerateFailed + " [" + rc + "]"); return }

        val content = ReplyPolish.replyContent(
            ReplyPolish.deRobotify(turn.snapshotBuffer()).trim(),
            hasImage = false, toolResults = emptyList(), userText = lastUserText
        )
        turn.thinking = false
        turn.finishTyping(content)
        val tokens = TokenEstimator.estimateAll(messages.map { it.second }) + TokenEstimator.estimate(content)
        insertAssistant(db, conversationId, content, null, null, tokens, answeredId)
        turn.turnPersisted = true
        turn.completionBuzz()
    }

    private val MAX_LOCAL_TOOL_ROUNDS = 10

    private fun localTranscriptWithin(
        systemPrompt: String,
        turns: List<Pair<String, String>>
    ): List<Pair<String, String>> {
        val budget = com.lucent.app.local.LocalLlm.N_CTX -
            com.lucent.app.local.LocalLlm.MAX_NEW_TOKENS -
            com.lucent.app.local.LocalLlm.MAX_NEW_TOKENS / 2
        var remaining = budget - TokenEstimator.estimate(systemPrompt)
        val kept = ArrayDeque<Pair<String, String>>()
        for (message in turns.asReversed()) {
            val cost = TokenEstimator.estimate(message.second)
            if (kept.isNotEmpty() && cost > remaining) break
            kept.addFirst(message)
            remaining -= cost
        }
        return kept.toList()
    }

    private fun localTier(tier: MemoryTier): MemoryTier =
        if (tier == MemoryTier.HIGH) MemoryTier.MEDIUM else tier

    private suspend fun buildHistory(db: AppDatabase, conversationId: Long, tier: MemoryTier): List<ChatTurn> {
        val current = db.chatDao().getForConversationOnce(conversationId)
            .map {
                ChatTurn(
                    it.role, it.content, it.attachmentMime, it.attachmentData,
                    thinkingBlocksJson = it.reasoningBlocks.orEmpty(),
                    reasoningContent = it.reasoningText.orEmpty()
                )
            }
        return when (tier) {
            MemoryTier.LOW -> current.takeLast(1)
            MemoryTier.MEDIUM, MemoryTier.HIGH -> current
        }
    }

    private suspend fun resolveUpload(
        db: AppDatabase,
        conversationId: Long,
        mime: String?,
        data: String?,
        name: String?
    ): Triple<String?, String?, String?> {
        if (!data.isNullOrBlank()) return Triple(mime, data, name)
        val previous = db.chatDao().getForConversationOnce(conversationId)
            .lastOrNull { it.role == "user" && !it.attachmentData.isNullOrBlank() }
            ?: return Triple(null, null, null)
        return Triple(previous.attachmentMime, previous.attachmentData, previous.attachmentName)
    }

    private suspend fun crossConversationMemory(
        db: AppDatabase,
        currentConversationId: Long,
        tier: MemoryTier,
        cap: Int = MemoryTier.HIGH_CROSS_MESSAGE_BUDGET
    ): String {
        if (tier != MemoryTier.HIGH) return ""
        val all = db.chatDao().getAll().first()
        val others = all.filter { it.conversationId != currentConversationId }
            .takeLast(cap)
        if (others.isEmpty()) return ""
        val sb = StringBuilder()
        others.forEach { m ->
            val who = if (m.role == "assistant") "You" else "User"
            sb.append(who).append(": ").append(m.content.trim().take(400)).append("\n")
        }
        return sb.toString().trim()
    }

    private data class TouchedItem(val kind: String, val id: Long)

    private val touchedByConversation = LinkedHashMap<Long, MutableList<TouchedItem>>()

    private fun toolItemKind(toolName: String, result: ToolExecResult): String? = when {
        result.openNoteId != null && result.openNoteId > 0L -> "note"
        result.openTaskId != null && result.openTaskId > 0L -> "task"
        toolName.contains("notebook") -> null
        toolName.endsWith("_note") || toolName.startsWith("note_") || toolName.contains("_note_") -> "note"
        toolName.endsWith("_task") || toolName.startsWith("task_") || toolName.contains("_task_") -> "task"
        toolName.contains("subtask") -> "task"
        else -> null
    }

    private fun titleQueryOf(argsJson: String): String = try {
        val args = org.json.JSONObject(argsJson)
        listOf("title", "note_title", "task_title", "new_title", "query", "name")
            .firstNotNullOfOrNull { key -> args.optString(key, "").takeIf { it.isNotBlank() } }
            .orEmpty()
    } catch (t: Throwable) {
        ""
    }

    private suspend fun rememberTouched(
        db: AppDatabase,
        conversationId: Long,
        toolName: String,
        argsJson: String,
        result: ToolExecResult
    ) {
        if (!result.success) return
        val kind = toolItemKind(toolName, result) ?: return
        val query = titleQueryOf(argsJson)
        val id = if (kind == "note") {
            result.openNoteId ?: AppTools.resolveNote(AppTools.activeNotes(db), query)?.id
        } else {
            result.openTaskId ?: AppTools.resolveTask(AppTools.activeTasks(db), query)?.id
        } ?: return
        while (touchedByConversation.size >= RECENT_CONVERSATIONS_MAX &&
            !touchedByConversation.containsKey(conversationId)
        ) {
            val oldest = touchedByConversation.keys.firstOrNull() ?: break
            touchedByConversation.remove(oldest)
        }
        val list = touchedByConversation.getOrPut(conversationId) { mutableListOf() }
        list.removeAll { it.kind == kind && it.id == id }
        list.add(TouchedItem(kind, id))
        while (list.size > RECENT_ITEMS_MAX) list.removeAt(0)
    }

    private suspend fun recentItemsContext(db: AppDatabase, conversationId: Long): String {
        val notes = AppTools.activeNotes(db)
        val tasks = AppTools.activeTasks(db)
        val noteById = notes.associateBy { it.id }
        val taskById = tasks.associateBy { it.id }
        val lines = LinkedHashSet<String>()
        touchedByConversation[conversationId].orEmpty().asReversed().forEach { item ->
            if (item.kind == "note") {
                noteById[item.id]?.let { lines.add("note \"" + it.title.ifBlank { "Untitled" } + "\"") }
            } else {
                taskById[item.id]?.let { lines.add("task \"" + it.title.ifBlank { "Untitled" } + "\"") }
            }
        }
        if (lines.size < RECENT_ITEMS_MAX) {
            val fallback = buildList {
                notes.sortedByDescending { it.updatedAt }.forEach { add("note \"" + it.title.ifBlank { "Untitled" } + "\"") }
                tasks.sortedByDescending { it.createdAt }.forEach { add("task \"" + it.title.ifBlank { "Untitled" } + "\"") }
            }
            fallback.forEach { if (lines.size < RECENT_ITEMS_MAX) lines.add(it) }
        }
        return lines.joinToString("\n") { "- " + it }
    }

    private fun signatureOf(name: String, argsJson: String): String {
        val norm = try {
            val o = org.json.JSONObject(argsJson)
            o.keys().asSequence().sorted().joinToString(";") { k -> "$k=${o.opt(k)}" }
        } catch (e: Exception) {
            argsJson.trim()
        }
        return "$name|$norm"
    }

    private suspend fun confirmToolCall(turn: Turn, name: String, argsJson: String): ConfirmedCall {
        confirmationMutex.withLock {
            val deferred = CompletableDeferred<ConfirmationOutcome>()
            turn.confirmationDeferred = deferred
            confirmingTurn = turn
            turn.thinking = false
            val editable = AppTools.editableArguments(name, argsJson)
            pendingConfirmation = PendingConfirmation(
                actionTitle = confirmTitleFor(name),
                details = AppTools.describeToolCall(name, argsJson),
                toolName = name,
                edits = editable,
                editorKind = editorKindFor(name)
            )
            return try {
                val outcome = deferred.await()
                val changed = outcome.edits
                    .mapValues { (_, v) -> v.trim() }
                    .filter { (k, v) ->
                        v.isNotBlank() && editable.firstOrNull { it.key == k }?.value != v
                    }
                val finalArgs =
                    if ((outcome.approved || outcome.refine) && changed.isNotEmpty()) {
                        AppTools.withArguments(argsJson, changed)
                    } else argsJson
                ConfirmedCall(outcome.approved, finalArgs, outcome.refine)
            } finally {
                if (confirmingTurn === turn) {
                    confirmingTurn = null
                    pendingConfirmation = null
                }
                turn.confirmationDeferred = null
            }
        }
    }

    private fun editorKindFor(name: String): EditorKind? = when (name) {
        "create_note", "update_note" -> EditorKind.NOTE
        "create_task", "update_task" -> EditorKind.TASK
        else -> null
    }


    private fun confirmTitleFor(name: String): String = when {
        name.startsWith("delete_") -> com.lucent.app.i18n.S.confirmMoveTrash
        name.startsWith("create_") -> com.lucent.app.i18n.S.confirmCreate
        name.startsWith("complete_") -> com.lucent.app.i18n.S.confirmMarkDone
        name.startsWith("restore_") -> com.lucent.app.i18n.S.confirmRestore
        name.contains("remove") -> com.lucent.app.i18n.S.confirmRemove
        else -> com.lucent.app.i18n.S.confirmGeneric
    }

    private fun startGenerationService(assistantName: String) {
        val ctx = appContextRef ?: return
        try {
            GenerationService.start(ctx, assistantName)
        } catch (_: Throwable) {
        }
    }

    private fun stopGenerationService() {
        val ctx = appContextRef ?: return
        try {
            GenerationService.stop(ctx)
        } catch (_: Throwable) {
        }
    }

    private suspend fun insertAssistant(
        db: AppDatabase,
        conversationId: Long,
        content: String,
        mime: String?,
        data: String?,
        tokens: Int = 0,
        replyToId: Long = 0,
        traceJson: String? = null,
        thinkingBlocks: String = "",
        reasoningText: String = ""
    ) {
        val hasImage = !data.isNullOrBlank()
        db.chatDao().insert(
            ChatMessage(
                role = "assistant",
                content = content,
                attachmentMime = if (hasImage) mime else null,
                attachmentData = data?.takeIf { it.isNotBlank() },
                attachmentName = if (hasImage) ReplyPolish.imageFileName(mime) else null,
                conversationId = conversationId,
                tokens = tokens,
                replyToId = replyToId,
                agentTrace = traceJson,
                reasoningBlocks = thinkingBlocks.takeIf { it.isNotBlank() },
                reasoningText = reasoningText.takeIf { it.isNotBlank() }
            )
        )
    }

    private fun reportCacheUse(turn: Turn, usage: com.lucent.app.network.TokenUsage) {
        if (!usage.known) return
        appContextRef?.let { ctx ->
            com.lucent.app.data.StartupLog.event(
                ctx,
                "prompt cache: ${usage.cachedTokens} of ${usage.promptTokens} input tokens reused " +
                    "(${usage.hitPercent}%), ${usage.outputTokens} generated"
            )
        }
        if (usage.cachedTokens > 0) {
            val tokens = java.text.NumberFormat.getIntegerInstance().format(usage.cachedTokens.toLong())
            turn.recorder.addNote(com.lucent.app.i18n.S.agentTraceCacheUsed(tokens, usage.hitPercent))
        }
    }



    private fun fail(turn: Turn, t: Throwable) {
        if (t is java.io.IOException) {
            lastSend = turn.params
            lastSendConversationId = turn.conversationId
            networkErrorMessage =
                com.lucent.app.i18n.S.networkCantReach +
                    (t.message?.takeIf { it.isNotBlank() && it != "network error" }?.let { "\n\n($it)" } ?: "")
            recordTraceFailure(turn, t.message ?: com.lucent.app.i18n.S.noDetails)
        } else {
            postError(turn, "${t.javaClass.simpleName}: ${t.message ?: com.lucent.app.i18n.S.noDetails}")
        }
    }

    private fun postError(turn: Turn, text: String) {
        recordTraceFailure(turn, text)
        errorText = text
        errorConversationId = turn.conversationId
    }

    private fun recordTraceFailure(turn: Turn, detail: String) {
        turn.recorder.addNote(
            com.lucent.app.i18n.S.agentTraceError,
            AgentTraceLabels.summarize(detail, 240),
            AgentStepStatus.FAILED
        )
        turn.recorder.finish(AgentStepStatus.FAILED)
        errorTrace = turn.recorder.snapshot()
        errorTraceConversationId = turn.conversationId
    }

    private fun toolFailureDetail(t: Throwable): String =
        "${t.javaClass.simpleName}: ${t.message ?: com.lucent.app.i18n.S.noDetails}"

    private fun logToolFailure(name: String, t: Throwable?) {
        val ctx = appContextRef ?: return
        val detail = t?.let { toolFailureDetail(it) } ?: "reported failure"
        com.lucent.app.data.StartupLog.event(ctx, "assistant tool $name failed - $detail")
    }

    private fun stepFor(backlog: Int): Int = when {
        backlog > 400 -> 8
        backlog > 200 -> 4
        backlog > 80 -> 2
        else -> 1
    }

    private val SMALL_MODEL_CROSS_BUDGET = 10


    private val _state = MutableStateFlow(AssistantUiState())

    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    private fun publishState() {
        _state.value = AssistantUiState(
            sending = sending,
            errorText = errorText,
            errorConversationId = errorConversationId,
            errorTrace = errorTrace,
            networkErrorMessage = networkErrorMessage,
            pendingConfirmation = pendingConfirmation,
            messages = messages,
            currentConversationId = currentConversationId,
            conversations = conversations,
            localTurnInFlight = localTurnInFlight,
            variantSelection = variantSelection.toMap()
        )
    }

    private inner class SyncedState<T>(initial: T) {
        private val backing = mutableStateOf(initial)
        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = backing.value
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
            backing.value = value
            publishState()
        }
    }

    private inner class SubAgentBridge(
        private val url: String,
        private val spec: ApiSpec,
        private val key: String,
        private val defaultModel: String
    ) : com.lucent.app.harness.SubAgentLlm {

        override suspend fun step(
            systemPrompt: String,
            userPrompt: String,
            transcript: List<String>,
            allowedTools: Set<String>,
            model: String
        ): com.lucent.app.harness.SubAgentStep {
            val history = mutableListOf(ChatTurn(role = "user", content = userPrompt))
            transcript.takeLast(24).forEach { line ->
                history.add(ChatTurn(role = "user", content = "Progress: " + line.take(2000)))
            }
            val definitions = AppTools.definitions(includeWebSearch = true).filter { definition ->
                definition.name != "spawn_agent" &&
                    (allowedTools.isEmpty() || allowedTools.contains(definition.name))
            }
            val chosen = model.ifBlank { defaultModel }
            val reply = llmClient.streamChat(
                url, spec, key, chosen, history, systemPrompt, definitions,
                { delta -> com.lucent.app.harness.HarnessRuntime.note("sub-agent: " + delta.take(200)) },
                { },
                { },
                reasoning = com.lucent.app.data.ReasoningEffort.DEFAULT.key,
                cacheKey = "lucent-subagent",
                context = ""
            )
            val value = reply.getOrElse { error ->
                return com.lucent.app.harness.SubAgentStep("", emptyList(), true, error.message ?: "request failed")
            }
            return com.lucent.app.harness.SubAgentStep(
                text = value.text.orEmpty(),
                toolCalls = value.toolCalls.map {
                    com.lucent.app.harness.SubAgentCall(it.name, it.argumentsJson)
                },
                done = value.toolCalls.isEmpty()
            )
        }
    }
}

object AssistantController {

    @Volatile private var backing: AssistantControllerImpl? = null

    private fun impl(appContext: Context): AssistantControllerImpl {
        backing?.let { return it }
        synchronized(this) {
            backing?.let { return it }
            val created = AssistantControllerImpl(
                appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                db = AppDatabase.getInstance(appContext.applicationContext),
                llmClient = RealAssistantLlmClient,
                context = appContext.applicationContext
            )
            backing = created
            return created
        }
    }

    private val fallbackState = MutableStateFlow(AssistantUiState()).asStateFlow()
    private val fallbackVariantSelection = androidx.compose.runtime.mutableStateMapOf<Long, Int>()


    val sending: Boolean get() = backing?.sending ?: false
    fun isGenerating(conversationId: Long?): Boolean = backing?.isGenerating(conversationId) ?: false
    fun streamingTextFor(conversationId: Long?): String? = backing?.streamingTextFor(conversationId)
    fun thinkingFor(conversationId: Long?): Boolean = backing?.thinkingFor(conversationId) ?: false
    fun loadingModelFor(conversationId: Long?): Boolean = backing?.loadingModelFor(conversationId) ?: false
    fun agentTraceFor(conversationId: Long?): AgentTrace? = backing?.agentTraceFor(conversationId)
    val localTurnInFlight: Boolean get() = backing?.localTurnInFlight ?: false


    val state: StateFlow<AssistantUiState> get() = backing?.state ?: fallbackState


    val errorText: String get() = backing?.errorText ?: ""
    val errorConversationId: Long? get() = backing?.errorConversationId
    val errorTrace: AgentTrace? get() = backing?.errorTrace
    val errorTraceConversationId: Long? get() = backing?.errorTraceConversationId
    val networkErrorMessage: String? get() = backing?.networkErrorMessage
    var pendingConfirmation: PendingConfirmation?
        get() = backing?.pendingConfirmation
        set(value) { backing?.pendingConfirmation = value }
    val messages: List<ChatMessage> get() = backing?.messages ?: emptyList()
    val currentConversationId: Long? get() = backing?.currentConversationId
    val conversations: List<ChatConversation> get() = backing?.conversations ?: emptyList()
    val variantSelection get() = backing?.variantSelection ?: fallbackVariantSelection


    fun ensureMessagesLoaded(appContext: Context) = impl(appContext).ensureMessagesLoaded(appContext)
    fun onAllChatsCleared(appContext: Context) = impl(appContext).onAllChatsCleared(appContext)
    fun startNewConversation(appContext: Context) = impl(appContext).startNewConversation(appContext)
    fun switchConversation(appContext: Context, id: Long) = impl(appContext).switchConversation(appContext, id)
    fun deleteConversation(appContext: Context, id: Long) = impl(appContext).deleteConversation(appContext, id)
    fun renameConversation(appContext: Context, id: Long, newTitle: String) =
        impl(appContext).renameConversation(appContext, id, newTitle)
    fun deleteMessages(appContext: Context, ids: Set<Long>) = impl(appContext).deleteMessages(appContext, ids)

    fun resend(
        appContext: Context,
        message: ChatMessage,
        url: String,
        spec: ApiSpec,
        key: String,
        model: String,
        name: String,
        style: String,
        memoryTier: MemoryTier,
        webSearchEnabled: Boolean,
        typingHapticsEnabled: Boolean,
        useLocalModel: Boolean,
        useLocalTools: Boolean,
        useLocalGpu: Boolean,
        confirmTools: Boolean,
        smallModelMode: Boolean,
        agentMode: Boolean = true,
        localWebSearch: Boolean = false,
        reasoning: String = com.lucent.app.data.ReasoningEffort.DEFAULT.key
    ) = impl(appContext).resend(
        appContext, message, url, spec, key, model, name, style, memoryTier, webSearchEnabled,
        typingHapticsEnabled, useLocalModel, useLocalTools, useLocalGpu, confirmTools, smallModelMode,
        agentMode, localWebSearch, reasoning
    )

    fun send(
        appContext: Context,
        text: String,
        attachmentMime: String?,
        attachmentData: String?,
        attachmentName: String?,
        url: String,
        spec: ApiSpec,
        key: String,
        model: String,
        name: String,
        style: String,
        memoryTier: MemoryTier,
        webSearchEnabled: Boolean,
        typingHapticsEnabled: Boolean = true,
        insertUserMessage: Boolean = true,
        useLocalModel: Boolean = false,
        useLocalTools: Boolean = false,
        useLocalGpu: Boolean = false,
        confirmTools: Boolean = true,
        smallModelMode: Boolean = false,
        agentMode: Boolean = true,
        localWebSearch: Boolean = false,
        reasoning: String = com.lucent.app.data.ReasoningEffort.DEFAULT.key,
        answersMessageId: Long = 0,
        targetConversationId: Long? = null,
        attachmentListJson: String? = null,
        attachments: List<com.lucent.app.data.Attachment> = emptyList()
    ) = impl(appContext).send(
        appContext = appContext, text = text, attachmentMime = attachmentMime,
        attachmentData = attachmentData, attachmentName = attachmentName, url = url, spec = spec,
        key = key, model = model, name = name, style = style, memoryTier = memoryTier,
        webSearchEnabled = webSearchEnabled, typingHapticsEnabled = typingHapticsEnabled,
        insertUserMessage = insertUserMessage, useLocalModel = useLocalModel,
        useLocalTools = useLocalTools, useLocalGpu = useLocalGpu, confirmTools = confirmTools,
        smallModelMode = smallModelMode, agentMode = agentMode, localWebSearch = localWebSearch,
        reasoning = reasoning, answersMessageId = answersMessageId,
        targetConversationId = targetConversationId, attachmentListJson = attachmentListJson,
        attachments = attachments
    )


    fun clearError() { backing?.clearError() }
    fun clearNetworkError() { backing?.clearNetworkError() }

    fun resolveConfirmation(approved: Boolean, edits: Map<String, String> = emptyMap(), refine: Boolean = false) {
        backing?.resolveConfirmation(approved, edits, refine)
    }

    fun stopGeneration(reason: String? = null, silent: Boolean = false) {
        backing?.stopGeneration(reason, silent)
    }

    fun stopAllGeneration(reason: String? = null, silent: Boolean = false) {
        backing?.stopAllGeneration(reason, silent)
    }

    fun onAppBackgrounded(backgroundRepliesEnabled: Boolean) {
        backing?.onAppBackgrounded(backgroundRepliesEnabled)
    }

    fun retryLast() { backing?.retryLast() }

    fun selectVariant(replyToId: Long, index: Int) { backing?.selectVariant(replyToId, index) }
}
