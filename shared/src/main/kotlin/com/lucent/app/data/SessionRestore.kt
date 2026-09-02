package com.lucent.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * **Session restore** (round R1, task 5) — offer the user the page they were on when Lucent died.
 *
 * ### What was actually wrong
 *
 * There was already a safety net: `UnsavedChangesGuard.autoDraft` parks an open, dirty editor in
 * the draft area when the app leaves the foreground. It is a good net and it stays. But it is hung
 * from `onStop`, which is a callback — and a process that is killed, crashes below the JVM, or is
 * force-stopped never reaches one. Worse, even in the cases where it *did* fire, what the user got
 * on the next launch was a note sitting in a drawer they had to know about, on a tab they might not
 * have been on. Nothing said "you were in the middle of something"; they simply opened the app and
 * their work was not where they left it. Reported, accurately, as "the last edit is all lost".
 *
 * ### The shape of the fix
 *
 * Two separate promises, kept by two separate mechanisms, because they fail in different ways:
 *
 *  - **The content** is written down continuously while an editor is open and dirty — debounced by
 *    the screen that owns it, so a burst of typing is one write, not one per keystroke. That write
 *    goes through the preferences store, which replaces its file atomically: a process killed
 *    mid-write leaves either the old snapshot or the new one, never half of one.
 *  - **The question** is asked exactly once per launch, at startup, from the snapshot that survived.
 *
 * ### How "abnormal" is decided, and why it needs no crash detection
 *
 * A snapshot is written while an editor is open and cleared the moment that editor closes for any
 * ordinary reason — saved, discarded, backed out of. So the rule is simply: **a snapshot that is
 * still there at startup is one whose editor never closed**, which is exactly the situation the
 * user is describing. No crash handler, no "did we exit cleanly?" flag that itself has to survive
 * the crash, and no dependence on a callback firing. The absence of a clean close IS the signal.
 *
 * Backgrounding deliberately does NOT clear it. A stopped app can be killed at any moment without
 * another line of our code running, so leaving the snapshot in place is the honest state — and if
 * the same process does come back, nothing is asked, because the question is only put at startup.
 */
object SessionRestore {

    const val KIND_NOTE = "note"
    const val KIND_TASK = "task"

    /**
     * One editor's worth of recoverable state.
     *
     * [savedAt] defaults to zero and is stamped by [save], which is not cosmetic: the composers
     * rebuild this value on every recomposition and use it as the key of the effect that persists
     * it, so a live timestamp inside it would make every snapshot unequal to the last and defeat
     * the debounce entirely.
     */
    data class Snapshot(
        /** [KIND_NOTE] or [KIND_TASK] — which screen owns this and can put it back. */
        val kind: String,
        /** The row being edited, or null when the editor held a brand-new item. */
        val itemId: Long?,
        /** What to call it in the prompt. May be blank; the prompt substitutes "Untitled". */
        val title: String,
        /** The composer's fields, as JSON. Opaque here; only the owning screen reads it. */
        val payload: String,
        val savedAt: Long = 0L
    )

    /**
     * The snapshot left behind by the previous run, or null. Read once at startup and then only
     * consumed — a plain field rather than snapshot state because it is written before any
     * composition exists.
     */
    @Volatile
    var pending: Snapshot? = null
        private set

    /** Set once the question has been put this launch, answered or dismissed. */
    @Volatile
    var asked: Boolean = false

    /**
     * The snapshot the user accepted, waiting for its screen to pick it up. Compose state, because
     * the screen that honours it may not even be composed at the moment the user answers — the
     * prompt can be shown by the Tasks tab and be about a note. Same one-shot read-and-clear
     * contract as [com.lucent.app.AppNavigation]: the consumer clears it, so switching tabs later
     * can never re-open an editor the user has since closed.
     */
    var restoring: Snapshot? by mutableStateOf(null)
        private set

    /** Seed [pending] from the value carried in the startup preferences read. */
    fun hydrate(stored: String?) {
        pending = stored?.takeIf { it.isNotBlank() }?.let { parse(it) }
    }

    /** Accept the offer: hand the snapshot to whichever screen owns it. */
    fun beginRestore(snapshot: Snapshot) {
        pending = null
        restoring = snapshot
    }

    /** Decline the offer. The draft copy, if `autoDraft` managed to leave one, is untouched. */
    fun decline() {
        pending = null
    }

    /** Read-and-clear, but only for the screen that can actually honour it. */
    fun consumeRestore(kind: String): Snapshot? {
        val current = restoring ?: return null
        if (current.kind != kind) return null
        restoring = null
        return current
    }

    // ---- Persistence -------------------------------------------------------------------------

    /**
     * Write [snapshot] as the current session. Callers debounce; this does not, so that a save
     * triggered by closing an editor is not delayed behind one.
     */
    suspend fun save(context: android.content.Context, snapshot: Snapshot) {
        runCatching {
            SettingsRepository(context)
                .setSessionSnapshot(serialize(snapshot.copy(savedAt = System.currentTimeMillis())))
        }
    }

    /**
     * Forget the current session. Called whenever an editor closes for an ordinary reason — that
     * is the whole of the "was this abnormal?" decision, so it must not be missed on any of the
     * ways out (save, save-as-draft, discard, back, tab switch).
     */
    suspend fun clear(context: android.content.Context) {
        runCatching { SettingsRepository(context).setSessionSnapshot("") }
    }

    // ---- Serialization -----------------------------------------------------------------------

    fun serialize(s: Snapshot): String = JSONObject()
        .put("kind", s.kind)
        .put("itemId", s.itemId ?: JSONObject.NULL)
        .put("title", s.title)
        .put("payload", s.payload)
        .put("savedAt", s.savedAt)
        .toString()

    /** Never throws: an unreadable snapshot is treated as no snapshot, which is the safe reading. */
    fun parse(json: String): Snapshot? = try {
        val o = JSONObject(json)
        val kind = o.optString("kind")
        if (kind != KIND_NOTE && kind != KIND_TASK) null
        else Snapshot(
            kind = kind,
            itemId = if (o.isNull("itemId")) null else o.optLong("itemId"),
            title = o.optString("title"),
            payload = o.optString("payload"),
            savedAt = o.optLong("savedAt")
        )
    } catch (_: Throwable) {
        null
    }

    // ---- Payload helpers ---------------------------------------------------------------------
    //
    // The composers hold their fields as a dozen separate `var`s, and a snapshot has to carry all
    // of them or it restores something subtly different from what was on screen — which is worse
    // than restoring nothing, because it looks like it worked. Building and reading the JSON lives
    // here, next to its twin, so the two can never fall out of step field by field.

    fun put(vararg pairs: Pair<String, Any?>): String {
        val o = JSONObject()
        pairs.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        return o.toString()
    }

    /** Read a payload back. Returns an empty object on anything unreadable, so every getter defaults. */
    fun read(payload: String): JSONObject = try {
        JSONObject(payload)
    } catch (_: Throwable) {
        JSONObject()
    }
}
