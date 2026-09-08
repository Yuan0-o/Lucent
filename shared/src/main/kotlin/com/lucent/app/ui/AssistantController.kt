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
        val cleaned = ReplyPolish.deRobotify(turn.snapshotBuffer()).trim()

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
                    if (smallModelMode) SystemPrompts.compact(name, style, tier = memoryTier, webSearchEnabled = webSearchEnabled, userText = text, crossMemory = compactCrossMemory)
                    else SystemPrompts.full(name, style, memoryTier, webSearchEnabled, crossMemory, text)
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
                        val content = ReplyPolish.replyContent(reply.text, img != null, lastToolResults, userText = text)
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
     * the model emits a single JSON object to call a tool, [LocalToolCallParser.parseLocalToolCall] extracts it, the
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
        messages.add("system" to SystemPrompts.local(tools, lastUserText, smallModelMode))
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

            val call = LocalToolCallParser.parseLocalToolCall(raw, validToolNames)
            if (call == null) {
                val attempted = LocalToolCallParser.attemptedToolCallName(raw)
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
                finalText = ReplyPolish.deRobotify(raw).trim()
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
            messages.add("assistant" to LocalToolCallParser.renderLocalToolCall(call))
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
            finalText = ReplyPolish.deRobotify(turn.snapshotBuffer()).trim()
        }

        // A model that never recovered can leave tool-JSON standing as its "answer". Raw JSON must
        // never reach the screen, so a mostly-JSON final is dropped here and replyContent below
        // falls back to its honest summary of what the tools actually did, or its friendly
        // ask-again line — both in the user's language.
        run {
            val ft = finalText
            if (ft != null && LocalToolCallParser.attemptedToolCallName(ft) != null) {
                val shape = ft.trim()
                if (shape.startsWith("{") || shape.startsWith("<tool_call") || shape.startsWith("```")) {
                    finalText = ""
                }
            }
        }

        // Honest final text: the model's own words if it wrote any, otherwise a summary of what the
        // tools actually did (or a friendly retry line in the user's language) — never a bare "done".
        val content = ReplyPolish.replyContent(finalText, hasImage = false, toolResults = toolResults, userText = lastUserText)
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

    /** How many tool rounds the on-device model may take before it is forced to answer in words. */
    private val MAX_LOCAL_TOOL_ROUNDS = 6

    

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
                attachmentName = if (hasImage) ReplyPolish.imageFileName(mime) else null,
                conversationId = conversationId,
                tokens = tokens,
                replyToId = replyToId
            )
        )
    }

    /**
     * A short reply whose whole content is "I can't" in any of the app's four languages. Kept
     * deliberately narrow — anything over 64 characters, or with substance beyond the refusal,
     * passes through untouched, because second-guessing real prose would be worse than the
     * occasional confused line this exists to catch. Only consulted when a tool actually
     * succeeded this turn (see replyContent), so it can never suppress a legitimate "I can't"
     * about something the assistant truly cannot do.
     */


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

    


}
