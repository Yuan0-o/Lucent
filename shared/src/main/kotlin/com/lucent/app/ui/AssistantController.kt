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
import com.lucent.app.tools.AppTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the assistant's send/stream lifecycle *outside* of any composable, so a reply keeps
 * generating and saving even when the user leaves the Assistant tab.
 *
 * ### Multi-round tool loop
 * A turn is no longer "one tool round then one final reply". The model can now call tools
 * several times in a row before it answers, up to [MAX_TOOL_ROUNDS]. That is what makes actions
 * like "delete the pdf from my Homework note" reliable: the model can read the note to learn the
 * exact file name, then delete it with that real name, all in one user turn. Each round is
 * buffered silently (reveal = false) so a pre-tool preamble never flashes on screen; only the
 * final written reply is revealed by the typewriter.
 *
 * ### Honesty
 * Tool results carry a success flag. If the model produces no text of its own, the reply falls
 * back to a clean confirmation on success, or to the tool's honest failure message on failure —
 * never to the raw bracketed tool summary.
 */
object AssistantController {

    // Flow observation (message/conversation streams) stays on the main dispatcher.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Generation runs on its own process-lifetime scope on a background dispatcher, deliberately NOT
    // tied to any composable or the Activity. Leaving the Assistant tab, or the app going to the
    // background, disposes the screen but not this scope, so a reply keeps generating and saving to
    // the database (issue 17). A foreground service (see GenerationService) additionally keeps the
    // process alive while a reply is in flight so the OS is far less likely to kill it.
    private val genScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Typewriter cadence (issue 11). One code point is revealed every [REVEAL_STEP_MS], which reads
    // as steady "typing" at a language-independent pace — CJK and Latin advance at the same rhythm
    // because the unit is a glyph, not a word. For long replies the whole text is already buffered,
    // so [stepFor] widens the stride to clear a big backlog smoothly instead of crawling, keeping the
    // *visible speed* consistent whether the reply is one line or twenty.
    private const val REVEAL_STEP_MS = 22L
    private const val MAX_TOOL_ROUNDS = 6

    // ---- Observable UI state (read from AssistantScreen composition) ----
    //
    // Live turn state (sending / thinking / streaming / loading) is PER TURN now — several
    // conversations can generate at once — so the screen reads it through the *For(conversationId)
    // helpers below, which look the turn up in the registry. The lookup by id is itself the
    // isolation: a view can only ever see the turn that belongs to the conversation it shows.

    /** True while ANY reply is generating, in any conversation. The lifecycle and exit paths read
     *  this; the per-conversation state a screen shows comes from the *For helpers instead. */
    val sending: Boolean get() = turns.isNotEmpty()

    /** Whether a reply is generating in [conversationId] — drives that conversation's Stop/Send button. */
    fun isGenerating(conversationId: Long?): Boolean = turnFor(conversationId) != null

    /** The live typewriter text of [conversationId]'s in-flight reply, if any. */
    fun streamingTextFor(conversationId: Long?): String? = turnFor(conversationId)?.streamingText

    /** Whether [conversationId]'s in-flight reply is still thinking (no text revealed yet). */
    fun thinkingFor(conversationId: Long?): Boolean = turnFor(conversationId)?.thinking == true

    /** Whether [conversationId]'s in-flight LOCAL reply is waiting on the model load. */
    fun loadingModelFor(conversationId: Long?): Boolean = turnFor(conversationId)?.loadingModel == true

    var errorText by mutableStateOf("")
        private set
    // The conversation the inline error banner belongs to. With several turns possible, a
    // background turn's failure must show up in ITS conversation, not under whichever chat the
    // user happens to be reading; the screen compares this id before rendering [errorText].
    var errorConversationId by mutableStateOf<Long?>(null)
        private set
    // A genuine connectivity failure (not an HTTP/status error), surfaced as a modal rather than an
    // inline banner (issue 19). Null when there's nothing to show.
    var networkErrorMessage by mutableStateOf<String?>(null)
        private set
    // A tool call awaiting the user's explicit yes/no (issue 13). Non-null means the confirm modal
    // is up and the generation coroutine is parked on the user's decision.
    var pendingConfirmation by mutableStateOf<PendingConfirmation?>(null)
    // v2.7.4: a proposal the user parked via "keep refining with the assistant". The proposal is
    // remembered (and injected into the next turn's system prompt) so that after the user answers
    // "what should change" the model re-proposes the action with their adjustments instead of
    // dropping it - and never reports "added" for something that wasn't.
    private var refinementContext: String? = null
        private set

    /**
     * A pending function-call confirmation: a short header, a one-line summary of what will happen,
     * and — when the call has one — the single argument the user may correct before approving.
     *
     * [editKey] null means the action is a plain yes/no (a delete, a pin, a completion). When it is
     * non-null the modal shows a text field pre-filled with [editValue]; approving with a changed
     * value rewrites that argument and runs the corrected call. See [AppTools.editableArgument].
     */
    data class PendingConfirmation(
        val actionTitle: String,
        val details: String,
        val toolName: String,
        // Every argument worth reviewing before the call runs, in display order (B-group task 3).
        // Empty for a plain yes/no action (a delete, a pin, a completion), which shows no form.
        val edits: List<AppTools.EditableArgument> = emptyList(),
        // Non-null when the call would create or edit a note/task the app can open. It no longer
        // drives an "approve and then fine-tune" button — the fine-tuning now happens BEFORE
        // anything is written — but it still tells the dialog it is looking at a whole item rather
        // than a single field, which is what earns the fuller layout.
        val editorKind: EditorKind? = null
    )

    /** The kind of item an approved call would create or edit, for the dialog's editor entry. */
    enum class EditorKind { NOTE, TASK }

    /** The user's answer to a confirmation, plus any edits they made to the proposed arguments. */
    private data class ConfirmationOutcome(
        val approved: Boolean,
        val edits: Map<String, String> = emptyMap(),
        // "Keep talking about it" (B-group task 3). Not an approval and not a plain refusal: the
        // action does not run, but the conversation continues from the proposal rather than ending
        // on it — the assistant is told what was proposed and asked what should change.
        val refine: Boolean = false
    )

    /** A confirmation that has been answered: the decision, and the arguments to actually run. */
    private data class ConfirmedCall(
        val approved: Boolean,
        val argumentsJson: String,
        val refine: Boolean = false
    )

    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    private var messagesJob: Job? = null

    // ---- The turn registry ----
    //
    // Every in-flight reply is one [Turn] here; each owns its coroutine, its typewriter, and its
    // bookkeeping outright, which is what makes several conversations generating at once safe —
    // turns cannot see, let alone clobber, each other's state. A snapshot-state list so
    // composition observes membership; compound mutations go through [turnsGuard] so the
    // "first turn in / last turn out" foreground-service decisions are exact.
    private val turns = mutableStateListOf<Turn>()
    private val turnsGuard = Any()

    private fun turnFor(conversationId: Long?): Turn? =
        turns.firstOrNull { it.conversationId == conversationId }

    private fun localTurnOrNull(): Turn? = turns.firstOrNull { it.isLocal }

    private fun registerTurn(turn: Turn, assistantName: String) {
        synchronized(turnsGuard) {
            val first = turns.isEmpty()
            turns.add(turn)
            // The keep-alive service spans ALL turns: up with the first, down with the last.
            if (first) startGenerationService(assistantName)
        }
    }

    private fun unregisterTurn(turn: Turn) {
        synchronized(turnsGuard) {
            turns.remove(turn)
            if (turns.isEmpty()) stopGenerationService()
        }
    }

    // Held so background work (haptics, the foreground service) has an application context even when
    // no composable is currently alive.
    private var appContextRef: Context? = null

    // Several turns can want a confirmation at once now; the modal is one. Turns take this mutex
    // for the whole ask-and-wait, so questions are posed one at a time in arrival order, and
    // [confirmingTurn] records whose question is on screen so an answer (or a stop) can never be
    // delivered to the wrong turn.
    private val confirmationMutex = Mutex()
    @Volatile private var confirmingTurn: Turn? = null

    // The conversation currently shown/active. Null until resolved on first load (we pick the
    // most-recent conversation, or lazily create one when the first message is sent). Everything
    // the assistant screen renders and everything send() writes is scoped to this id.
    var currentConversationId by mutableStateOf<Long?>(null)
        private set


    // The list of all conversations, for the switcher UI. Most-recent first.
    var conversations by mutableStateOf<List<ChatConversation>>(emptyList())
        private set
    private var conversationsJob: Job? = null

    fun ensureMessagesLoaded(appContext: Context) {
        appContextRef = appContext.applicationContext
        val db = AppDatabase.getInstance(appContext.applicationContext)
        if (conversationsJob == null) {
            conversationsJob = scope.launch {
                db.chatConversationDao().getAll().collect { conversations = it }
            }
        }
        if (messagesJob != null) return
        messagesJob = scope.launch {
            // One-time cleanup: earlier builds could create empty "New conversation" rows that
            // never received a message, so a user might already have several piled up (issue 12).
            // Delete any conversation that has no messages so the switcher is clean. New empty
            // conversations are no longer created (see startNewConversation), so this only ever
            // removes leftovers.
            db.chatConversationDao().getAllOnce().forEach { conv ->
                if (db.chatDao().countInConversation(conv.id) == 0) {
                    db.chatConversationDao().delete(conv)
                }
            }
            // Every launch opens a **fresh** conversation, not the last one you were in.
            //
            // This used to restore the most recent conversation, on the reasonable-sounding theory
            // that you would want to carry on where you left off. In practice a chat assistant is
            // not a document: you come back to it with a new question, and being dropped into the
            // tail of yesterday's thread means the model's context is full of something unrelated —
            // and the first thing you have to do on opening the app is tap "+" to get rid of it.
            //
            // So the process simply starts pointing at "no conversation" (id null), which shows the
            // greeting and an empty thread. Nothing is lost and nothing is created: every previous
            // conversation is still in the switcher one tap away, and no row is written until the
            // first message is actually sent (see startNewConversation / send), so relaunching the
            // app a hundred times without typing leaves no trace at all.
            //
            // Note this runs once per *process* — messagesJob guards it — so it is a cold-start
            // behaviour. Switching tabs, rotating, or returning from the background all keep
            // whatever conversation you are in.
            observeCurrentConversation(db)
        }
    }

    /**
     * Reset in-memory conversation state after every chat + conversation row has been deleted
     * elsewhere (the "Clear all assistant chat history" button in Settings). Without this the
     * screen would keep observing a conversation id that no longer exists.
     */
    fun onAllChatsCleared(appContext: Context) {
        // Every row has just been deleted, so any in-flight reply — in ANY conversation — would
        // insert itself into a table that was emptied a moment ago: a message reappearing in a
        // history the user just cleared. Stop them all silently and land on the fresh greeting
        // (task 4).
        stopAllGeneration(silent = true)
        val db = AppDatabase.getInstance(appContext.applicationContext)
        currentConversationId = null
        // Every conversation an error could have belonged to is gone; clear unconditionally.
        clearError()
        // Same for the retry machinery: a network-error modal still on screen describes a
        // conversation that no longer exists, and its Retry must not be able to re-send wiped
        // data into the fresh app.
        networkErrorMessage = null
        lastSend = null
        lastSendConversationId = null
        observeCurrentConversation(db)
    }

    // (Re)point the message stream at [currentConversationId]. A conversation id of null means
    // "no conversation yet" → an empty message list, which shows the greeting.
    //
    // To avoid a flicker of the *previous* conversation's messages during a switch (issue 15), we
    // load the target conversation's current messages with a fast one-shot query first and set
    // them atomically, then attach the live Flow for subsequent updates. The one-shot result is
    // only applied if this is still the active observe request (guarded by capturing the job).
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
            // Snapshot first for an immediate, correct swap.
            val snapshot = db.chatDao().getForConversationOnce(id)
            if (currentConversationId == id) messages = snapshot
            // Then follow live changes.
            db.chatDao().getForConversation(id).collect {
                if (currentConversationId == id) messages = it
            }
        }
        observeJob = job
    }

    /**
     * Start a brand-new conversation, preserving all existing ones.
     *
     * Crucially this does NOT insert a conversation row — it just clears the view to the greeting
     * by pointing at "no conversation" (id null). The row is created lazily by [send] when the
     * first message is actually sent. That's what prevents a pile of empty "New conversation"
     * entries from accumulating when the user taps + repeatedly or opens a new chat without typing
     * (issue 12). A LOCAL reply in flight is stopped first; a CLOUD reply keeps generating in the
     * background and lands in its own conversation when it finishes.
     */
    fun startNewConversation(appContext: Context) {
        // A LOCAL reply still in flight is interrupted rather than blocking the new chat (task 4):
        // the old `if (sending) return` made the + button silently do nothing while the assistant
        // was busy, and a multi-gigabyte model decoding for a chat the user is leaving is the most
        // expensive thing this app can do to a device. CLOUD replies are different: they cost
        // nothing locally while they wait on the network and the requests are already paid for, so
        // every one of them keeps generating in the background and lands in its own conversation
        // when it finishes — each turn owns its state outright and the screen looks turns up by
        // conversation id, so nothing can leak into the fresh chat.
        localTurnOrNull()?.let { stopTurn(it, silent = true) }
        val db = AppDatabase.getInstance(appContext.applicationContext)
        currentConversationId = null
        // Errors are tagged with the conversation they belong to and shown only there, so an error
        // from some other chat's turn must SURVIVE this navigation for the user to find. The one
        // error a fresh chat can own is one tagged null (a turn that failed before its
        // conversation row existed) — that is the only one starting another fresh chat clears.
        if (errorConversationId == null) clearError()
        observeCurrentConversation(db)
    }

    /**
     * Switch the visible conversation to [id].
     *
     * A LOCAL reply in flight is stopped first (the old `if (sending) return` silently ignored the
     * tap, which read as the switcher being broken): its partial text plus the "Reply stopped."
     * marker are written into the conversation it belongs to, so nothing is lost and nothing leaks
     * into the one being opened. CLOUD replies are left generating in the background instead —
     * each lands in its own conversation when it finishes, and switching into a conversation whose
     * reply is still in flight simply shows it generating.
     */
    fun switchConversation(appContext: Context, id: Long) {
        // A LOCAL reply only ever generates in the conversation on screen, so re-selecting that
        // same conversation is not a departure and must not stop it. Anywhere else is.
        val localTurn = localTurnOrNull()
        if (localTurn != null) {
            if (localTurn.conversationId == id) return
            stopTurn(localTurn)
        }
        val db = AppDatabase.getInstance(appContext.applicationContext)
        currentConversationId = id
        // Deliberately NOT clearing errorText here (the old single-turn code did): errors are
        // tagged with the conversation they belong to and rendered only there, so clearing on
        // every switch would wipe a background turn's failure before the user ever switched back
        // to see it. The banner clears when its conversation is sent in again, when the screen is
        // left, or when its conversation is deleted.
        observeCurrentConversation(db)
    }

    /**
     * Delete one conversation (its messages and the row).
     *
     * If it was the active one, land on a **brand-new, empty chat** rather than the previous
     * conversation (task 4). Pointing [currentConversationId] at null clears the view to the
     * greeting — the same fresh-start state the "+" button produces — and, as with
     * [startNewConversation], no empty row is inserted here; the next send() creates one lazily.
     * The earlier behaviour of silently reopening the most-recent remaining chat was surprising:
     * deleting the chat you were reading dropped you into an unrelated old one.
     */
    fun deleteConversation(appContext: Context, id: Long) {
        // Interrupt only the reply generating INTO the conversation being deleted (task 4) — even
        // one running in the background for it — because its target is about to vanish. Silent,
        // since the row a marker would land in is deleted a moment later. Replies generating into
        // OTHER conversations, local or cloud, are left alone: deleting an unrelated chat is no
        // reason to lose an answer.
        turnFor(id)?.let { stopTurn(it, silent = true) }
        val db = AppDatabase.getInstance(appContext.applicationContext)
        // An error belonging to the deleted chat disappears together with it (issue 14); one
        // belonging to some other conversation survives, to be seen there.
        if (errorConversationId == id) clearError()
        scope.launch {
            db.chatDao().clearConversation(id)
            db.chatConversationDao().getById(id)?.let { db.chatConversationDao().delete(it) }
            if (currentConversationId == id) {
                currentConversationId = null
                observeCurrentConversation(db)
            }
        }
    }

    /**
     * Rename a conversation to a user-chosen title (issue 3). A blank title falls back to the
     * conversation's existing name so a conversation is never left label-less. Bumping updatedAt
     * is intentionally avoided so renaming doesn't reshuffle the list order.
     */
    fun renameConversation(appContext: Context, id: Long, newTitle: String) {
        val db = AppDatabase.getInstance(appContext.applicationContext)
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
    }

    /** Dismiss the network-error modal (issue 19). */
    fun clearNetworkError() { networkErrorMessage = null }

    /**
     * The confirm modal's answer (issue 13). Feeds the user's choice back into the parked generation
     * coroutine, which then either runs the tool (approved) or reports the refusal to the model
     * (denied) so it knows the action did not happen.
     */
    /**
     * Delete the given messages from the conversation on screen (B-group task 11).
     *
     * Chat messages have no Trash — unlike notes and tasks, nothing here is recoverable — so the
     * caller is responsible for confirming first, and the UI does. Runs off the main thread; the
     * observed Flow pushes the new list back, so no local cache has to be patched by hand.
     */
    fun deleteMessages(appContext: Context, ids: Set<Long>) {
        if (ids.isEmpty()) return
        val db = AppDatabase.getInstance(appContext.applicationContext)
        AppScope.io.launch {
            try {
                db.chatDao().deleteByIds(ids.toList())
            } catch (t: Throwable) {
                // A failed delete leaves the messages in place, which is the safe direction; the
                // list simply does not change and the user can try again.
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
        // INTEGRATION (B task 3 x A task 10): drop the draft-area mirror on EVERY outcome —
        // approve, cancel and refine alike. See AssistantDraftBridge for why cancel is included:
        // the point of the edit-first flow is that declining leaves nothing behind, and a draft
        // that survives a cancel puts that back.
        appContextRef?.let { ctx ->
            scope.launch { com.lucent.app.data.AssistantDraftBridge.clear(ctx) }
        }
        turn?.confirmationDeferred?.complete(ConfirmationOutcome(approved, edits, refine))
        turn?.confirmationDeferred = null
    }

    /**
     * Interrupt the reply generating in the conversation currently ON SCREEN — the Stop button's
     * semantics now that several conversations can generate at once. [stopAllGeneration] is the
     * exit path's stop-everything variant; [stopTurn] is the shared core.
     */
    fun stopGeneration(reason: String? = null, silent: Boolean = false) {
        turnFor(currentConversationId)?.let { stopTurn(it, reason, silent) }
    }

    /**
     * Stop every in-flight reply, each persisting its partial into its OWN conversation. Used when
     * the whole app is going away (the exit warning's "stop and exit"), where leaving background
     * turns running would just lose their partials to process death, unmarked.
     */
    fun stopAllGeneration(reason: String? = null, silent: Boolean = false) {
        turns.toList().forEach { stopTurn(it, reason, silent) }
    }

    /**
     * Interrupt one in-progress reply. Cancels its coroutine, unblocks any confirmation it was
     * waiting on, and — so its thread isn't left with a dangling user message — saves whatever text
     * it had produced so far into ITS conversation, unless the normal path already saved a reply.
     *
     * Called from the Stop button (via [stopGeneration]), the "stop and exit" choice in the exit
     * warning (via [stopAllGeneration]), the app going to the background with background replies
     * switched off, and the conversation-lifecycle paths (new / switch / delete / clear-all).
     * [reason] is the line appended to the saved turn so the conversation says what happened; when
     * null it falls back to the plain "Reply stopped." marker.
     */
    private fun stopTurn(turn: Turn, reason: String? = null, silent: Boolean = false) {
        // Unblock a parked confirmation first so the coroutine can unwind cleanly; if this turn's
        // question is the one on screen, take the modal down with it. (A turn still queued on the
        // confirmation mutex is unblocked by the job cancellation below instead.)
        turn.confirmationDeferred?.complete(ConfirmationOutcome(approved = false))
        turn.confirmationDeferred = null
        if (confirmingTurn === turn) {
            confirmingTurn = null
            pendingConfirmation = null
        }

        // If the turn is running on the on-device model, flip its stop flag too: the native decode
        // loop doesn't hit coroutine suspension points, so cancelling the Job alone would let it
        // keep spending CPU until the token cap. This makes it return within one token (task 1's
        // "must not lag" also applies to stopping). Only a LOCAL turn ever needs this — and only
        // one local turn can exist at a time, so the flag cannot hit a bystander.
        if (turn.isLocal) com.lucent.app.local.LocalLlm.stop()

        val convId = turn.conversationId
        val ctx = appContextRef
        val cleaned = deRobotify(turn.snapshotBuffer()).trim()

        turn.job?.cancel()
        turn.job = null
        turn.finishStream()
        turn.thinking = false
        turn.loadingModel = false
        unregisterTurn(turn)

        // Write the stop into the conversation, always — including when not one token had been
        // produced yet (task 2).
        //
        // The old version only saved something if `cleaned` was non-blank, and marked it with a
        // trailing "…". Both were too quiet. Stopping during the model-loading pause left the thread
        // holding a user message with no reply under it and a thinking bubble that had just vanished,
        // which reads as the app losing the message rather than obeying the Stop button. And an
        // ellipsis is not a status: it is indistinguishable from a reply that merely trailed off.
        //
        // So there is now always a turn, and it always says so in words. `reason` carries the
        // specific cause when there is one worth naming — being backgrounded, in particular, needs
        // to point at the setting that would have prevented it.
        // A SILENT stop leaves nothing behind (task 4). The callers that ask for one are all about
        // to make the turn's conversation itself disappear — starting a new chat, deleting that
        // conversation, wiping every chat — so a "Reply stopped." marker would be written into a
        // row that is deleted (or abandoned) a moment later. Nothing to explain, so nothing is
        // said.
        if (silent) {
            turn.turnPersisted = true
            return
        }
        val marker = reason ?: com.lucent.app.i18n.S.replyStopped
        if (!turn.turnPersisted && convId != null && ctx != null) {
            turn.turnPersisted = true
            val db = AppDatabase.getInstance(ctx)
            val body = if (cleaned.isBlank()) marker else "$cleaned\n\n$marker"
            val tokens = TokenEstimator.estimate(body)
            AppScope.io.launch { insertAssistant(db, convId, body, null, null, tokens) }
        }
    }

    /**
     * The app left the foreground (task 2).
     *
     * Default behaviour is to stop an in-flight LOCAL reply and free the model, because holding a
     * multi-gigabyte model resident for a screen nobody is looking at is the single most expensive
     * thing this app can do to a phone. [backgroundRepliesEnabled] is the user's opt-out — when they
     * have turned background replies on, the reply is left alone to finish under the foreground
     * service.
     *
     * Cloud replies are never touched: they cost nothing locally while they wait on the network, and
     * cutting one off would waste a request the user has already paid for.
     */
    fun onAppBackgrounded(backgroundRepliesEnabled: Boolean) {
        if (backgroundRepliesEnabled) return
        // Only a LOCAL turn is stopped by leaving the app; cloud turns — foreground or background
        // — are never touched, exactly as before.
        localTurnOrNull()?.let { stopTurn(it, reason = com.lucent.app.i18n.S.replyStoppedBackground) }
    }

    /**
     * Whether the reply currently being generated is running on the on-device model. Read by the
     * lifecycle and exit paths, which have to treat local and cloud replies differently: only the
     * local one is holding gigabytes of RAM and only it is stopped by leaving the app.
     */
    val localTurnInFlight: Boolean get() = turns.any { it.isLocal }

    // The full parameter set of the most recent send(), kept so the network-error modal can offer
    // a one-tap Retry (ported from the second assistant variant). By the time a connection failure
    // surfaces, the user's message is already persisted — so the retry re-runs generation for that
    // same message without inserting a duplicate row (insertUserMessage = false below).
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
        val answersMessageId: Long = 0
    )
    private var lastSend: LastSend? = null
    // The conversation the failed turn belonged to, captured together with [lastSend] at failure
    // time (see fail), so Retry re-runs into that conversation even if the user has since moved
    // elsewhere. With several turns possible, "the most recent send" and "the send that failed"
    // are no longer the same thing — the pairing is made where the failure is known.
    private var lastSendConversationId: Long? = null

    /** Whether the typewriter's per-character tick and finish pulse fire (a2's Behaviour toggle). */
    @Volatile private var typingHapticsOn = true

    /** Re-run the failed user turn after a connection failure (the Retry button on the modal). */
    fun retryLast() {
        val ctx = appContextRef ?: return
        val p = lastSend ?: return
        val target = lastSendConversationId
        // The failed conversation may already be generating again (the user resent by hand); a
        // retry must not stack a second turn onto it. Turns in other conversations don't block.
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
            answersMessageId = p.answersMessageId,
            // The failed turn's own conversation: the user may have moved to another chat while
            // the error modal was up, and the retry must not land wherever they happen to be now.
            targetConversationId = target
        )
    }

    /**
     * One in-flight reply, owning outright every piece of live state a generating turn touches:
     * its coroutine, its typewriter (buffer, reveal job, epoch), its thinking/streaming flags, its
     * parked confirmation, its retry parameters. That ownership is the whole concurrency model —
     * several conversations can generate at once precisely because turns cannot see, much less
     * clobber, one another's state. The screen looks a turn up by conversation id and renders only
     * the one belonging to the conversation on screen.
     *
     * The per-turn [streamEpoch] survives from the single-slot days as defence in depth: within a
     * turn, each round arms the stream anew, and a producer still draining (a cancelled cloud
     * stream's last chunk, a stopping local decode's last token) carries a stale epoch and is
     * dropped rather than contaminating the round that replaced it.
     */
    private class Turn(
        // Null only for a send into a brand-new conversation, until the row is lazily created;
        // resolved — and never changed again — the moment the id exists.
        initialConversationId: Long?,
        // Decided at send() time. Only a LOCAL turn holds gigabytes of RAM, is stopped by leaving
        // its conversation or the app, and needs the native stop flag flipped on interrupt. At
        // most one local turn can ever exist, because leaving its conversation stops it.
        val isLocal: Boolean
    ) {
        var conversationId by mutableStateOf(initialConversationId)
        var job: Job? = null
        @Volatile var turnPersisted = false
        // The full parameter set of this turn's send(), kept so a network failure can offer a
        // one-tap Retry of THIS turn (see fail / retryLast).
        var params: LastSend? = null

        // ---- Observable per-turn UI state (read through the controller's *For helpers) ----
        var thinking by mutableStateOf(false)
        var loadingModel by mutableStateOf(false)
        var streamingText by mutableStateOf<String?>(null)

        // The confirm modal's answer for THIS turn is delivered back through this.
        var confirmationDeferred: CompletableDeferred<ConfirmationOutcome>? = null

        // ---- Typewriter internals (strictly per-turn; see the class comment) ----
        val lock = Any()
        val buffer = StringBuilder()
        var shown = 0
        var typewriterJob: Job? = null
        @Volatile var streamEpoch = 0L

        fun currentLen(): Int = synchronized(lock) { buffer.length }
        fun snapshotBuffer(): String = synchronized(lock) { buffer.toString() }

        fun onDelta(epoch: Long, delta: String) {
            synchronized(lock) {
                // Stale round — the stream was reset or finished after this producer started.
                if (epoch != streamEpoch) return
                buffer.append(delta)
            }
        }

        /**
         * Reveal the buffered reply as a typewriter (issue 11): one code point per beat, stride
         * widening with the backlog (see stepFor) so the visible pace is even for short and long
         * replies alike.
         */
        fun resetStream(reveal: Boolean) {
            typewriterJob?.cancel()
            synchronized(lock) {
                streamEpoch++
                buffer.setLength(0)
            }
            shown = 0
            streamingText = null
            if (!reveal) {
                typewriterJob = null
                return
            }
            val ctx = AssistantController.appContextRef
            typewriterJob = AssistantController.genScope.launch {
                // Within one epoch the buffer is strictly append-only (onDelta only appends, and
                // every path that clears it also cancels this job first), so a copied-out String is
                // always a valid prefix of the live buffer. The old loop did buffer.toString() on
                // EVERY 16 ms beat — a full copy of the entire reply-so-far, 60×/s. On a long reply
                // whose stream has finished but whose reveal is still catching up (the common case
                // for a wall of text), that was a fresh full copy per beat for the whole remainder
                // of the reveal: O(beats × length) character copying and steady GC churn during the
                // most animation-heavy moment the app has. Now the buffer is re-copied only when it
                // has actually GROWN past the snapshot — i.e. at the network's chunk rate while
                // streaming, and exactly once after the stream ends. The per-beat substring for the
                // visible prefix remains, as Compose state needs an immutable String.
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
                    val step = AssistantController.stepFor(len - shown)
                    var moved = 0
                    while (moved < step && shown < len) {
                        val cp = snapshot.codePointAt(shown)
                        shown += Character.charCount(cp)
                        moved++
                    }
                    streamingText = snapshot.substring(0, shown.coerceAtMost(len))
                    // Haptics belong to the conversation ON SCREEN only. Several turns can run at
                    // once now, and a background conversation's typewriter ticking the motor would
                    // keep the device vibrating for as long as it types — so a turn may only buzz
                    // while it IS the conversation being looked at.
                    if (ctx != null &&
                        AssistantController.typingHapticsOn &&
                        conversationId == AssistantController.currentConversationId
                    ) {
                        Haptics.typingTick(ctx)
                    }
                    delay(AssistantController.REVEAL_STEP_MS)
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

        /**
         * The completion buzz, gated exactly like the typing tick: on-screen conversation only.
         *
         * B-group task 1: the typewriter job is stopped FIRST. finishTyping() only waits until the
         * reveal has caught up — it leaves the reveal coroutine alive and looping, so a stray
         * per-character tick could still be issued microseconds before (or after) the strong pulse.
         * On OEM vibrators that drop an effect arriving while another plays, that tick was eating
         * the finish buzz outright, which is why no completion vibration was ever felt. Cancelling
         * here makes the strong pulse the only effect in flight; Haptics.finishBuzz then cancels
         * the motor and lets it settle before firing at full amplitude.
         *
         * The reveal is complete by the time this runs (every caller awaits finishTyping first), so
         * cancelling changes nothing the user sees — streamingText already holds the whole reply,
         * and the stored message replaces it a moment later.
         */
        fun completionBuzz() {
            typewriterJob?.cancel()
            typewriterJob = null
            if (!AssistantController.typingHapticsOn) return
            if (conversationId != AssistantController.currentConversationId) return
            AssistantController.appContextRef?.let { Haptics.finishBuzz(it) }
        }
    }

    /**
     * Ask the same question again and keep BOTH answers (B-group task 12).
     *
     * Two things people want and could not do: get a second opinion on the same prompt, and re-send
     * a message whose reply failed. Both are "run this user message again", so both are this.
     *
     * The user message is NOT re-inserted — it is already in the thread, and duplicating it would
     * turn "ask again" into a conversation that looks like the user repeated themselves. Instead the
     * new reply is tagged with that message's id, which is what puts the two answers in one variant
     * group and gives the chat its 1/2 switcher. The newest variant is selected automatically, so
     * asking again shows the new answer while the previous one stays one tap away.
     *
     * Every send parameter is taken live from settings rather than replayed from the original turn:
     * a resend after switching model is a resend ON THE NEW MODEL, which is most of the point.
     */
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
        smallModelMode: Boolean
    ) {
        if (message.role != "user") return
        send(
            appContext = appContext,
            text = message.content,
            attachmentMime = message.attachmentMime,
            attachmentData = message.attachmentData,
            attachmentName = message.attachmentName,
            // A resent message keeps whatever multiple attachments it was sent with.
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
            answersMessageId = message.id,
            targetConversationId = message.conversationId
        )
        // Show the new answer as it arrives; the older one is still reachable from the switcher.
        variantSelection.remove(message.id)
    }

    /**
     * Which variant of each answer group is on screen, keyed by the user message id. Absent means
     * "the newest", which is what a fresh reply should always show; a value is written only when the
     * user has deliberately paged back to an earlier answer.
     *
     * Snapshot state so the chat recomposes on a switch, and NOT persisted: which of two equally
     * saved answers you were last looking at is view state, not data, and restoring it days later
     * would be surprising rather than helpful.
     */
    val variantSelection = androidx.compose.runtime.mutableStateMapOf<Long, Int>()

    /** Page to a specific variant within one answer group. */
    fun selectVariant(replyToId: Long, index: Int) {
        variantSelection[replyToId] = index
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
        // Whether every tool call this turn makes — reads as well as writes — must be confirmed by
        // the user first (the Settings toggle, default ON). OFF runs tools directly, no modal.
        confirmTools: Boolean = true,
        // Small-model mode (B-group task 4). Captured at send time like every other mode flag, so
        // flipping the setting mid-reply never changes the prompt a turn is already running on.
        smallModelMode: Boolean = false,
        // The user message this turn answers (B-group task 12). Non-zero only on the resend path,
        // where the question is already in the thread and a SECOND reply to it is being produced —
        // the two then share a replyToId and the chat offers a 1/2 switcher between them. A fresh
        // send leaves this 0 and learns the id from its own insert below.
        answersMessageId: Long = 0,
        // Non-null only on the Retry path: the conversation the failed turn belongs to, so the
        // re-run writes there even if the user has since moved to another chat. A fresh send
        // always targets the conversation currently on screen.
        targetConversationId: Long? = null,
        // JSON list of a message's multiple attachments (R3 task #15). Populated on resend/retry so
        // the re-run keeps the exact files the original message carried; a fresh send passes
        // emptyList through [attachments] instead and never sets this.
        attachmentListJson: String? = null,
        // Every attachment of a fresh send, in order (R3 task #15). The first entry also populates
        // the legacy single-attachment columns; entries beyond the first live only in the JSON list.
        attachments: List<com.lucent.app.data.Attachment> = emptyList()
    ) {
        val targetKey = targetConversationId ?: currentConversationId
        // One turn PER CONVERSATION: if this conversation is already generating, the button on its
        // screen is a Stop button and a second send is meaningless. Turns in other conversations
        // do NOT block — several conversations generating at once is the point of the registry.
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
            useLocalTools, useLocalGpu, confirmTools, smallModelMode, answersMessageId
        )
        turn.thinking = true
        // Clear a leftover error banner only when it belongs to the conversation being sent in;
        // another conversation's pending error must survive a send made elsewhere.
        if (errorConversationId == targetKey) {
            errorText = ""
            errorConversationId = null
        }
        networkErrorMessage = null
        val db = AppDatabase.getInstance(appContext.applicationContext)
        // First turn in brings the keep-alive service up; the last one out takes it down.
        registerTurn(turn, name)

        turn.job = genScope.launch {
            try {
                // Make sure there's a conversation to write into. If this is the first message of
                // a fresh app (or right after "new conversation"), create the row now and switch
                // the observed stream to it.
                var convId = targetConversationId ?: currentConversationId
                if (convId == null) {
                    convId = db.chatConversationDao().insert(ChatConversation())
                    currentConversationId = convId
                    // Written back-to-back with currentConversationId so the screen's turn lookup
                    // (keyed by conversation id) can never observe a mismatched frame during the
                    // lazy creation.
                    turn.conversationId = convId
                    observeCurrentConversation(db)
                }
                val conversationId = convId
                turn.conversationId = conversationId

                // On a Retry after a connection failure the user's message is already in the
                // thread, so only a fresh send inserts one (and refreshes the title/recency).
                // Which question this turn's reply will be tagged with (B-group task 12). A fresh
                // send learns it from its own insert; a resend was given it by the caller.
                var answeredId = answersMessageId
                if (insertUserMessage) {
                    answeredId = db.chatDao().insert(
                        ChatMessage(
                            role = "user", content = text,
                            attachmentMime = attachmentMime,
                            attachmentData = attachmentData,
                            attachmentName = attachmentName,
                            // R3 task #15: fresh sends carry their files as a list; the first one
                            // stays in the legacy trio above, extras live in this JSON column.
                            attachmentList =
                                if (attachments.size > 1) com.lucent.app.data.Attachments.serialize(attachments)
                                else attachmentListJson,
                            conversationId = conversationId
                        )
                    )
                    // Keep the conversation's label and recency fresh. The title is derived from the
                    // first user message so the switcher shows something recognizable; later messages
                    // only bump updatedAt so it sorts to the top.
                    db.chatConversationDao().getById(conversationId)?.let { conv ->
                        val newTitle = if (conv.title.isBlank() || conv.title == "New conversation") {
                            text.trim().take(40).ifBlank { conv.title }
                        } else conv.title
                        db.chatConversationDao().update(
                            conv.copy(title = newTitle, updatedAt = System.currentTimeMillis())
                        )
                    }
                }

                // ---- On-device model path (task: local GGUF assistant) ----
                // Splits off AFTER the user's message is persisted (so the thread is identical in
                // both modes) and BEFORE anything cloud-shaped is assembled. Everything past this
                // point in the cloud branch — tools, web search, memory tiers, cross-conversation
                // digests — is deliberately absent from the local turn: the task pins local mode to
                // zero configuration and no cross-conversation memory, and a small on-device model
                // is at its most fluent when it is fed a short, clean prompt rather than a tool
                // protocol it will imitate badly.
                if (useLocalModel) {
                    // The turn was flagged local at construction; the lifecycle paths read that
                    // off the registry — only a LOCAL turn holds gigabytes of RAM, and only a
                    // local turn is stopped by leaving its conversation or the app (task 2).
                    runLocalTurn(turn, db, conversationId, useLocalTools, useLocalGpu, confirmTools, memoryTier, smallModelMode, answeredId)
                    return@launch // the shared finally below still cleans this turn's state
                }

                // Which stored messages travel to the model this turn is entirely the memory tier's
                // call (issue 9); nothing above ever gets un-stored. HIGH also folds a bounded digest
                // of other conversations into the system prompt as background memory.
                var history = buildHistory(db, conversationId, memoryTier)
                val crossMemory = crossConversationMemory(db, conversationId, memoryTier)
                // R3 report: small-model mode used to DROP the HIGH-tier cross-conversation digest
                // entirely (the compact prompt accepted no memory at all), so "High · cross-chat"
                // silently behaved like "Medium" whenever the small-model switch was on. HIGH must
                // keep its promise under the compact prompt too, so the compact path receives a
                // heavily trimmed digest (SMALL_MODEL_CROSS_BUDGET messages instead of the full
                // HIGH_CROSS_MESSAGE_BUDGET) — still a memory, just a shorter one.
                val compactCrossMemory =
                    if (smallModelMode) crossConversationMemory(db, conversationId, memoryTier, cap = SMALL_MODEL_CROSS_BUDGET)
                    else ""
                // v2.7.4: carry a parked "refine" proposal into this turn once, then let it go.
                val parkedRefine = refinementContext
                refinementContext = null
                val basePrompt =
                    if (smallModelMode) buildCompactSystemPrompt(name, style, tier = memoryTier, webSearchEnabled = webSearchEnabled, userText = text, crossMemory = compactCrossMemory)
                    else buildSystemPrompt(name, style, memoryTier, webSearchEnabled, crossMemory, text)
                val systemPrompt = if (parkedRefine != null) {
                    basePrompt +
                        "\n\nIMPORTANT (the user is refining an earlier proposal of yours): you proposed this before, " +
                        "the user paused it to ask for changes, and it was NOT executed. Proposal: " + parkedRefine +
                        "\nThe user has now told you what to change. Re-propose the action with their adjustments by calling " +
                        "the matching tool again. The app will show them a confirmation dialog - only call the tool; never " +
                        "claim anything was done, because it only happens after they approve it."
                } else basePrompt
                val tools = AppTools.definitions(includeWebSearch = webSearchEnabled)

                // The upload the attach_upload_* tools may store this turn: the file on the
                // message just sent, or — when it has none — the user's most recent earlier
                // upload in this conversation (see resolveUpload).
                val (uploadMime, uploadData, uploadName) =
                    resolveUpload(db, conversationId, attachmentMime, attachmentData, attachmentName)

                var finalReply: RawModelReply? = null
                var lastToolResults: List<ToolExecResult> = emptyList()
                var errored = false
                // Signatures of tool calls already run this turn, with their results — so the same
                // action can never execute twice within one user turn (issue 12).
                val executed = HashMap<String, ToolExecResult>()

                // Set when the user declines a confirmation. A refusal ends the turn immediately
                // (task 2) — see the comment at the break below for why the loop must not continue.
                var declinedDetails: String? = null

                var round = 0
                while (round < MAX_TOOL_ROUNDS) {
                    turn.thinking = true
                    // Buffer this round silently: we don't yet know whether the model will call a
                    // tool (whose preamble must never flash) or answer directly.
                    turn.resetStream(reveal = false)
                    // Bind this round's deltas to the epoch armed above: late arrivals from a
                    // cancelled stream can then never contaminate a newer turn's buffer.
                    val roundEpoch = turn.streamEpoch
                    val result = LlmClient.streamChat(
                        url, spec, key, model, history, systemPrompt, tools
                    ) { delta -> turn.onDelta(roundEpoch, delta) }

                    if (result.isFailure) {
                        fail(turn, result.exceptionOrNull() ?: Exception("Unknown error"))
                        errored = true
                        break
                    }
                    val reply = result.getOrThrow()

                    if (reply.toolCalls.isEmpty()) {
                        finalReply = reply
                        break
                    }

                    // Execute the requested tools in order. Mutating ones pause for an explicit
                    // confirmation (issue 13); duplicates of an already-run call are skipped and their
                    // cached result reused (issue 12). Sequential, not concurrent, so each confirm
                    // modal is a clean one-at-a-time decision.
                    val results = mutableListOf<ToolExecResult>()
                    for (call in reply.toolCalls) {
                        val sig = signatureOf(call.name, call.argumentsJson)
                        val cached = executed[sig]
                        if (cached != null) {
                            // Exact same call already handled this turn — reuse its result, never run
                            // it again (issue 12).
                            results.add(cached)
                            continue
                        }

                        // Only calls that CHANGE the user's data ask (B-group task 2).
                        //
                        // The previous rule confirmed every call, reads included, on the reasoning
                        // that "the assistant acts only with my say-so" should cover everything.
                        // In practice that made the toggle unusable: listing tasks, reading a note
                        // the model needs before it can act, or running a web search each threw a
                        // modal, so a single ordinary request ("search the web and update my note")
                        // could demand three or four approvals before anything happened — and a
                        // dialog people must dismiss that often is a dialog they stop reading,
                        // which defeats the protection on the calls that genuinely matter.
                        //
                        // AppTools.READ_ONLY_TOOLS is the authority for what may run unattended:
                        // list_notes, read_note, list_tasks, read_task, search_items, web_search,
                        // read_attachment, list_note_versions, list_trash. Every one of those only
                        // reads; nothing in that set can create, edit, move, or delete anything.
                        // Everything else — every create/update/delete/pin/archive/attach, on both
                        // notes and tasks alike — still stops for an explicit confirmation.
                        val confirmed = if (confirmTools && AppTools.isMutating(call.name)) {
                            confirmToolCall(turn, call.name, call.argumentsJson)
                        } else ConfirmedCall(approved = true, argumentsJson = call.argumentsJson)

                        if (confirmed.refine) {
                            // v2.7.4 (was B-group task 3): "keep refining". Nothing runs and nothing
                            // is written - but the follow-up question is no longer left to the model
                            // (it sometimes reported "added" for an action that never ran). The
                            // question is written here, deterministically, and the proposal is parked
                            // in [refinementContext]; the next user message carries it into the
                            // system prompt so the model re-proposes the action with the adjustments.
                            val proposal = AppTools.describeToolCall(call.name, confirmed.argumentsJson)
                            refinementContext = proposal
                            declinedDetails = "refine\u0001" + proposal
                            break
                        }

                        if (!confirmed.approved) {
                            // The user said no. END THE TURN — do not feed the refusal back and let
                            // the model have another go (task 2).
                            //
                            // Telling the model "the user declined, don't retry" and continuing was
                            // the reasonable-looking version of this, and it did not work. Models
                            // frequently re-propose the same call anyway; the per-turn dedup then
                            // returns the cached refusal WITHOUT re-showing a modal, so the loop
                            // spends its remaining rounds silently arguing with itself while the
                            // user watches a thinking indicator that has nothing behind it. On the
                            // on-device path, where a round is tens of seconds, that reads as a
                            // hang — and it is the "assistant stuck thinking forever" report.
                            //
                            // A refusal is also simply not a situation that needs a model. The user
                            // has said what they want; the only correct reply is to confirm nothing
                            // happened, and that sentence can be written here, instantly, in their
                            // own language, without another round trip.
                            declinedDetails = AppTools.describeToolCall(call.name, call.argumentsJson)
                            break
                        }

                        turn.thinking = true
                        val r = AppTools.execute(
                            appContext, db, call.name, confirmed.argumentsJson,
                            uploadMime, uploadData, uploadName
                        )
                        executed[sig] = r
                        results.add(r)
                    }
                    if (declinedDetails != null) break

                    val toolImages = mutableListOf<ToolImage>()
                    for (r in results) toolImages.addAll(r.images)
                    lastToolResults = results

                    // Record this round in the history in the model's NATIVE tool format (task 13):
                    // first the assistant turn that MADE the call(s), then a tool turn carrying each
                    // call's result paired to the call it answers. Previously the results were fed back
                    // as a single plain "user" message and the assistant's own call was never recorded,
                    // so the model — not seeing that it had already asked — would ask again and the
                    // action ran twice (the "created it twice" bug). Threading the call and its result
                    // back the way the model expects closes that loop; the existing per-call dedup above
                    // stays as a belt-and-braces guard. The honesty/keep-going guidance the model needs
                    // is already in the system prompt, so it isn't repeated in these turns.
                    history = history + ChatTurn(
                        role = "assistant",
                        content = reply.text?.trim().orEmpty(),
                        toolCalls = reply.toolCalls
                    )
                    val resultTurns = reply.toolCalls.zip(results).map { (call, r) ->
                        ToolResultTurn(id = call.id, name = call.name, content = r.summary)
                    }
                    // One image (the first any tool surfaced) rides along on the tool turn so a vision
                    // model can see what was read; extra images are noted but not all inlined.
                    val primaryImage = toolImages.firstOrNull()
                    val resultContent = if (primaryImage != null && toolImages.size > 1) {
                        // Append a short note to the last result so the model knows more images exist.
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
                    // A fixed, honest reply written here rather than asked for — see the break above.
                    // v2.7.4: the "keep refining" variant asks the deterministic follow-up question.
                    val content = if (declinedDetails.startsWith("refine\u0001")) {
                        com.lucent.app.i18n.S.assistantRefineQuestion(declinedDetails.substringAfter("\u0001"))
                    } else com.lucent.app.i18n.S.assistantDeclinedReply(declinedDetails)
                    turn.thinking = false
                    turn.resetStream(reveal = true)
                    turn.finishTyping(content)
                    insertAssistant(db, conversationId, content, null, null, TokenEstimator.estimate(content), answeredId)
                    turn.turnPersisted = true
                    turn.completionBuzz()
                } else if (!errored) {
                    if (finalReply == null) {
                        // Model kept calling tools past the cap — ask once more with tools OFF so
                        // it has to produce a written reply now.
                        turn.thinking = true
                        turn.resetStream(reveal = false)
                        val forcedEpoch = turn.streamEpoch
                        val forced = LlmClient.streamChat(
                            url, spec, key, model, history, systemPrompt, emptyList()
                        ) { delta -> turn.onDelta(forcedEpoch, delta) }
                        if (forced.isFailure) {
                            fail(turn, forced.exceptionOrNull() ?: Exception("Unknown error"))
                            errored = true
                        } else {
                            finalReply = forced.getOrThrow()
                        }
                    }
                    if (!errored) finalReply?.let { reply ->
                        val img = reply.imageData?.takeIf { it.isNotBlank() }
                        val content = replyContent(reply.text, img != null, lastToolResults, userText = text)
                        // Hand off from the "thinking" bubble to the typewriter with no overlap.
                        turn.thinking = false
                        // The final round was buffered silently; reveal it now so it types out.
                        turn.resetStream(reveal = true)
                        turn.finishTyping(content)
                        // Approximate cost of this turn (issue 9): the system prompt plus every turn
                        // actually sent (including any tool-result turns added along the way) plus the
                        // reply. Labelled approximate in the UI — see TokenEstimator.
                        val tokens = TokenEstimator.estimate(systemPrompt) +
                            TokenEstimator.estimateAll(history.map { it.content }) +
                            TokenEstimator.estimate(content)
                        insertAssistant(db, conversationId, content, reply.imageMime, img, tokens, answeredId)
                        turn.turnPersisted = true
                        // One firm buzz to mark the reply is complete (issue 11).
                        turn.completionBuzz()
                    }
                }
            } catch (e: CancellationException) {
                // A Stop press (issue 14) — stopTurn already handled this turn's state and any
                // partial save; just let the coroutine end.
                throw e
            } catch (e: Exception) {
                fail(turn, e)
            } finally {
                // This turn owns every piece of state it touches, so its cleanup cannot clobber
                // another conversation's in-flight reply — the failure mode the old single-slot
                // controller had to guard with job-identity checks. Idempotent against stopTurn
                // having already done the same.
                turn.finishStream()
                turn.thinking = false
                turn.loadingModel = false
                if (confirmingTurn === turn) {
                    confirmingTurn = null
                    pendingConfirmation = null
                }
                // Last turn out takes the keep-alive service down (see registerTurn).
                unregisterTurn(turn)
            }
        }
    }

    /**
     * One full assistant turn on the imported GGUF model (task: local assistant).
     *
     * ### What it feeds the model
     * A tool-aware system prompt plus the tail of THIS conversation only ([LocalLlm.HISTORY_TURNS]
     * user/assistant pairs). The in-app note/task tools ARE available here (so the local assistant
     * can create and edit things, exactly like the cloud one); web search and cross-conversation
     * memory stay off, keeping the model offline-capable and its prompt short. The prompt is English
     * (models follow English instructions most reliably) but explicitly orders replies in the user's
     * own language, so a Chinese-language question gets a Chinese-language answer.
     *
     * ### The tool loop
     * GGUF models have no native function-calling channel, so tools run over a small text protocol:
     * the model emits a single JSON object to call a tool, [parseLocalToolCall] extracts it, the
     * real [AppTools.execute] runs it (mutating calls still pause for the same confirmation modal),
     * and the result is fed back for the next round — up to [MAX_LOCAL_TOOL_ROUNDS], after which the
     * model is forced to answer in words. Each round is buffered silently so a raw tool-call JSON
     * never flashes on screen; only the final written answer is revealed through the typewriter.
     *
     * ### Failure surfaces
     * Everything lands in the existing inline error banner ([errorText]), localized. A Stop press
     * is not an error: [stopGeneration] already flipped the native stop flag, saved the partial
     * text with the app's usual " …" suffix, and reset state — so return code 1 simply ends the
     * turn quietly.
     */
    private suspend fun runLocalTurn(
        turn: Turn,
        db: AppDatabase,
        conversationId: Long,
        useTools: Boolean,
        useGpu: Boolean,
        confirmTools: Boolean,
        // The memory tier now reaches this path instead of being hard-coded to MEDIUM. In local mode
        // Settings offers LOW and MEDIUM and withholds HIGH (task 8), so honouring the value here is
        // what makes that choice mean anything: LOW sends just the latest message, MEDIUM sends the
        // conversation tail. HIGH is clamped to MEDIUM as a belt-and-braces measure — a backup
        // restored from a cloud-configured device could still carry it — because HIGH additionally
        // folds in a cross-conversation digest that would blow past a small model's context window.
        memoryTier: MemoryTier,
        // See the send() parameter of the same name (B-group task 4).
        smallModelMode: Boolean,
        // The user message this reply answers (B-group task 12); 0 when unknown.
        answeredId: Long
    ) {
        val ctx = appContextRef ?: return

        if (!com.lucent.app.local.LocalLlm.isSupported()) {
            turn.thinking = false
            // W-1: name the real reason when it's the CPU (desktop AVX2 guard), not a packaging gap.
            postError(
                turn,
                if (com.lucent.app.local.LocalLlm.unsupportedBecauseCpuLacksAvx2())
                    com.lucent.app.i18n.S.localModelNeedsAvx2
                else com.lucent.app.i18n.S.localModelUnsupportedAbi
            )
            return
        }
        if (!com.lucent.app.local.LocalModelStore.hasModel(ctx)) {
            turn.thinking = false
            postError(turn, com.lucent.app.i18n.S.localModelMissing)
            return
        }

        // Apply the CPU/GPU choice BEFORE loading. ensureLoaded reloads the model if the backend
        // changed since last time; a failed GPU load quietly falls back to CPU inside LocalLlm.
        // useGpu was captured once, at send — so a Settings flip made while THIS reply streams
        // changes nothing here and simply rides in with the next reply (LocalLlm.setGpuEnabled
        // documents the full contract). Keep it a captured parameter; never re-read the setting
        // from inside the turn.
        com.lucent.app.local.LocalLlm.setGpuEnabled(useGpu)

        // Load (or re-use) the model. First load of a multi-GB file takes real seconds; surface a
        // dedicated "loading the model…" state so the wait is visibly *loading*, not a hang. If the
        // model is already resident (same slot, same backend) ensureLoaded returns instantly and the
        // flag barely flickers.
        turn.loadingModel = true
        val loaded = try {
            com.lucent.app.local.LocalLlm.ensureLoaded(ctx)
        } finally {
            turn.loadingModel = false
        }
        // Diagnostic trail (only written when the user has logging on): records how far the local
        // turn got and, below, the exact result code — so a "couldn't reply" report is actionable
        // even on a device whose OEM blocks logcat.
        com.lucent.app.data.StartupLog.event(
            ctx,
            "local turn: supported=true, model=${com.lucent.app.local.LocalModelStore.displayName(ctx) ?: "?"}, gpu=$useGpu, loaded=$loaded"
        )
        if (!loaded) {
            turn.thinking = false
            postError(turn, com.lucent.app.i18n.S.localModelLoadFailed(com.lucent.app.i18n.S.localModelLoadFailedDetail))
            return
        }

        // Tools are opt-in (default off, for phone performance — see the Settings toggle). With them
        // off this is a plain, fast chat that never spends a round on the tool protocol.
        if (!useTools) { runLocalChatOnly(turn, db, conversationId, memoryTier, smallModelMode, answeredId); return }

        // The in-app tools (create/read/update/… notes and tasks). Web search is left off on
        // purpose: local mode is meant to work with no network, and a small model drives the
        // note/task actions far more reliably than an open-web tool. Cross-conversation memory is
        // still absent — this turn sees only the tail of THIS chat.
        val tools = AppTools.definitions(includeWebSearch = false)
        val validToolNames = tools.map { it.name }.toHashSet()

        // The tail of this conversation, oldest→newest, as plain role/text pairs. Attachments are
        // text-invisible to a local text model, so only message text travels. The just-sent user
        // message is already in the table, so it arrives as the final pair entry.
        val turns = buildHistory(db, conversationId, localTier(memoryTier))
            .filter { it.role == "user" || it.role == "assistant" }
            .map { it.role to it.content }
            .filter { it.second.isNotBlank() }
            .takeLast(com.lucent.app.local.LocalLlm.HISTORY_TURNS * 2)
        val lastUserText = turns.lastOrNull { it.first == "user" }?.second ?: ""

        // The running transcript we re-feed each round. It grows as the model calls tools: the
        // assistant's tool-call JSON and the tool's result are appended so the next generation can
        // see what already happened — the same loop the cloud path runs, but over a text protocol
        // the on-device model can follow (there is no native function-calling channel through GGUF).
        val messages = mutableListOf<Pair<String, String>>()
        messages.add("system" to buildLocalSystemPrompt(tools, lastUserText, smallModelMode))
        messages.addAll(turns)

        // The upload the attach_upload_* tools may store this turn. The just-sent message is
        // already persisted, so the newest user upload in this conversation IS that message's
        // file when it has one, and otherwise the most recent earlier upload (see resolveUpload).
        // A text-only local model can't look inside the file, but attaching it doesn't need to —
        // the bytes ride outside the model — so the transcript only has to say the file exists.
        val (uploadMime, uploadData, uploadName) = resolveUpload(db, conversationId, null, null, null)
        if (!uploadData.isNullOrBlank()) {
            messages.add(
                "system" to ("The user has an uploaded file in this conversation: \"" +
                    (uploadName ?: "file") + "\". You cannot see inside it, but you CAN save it: " +
                    "call attach_upload_to_note or attach_upload_to_task to attach that exact " +
                    "file to a note or task when asked.")
            )
        }

        // Per-turn dedup so the exact same call can't run twice, and the results gathered so far so
        // an empty final reply can still be turned into an honest summary of what was done.
        val executed = HashMap<String, ToolExecResult>()
        val toolResults = mutableListOf<ToolExecResult>()
        var finalText: String? = null

        var round = 0
        while (round < MAX_LOCAL_TOOL_ROUNDS) {
            // Buffer silently: this round's output might be a tool call, which must never flash on
            // screen as raw JSON. Only the final answer is revealed, at the end.
            turn.resetStream(reveal = false)
            turn.thinking = true
            val roundEpoch = turn.streamEpoch
            val rc = com.lucent.app.local.LocalLlm.generate(messages) { piece -> turn.onDelta(roundEpoch, piece) }
            val raw = turn.snapshotBuffer()

            if (rc == 1) return                       // Stopped by the user (handled by stopGeneration).
            if (rc != 0) { turn.thinking = false; postError(turn, com.lucent.app.i18n.S.localModelGenerateFailed + " [" + rc + "]"); return }

            val call = parseLocalToolCall(raw, validToolNames)
            if (call == null) {
                val attempted = attemptedToolCallName(raw)
                if (attempted != null && round < MAX_LOCAL_TOOL_ROUNDS - 1) {
                    // Shaped like a tool call, but it names no tool that exists even after alias
                    // mapping. Showing the raw JSON as the reply is the one unacceptable outcome
                    // (the reported bug), and silently dropping the action the user asked for is
                    // the second-worst — so the mistake goes back into the transcript, named
                    // precisely, and the model gets another round to use a real tool or answer
                    // in prose.
                    messages.add("assistant" to raw.trim().take(600))
                    messages.add(
                        "tool" to ("Result of " + attempted + ": ERROR — no tool named \"" + attempted +
                            "\" exists. Use EXACTLY one tool name from the list in the system " +
                            "message, or answer the user in plain text without any JSON.")
                    )
                    round++
                    continue
                }
                // Plain prose → this is the final answer.
                finalText = deRobotify(raw).trim()
                break
            }

            // It's a tool call. Clear the buffer now so a Stop landing during the (suspending) tool
            // execution can't persist the tool-call JSON as if it were a reply.
            turn.resetStream(reveal = false)

            val sig = signatureOf(call.name, call.argsJson)
            val cached = executed[sig]
            val result = if (cached != null) cached else {
                // Same contract as the cloud loop (B-group task 2): the toggle decides WHETHER
                // confirmation is in play, and the tool's category decides whether THIS call needs
                // it. Read-only calls run straight through. That matters even more here than on the
                // cloud path — an on-device round costs tens of seconds, so a modal in front of a
                // plain list_tasks was pure dead time on top of an already slow turn.
                val confirmed = if (confirmTools && AppTools.isMutating(call.name)) {
                    confirmToolCall(turn, call.name, call.argsJson)
                } else ConfirmedCall(approved = true, argumentsJson = call.argsJson)

                if (confirmed.refine) {
                    // v2.7.4: deterministic "what would you like to change?" - same reasoning as the
                    // cloud path; a local round would cost tens of seconds to maybe say "added".
                    val proposal = AppTools.describeToolCall(call.name, confirmed.argumentsJson)
                    refinementContext = proposal
                    val content = com.lucent.app.i18n.S.assistantRefineQuestion(proposal)
                    turn.thinking = false
                    turn.resetStream(reveal = true)
                    turn.finishTyping(content)
                    insertAssistant(db, conversationId, content, null, null, TokenEstimator.estimate(content), answeredId)
                    turn.turnPersisted = true
                    turn.completionBuzz()
                    return
                } else if (!confirmed.approved) {
                    // Same as the cloud path, and it matters more here: one extra local round costs
                    // tens of seconds of on-device decoding, so continuing after a refusal is what
                    // turned "no" into a minutes-long hang. Reply now and stop (task 2).
                    val content = com.lucent.app.i18n.S.assistantDeclinedReply(
                        AppTools.describeToolCall(call.name, call.argsJson)
                    )
                    turn.thinking = false
                    turn.resetStream(reveal = true)
                    turn.finishTyping(content)
                    insertAssistant(db, conversationId, content, null, null, TokenEstimator.estimate(content), answeredId)
                    turn.turnPersisted = true
                    turn.completionBuzz()
                    return
                } else {
                    // Approved: run it. This is the ONLY branch that reaches execute(), which is
                    // what makes "refine" genuinely non-destructive — an `if` without this `else`
                    // would fall through and run the tool anyway.
                    turn.thinking = true
                    val r = AppTools.execute(
                        ctx, db, call.name, confirmed.argumentsJson,
                        uploadMime, uploadData, uploadName
                    )
                    executed[sig] = r
                    r
                }
            }
            toolResults.add(result)

            // Record the exchange for the next round: what the assistant asked, and what came back.
            messages.add("assistant" to renderLocalToolCall(call))
            messages.add("tool" to "Result of ${call.name}: ${result.summary}")
            round++
        }

        // The model kept calling tools past the cap — ask once more, tools disabled, so it must
        // produce a written answer now instead of looping forever.
        if (finalText == null) {
            turn.resetStream(reveal = false)
            turn.thinking = true
            messages.add("system" to "Stop calling tools now and write your final answer to the user, in their language, as plain text. Do not output any JSON.")
            val finalEpoch = turn.streamEpoch
            val rc = com.lucent.app.local.LocalLlm.generate(messages) { piece -> turn.onDelta(finalEpoch, piece) }
            if (rc == 1) return
            finalText = deRobotify(turn.snapshotBuffer()).trim()
        }

        // A model that never recovered can leave tool-JSON standing as its "answer". Raw JSON must
        // never reach the screen, so a mostly-JSON final is dropped here and replyContent below
        // falls back to its honest summary of what the tools actually did, or its friendly
        // ask-again line — both in the user's language.
        run {
            val ft = finalText
            if (ft != null && attemptedToolCallName(ft) != null) {
                val shape = ft.trim()
                if (shape.startsWith("{") || shape.startsWith("<tool_call") || shape.startsWith("```")) {
                    finalText = ""
                }
            }
        }

        // Honest final text: the model's own words if it wrote any, otherwise a summary of what the
        // tools actually did (or a friendly retry line in the user's language) — never a bare "done".
        val content = replyContent(finalText, hasImage = false, toolResults = toolResults, userText = lastUserText)
        turn.thinking = false
        turn.resetStream(reveal = true)
        turn.finishTyping(content)
        val tokens = TokenEstimator.estimateAll(messages.map { it.second }) + TokenEstimator.estimate(content)
        insertAssistant(db, conversationId, content, null, null, tokens, answeredId)
        turn.turnPersisted = true
        turn.completionBuzz()
    }

    /**
     * The tools-off local path (the default). A plain chat: a short system prompt with NO tool guide
     * plus the tail of this conversation, and a single generation streamed straight to the typewriter
     * — no silent buffering, because with no tools there is never a tool-call JSON that could flash on
     * screen. This is the fast, low-overhead mode a weak phone gets unless the user opts tools in.
     */
    private suspend fun runLocalChatOnly(
        turn: Turn,
        db: AppDatabase,
        conversationId: Long,
        memoryTier: MemoryTier,
        smallModelMode: Boolean,
        // The user message this reply answers (B-group task 12); 0 when unknown.
        answeredId: Long
    ) {
        // Small-model mode also shortens the HISTORY, not just the instructions (B-group task 4).
        // Trimming the prompt but still feeding sixteen past messages would give most of the
        // reclaimed context window straight back — and on a 4K-context model the conversation tail
        // is usually the larger of the two. Four exchanges is enough for "and delete that one" to
        // still resolve, which is the thing history is actually load-bearing for here.
        val historyTurns =
            if (smallModelMode) com.lucent.app.local.LocalLlm.HISTORY_TURNS
            else com.lucent.app.local.LocalLlm.HISTORY_TURNS * 2
        val turns = buildHistory(db, conversationId, localTier(memoryTier))
            .filter { it.role == "user" || it.role == "assistant" }
            .map { it.role to it.content }
            .filter { it.second.isNotBlank() }
            .takeLast(historyTurns)
        val lastUserText = turns.lastOrNull { it.first == "user" }?.second ?: ""

        // PHASE 4 — local multimodal. The image the user attached to THIS message, decoded only
        // when the resident model actually has a projector (supportsVision). android.util.Base64
        // matches the encoder in AssistantScreen; the desktop tree satisfies it via its android.*
        // shims, so this twin file stays identical on both platforms.
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

        val messages = buildList {
            add(
                "system" to (
                    "You are a helpful assistant living inside Lucent, a personal notes and tasks app. " +
                    "Always reply in the same language the user writes in. Be concise, warm, and clear. " +
                    "Write plain conversational text only — never markdown, asterisks, bullet points, or headings.\n\n" +
                    // ---- Capability honesty (task 6) + name the reason (tool-permission fix) ----
                    //
                    // This paragraph is the fix for "the assistant says it created the task
                    // and nothing happened". Tools are OFF on this path, and the prompt used to be
                    // silent about it — so the model, which has no way to observe its own tool
                    // access, answered the only way a helpful assistant can when asked to do
                    // something: it said it had done it. The lie was not the model being careless,
                    // it was the model being uninformed, and the cure is to inform it.
                    //
                    // Stated as a hard capability limit rather than a style preference, because a
                    // soft phrasing ("you may not be able to…") leaves room for the model to decide
                    // it probably can.
                    //
                    // The second half of the fix is the OTHER failure this mode produced: a bare
                    // "I can't do that" — or worse, "your task cannot be completed" — with no
                    // reason given, which reads as a malfunction and tells the user nothing about
                    // the one setting that would fix it. So the prompt now states the cause (the
                    // "Allow tools" switch is off) and ORDERS the model to pass that reason on,
                    // with the exact Settings path. A very small model may still ignore even
                    // this, which is why the Assistant screen ALSO shows a deterministic hint
                    // (S.localToolsOffHint) whenever local mode runs without tool permission:
                    // the user learns the cause from the UI even if the model never says it.
                    "IMPORTANT — WHAT YOU CANNOT DO RIGHT NOW, AND WHY. You have NO tools and NO " +
                    "ability to change anything in this app in this conversation. You cannot " +
                    "create, read, edit, complete, or delete notes or tasks, you cannot attach " +
                    "files, and you cannot see the user's existing notes or tasks at all. You also " +
                    "have NO internet access, so you cannot look anything up or fetch anything " +
                    "current. The ONLY reason for all of this is a setting: the user has not " +
                    "granted you tool permission — the \"Allow tools\" switch under Settings > " +
                    "Assistant > Local model is OFF. It is a setting, not a flaw in their request; " +
                    "their requests are not impossible.\n\n" +
                    "Because of that, you must NEVER say or imply that you have created, added, " +
                    "saved, changed, completed, deleted, or found anything. Never say \"done\", " +
                    "\"added it\", \"I've made that note\", or anything of that shape. That would be " +
                    "false, and the user would go looking for something that does not exist.\n\n" +
                    "If they ask you to create, change, find, or do anything with a note or task " +
                    "(for example \"add a task for tomorrow morning\"), you MUST give them the real " +
                    "reason, in their own language: you can't act right now because tool " +
                    "permission is turned off, and they can enable it under Settings > Assistant > " +
                    "Local model > Allow tools (or add the item themselves on the Notes or Tasks " +
                    "tab). NEVER answer with only a bare refusal like \"I can't do that\" or " +
                    "\"your task cannot be completed\" — a refusal that hides the reason reads as " +
                    "a malfunction and leaves them stuck, when one sentence about the setting " +
                    "fixes it. If they ask WHY you can't, that setting IS the answer. You can " +
                    "still help fully in words — draft the wording, think it through, talk it " +
                    "over — and offering that is far more useful than an apology."
                )
            )
            // The concrete output-language order (B-group task 8). Added as its own trailing system
            // message rather than folded into the paragraph above: on this path the model is at its
            // smallest and least steerable, and a short standalone instruction sitting immediately
            // before the conversation is the placement it actually obeys. Omitted entirely when
            // detection is not confident — see i18n/ReplyLanguage.
            com.lucent.app.i18n.ReplyLanguage.instructionFor(lastUserText)?.let { add("system" to it) }
            // PHASE 4: with a projector loaded and an image on this message, the blanket
            // "you cannot see attachments" above would be false for exactly this turn — and a
            // model told it is blind will refuse to describe what it is looking at.
            if (turnImages.isNotEmpty()) {
                add("system" to "The user's current message includes an image, and you CAN see it. Describe or use it directly; do not claim you cannot see images.")
            }
            addAll(turns)
        }

        // Arm the typewriter first, then decode: with no tools there is nothing to hide, so the reply
        // reveals live, the first token swapping the "thinking" bubble for streaming text.
        turn.resetStream(reveal = true)
        var first = true
        val chatEpoch = turn.streamEpoch
        val rc = com.lucent.app.local.LocalLlm.generate(messages, images = turnImages) { piece ->
            if (first) { first = false; turn.thinking = false }
            turn.onDelta(chatEpoch, piece)
        }
        appContextRef?.let { com.lucent.app.data.StartupLog.event(it, "local chat: generate rc=$rc") }
        if (rc == 1) return   // Stopped by the user; stopGeneration saved any partial.
        if (rc != 0) { turn.thinking = false; postError(turn, com.lucent.app.i18n.S.localModelGenerateFailed + " [" + rc + "]"); return }

        val content = replyContent(
            deRobotify(turn.snapshotBuffer()).trim(),
            hasImage = false, toolResults = emptyList(), userText = lastUserText
        )
        turn.thinking = false
        turn.finishTyping(content)
        val tokens = TokenEstimator.estimateAll(messages.map { it.second }) + TokenEstimator.estimate(content)
        insertAssistant(db, conversationId, content, null, null, tokens, answeredId)
        turn.turnPersisted = true
        turn.completionBuzz()
    }

    /** How many tool rounds the on-device model may take before it is forced to answer in words. */
    private val MAX_LOCAL_TOOL_ROUNDS = 6

    private data class LocalToolCall(val name: String, val argsJson: String)

    /**
     * The system prompt for a local tool-using turn: persona, the strict "reply in the user's
     * language, plain text only" rule, the exact JSON shape a tool call must take, and a compact
     * catalogue of the available tools. English instructions (models follow them most reliably)
     * that nonetheless order replies in the user's own language, so a Chinese-language question gets a Chinese-language answer.
     */
    private fun buildLocalSystemPrompt(
        tools: List<ToolDefinition>,
        userText: String,
        compact: Boolean = false
    ): String {
        val today = java.time.ZonedDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd, HH:mm"))
        return buildString {
            append("You are a helpful assistant living inside Lucent, a personal notes and tasks app. ")
            append("Always reply in the same language the user writes in. Be concise, warm, and clear. ")
            append("Write plain conversational text only — never markdown, asterisks, bullet points, or headings.\n\n")
            append("Right now it is ").append(today).append(" in the user's local time. ")
            append("Work any concrete date out from this and pass it as an absolute value.\n\n")
            append("You can act inside the app by calling tools. Only when you actually need to take an action ")
            append("or look something up, reply with EXACTLY ONE JSON object and nothing else, in this exact form:\n")
            append("{\"tool\": \"<tool_name>\", \"arguments\": { ... }}\n")
            append("When calling a tool: output the raw JSON with no code fences and no words before or after it; ")
            append("use only the tools listed below, copying the tool name EXACTLY as written ")
            append("(for example create_task — never an invented variant like add_task); ")
            append("include only the arguments you need. ")
            append("After each tool runs you receive a line beginning \"Result of <tool>:\". ")
            append("Then either call another tool the same way, or — once the task is done — write your final answer ")
            append("to the user in their language as plain text (no JSON). ")
            append("If the user is only chatting and no action is needed, just answer directly with no tool.\n\n")
            // ---- Capability honesty (task 6) ----
            //
            // Tools are on here, but only these tools. The model must not extrapolate from "I can
            // call functions" to "I can do anything the app can do" — in particular it has no
            // network in local mode, by design, and an offline model inventing a web lookup is the
            // same class of failure as a tool-less model inventing a task.
            if (compact) append("The tools below are the ONLY things you can do. You have NO internet " +
                "access. Never say you did something unless its \"Result of <tool>:\" line says it " +
                "worked.\n\n")
            else append("The tools listed below are the ONLY actions available to you. If something is " +
                "not in that list you cannot do it, and you must say so rather than claiming you " +
                "did it — and say the real reason in the user's language (this assistant doesn't " +
                "have that ability in this app), never a bare \"I can't\" and never that their " +
                "request itself is impossible. In particular you have NO internet access in this " +
                "mode: you cannot search " +
                "the web, open links, or fetch anything current, so never present a guess as a " +
                "looked-up fact. Only report an action as done after its \"Result of <tool>:\" line " +
                "confirms it worked; if a result says it failed, tell the user it failed.\n\n")
            append("Tools:\n")
            for (t in tools) {
                val params = t.params.joinToString(", ") { p -> p.name + if (p.required) "*" else "" }
                append("- ").append(t.name)
                if (params.isNotEmpty()) append("(").append(params).append(")")
                // In compact mode only the FIRST sentence of each description survives (B-group
                // task 4). The full catalogue is ~40 tools with a paragraph each — on its own that
                // is more than a small model's whole context window, and the tool NAME plus its
                // argument list already carries almost all of the signal a model needs to pick
                // correctly. The prose after the first sentence is nuance, and nuance is what a
                // small model has no room for.
                val desc = if (compact) t.description.substringBefore(". ").take(120) else t.description
                append(" — ").append(desc).append("\n")
            }
            append("\n(* = required argument. Booleans are true or false. Dates are \"YYYY-MM-DD\" or \"YYYY-MM-DD HH:mm\".)")

            // ---- What the user actually said, in words a small model recognises (task 14) ----
            append("\n\nPeople rarely use a tool's own word. Delete also means: remove, erase, get ")
            append("rid of, throw away, I don't need it, \u5220\u9664/\u5220\u6389/\u53bb\u6389/\u4e0d\u8981\u4e86, \u524a\u9664/\u6d88\u3059/\u3044\u3089\u306a\u3044, ")
            append("\uc0ad\uc81c/\uc9c0\uc6cc. Complete also means: done, finished, tick it off, \u5b8c\u6210/\u505a\u5b8c\u4e86/\u641e\u5b9a, ")
            append("\u5b8c\u4e86/\u7d42\u308f\u3063\u305f, \uc644\ub8cc/\ub05d\ub0ac\uc5b4. Restore also means: bring it back, undelete, \u6062\u590d/\u8fd8\u539f, ")
            append("\u5fa9\u5143, \ubcf5\uc6d0. Deleting a whole note or task is NOT the same as removing one ")
            append("checklist line or one attached file — pick the narrower tool when that is what ")
            append("they meant. If one message asks for several things, do them all, one tool call ")
            append("at a time, before you write your final answer.")

            // The concrete output-language order (B-group task 8), last and therefore heaviest.
            // Small on-device models are exactly the ones that answered in English regardless of
            // the prose rule above, so this is where it matters most. Null when detection is not
            // confident, in which case nothing is appended.
            com.lucent.app.i18n.ReplyLanguage.instructionFor(userText)?.let { append("\n\n").append(it) }
        }
    }

    /** Re-serialise a parsed call as compact, well-formed JSON for the assistant turn we feed back. */
    private fun renderLocalToolCall(call: LocalToolCall): String {
        val args = try { org.json.JSONObject(call.argsJson) } catch (e: Exception) { org.json.JSONObject() }
        return org.json.JSONObject().put("tool", call.name).put("arguments", args).toString()
    }

    /**
     * Pull a tool call out of a local model's raw output, tolerantly. Small GGUF models phrase tool
     * calls every which way, so this copes with: a bare JSON object, one wrapped in ``` fences, one
     * inside <tool_call>…</tool_call>, arguments under any of several key names, and arguments that
     * arrive double-encoded as a JSON string. A candidate only counts as a call when its tool name
     * is one that actually exists ([valid]) — so incidental JSON in a normal prose answer is never
     * mistaken for a call, and plain chat falls straight through to being the final reply.
     */
    private fun parseLocalToolCall(raw: String, valid: Set<String>): LocalToolCall? {
        if (raw.isBlank()) return null
        val s = stripToolWrappers(raw)

        for (candidate in jsonObjectCandidates(s)) {
            var obj = try { org.json.JSONObject(candidate) } catch (e: Exception) { continue }
            // Some models nest the call one level deep ({"tool_call": {"name": …}}); unwrap it.
            // optJSONObject is null when the key holds a string, so flat forms pass unchanged.
            for (k in TOOL_WRAPPER_KEYS) obj.optJSONObject(k)?.let { obj = it }
            val rawName = firstJsonString(obj, "tool", "name", "function", "action", "tool_name")?.trim()
            if (rawName.isNullOrBlank()) continue
            // Exact match first; then alias mapping, because small on-device models routinely
            // invent near-miss names — the reported bug was Qwen2.5-0.5B emitting "add_task"
            // for create_task and the raw JSON landing on screen as the reply.
            val name = resolveToolName(rawName, valid) ?: continue
            val argsObj = firstJsonObject(obj, "arguments", "args", "parameters", "input", "params")
            return LocalToolCall(name, stripBlankArguments(argsObj).toString())
        }
        return null
    }

    // Wrapper keys some chat templates put around the call object itself.
    private val TOOL_WRAPPER_KEYS = arrayOf("tool_call", "function_call", "call", "tool", "function", "action")

    // Compiled once: shared by resolveToolName and attemptedToolName below.
    private val NAME_SEPARATORS = Regex("[\\s\\-]+")
    private val SNAKE_CASE_NAME = Regex("^[a-z0-9]+(_[a-z0-9]+)+$")

    // Compiled once: this scan runs on every reply a local model produces with tools enabled.
    private val TOOL_CALL_TAG = Regex("(?s)<tool_call>(.*?)</tool_call>")
    private val CODE_FENCE = Regex("(?s)```(?:json|tool_call)?\\s*(.*?)```")

    /** Peel `<tool_call>` tags and code fences off a model's output before scanning it for JSON. */
    private fun stripToolWrappers(raw: String): String {
        var s = raw.trim()
        TOOL_CALL_TAG.find(s)?.let { s = it.groupValues[1].trim() }
        CODE_FENCE.find(s)?.let { s = it.groupValues[1].trim() }
        return s
    }

    /**
     * Map a model-emitted tool name onto a real one. Exact (after snake_case normalisation) wins;
     * otherwise the leading verb is swapped through its synonym group and the trailing noun through
     * singular/plural, and the first candidate that names a real tool is taken ("add_task" →
     * create_task, "edit_note" → update_note, "list_task" → list_tasks). As a last resort a UNIQUE
     * containment match is accepted. Anything still unresolved is null — the caller decides whether
     * to feed an error back to the model rather than guessing at an action on the user's data.
     */
    private fun resolveToolName(rawName: String, valid: Set<String>): String? {
        val n = rawName.trim().lowercase().replace(NAME_SEPARATORS, "_").trim('_')
        if (n.isBlank()) return null
        if (n in valid) return n

        val tokens = n.split('_').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        val verbGroups = listOf(
            setOf("create", "add", "new", "make", "insert"),
            setOf("delete", "remove", "del", "erase", "discard"),
            setOf("update", "edit", "modify", "change", "rename"),
            setOf("complete", "finish", "done", "check", "close"),
            setOf("read", "get", "show", "view", "open"),
            setOf("list", "show", "view", "get"),
            setOf("reopen", "uncomplete", "undone", "uncheck"),
            setOf("search", "find", "query", "lookup")
        )
        val firstVariants = linkedSetOf(tokens.first())
        for (g in verbGroups) if (tokens.first() in g) firstVariants.addAll(g)
        val last = tokens.last()
        val lastVariants = linkedSetOf(last, if (last.endsWith("s")) last.dropLast(1) else last + "s")

        val candidates = linkedSetOf<String>()
        if (tokens.size == 1) {
            candidates.addAll(firstVariants)
            candidates.addAll(lastVariants)
        } else {
            val mid = tokens.subList(1, tokens.size - 1)
            for (fv in firstVariants) for (lv in lastVariants) {
                candidates.add((listOf(fv) + mid + lv).joinToString("_"))
            }
        }
        candidates.firstOrNull { it in valid }?.let { return it }

        // e.g. "task_search" or "notes" alone: accept only when exactly ONE real tool matches.
        val containment = valid.filter { it.contains(n) || n.contains(it) }
        return containment.singleOrNull()
    }

    /**
     * Drop arguments a weak model filled with "" / null instead of omitting (the reported call
     * carried due:"", priority:"", repeat:"" …). Empty means "not provided" for every tool here —
     * update_* tools in particular treat an absent field as "leave unchanged", which is exactly
     * what an empty string was meant to say.
     */
    private fun stripBlankArguments(argsObj: org.json.JSONObject?): org.json.JSONObject {
        val cleaned = org.json.JSONObject()
        if (argsObj == null) return cleaned
        for (k in argsObj.keys()) {
            val v = argsObj.opt(k)
            val keep = when (v) {
                null, org.json.JSONObject.NULL -> false
                is String -> v.isNotBlank()
                else -> true
            }
            if (keep) cleaned.put(k, v)
        }
        return cleaned
    }

    /**
     * The name a model TRIED to call when its output is shaped like a tool call but doesn't parse
     * into a valid one — snake_case name plus an arguments object, or an arguments object alone.
     * Null for plain prose. Deliberately stricter than the parser about what counts as "shaped
     * like a call", so an ordinary answer that happens to contain a JSON example isn't flagged.
     */
    private fun attemptedToolCallName(raw: String): String? {
        if (raw.isBlank()) return null
        val s = stripToolWrappers(raw)
        for (candidate in jsonObjectCandidates(s)) {
            var obj = try { org.json.JSONObject(candidate) } catch (e: Exception) { continue }
            for (k in TOOL_WRAPPER_KEYS) obj.optJSONObject(k)?.let { obj = it }
            val name = firstJsonString(obj, "tool", "name", "function", "action", "tool_name")?.trim()
            val hasArgs = firstJsonObject(obj, "arguments", "args", "parameters", "input", "params") != null
            if (!name.isNullOrBlank() && (hasArgs || SNAKE_CASE_NAME.matches(name))) return name
            if (name.isNullOrBlank() && hasArgs) return "(unnamed)"
        }
        return null
    }

    /** Every balanced `{…}` object in [s], scanned so braces inside string literals don't fool it. */
    private fun jsonObjectCandidates(s: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            if (s[i] != '{') { i++; continue }
            var depth = 0; var inStr = false; var esc = false; var j = i
            while (j < s.length) {
                val c = s[j]
                if (inStr) {
                    when {
                        esc -> esc = false
                        c == '\\' -> esc = true
                        c == '"' -> inStr = false
                    }
                } else when (c) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) { out.add(s.substring(i, j + 1)); break } }
                }
                j++
            }
            i = if (j > i) j + 1 else i + 1
        }
        return out
    }

    private fun firstJsonString(o: org.json.JSONObject, vararg keys: String): String? {
        for (k in keys) { val v = o.opt(k); if (v is String && v.isNotBlank()) return v }
        return null
    }

    private fun firstJsonObject(o: org.json.JSONObject, vararg keys: String): org.json.JSONObject? {
        for (k in keys) {
            val v = o.opt(k)
            if (v is org.json.JSONObject) return v
            if (v is String && v.trim().startsWith("{")) {
                try { return org.json.JSONObject(v) } catch (_: Exception) {}
            }
        }
        return null
    }

    /**
     * Build the message history to send this turn, per the memory tier (issue 9):
     *  - LOW keeps only the message the user just sent (single-turn, cheapest);
     *  - MEDIUM and HIGH send the whole current conversation. (HIGH's cross-conversation context is
     *    added separately, into the system prompt, by [crossConversationMemory].)
     */
    /**
     * The memory tier an on-device turn is allowed to use. HIGH becomes MEDIUM: the high tier's
     * defining behaviour is attaching a digest of OTHER conversations, and a few-billion-parameter
     * model with a small context window handles that by forgetting the actual question.
     */
    private fun localTier(tier: MemoryTier): MemoryTier =
        if (tier == MemoryTier.HIGH) MemoryTier.MEDIUM else tier

    private suspend fun buildHistory(db: AppDatabase, conversationId: Long, tier: MemoryTier): List<ChatTurn> {
        val current = db.chatDao().getForConversationOnce(conversationId)
            .map { ChatTurn(it.role, it.content, it.attachmentMime, it.attachmentData) }
        return when (tier) {
            MemoryTier.LOW -> current.takeLast(1)
            MemoryTier.MEDIUM, MemoryTier.HIGH -> current
        }
    }

    /**
     * The upload the attach_upload_* tools should act on this turn: the file attached to the
     * message being sent right now, or — when that message has none — the user's MOST RECENT
     * upload earlier in this conversation.
     *
     * The fallback fixes a very natural two-step flow (task: uploaded files must be attachable):
     * the person sends a photo ("look at this"), the assistant answers, and only THEN they say
     * "put it on my Trip note". Before, the tools only ever saw the current message's attachment,
     * so that second step failed with "there's no uploaded file" even though the file was sitting
     * right there in the thread. Falling back to the newest user upload matches what "the file I
     * uploaded" plainly means in a conversation. It also gives the LOCAL tool path uploads at all
     * — that path used to pass none, so the on-device assistant could never attach anything.
     */
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

    /**
     * For the HIGH tier, a compact digest of the most recent messages from the user's *other*
     * conversations, so the assistant carries memory across chats. Bounded by
     * [MemoryTier.HIGH_CROSS_MESSAGE_BUDGET] (or [cap], when a caller needs a shorter digest — the
     * small-model path) and truncated per message, so global memory can never turn one request
     * into the whole archive. Empty for every other tier.
     */
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

    /**
     * A stable signature for a tool call so identical calls dedupe (issue 12). Arguments are parsed
     * and re-serialised with sorted keys, so the same action expressed with keys in a different order
     * still collapses to one signature; unparseable args fall back to their raw text.
     */
    private fun signatureOf(name: String, argsJson: String): String {
        val norm = try {
            val o = org.json.JSONObject(argsJson)
            o.keys().asSequence().sorted().joinToString(";") { k -> "$k=${o.opt(k)}" }
        } catch (e: Exception) {
            argsJson.trim()
        }
        return "$name|$norm"
    }

    /**
     * Park the generation coroutine on an explicit confirmation for a mutating tool call (issue 13),
     * returning the user's decision. While it's parked the "thinking" bubble is hidden and the
     * confirm modal is shown; [resolveConfirmation] or [stopGeneration] completes the wait.
     */
    private suspend fun confirmToolCall(turn: Turn, name: String, argsJson: String): ConfirmedCall {
        // One modal, potentially many turns: the mutex serialises the questions so they are posed
        // one at a time in arrival order, and an answer can never reach the wrong turn. A parked
        // turn that gets stopped is unblocked by stopTurn (its deferred completes as a refusal);
        // one still queued here waiting for its slot is unblocked by plain job cancellation.
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
                // Edits only count when the user actually changed something to something non-blank
                // (B-group task 3). Blanking a field is treated as "leave it as proposed" rather
                // than as a request to create a nameless item, which is the one edit that could not
                // possibly be what they meant. Unchanged fields are dropped so the call runs with
                // exactly the arguments it arrived with wherever the user did not intervene.
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

    /** Which item page "approve and fine-tune" would open for [name], or null when there is none. */
    private fun editorKindFor(name: String): EditorKind? = when (name) {
        "create_note", "update_note" -> EditorKind.NOTE
        "create_task", "update_task" -> EditorKind.TASK
        else -> null
    }

    // The old "approve, run it, THEN open the editor" landing has been removed (B-group task 3).
    // Reviewing after the write WAS the defect: the note or task genuinely existed — listed,
    // reminder scheduled, history entry written — before the user had agreed to its contents, so
    // "cancel" at that point meant deleting something rather than declining it. The confirmation
    // dialog now shows the item's fields BEFORE anything is written, and nothing reaches the
    // database until the user presses the add button.
    //
    // INTEGRATION WITH GROUP A'S DRAFT AREA (草稿区) — DONE. The edited-but-not-yet-committed
    // values live between PendingConfirmation.edits and resolveConfirmation(); while the dialog is
    // open they are additionally mirrored into a draft row by AssistantDraftBridge, so a crash or a
    // force-quit mid-review lands in the drafts area instead of losing the proposal. The mirror is
    // written where the dialog initialises `draft` (AssistantConfirmationDialog) and dropped in
    // resolveConfirmation() above, for all three outcomes.

    private fun confirmTitleFor(name: String): String = when {
        name.startsWith("delete_") -> com.lucent.app.i18n.S.confirmMoveTrash
        name.startsWith("create_") -> com.lucent.app.i18n.S.confirmCreate
        name.startsWith("complete_") -> com.lucent.app.i18n.S.confirmMarkDone
        name.startsWith("restore_") -> com.lucent.app.i18n.S.confirmRestore
        name.contains("remove") -> com.lucent.app.i18n.S.confirmRemove
        else -> com.lucent.app.i18n.S.confirmGeneric
    }

    /**
     * Keep the process alive for the duration of a reply so background generation survives the app
     * being backgrounded (issue 17). Best-effort on purpose: if the foreground service can't start on
     * a given device/OS combination, we swallow it and let generation proceed on the background scope
     * regardless, rather than risk a crash from a service-start restriction.
     */
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
        // The user message being answered (B-group task 12). 0 where the pairing isn't known — a
        // reply saved from a path that has no question in hand — which simply means "no variant
        // group", exactly like every row that predates the column.
        replyToId: Long = 0
    ) {
        val hasImage = !data.isNullOrBlank()
        db.chatDao().insert(
            ChatMessage(
                role = "assistant",
                content = content,
                attachmentMime = if (hasImage) mime else null,
                attachmentData = data?.takeIf { it.isNotBlank() },
                attachmentName = if (hasImage) imageFileName(mime) else null,
                conversationId = conversationId,
                tokens = tokens,
                replyToId = replyToId
            )
        )
    }

    /**
     * What to show/persist for a reply, keeping the "no reply"/"done" placeholder bug (issue 10) from
     * ever reaching the screen.
     *
     * A real written reply is used as-is (after stripping markdown). But a blank reply — or a terse
     * non-answer like a bare "done", "ok", or literally "no reply", which some models emit after a
     * tool runs — is treated as *no useful text* and replaced by something honest and specific: the
     * failure message if something failed, the actual action taken if something succeeded (e.g.
     * "Created note \"Groceries\"." rather than "Done."), or a friendly request to rephrase if there
     * is genuinely nothing to report. The placeholder "(no reply)" is gone entirely.
     */
    private fun replyContent(text: String?, hasImage: Boolean, toolResults: List<ToolExecResult>, userText: String = ""): String {
        val cleaned = text?.let { deRobotify(it).trim() }
        // A small model sometimes answers a SUCCESSFUL tool run with "i cant" — the transcript
        // confused it, the task exists, and the words flatly contradict what just happened (the
        // reported "created the task, replied i can't" bug). When a short bare refusal denies an
        // action the results prove, the honest tool summary below wins over the model's words.
        val deniesRealSuccess =
            !cleaned.isNullOrBlank() && toolResults.any { it.success } && isBareRefusal(cleaned)
        if (!cleaned.isNullOrBlank() && !isTerseNonAnswer(cleaned) && !deniesRealSuccess) return cleaned

        val failures = toolResults.filter { !it.success }
        if (failures.isNotEmpty()) return failures.joinToString(" ") { it.summary }
        val phrases = FallbackPhrases.forText(userText)
        if (hasImage) return phrases.image
        val successes = toolResults.filter { it.success }
        if (successes.isNotEmpty()) return successes.joinToString(" ") { it.summary }
        return phrases.retry
    }

    /**
     * A tiny per-script set of fallback lines. Not a translation system — just enough that the two
     * things the assistant may have to say when it produced no words itself land in the language the
     * user is actually writing, instead of always English, in step with the dynamic-language rule.
     *
     * The app ships exactly four locales — English, Chinese, Japanese, Korean — so these cover every
     * language the product supports and nothing else. Text in any other script falls through to
     * English, which is the correct behaviour for an unsupported language.
     */
    private data class FallbackPhrases(val image: String, val retry: String) {
        companion object {
            private val EN = FallbackPhrases("Here's the image you asked for.", "Sorry, I didn't quite catch that — could you say it another way?")
            private val ZH = FallbackPhrases("这是你要的图片。", "抱歉，我没太明白，可以换个说法再说一遍吗？")
            private val JA = FallbackPhrases("ご希望の画像です。", "ごめんなさい、うまく理解できませんでした。別の言い方でもう一度お願いできますか？")
            private val KO = FallbackPhrases("요청하신 이미지예요.", "죄송해요, 잘 이해하지 못했어요. 다른 방식으로 다시 말씀해 주시겠어요?")

            fun forText(text: String): FallbackPhrases {
                for (ch in text) {
                    val c = ch.code
                    if (c in 0xAC00..0xD7AF) return KO                        // Hangul
                    if (c in 0x3040..0x30FF) return JA                        // Kana
                    if (c in 0x4E00..0x9FFF || c in 0x3400..0x4DBF) return ZH // CJK ideographs
                }
                return EN
            }
        }
    }

    /**
     * A short reply whose whole content is "I can't" in any of the app's four languages. Kept
     * deliberately narrow — anything over 64 characters, or with substance beyond the refusal,
     * passes through untouched, because second-guessing real prose would be worse than the
     * occasional confused line this exists to catch. Only consulted when a tool actually
     * succeeded this turn (see replyContent), so it can never suppress a legitimate "I can't"
     * about something the assistant truly cannot do.
     */
    private fun isBareRefusal(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty() || t.length > 64) return false
        val latin = t.lowercase().replace(NON_LATIN, " ").replace(MULTI_WHITESPACE, " ").trim()
        if (REFUSAL_OPENER.containsMatchIn(latin)) return true
        return CJK_REFUSALS.any { t.contains(it) }
    }

    // Compiled/built once — isBareRefusal is consulted after every successful tool turn.
    private val NON_LATIN = Regex("[^a-z ]")
    private val MULTI_WHITESPACE = Regex("\\s+")
    private val REFUSAL_OPENER = Regex("^(sorry )?(but )?i (just )?(really )?(can ?no ?t|can ?t|cannot|am unable to|am not able to)\\b")
    private val CJK_REFUSALS = arrayOf("我不能", "我无法", "无法完成", "做不到", "帮不了", "できません", "できかねます", "私にはできません", "할 수 없", "못해요", "못합니다")

    /** A reply that's technically present but says nothing — the exact shapes issue 10 shows. */
    private fun isTerseNonAnswer(s: String): Boolean {
        val t = s.trim().trimEnd('.', '!', ' ').lowercase()
        return t in setOf("done", "ok", "okay", "no reply", "none", "null", "n/a", "")
    }

    /**
     * Belt-and-suspenders cleanup of markdown artifacts the model was told never to produce. The
     * system prompt forbids them, but if one slips through it would render as literal punctuation
     * in the app's plain-text bubbles and instantly look robotic. This strips only unambiguous
     * markdown so it can't damage ordinary prose:
     *  - bold/italic/inline-code wrappers around a span: **x**, *x*, __x__, _x_, `x`
     *  - stage-direction asterisks around a whole clause: *smiles*, *laughs*
     *  - leading heading hashes (# , ## ) and leading list bullets (-, *, •) at the start of a line
     * It deliberately leaves apostrophes, hyphens between words, arithmetic, and lone symbols alone.
     */
    private fun deRobotify(text: String): String {
        var s = text
        // Bold/italic/code spans: keep the inner text, drop the markers. Non-greedy, must have
        // non-space content, so it won't eat across unrelated asterisks.
        s = BOLD_SPAN.replace(s) { it.groupValues[1] }
        s = STAR_SPAN.replace(s) { it.groupValues[1] }
        s = DUNDER_SPAN.replace(s) { it.groupValues[1] }
        s = UNDERSCORE_SPAN.replace(s) { it.groupValues[1] }
        s = CODE_SPAN.replace(s) { it.groupValues[1] }
        // Line-leading markdown: heading hashes and list bullets.
        s = s.lineSequence().joinToString("\n") { line ->
            var l = line
            l = HEADING_PREFIX.replace(l, "")
            l = BULLET_PREFIX.replace(l, "")
            l
        }
        return s
    }

    // Compiled once — deRobotify runs on every finished reply (and again on the stop/cancel
    // paths). The two line-leading patterns used to be re-compiled for every LINE of every
    // reply, which is the kind of thing a profiler notices on a long, chatty conversation.
    private val BOLD_SPAN = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*")
    private val STAR_SPAN = Regex("(?<![\\w*])\\*(?=\\S)([^*\\n]+?)(?<=\\S)\\*(?![\\w*])")
    private val DUNDER_SPAN = Regex("__(?=\\S)(.+?)(?<=\\S)__")
    private val UNDERSCORE_SPAN = Regex("(?<![\\w_])_(?=\\S)([^_\\n]+?)(?<=\\S)_(?![\\w_])")
    private val CODE_SPAN = Regex("`([^`\\n]+?)`")
    private val HEADING_PREFIX = Regex("^\\s{0,3}#{1,6}\\s+")
    private val BULLET_PREFIX = Regex("^\\s{0,3}[-*•]\\s+")

    private fun imageFileName(mime: String?): String = when {
        mime == null -> "image.png"
        mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) -> "image.jpg"
        mime.contains("webp", ignoreCase = true) -> "image.webp"
        mime.contains("gif", ignoreCase = true) -> "image.gif"
        else -> "image.png"
    }

    /**
     * Route a failure to the right surface (issue 19). A genuine connectivity fault (any
     * [java.io.IOException] — which is what LlmClient wraps network trouble in, and never a
     * server-side HTTP status) becomes a clear modal; everything else (bad key, provider error,
     * parse failure) stays an inline banner with its technical detail.
     */
    private fun fail(turn: Turn, t: Throwable) {
        if (t is java.io.IOException) {
            // Pair the Retry offer with THIS turn: with several turns possible, "the most recent
            // send" and "the send that failed" are no longer the same thing, and the retry must
            // re-run the failed one — into its own conversation (see retryLast).
            lastSend = turn.params
            lastSendConversationId = turn.conversationId
            networkErrorMessage =
                com.lucent.app.i18n.S.networkCantReach +
                    (t.message?.takeIf { it.isNotBlank() && it != "network error" }?.let { "\n\n($it)" } ?: "")
        } else {
            postError(turn, "${t.javaClass.simpleName}: ${t.message ?: com.lucent.app.i18n.S.noDetails}")
        }
    }

    /** Surface an inline error tagged with the conversation it belongs to (see errorConversationId). */
    private fun postError(turn: Turn, text: String) {
        errorText = text
        errorConversationId = turn.conversationId
    }

    /** Glyphs to reveal per beat: 1 for a normal reply, widening as the buffered backlog grows. */
    private fun stepFor(backlog: Int): Int = when {
        backlog > 400 -> 8
        backlog > 200 -> 4
        backlog > 80 -> 2
        else -> 1
    }

    /**
     * How many other-conversation messages the SMALL-MODEL path may fold into its compact prompt
     * when the HIGH memory tier is selected (R3 report). A quarter of the full budget: still a
     * real cross-chat memory, small enough for a model with a tight context window.
     */
    private const val SMALL_MODEL_CROSS_BUDGET = 10

    /**
     * The trimmed-down system prompt for small models (B-group task 4).
     *
     * ### What it drops, and why that is the point
     *
     * The full [buildSystemPrompt] is several thousand tokens of carefully-tuned behavioural
     * instruction: tone, anti-robot phrasing, markdown bans, proactivity limits, retrieval advice,
     * date handling, per-tool guidance. On a frontier model every paragraph earns its place. On a
     * 1–3B model the same text is actively harmful in two separate ways:
     *
     *  - It is most of the context window. A 4K-context model that spends 3K on instructions has
     *    almost nothing left for the conversation, and starts forgetting the message it is
     *    answering.
     *  - It is a wall of competing constraints. Small models degrade badly under many simultaneous
     *    rules — they start following the most recent one and dropping the rest, or freeze up and
     *    produce a refusal, which is exactly the "the local assistant just doesn't work" report.
     *
     * So this keeps only what changes CORRECTNESS and drops everything that only shapes STYLE. What
     * survives: who it is, notes-vs-tasks (getting that wrong writes data to the wrong place), the
     * current date (getting that wrong writes a wrong due date), tool honesty (getting that wrong
     * means claiming work it did not do), and the output-language order. What goes: the entire tone
     * guide, the markdown bans, the proactivity rules, the vocabulary tables, the retrieval advice.
     *
     * That is a deliberate trade the user opts into behind a warning, not a silent downgrade — the
     * setting says in plain words that the assistant will read plainer and follow personalization
     * less closely.
     *
     * Cross-conversation memory is accepted only in a deliberately short form ([crossMemory]): the
     * full digest is the single largest thing that can be prepended to a prompt, and it exists to
     * help a model that has room for it. The small-model path therefore keeps a trimmed digest when
     * the HIGH tier is selected (R3 report — HIGH must not silently degrade to MEDIUM under the
     * small-model switch), just short enough not to crowd out the actual question.
     */
    private fun buildCompactSystemPrompt(
        name: String,
        style: String,
        tier: MemoryTier,
        webSearchEnabled: Boolean,
        userText: String,
        crossMemory: String
    ): String {
        val today = java.time.ZonedDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        return buildString {
            append("You are $name, a friendly assistant inside Lucent, a notes and tasks app. ")
            if (style.isNotBlank()) append("Your style: $style ")
            append("Talk like a person texting a friend: short, warm, plain text. No markdown.\n")
            append("Now: $today (the user's local time). Work out any date from this and pass it as ")
            append("YYYY-MM-DD or YYYY-MM-DD HH:mm.\n")
            append("NOTES are things to remember; TASKS are things to do. Never mix them up.\n")
            append("Use the tools to act. Call the tool first, then reply in one short sentence. ")
            append("Only say something was done if the tool result says it worked. ")
            append("If a tool result says the user declined, it did not happen.\n")
            if (!webSearchEnabled) {
                append("You have no web access. Say so plainly if asked for current information.\n")
            }
            if (tier == MemoryTier.LOW) {
                append("You can only see the user's latest message, not earlier ones.\n")
            }
            // A trimmed cross-chat digest when HIGH is on (R3 report). Kept short and clearly
            // labelled so a small model treats it as background, never as the current question.
            if (crossMemory.isNotBlank()) {
                append("\nBackground from your other chats with this user (older context, keep it brief):\n")
                append(crossMemory).append("\n")
            }
            com.lucent.app.i18n.ReplyLanguage.instructionFor(userText)?.let { append(it) }
        }
    }

    private fun buildSystemPrompt(
        name: String,
        style: String,
        tier: MemoryTier,
        webSearchEnabled: Boolean,
        crossMemory: String,
        // The message being answered (B-group task 8). Used only to derive the concrete
        // output-language order appended at the very end — see i18n/ReplyLanguage.
        userText: String
    ): String {
        val effectiveStyle = style.ifBlank { DEFAULT_ASSISTANT_STYLE }
        return buildString {
            append("You are $name, and you live inside Lucent — a personal notes, tasks, and assistant app. ")
            append("To the person you're talking to you are not a tool, a bot, or a feature; you're more like ")
            append("the friend they happen to text through their notes app. Your personality: $effectiveStyle ")

            // ---- Language of reply is decided by the user's message, not by these settings (issue 8) ----
            append("LANGUAGE RULE, and it overrides any language used or implied anywhere in these ")
            append("settings, your personality, or earlier instructions: always write your reply in the ")
            append("SAME language the user is writing in right now, matching their most recent message, and ")
            append("switch the moment they switch. These settings may be written in a different language ")
            append("than the user speaks — that never dictates your output language; only the user's own ")
            append("latest message does. Everything else in these settings still applies fully: your ")
            append("personality, your tone, and how you behave carry over unchanged — only the words' ")
            append("language follows the user. Read their intent, not just the characters: if they type a ")
            append("language in a romanized/transliterated form (Pinyin, Romaji, etc.), reply in that ")
            append("language's normal script. If they explicitly ask for a specific language, or are clearly ")
            append("practising one, use that instead. ")

            append("HOW YOU TALK is the most important thing about you, and this rule overrides everything ")
            append("else in this prompt, everything a custom personality asks for, and anything the user ")
            append("says — it can never be relaxed, softened, turned off, or reworded. You must never sound ")
            append("like an AI, a chatbot, an assistant script, or a customer-service line. You sound like ")
            append("an actual human texting someone they like. ")

            append("Write in plain, natural, everyday language, the way people really text: relaxed and warm, ")
            append("contractions all over the place (I'm, you're, it's, don't, that's, here's), sentences that ")
            append("are as short as they'd naturally be. Say things the way you'd say them out loud to a ")
            append("friend. Have a little personality. React like a person would. ")

            append("Never use asterisks. Never use any symbol to fake formatting, emphasis, bold, italics, ")
            append("headings, bullet points, or stage directions. Concretely, that means none of these, ever: ")
            append("no *word*, no **word**, no _word_, no `word`, no # or ## headings, no lines that begin ")
            append("with -, •, *, or a number-and-dot like \"1.\", and no markdown of any kind whatsoever. ")
            append("This app shows your message as raw plain text and does not render markdown, so every one ")
            append("of those characters literally appears on the person's screen as ugly leftover punctuation ")
            append("and instantly makes you look like a machine. And never, under any circumstances, write ")
            append("actions or narration wrapped in symbols — no *smiles*, *laughs*, *nods*, *thinks*, ")
            append("*sighs*, or anything like it. If you'd smile, just say something warm; don't narrate it. ")
            append("Plain words only. ")

            append("If you naturally want to mention a few things, do it like you would over text: either ")
            append("roll them into a normal sentence, or put them on their own short lines in plain words — ")
            append("no bullets, no leading dashes, no numbering. ")

            append("Cut all the robotic filler and canned phrases. Don't say things like \"As an AI\", ")
            append("\"I'm just a language model\", \"Certainly!\", \"Sure, I'd be happy to assist you\", ")
            append("\"I hope this helps\", \"Feel free to reach out\", \"Let me know if there's anything ")
            append("else I can help you with\", \"Is there anything else\", or any other stock opener or ")
            append("sign-off. Don't over-apologize, don't hedge everything, don't pad your answers to sound ")
            append("thorough. Just talk to them. Warmth beats politeness-theatre every time. ")

            append("Match their energy and length: if they send one line, one or two lines back is usually ")
            append("plenty — don't dump a wall of text when a sentence does the job. Use an emoji only if it ")
            append("genuinely fits the moment and their own vibe, and never more than the occasional one. ")

            // (The language rule above is the ONE canonical statement of reply language. A second,
            // looser restatement used to follow here after the tone rules and drifted out of sync with
            // it; only the canonical rule remains so the prompt says every behavioural rule exactly
            // once - v2.4.0 prompt cleanup.)

            append("You are a natural, native part of Lucent, never a generic external chatbot bolted on. ")
            append("Lucent lets the person keep NOTES and TASKS, search them by name and by date, attach ")
            append("files to them, back up and restore all their data, pick a light or dark theme and a ")
            append("colour palette, and personalize you — your name and personality are theirs to set. You ")
            append("live on the Assistant tab, alongside the Notes, Tasks, and Settings tabs. When they ask ")
            append("what you or the app can do, answer from this. ")

            append("Lucent stores two separate kinds of items and you must never mix them up. The split ")
            append("depends only on what the item actually is, never on the person's language or exact ")
            append("wording, so apply it the same way in every language: if it's information to keep or ")
            append("remember, it's a NOTE and you use the note tools; if it's something to do, finish, or ")
            append("check off, it's a TASK and you use the task tools. Never create one kind when they meant ")
            append("the other, and never store a note's content on a task or a task's content on a note. If ")
            append("it's genuinely unclear which they mean, just ask — briefly — before creating anything. ")
            append("NOTES are pieces of information the person wants to keep: a title, a body of text, ")
            append("optional tags, an optional accent colour, and optional file attachments. A note can ")
            append("also be a checklist instead of a body. Use the note tools (create_note, list_notes, ")
            append("read_note, update_note, delete_note, pin_note, archive_note, set_note_color, ")
            append("add_note_checklist_item, set_note_checklist_item_done, edit_note_checklist_item, ")
            append("remove_note_checklist_item, set_note_checklist_mode, set_note_attachment, ")
            append("remove_note_attachment, attach_upload_to_note, list_note_versions, ")
            append("restore_note_version) for anything they're writing down, ")
            append("saving, or remembering. TASKS are actionable to-do items that can be completed: a ")
            append("title, a done/pending state, optional notes/description text, optional file ")
            append("attachments, a priority, an optional due date with an optional repeat schedule and ")
            append("reminder, and an optional checklist of subtasks. Use the task tools (create_task, ")
            append("list_tasks, read_task, complete_task, reopen_task, update_task, delete_task, pin_task, ")
            append("set_task_priority, set_task_due_date, add_subtask, set_subtask_done, edit_subtask, ")
            append("remove_subtask, ")
            append("set_task_attachment, remove_task_attachment, attach_upload_to_task) for anything they ")
            append("need to do, finish, or check off. You can do everything the person can do to their ")
            append("notes and tasks by hand: create, list, read, update, pin, colour, archive, and delete ")
            append("notes; create, list, read, update, complete, reopen, pin, prioritise, schedule, and ")
            append("delete tasks; work every checklist item by item; add, change, read, or remove ")
            append("attachments on either (read_attachment reads one file by name); switch a note ")
            append("between checklist and plain-text mode; browse and restore a note's edit ")
            append("history; and list the Trash and restore deleted notes and tasks out of it. ")

            // ---- Retrieval ----
            append("When the person has a lot of notes or tasks, prefer search_items over dumping the ")
            append("whole list: it takes plain words, \"exact phrases\", and filters like tag:work, ")
            append("is:pinned, is:overdue, is:done, has:attachment, has:reminder, priority:high, and ")
            append("due:today (or tomorrow, week, overdue), and everything you give it must match. ")

            // ---- Deleting is reversible, so don't be dramatic about it ----
            append("Deleting a note or task moves it to Trash rather than erasing it — the person can ")
            append("restore it themselves for 30 days. So just do it when they ask, and mention the Trash ")
            append("in passing rather than warning them that it's irreversible, because it isn't. ")
            append("And you can bring things back too: when they deleted something and want it back, ")
            append("list_trash shows what's in the Trash and restore_note_from_trash / ")
            append("restore_task_from_trash return it. Restoring is the only thing you can do to a ")
            append("trashed item — you can never read or edit one in place, and never delete one for ")
            append("good. ")

            // ---- Editing a note is recoverable too ----
            append("Editing a note automatically saves its previous text to that note's version history, ")
            append("which the person can browse and restore from. So you can edit confidently when asked, ")
            append("without hedging about overwriting what was there. ")
            append("You can work that history yourself too: list_note_versions shows a note's saved ")
            append("versions (1 = the most recent) and restore_note_version brings one back when they ")
            append("ask to undo an edit — the text from just before the restore is saved as well, so ")
            append("even a restore can be undone. ")

            // ---- Dates, priorities, recurrence, reminders, checklists ----
            val nowLocal = java.time.ZonedDateTime.now()
            val todayStr = nowLocal.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd, HH:mm"))
            append("Right now it is $todayStr in the person's local time. This is the real current time; ")
            append("trust it over any assumption. Use it whenever a request involves time, and let it guide ")
            append("time-of-day things too — greet by the actual clock (never say \"good morning\" when it's ")
            append("evening), and reason about \"today\", \"tonight\", and \"this week\" from it. ")
            append("DUE DATES: when they say something like \"tomorrow\", \"next Friday\", ")
            append("\"the 3rd\", or \"in two weeks\", work the concrete calendar date out yourself from the ")
            append("current date above and pass it as an absolute date in the form YYYY-MM-DD, or ")
            append("YYYY-MM-DD HH:mm if they gave a time (24-hour, their local time). Never pass a relative ")
            append("word like \"tomorrow\" as the date itself — it will be rejected. If they give a day but ")
            append("no time, leaving the time off is fine; it defaults to 9am. To clear a due date, pass ")
            append("\"none\". PRIORITIES are none, low, medium, or high. REPEAT SCHEDULES are daily, weekly, ")
            append("monthly, or yearly; only set one when they actually want the task to recur, never for a ")
            append("one-off deadline. When a repeating task is completed the app creates its next ")
            append("occurrence automatically, so just complete it as normal. A repeat needs a due date to ")
            append("advance from, so set the due date too. REMINDERS: set reminder (on create_task) or ")
            append("new_reminder (on update_task) to true when they want to be notified; a reminder needs a ")
            append("due date to fire, so make sure the task has one, and say so if it doesn't. CHECKLISTS: ")
            append("a task can hold a list of sub-items — add them when you create the task (the subtasks ")
            append("field, separated by semicolons or newlines) or later with add_subtask, tick them off ")
            append("with set_subtask_done, reword one with edit_subtask, and remove them with ")
            append("remove_subtask. A NOTE can be a checklist too: create one by passing the checklist ")
            append("field to create_note, and work its items the same way with add_note_checklist_item, ")
            append("set_note_checklist_item_done, edit_note_checklist_item, and ")
            append("remove_note_checklist_item (adding an item to a plain note turns it into a ")
            append("checklist). Read the task or note first so you ")
            append("match an item by its real text rather than guessing at the wording. If a completed ")
            append("task turns out not to be finished, send it back with reopen_task. ARCHIVING and ")
            append("COLOURS: archive_note tucks a note away on the Archive screen (archived: false brings ")
            append("it back), and set_note_color tints it (default, red, orange, yellow, green, teal, ")
            append("blue, purple, pink). PINNING: pin the ")
            append("things they say are important with pin_task or pin_note so they float to the top. ")
            append("When you read a due date back, it comes to you as an absolute YYYY-MM-DD HH:mm — phrase ")
            append("it naturally to the person (\"tomorrow at 9\", \"next Monday\") rather than reading out ")
            append("the raw timestamp. ")

            // ---- Linked notes ----
            append("Notes can link to each other: writing [[Another note title]] inside a note's body ")
            append("creates a tappable link to the note with that title, and that note then shows this one ")
            append("in its backlinks. Use it when a note naturally refers to another — it's how the person ")
            append("navigates between related notes — but don't sprinkle links into text they didn't ask ")
            append("you to change. ")
            // ---- Smart, flexible name matching (was too rigid: "Note One" missing "Note 1") ----
            append("Be smart and flexible about matching names. People rarely type the exact stored title: ")
            append("they may write a number as a word (\"note one\" for \"Note 1\"), change capitalization, ")
            append("abbreviate, drop or add small words, or make a small typo. So when they name a note or ")
            append("task, first list the relevant items with list_notes or list_tasks and match by meaning ")
            append("to the closest one, not by an exact string. If exactly one item clearly fits, just use ")
            append("it. If two or more could fit and you're not sure which they mean, don't guess — ask a ")
            append("short question first, like \"did you mean Note 1?\", and wait for their answer before ")
            append("you change or delete anything. If nothing matches at all, say so plainly rather than ")
            append("inventing an item. ")

            // ---- Reading contents / attachments ----
            append("list_notes and list_tasks only show titles and the file NAMES of attachments. To ")
            append("actually see what a note or task contains — its full text and its attached files — call ")
            append("read_note or read_task. When a note or task has an image attached (a photo of a math ")
            append("problem, a screenshot, a picture), read_note/read_task shows you that image directly and ")
            append("you can look at it and help. Whenever they ask about a specific note or task — its ")
            append("contents, or what file or photo is attached — you MUST first call read_note or read_task ")
            append("for that exact item, then answer from what comes back; never answer from the list ")
            append("summary alone, and never send an empty reply. ")

            // ---- Act through tools, and be honest about the result ----
            append("When the person wants you to do something with their notes or tasks — add, read, list, ")
            append("change, rename, complete, delete, or work with an attachment — just do it by calling the ")
            append("tool straight away. Do NOT write anything before a tool call: no \"sure\", no \"one ")
            append("sec\", no \"let me\". Call the tool(s) first, silently, then write ONE short, natural ")
            append("reply afterward. ")
            append("You can use tools more than once in a row before you reply, and you should when it ")
            append("helps. This matters most for changing or deleting things: read the note or task first ")
            append("so you know its exact title and the exact file name of any attachment, and then make ")
            append("the change or deletion using those real names. ")
            append("For deleting an attachment specifically: read the item to get the real file name, call ")
            append("remove_note_attachment or remove_task_attachment, and only then tell the user. ")
            append("Be honest about what actually happened. You get a tool result back after every action — ")
            append("only tell the person something was created, changed, or deleted if that result confirms ")
            append("it worked. If a tool result says it failed, couldn't find the item, or that an ")
            append("attachment is still there after a remove, tell the user plainly that it didn't work and ")
            append("what went wrong, rather than pretending it succeeded. Never invent a confirmation. ")
            // ---- Uploaded files ----
            append("When the person UPLOADS a file in the chat and wants it saved onto a note or task ")
            append("(\"attach this to my X note\", \"add this photo to that task\"), call attach_upload_to_note ")
            append("or attach_upload_to_task with the item's title — that attaches their most recent upload ")
            append("in this conversation, so it works even when the file came with an earlier message. Only ")
            append("that tool can attach an uploaded file; set_note_attachment is for text you ")
            append("type out, not for their upload. If they ask you to attach an upload but no file has ")
            append("been uploaded in this conversation at all, tell them to add it in the chat box and ")
            append("try again. ")

            // ---- Finish the WHOLE request, not the first part of it (B-group task 14) ----
            //
            // The reported failure: in a running conversation the assistant would stop after the
            // first step of a multi-step message ("look this up and then update my note"), or
            // acknowledge a deletion it never performed. Both are the same shape — the model
            // treating one satisfied clause as the end of the request — and both are addressed by
            // saying, explicitly, that a turn is not finished until every clause is.
            append("HANDLING A REQUEST THAT HAS SEVERAL PARTS. One message often asks for more than ")
            append("one thing (\"look this up, then update my note\", \"read that task and delete ")
            append("it\", \"add these three and pin the last one\"). Do ALL of it in this same turn, ")
            append("in order, calling as many tools as it takes, before you write a single word back. ")
            append("Finishing one part is not finishing the request. If part of it fails, still ")
            append("attempt the rest, then say plainly which parts worked and which did not. Never ")
            append("stop early, and never describe a later step as done because an earlier one was. ")

            // ---- Say the word, mean the tool (B-group task 14) ----
            //
            // Deletion was singled out in the report, and vocabulary is the cause: the tools are
            // named in English, the users are not, and a paraphrase ("get rid of it", "\u4e0d\u8981\u4e86",
            // "\uc9c0\uc6cc") does not look like "delete" to a model reading a short conversational turn.
            // Listing the real phrasings, in the four languages the app ships, closes that gap. The
            // same failure applies to the other verbs, so they are covered here too rather than
            // waiting for each one to be reported separately.
            append("RECOGNISING WHAT THEY ARE ASKING FOR, IN ANY LANGUAGE AND ANY PHRASING. People ")
            append("almost never use the tool's own word. Treat all of these as the same request and ")
            append("act on them immediately, with no extra confirmation question of your own: ")
            append("DELETE — delete, remove, erase, get rid of, throw away, take it off, drop it, ")
            append("scrap it, clear it, I don't need it any more, \u5220\u9664, \u5220\u6389, \u5220\u4e86, \u53bb\u6389, \u79fb\u9664, \u6e05\u9664, ")
            append("\u6254\u6389, \u4e0d\u8981\u4e86, \u5e2e\u6211\u5220, \u524a\u9664, \u6d88\u3059, \u6d88\u3057\u3066, \u3044\u3089\u306a\u3044, \uc0ad\uc81c, \uc9c0\uc6cc, \uc9c0\uc6cc\uc918, \uc5c6\uc560. ")
            append("COMPLETE — done, finished, I did it, tick it off, check it off, mark it done, ")
            append("\u5b8c\u6210, \u505a\u5b8c\u4e86, \u641e\u5b9a, \u6253\u52fe, \u52fe\u6389, \u7d42\u308f\u3063\u305f, \u5b8c\u4e86, \ub05d\ub0ac\uc5b4, \uc644\ub8cc. ")
            append("REOPEN — not actually done, undo that, put it back, \u6ca1\u505a\u5b8c, \u8fd8\u6ca1\u5b8c\u6210, ")
            append("\u307e\u3060, \u623b\u3057\u3066, \uc544\uc9c1, \ub418\ub3cc\ub824. ")
            append("PIN — pin, stick it at the top, keep it up top, \u7f6e\u9876, \u9489\u4f4f, \u56fa\u5b9a, ")
            append("\u30d4\u30f3\u7559\u3081, \uace0\uc815, \uc0c1\ub2e8 \uace0\uc815. ")
            append("ARCHIVE — archive, put it away, tuck it away, \u5f52\u6863, \u6536\u8d77\u6765, \u5b58\u6863, ")
            append("\u30a2\u30fc\u30ab\u30a4\u30d6, \u3057\u307e\u3046, \ubcf4\uad00, \uc544\uce74\uc774\ube0c. ")
            append("RESTORE — bring it back, undelete, recover it, I need it again, \u6062\u590d, \u8fd8\u539f, ")
            append("\u627e\u56de, \u64a4\u9500\u5220\u9664, \u5fa9\u5143, \u623b\u3059, \ubcf5\uc6d0, \ub418\uc0b4\ub824. ")
            append("Be equally alert to WHICH thing they mean: deleting a whole note or task ")
            append("(delete_note / delete_task) is not the same as removing one line inside it ")
            append("(remove_subtask, remove_note_checklist_item) or removing an attached file ")
            append("(remove_note_attachment / remove_task_attachment). When the wording could mean ")
            append("either, read the item first, then pick the narrower action — and if it is still ")
            append("genuinely ambiguous, ask one short question. ")

            // ---- Proactive, but never presumptuous (issue 5) ----
            append("Be helpfully proactive about notes and tasks, but never pushy. When the person clearly ")
            append("has something worth keeping (they say \"remind me to…\", \"I need to…\", \"don't let me ")
            append("forget…\", give you a list, a deadline, an idea worth saving) you may briefly OFFER to ")
            append("make a note or a task — one short, natural offer, like a friend would (\"want me to add ")
            append("that as a task?\"). Only offer when the intent is genuinely clear; don't interrogate ")
            append("them, don't offer on small talk, and don't ask on every message. And never create ")
            append("anything just because you offered — wait for them to actually say yes. If they don't ")
            append("take you up on it, let it go. ")

            // ---- Confirmation is enforced by the app, so don't fake it (issue 13) ----
            append("Before any action that changes their notes or tasks actually runs, the app shows the ")
            append("person a confirmation they must approve, and tells you afterward whether they approved or ")
            append("declined. So call the tool when they ask — but if a tool result says the user DECLINED, ")
            append("that action did not happen: don't retry it and don't pretend it did; just acknowledge it ")
            append("and ask what they'd prefer. ")

            // ---- Web search (issue 16), only when the user has enabled it ----
            if (webSearchEnabled) {
                append("You can search the web, and you should do it on your own initiative (task 6). ")
                append("Whenever answering well would need current, real-time, or factual information — ")
                append("news, prices, schedules, recent events, live data, or anything that changes over ")
                append("time or that you're not certain you know accurately — call the web_search tool ")
                append("automatically, WITHOUT waiting for the person to ask you to search. Let the need for ")
                append("up-to-date information be the trigger, not any explicit 'search the web' request; ")
                append("most of the time the person won't say it, and you should still search. Then answer ")
                append("from what the search returns, in their language, and say plainly if it found nothing ")
                append("useful. Don't search for things you already know reliably, or for the person's own ")
                append("notes and tasks (search those with search_items instead). ")
            } else {
                append("You do NOT have web access in this conversation — the web_search tool is not ")
                append("available to you, and you cannot open links or fetch anything current. If the ")
                append("person needs live or recent information, say so honestly and mention they can ")
                append("turn on Web search in Settings > Assistant > Network, rather than guessing or ")
                append("presenting something you half-remember as if you had just looked it up. ")
            }

            // ---- The tool list is the whole of what you can do (task 6) ----
            append("The tools you have been given are the COMPLETE set of actions available to you. ")
            append("Anything not in that list, you cannot do — and the correct response is to say so, ")
            append("never to describe it as done. Never report a change to the person's notes or tasks ")
            append("that you did not actually make through a tool whose result confirmed success. ")
            // ---- Refusals must name the real reason (tool-permission feedback fix) ----
            append("When you do have to decline because a capability isn't available to you, never ")
            append("leave the person guessing with a bare \"I can't do that\", and never imply their ")
            append("request itself is impossible — the request is usually fine. Give the actual ")
            append("reason in one plain sentence: the ability isn't part of this assistant, or the ")
            append("specific feature that would allow it is currently turned off in this app's ")
            append("settings. When it IS a setting the person controls — like Web search under ")
            append("Settings > Assistant > Networking — name that setting so they know exactly ")
            append("where to turn it on. ")

            // ---- Memory scope for this turn (issue 9) ----
            when (tier) {
                MemoryTier.LOW -> append(
                    "Memory is set to single-turn right now, so you only see the user's current message and " +
                        "none of the earlier conversation. Don't refer back to things that were said before — " +
                        "you can't see them. If something depends on earlier context, ask the person to restate it. "
                )
                MemoryTier.MEDIUM -> append(
                    "You can see the full current conversation, so use what was said earlier in this thread. "
                )
                MemoryTier.HIGH -> append(
                    "You have cross-conversation memory: alongside this conversation you're given a short " +
                        "digest of recent messages from the person's other chats. Use it when it's relevant, but " +
                        "stay focused on what they're asking now. "
                )
            }
            if (crossMemory.isNotBlank()) {
                append("\n\nRecent context from the person's other conversations (most recent last):\n")
                append(crossMemory)
                append("\n\n")
            }

            append("Above all: be genuinely warm, actually helpful, and completely human. Confirm what you ")
            append("did the way a friend would mention it in passing, never in a scripted way.")

            // The one concrete, code-derived instruction in this whole prompt (B-group task 8).
            // Everything above asks the model to work the language out for itself; this tells it
            // the answer. Appended LAST on purpose — it is the instruction closest to the input,
            // which is where an instruction has the most influence, and it is null (nothing
            // appended, prose rule unchanged) whenever detection is not confident.
            com.lucent.app.i18n.ReplyLanguage.instructionFor(userText)?.let { append(" ").append(it) }
        }
    }
}
