package com.lucent.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import org.json.JSONObject
import java.io.OutputStream

/**
 * Backup / restore — a single encrypted `.lcb` file, and nothing else.
 *
 * ### The only format: an encrypted envelope (`.lcb`)
 *
 * Export produces one [BackupCrypto] envelope. Inside it is a self-contained JSON manifest with
 * every note (archived ones included), task, note-version, chat, conversation, setting, and every
 * attachment inlined as Base64. The whole thing is sealed — nothing readable survives outside the
 * envelope except the small plaintext header import needs to know whether a password is required.
 *
 * **Legacy formats have been removed (task 5).** Earlier builds also wrote and read a ZIP bundle
 * (`manifest.json` + `attachments/<id>` entries) and a bare `.json`. All of that reader/writer code
 * is gone: import now accepts *only* a `.lcb` envelope and rejects anything else with a clear
 * message, and export only ever writes a `.lcb`. Keeping three half-tested code paths alive for
 * files almost nobody still had was a liability out of proportion to the benefit.
 *
 * ### Cross-device restore (task 5)
 *
 * The manifest is portable by construction, and the two places that used to make it *not* portable
 * are both handled here:
 *
 *  - **Attachments** are inlined as the file's **plaintext** bytes (decrypted through
 *    [AttachmentStore] on the way out), never the on-disk ciphertext — because the on-disk key is
 *    device-bound, so inlined ciphertext would round-trip on the origin device and be permanent
 *    garbage anywhere else. On import each blob is re-encrypted with *this* device's key.
 *  - **API keys** inside the manifest are sealed with [CryptoUtil], which derives its key from an
 *    app-embedded passphrase (not the Keystore), so they decrypt on any install.
 *
 * The remaining cross-device failure was a *usability* one, and it's fixed in the export UI rather
 * than here: the old default silently re-used a password saved only on the origin device, so a
 * backup that "just worked" when re-imported on the same phone demanded a forgotten password on a
 * new one. The portable built-in key is now the default; a password is an explicit opt-in that the
 * UI is clear you must re-enter elsewhere. See [BackupCrypto] and the export dialog in SettingsScreen.
 *
 * The payload is streamed through the envelope, so a large backup full of photos never has to fit in
 * memory twice.
 *
 * ### Streaming import (crash fix)
 *
 * Import is streamed too, and for the same reason export always was: a backup that carries the
 * LOCAL_MODEL_FILES module is gigabytes, and the old import path read the whole file into one
 * ByteArray, decrypted it into a second, and then held the decrypted copy alive behind the preview
 * dialog. On a phone that is an immediate OutOfMemoryError — which is an Error, not an Exception,
 * so the UI's catch blocks never saw it and the app simply died. It also could not have worked past
 * 2 GB at all, since a JVM array cannot exceed Int.MAX_VALUE bytes.
 *
 * So [inspect] and [commit] now take a [BackupSource] — something that can be opened as an
 * InputStream more than once — and never materialise the payload. Inspect makes one streaming pass
 * to read the manifest and count the blob frames (names and sizes only; the gigabytes are skipped,
 * not buffered). Commit makes a second pass and copies each wanted blob straight from the cipher
 * stream to its destination file in 64 KB pieces. Peak memory is the manifest plus one buffer,
 * regardless of how many model files ride along, and blob sizes are handled as Long throughout so
 * a single >2 GB model restores correctly.
 *
 * ### One format, every platform
 *
 * The `.lcb` produced here is byte-portable between the Android and desktop builds: this file, and
 * BackupCrypto/FileCrypto/CryptoUtil under it, are compiled verbatim on both, the manifest and the
 * model/font slot manifests are plain JSON, PBKDF2/AES-GCM are standard primitives (the Rust fast
 * path and the JCE fallback produce identical bytes), and blob names never reach the filesystem
 * un-sanitised. Exporting on one platform and restoring on the other is a supported path, not a
 * lucky one.
 */
object BackupManager {

    /**
     * The parts of a backup a user can include or leave out (task 9).
     *
     * ### Why a backup became selectable at all
     *
     * It used to be one indivisible artefact: everything, always. That is the right default and a
     * poor only-option, because the things inside a backup have wildly different sizes and wildly
     * different sensitivities. Someone moving to a new phone wants all of it. Someone archiving
     * their writing wants notes and nothing else. Someone sending a backup to a second device they
     * share with family very reasonably does not want their API keys in it, and someone whose model
     * file is four gigabytes does not want it in a file they are about to email themselves.
     *
     * Splitting the file into modules answers all four with one mechanism, and — because the reader
     * checks for each section rather than assuming it — a partial file restores exactly as cleanly
     * as a complete one.
     *
     * ### Why the model FILES are their own module
     *
     * [LOCAL_MODEL_FILES] is separate from [LOCAL_ASSISTANT] and off by default. The settings are
     * kilobytes; the payloads are gigabytes. Bundling them would mean nobody could back up their
     * local-assistant configuration without also moving a model file around, which is the sort of
     * all-or-nothing that made the old format worth changing in the first place.
     */
    enum class BackupModule {
        NOTES,
        TASKS,
        CHATS,
        SETTINGS,
        API,
        LOCAL_ASSISTANT,
        LOCAL_MODEL_FILES
    }

    /**
     * Everything except the model files — the sensible default for the export dialog, and exactly
     * what the old unselectable format produced, so an ordinary export is unchanged.
     */
    val DEFAULT_MODULES: Set<BackupModule> =
        BackupModule.entries.toSet() - BackupModule.LOCAL_MODEL_FILES

    /**
     * A backup the importer can open **more than once** — the shape streaming import needs.
     *
     * Inspect makes one pass (manifest + blob counts) and commit makes another (the blob bytes),
     * so the source must be re-openable rather than a one-shot stream. On desktop that is simply
     * the picked file; on Android the UI stages the picked document into the app's cache first,
     * because a SAF Uri's read grant is not guaranteed to survive until the user finishes reading
     * the confirm dialog — and losing a restore to an expired permission would be an absurd way
     * to lose one.
     *
     * This is a fun interface rather than a File parameter so the class stays platform-neutral:
     * both builds compile this file verbatim, which is what keeps the format cross-platform by
     * construction instead of by promise.
     */
    fun interface BackupSource {
        /** Open a fresh stream over the whole `.lcb` file, positioned at byte 0. */
        fun open(): java.io.InputStream
    }

    /** The common case: a source backed by a plain file on disk. */
    fun fileSource(file: java.io.File): BackupSource = BackupSource { file.inputStream() }

    /**
     * A backup selection: which modules, and — optionally — which individual notes and tasks.
     *
     * ### Why per-item, on top of per-module
     *
     * Modules answer "I don't want my chats in this file". They cannot answer "I want these four
     * notes", which is the request behind every "can you back up just my journal" — and which the
     * app could already do for *document* export (Settings > Data > choose notes to export) but not
     * for a real restorable backup. So the same granularity now reaches the backup.
     *
     * [noteIds] and [taskIds] are null by default, meaning **everything in that module**, which is
     * both the sensible default and the shape every existing caller already implies. A non-null set
     * is an explicit subset; an EMPTY set is an explicit "none", not "all" — the distinction matters
     * because a user who unticks every note in the second-level list means it.
     *
     * Note history follows its note: unselecting a note takes its revisions with it, since versions
     * of a note you did not back up would restore as history attached to nothing.
     */
    data class BackupSelection(
        val modules: Set<BackupModule> = DEFAULT_MODULES,
        val noteIds: Set<Long>? = null,
        val taskIds: Set<Long>? = null,
        // Which chat conversations and which API profiles ride along (task F1). Same convention as
        // [noteIds]/[taskIds]: null = "everything in that module" (the default and what every prior
        // release did), a non-null set is an explicit subset, and an EMPTY set is an explicit "none".
        //
        // Chats are chosen by CONVERSATION, not by individual message: a conversation is the thing a
        // user can see and name, a message is not, and "back up this chat" always means the thread.
        // Messages follow their conversation, so unselecting a conversation takes its messages with it.
        //
        // API profiles are identified by NAME (task F1 follow-up): the name is what the user sees and
        // chooses in the picker, and matching on it keeps a selection meaningful even if the profile
        // list is reordered. Names are distinct in normal use — the app's auto-naming (nextDefaultName)
        // never repeats one — so a name maps to a single profile; a user who hand-duplicates a name
        // simply selects both, the predictable reading of "include the profiles called X".
        val conversationIds: Set<Long>? = null,
        val apiProfileNames: Set<String>? = null
    ) {
        fun has(m: BackupModule) = m in modules
        fun wantsNote(id: Long) = noteIds?.contains(id) ?: true
        fun wantsTask(id: Long) = taskIds?.contains(id) ?: true
        fun wantsConversation(id: Long) = conversationIds?.contains(id) ?: true
        fun wantsApiProfileName(name: String) = apiProfileNames?.contains(name) ?: true
        /** Nothing at all would be written — the export button stays disabled on this. */
        val isEmpty: Boolean
            get() = modules.isEmpty() ||
                modules.all { m ->
                    when (m) {
                        BackupModule.NOTES -> noteIds?.isEmpty() == true
                        BackupModule.TASKS -> taskIds?.isEmpty() == true
                        BackupModule.CHATS -> conversationIds?.isEmpty() == true
                        BackupModule.API -> apiProfileNames?.isEmpty() == true
                        else -> false
                    }
                }
    }

    // ---------------------------------------------------------------------------------------
    // Payload framing (task 9)
    // ---------------------------------------------------------------------------------------
    //
    // Inside the encrypted envelope the payload used to be, simply, the manifest JSON. Model files
    // cannot travel that way: inlining a 4 GB file as Base64 would inflate it by a third, require
    // the whole thing to exist as one Java String on the way out AND as one parsed JSON document on
    // the way in, and fall over on any phone long before it finished.
    //
    // So the payload is now optionally FRAMED: a length-prefixed manifest followed by raw binary
    // blobs, each with its own name and length. Blobs are streamed straight through — from disk to
    // the cipher on export, from the cipher to disk on import — and never held in memory whole.
    //
    // Backwards compatibility is free and needs no version flag: a legacy payload is raw JSON, so it
    // begins with '{'. The framed payload begins with [FRAME_MAGIC], which is not '{'. One byte
    // tells the reader which it is holding, and every previously written .lcb still restores.

    // Framed blob names are name-spaced by destination: an imported font travels as
    // "font:<fileName>" and is routed to FontStore on restore; any other name is a local model
    // file — which is also what every pre-v11 backup contains, so old files restore unchanged
    // without a version check. The prefix never reaches the filesystem (restore strips it before
    // resolving a target), so no platform's filename rules are in play.

    // Bumped whenever the manifest shape changes. Import reads this only for information; every
    // field added since is read back with a default, so an older manifest inside a `.lcb` still
    // restores cleanly. 8 (this build) covers pin/colour/checklist/trash state, task
    // priority/repeat/reminder/subtasks, and note version history.
    // 9: the settings block became complete — every user-visible preference travels now, not just
    // the API/theme subset it started as (task 17). Purely additive: nothing reads this number to
    // gate behaviour, because every restored key is guarded by has(), so a v8 file restores exactly
    // as it always did and a v9 file restores fully on any build that understands the keys.
    // 10: sections became selectable (task 9). The manifest now carries a "modules" list, each
    // section is written only when its module was chosen, and the local-model slot manifest travels
    // too. Still purely additive on the read side — every section is optional and every key is
    // guarded by has() — so a v8 or v9 file restores exactly as it always did.
    // 11: the imported font library travels (font library task). The settings block gains
    // "fontLibrary" (the FontStore manifest) and the font files themselves ride as framed blobs
    // prefixed with FONT_BLOB_PREFIX whenever the SETTINGS module is chosen — unlike model files
    // they are small enough that opting in per-export would be a question without a point.
    // Additive as ever: every new key is guarded by has(), and unprefixed blobs still restore as
    // model files, so a v10 file restores exactly as it always did.
    // 12: the audit's remaining settings coverage gaps closed, still purely additive. The
    // saved-searches JSON travels as "savedSearches" (G1), and the preferences added since
    // "task 17" claimed completeness — note/task history toggles, the unlock attempts-ladder
    // configuration (pwFirstRoundLimit/pwLaterRoundLimit/pwSelfDestructEnabled/
    // pwSelfDestructThreshold), crash shield, blackout, the rich-text editor toggle,
    // open-links-externally, assistant tool confirmation and small-model mode — travel as their
    // own keys in the settings block (G2). Each is read back guarded by has(), so a v11 file
    // restores exactly as it always did and a v12 file restores fully on any build that
    // understands the keys.
    // 13: the v2.7.2 audit's two real gaps closed, additive as ever. The Material You dynamic
    // colour flag (dynamic_color_enabled — a v2.7.0 addition that the v12 audit had not caught,
    // since it was shipped after that audit ran) now travels next to themeMode/palette so a
    // restore puts the wallpaper-colour switch back exactly where it was. And chat messages now
    // carry their full multi-attachment JSON ("attachmentList", the v16 column that holds the
    // second and later files of a message) alongside the legacy single-attachment trio — before
    // this, restoring a chat that had N files per message kept only the first one. replyToId
    // remains deliberately excluded (see the import comment): restoring raw row ids without a
    // message-id remap would make replies point at unrelated content.
    internal const val BACKUP_VERSION = 13

    // ---------------------------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------------------------

    /**
     * Build the self-contained manifest JSON — every attachment inlined as Base64 — that gets sealed
     * inside the `.lcb`. Reads **all** notes (archived included) and tasks via the one-shot DAO
     * queries so nothing is filtered out of the backup.
     *
     * Note the memory trade-off: inlining puts the full attachment payload into one JSON string, so a
     * backup with very large attachments is held in memory while it's built. This is a deliberate
     * choice in favour of a single, directly-importable, portable file — the same inline form is what
     * makes cross-device restore work (the bytes travel in the file, not as device-bound ids).
     */
    suspend fun exportJsonFull(
        context: Context,
        db: AppDatabase,
        settings: SettingsRepository,
        selection: BackupSelection = BackupSelection()
    ): String {
        val modules = selection.modules
        // Only read what is actually going into the file. Skipping the query as well as the write
        // is not just tidiness: a notes-only backup on a phone with thousands of chat messages
        // should not pay to load and inline them before throwing them away.
        val notes = if (BackupModule.NOTES in modules) {
            db.noteDao().getAllOnce().filter { selection.wantsNote(it.id) }
        } else emptyList()
        val tasks = if (BackupModule.TASKS in modules) {
            db.taskDao().getAllOnce().filter { selection.wantsTask(it.id) }
        } else emptyList()
        // History is filtered by the notes that survived, not by the selection directly, so a
        // deselected note cannot leave orphaned revisions in the file.
        val keptNoteIds = notes.map { it.id }.toHashSet()
        val noteVersions = if (BackupModule.NOTES in modules) {
            db.noteVersionDao().getAllOnce().filter { it.noteId in keptNoteIds }
        } else emptyList()
        // Task A19 — the task side of the same rule: revisions travel only for tasks that survived
        // the selection, so a deselected task cannot leave orphaned history in the file.
        val keptTaskIds = tasks.map { it.id }.toHashSet()
        val taskVersions = if (BackupModule.TASKS in modules) {
            db.taskVersionDao().getAllOnce().filter { it.taskId in keptTaskIds }
        } else emptyList()
        // Conversations narrowed to the chosen subset (task F1); a null selection keeps them all.
        val conversations =
            if (BackupModule.CHATS in modules) {
                db.chatConversationDao().getAllOnce().filter { selection.wantsConversation(it.id) }
            } else emptyList()
        // Messages follow the conversations that survived: a message whose conversation was unticked
        // (or a legacy orphan message whose conversation id wasn't chosen) is dropped, so a
        // per-conversation choice never leaves stray messages behind. A null selection keeps every
        // message exactly as before.
        val chats = if (BackupModule.CHATS in modules) {
            db.chatDao().getAll().first().filter { selection.wantsConversation(it.conversationId) }
        } else emptyList()
        // Notebooks travel with the NOTES+TASKS module (they organize notes and tasks, so they only
        // make sense when at least one of those is present). Membership is copied verbatim; on
        // import the notebook ids are remapped and membership re-linked to the notes/tasks that
        // actually landed on this device.
        val notebooks = if (BackupModule.NOTES in modules || BackupModule.TASKS in modules) {
            db.notebookDao().getAllOnce()
        } else emptyList()
        val notebookItems = if (notebooks.isNotEmpty()) {
            notebooks.flatMap { db.notebookDao().getItemsOnce(it.id) }
        } else emptyList()
        return BackupManifestBuilder.build(
            context, notes, tasks, noteVersions, taskVersions, chats, conversations, settings,
            notebooks = notebooks, notebookItems = notebookItems,
            inlineAttachments = true, modules = modules, apiProfileNames = selection.apiProfileNames
        ).toString(2)
    }

    /**
     * Write a complete, **encrypted** backup to [out].
     *
     * This is what the Export button produces. The payload is the same self-contained JSON as before
     * — notes, tasks, note history, chats, every attachment inlined — but the whole thing is now
     * sealed inside a [BackupCrypto] envelope instead of being written in the clear with only the API
     * key encrypted.
     *
     * That old shape was a strange one for a local-first app: a backup is the single artefact that
     * *deliberately* leaves the device, into a cloud drive or an email to yourself, and it was the one
     * file with nothing protecting it. Now nothing readable survives outside the envelope.
     *
     * [password] blank or null → the built-in key (portable, restores anywhere, honest obfuscation).
     * [password] set → PBKDF2 from that password (real encryption; lose it and the file is gone).
     *
     * The JSON is streamed straight into the cipher, so it is never held in memory twice.
     */
    suspend fun exportEncrypted(
        context: Context,
        db: AppDatabase,
        settings: SettingsRepository,
        out: OutputStream,
        password: String?,
        selection: BackupSelection = BackupSelection()
    ) {
        val modules = selection.modules
        // Snapshot of the caller's Job, polled by the streaming loop below: a cancelled export
        // (Cancel on the progress dialog) stops within one 64 KB piece instead of silently
        // writing the rest of a multi-gigabyte file in the background.
        val exportJob = coroutineContext[Job]
        val cancelled: () -> Boolean = { exportJob?.isActive == false }
        val json = exportJsonFull(context, db, settings, selection)
        val jsonBytes = json.toByteArray(Charsets.UTF_8)

        // Which model files, if any, ride along as framed blobs after the manifest.
        val modelFiles: List<Pair<String, java.io.File>> =
            if (BackupModule.LOCAL_MODEL_FILES in modules) {
                com.lucent.app.local.LocalModelStore.slots(context).mapNotNull { slot ->
                    com.lucent.app.local.LocalModelStore.modelFileForSlot(context, slot)
                        ?.let { slot.fileName to it }
                }
            } else emptyList()

        // Which imported font files ride along, name-spaced with FONT_BLOB_PREFIX so restore can
        // route them to FontStore. Fonts travel with the SETTINGS module — they are what the
        // "font" preference points at, and a restored appearance that silently dropped back to the
        // system font would be the settings equivalent of an attachment restored as a dead id.
        val fontFiles: List<Pair<String, java.io.File>> =
            if (BackupModule.SETTINGS in modules) {
                FontStore.fonts(context).mapNotNull { slot ->
                    FontStore.fontFileForSlot(context, slot)
                        ?.let { (BackupFrames.FONT_BLOB_PREFIX + slot.fileName) to it }
                }
            } else emptyList()

        val blobs = modelFiles + fontFiles
        BackupCrypto.encryptingStream(out, password).use { cipherOut ->
            if (blobs.isEmpty()) {
                // No blobs: write the plain JSON payload, byte-for-byte the format every previous
                // release produced. An ordinary backup is therefore completely unchanged, and an
                // older build could still read it.
                cipherOut.write(jsonBytes)
                return@use
            }
            cipherOut.write(byteArrayOf(BackupFrames.FRAME_MAGIC, BackupFrames.FRAME_VERSION))
            BackupFrames.writeInt(cipherOut, jsonBytes.size)
            cipherOut.write(jsonBytes)
            val buffer = ByteArray(1 shl 16)
            for ((name, file) in blobs) {
                BackupFrames.throwIfCancelled(cancelled)
                val nameBytes = name.toByteArray(Charsets.UTF_8)
                BackupFrames.writeInt(cipherOut, nameBytes.size)
                cipherOut.write(nameBytes)
                BackupFrames.writeLong(cipherOut, file.length())
                // Streamed in 64 KB pieces: a multi-gigabyte model passes through this loop without
                // ever existing in memory as a whole, which is the entire reason for the framing.
                // Fonts share the pipe for uniformity, not need — they are merely megabytes.
                file.inputStream().use { input ->
                    while (true) {
                        BackupFrames.throwIfCancelled(cancelled)
                        val n = input.read(buffer)
                        if (n < 0) break
                        cipherOut.write(buffer, 0, n)
                    }
                }
            }
        }
        // Diagnostic breadcrumb (task F4). Counts and flags only — never a title, a key, or any
        // content — so the log stays safe to share even after an encrypted export. A no-op unless the
        // user turned logging on. Per-item counts appear only when that dimension was narrowed.
        StartupLog.event(
            context,
            "Backup exported: modules=${modules.joinToString(",") { it.name }}" +
                (selection.noteIds?.let { "; notes=${it.size}" } ?: "") +
                (selection.taskIds?.let { "; tasks=${it.size}" } ?: "") +
                (selection.conversationIds?.let { "; chats=${it.size}" } ?: "") +
                (selection.apiProfileNames?.let { "; apiProfiles=${it.size}" } ?: "") +
                "; models=${modelFiles.size}; fonts=${fontFiles.size}" +
                "; password=${if (password.isNullOrEmpty()) "no" else "yes"}"
        )
    }

    // ---- Cooperative cancellation for the streaming passes ----
    //
    // The heavy copy loops in this file are plain blocking IO running on Dispatchers.IO, and
    // cancelling the calling coroutine cannot interrupt a blocking read/write by itself — a
    // cancelled export or import would otherwise keep grinding through gigabytes in the
    // background. Each suspend entry point captures its caller's Job once and hands the loops
    // this poll; the loops call it between 64 KB pieces and abort by throwing
    // CancellationException the moment the caller has been cancelled (the user pressed Cancel
    // on the progress dialog).




    // ---------------------------------------------------------------------------------------
    // Import
    // ---------------------------------------------------------------------------------------

    /**
     * What a backup file contains, worked out **without writing anything**.
     *
     * The import flow is two-phase for a reason. Restoring merges a stranger's file into the user's
     * live database, and the old flow did it the instant they picked the file — no idea what was
     * inside, no way back. Now they see exactly what is about to arrive and get to say no. The
     * decrypted payload is carried here so the confirm step doesn't have to re-read the file (whose
     * Uri may no longer be readable by then) or re-derive a PBKDF2 key that took a second the first
     * time.
     */
    data class BackupPreview(
        /** The decrypted manifest. Internal: this is the payload, not a summary of it. */
        internal val manifestJson: String,
        val formatVersion: Int,
        val exportedAt: Long?,
        val encrypted: Boolean,
        val passwordProtected: Boolean,
        val notes: Int,
        val archivedNotes: Int,
        val trashedNotes: Int,
        val tasks: Int,
        val completedTasks: Int,
        val trashedTasks: Int,
        val noteVersions: Int,
        val conversations: Int,
        val chatMessages: Int,
        val attachments: Int,
        val hasSettings: Boolean,
        /**
         * The modules this file claims to carry. Empty for a pre-v10 backup, which predates the
         * concept entirely — the UI treats that as "everything the sections show" rather than as
         * "nothing", because an old file really did contain all of it.
         */
        val modules: Set<BackupModule> = emptySet(),
        /** How many local model files are attached as framed blobs, and their combined size. */
        val modelFiles: Int = 0,
        val modelBytes: Long = 0L,
        /** How many imported fonts are attached as framed blobs, and their combined size. */
        val fontFiles: Int = 0,
        val fontBytes: Long = 0L,
        /**
         * Whether blob frames follow the manifest in this file. What used to sit here was the
         * decrypted payload itself — gigabytes of it for a backup carrying model files, pinned in
         * memory for as long as the confirm dialog stayed open. Commit now re-streams the blobs
         * from the [BackupSource] instead, so the preview carries a flag, not the freight.
         */
        internal val hasBlobs: Boolean = false,
        /**
         * The password that successfully decrypted this file at inspect time (null for the
         * built-in key). Commit's second streaming pass re-derives the same key from it, which
         * costs one more PBKDF2 run for password-protected files — a second of CPU, paid so that
         * multi-gigabyte payloads never have to be paid for in RAM.
         */
        internal val password: String? = null,
        /**
         * The conversations found in the file, as (id, title). Drives the import-side chat picker
         * (task F2), exactly as the loaded conversation list drives the export-side one. Empty for a
         * file with no chats, or one predating conversations.
         */
        val conversationList: List<Pair<Long, String>> = emptyList(),
        /**
         * The API profile NAMES found in the file. Drives the import-side API picker (task F2). Empty
         * for a legacy single-API backup (which carries only the flat keys, not a named profile list)
         * — matching the export side, where a file with no named profiles offers no per-profile choice.
         */
        val apiProfileNames: List<String> = emptyList()
    ) {
        /** True when the file parsed but holds nothing worth restoring. */
        val isEmpty: Boolean
            get() = notes == 0 && tasks == 0 && chatMessages == 0 && conversations == 0 &&
                !hasSettings && modelFiles == 0 && fontFiles == 0
    }

    /**
     * Decrypt (if needed), parse, and count a backup — **touching nothing**.
     *
     * Throws [BackupCrypto.WrongPasswordException] when the file needs a password and the one given
     * is missing or wrong, and [IllegalArgumentException] when the file isn't a Lucent backup at all.
     * Both are distinct on purpose: "try again", "this isn't a backup", and "this file is damaged"
     * send someone in three different directions, and telling them the wrong one is how a perfectly
     * good backup gets deleted in frustration.
     */
    suspend fun inspect(context: Context, source: BackupSource, password: String? = null): BackupPreview {
        // Only `.lcb` envelopes are accepted now (task 5). A file without our envelope header —
        // a legacy ZIP, a bare JSON, or something that isn't a Lucent backup at all — is refused
        // by openDecrypted rather than being parsed by a reader that no longer exists.
        //
        // Everything below is ONE streaming pass: the manifest (the only part JSON parsing forces
        // into memory) is read, and the blob frames after it are counted and sized without ever
        // being buffered. The old reader decrypted the whole payload — model files included — into
        // a ByteArray first, which on a backup carrying the LOCAL_MODEL_FILES module meant an
        // OutOfMemoryError, and an OOM is an Error the UI's catch(Exception) never saw: the app
        // simply vanished. That is the "import crashes the app" bug, fixed at the root here.
        // Cooperative cancel for the (potentially multi-gigabyte) read-through below.
        val inspectJob = coroutineContext[Job]
        val cancelled: () -> Boolean = { inspectJob?.isActive == false }
        val needsPassword: Boolean
        val scan: BackupFrames.PayloadScan
        var plain: java.io.InputStream? = null
        try {
            // The header decides whether a password is even relevant; read it once, cheaply, from
            // its own tiny stream so the answer can be reported on the preview.
            needsPassword = peekPasswordRequirement(source)?.needsPassword
                ?: throw IllegalArgumentException(com.lucent.app.i18n.S.notLcbBackup)
            plain = BackupFrames.openDecrypted(source, password)
            scan = try {
                BackupFrames.scanPayload(plain, cancelled)
            } catch (t: java.io.IOException) {
                if (t is BackupCrypto.WrongPasswordException) throw t
                // A GCM tag failure on the very first frame is, overwhelmingly, a wrong password —
                // a damaged file is far rarer than a typo. Report the likely cause, not the literal
                // one — the same reading BackupCrypto.decrypt always gave.
                if (needsPassword) throw BackupCrypto.WrongPasswordException()
                throw java.io.IOException("Backup file is damaged", t)
            }
        } finally {
            try { plain?.close() } catch (_: Throwable) {}
        }
        val manifestJson = scan.manifestJson

        val root = try {
            JSONObject(manifestJson)
        } catch (t: Throwable) {
            throw IllegalArgumentException("That backup couldn't be read — the file may be damaged.")
        }

        val notesArr = root.optJSONArray("notes")
        val tasksArr = root.optJSONArray("tasks")

        var archived = 0
        var trashedNotes = 0
        var attachments = 0
        for (i in 0 until (notesArr?.length() ?: 0)) {
            val o = notesArr!!.getJSONObject(i)
            if (o.optBoolean("archived", false)) archived++
            if (!o.isNull("trashedAt")) trashedNotes++
            attachments += Attachments.parse(o.optString("attachments", "[]")).size
        }

        var completed = 0
        var trashedTasks = 0
        for (i in 0 until (tasksArr?.length() ?: 0)) {
            val o = tasksArr!!.getJSONObject(i)
            if (o.optBoolean("isDone", false)) completed++
            if (!o.isNull("trashedAt")) trashedTasks++
            attachments += Attachments.parse(o.optString("attachments", "[]")).size
        }

        // The blob counts and sizes come out of the same streaming pass that read the manifest —
        // the preview only needs to be able to say "2 model files, 3.1 GB; 4 imported fonts",
        // which is exactly the fact a user needs before agreeing to a restore of that size. The
        // short blob NAMES were read, purely to tell font blobs from model blobs by their prefix;
        // the payload bytes themselves were skipped, never copied.

        // The conversations and API profile names in the file, for the import-side pickers (task F2).
        // Parsed here (not counted), so the confirm dialog can offer "restore only these chats / these
        // APIs" the same way the export dialog offers "back up only these".
        val convList = root.optJSONArray("conversations")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optLong("id", 0L)
                if (id == 0L) null else id to o.optString("title", "")
            }
        } ?: emptyList()
        val profileNames = root.optJSONObject("settings")?.optString("apiProfiles")?.let { pj ->
            if (pj.isBlank()) emptyList() else ApiProfiles.parse(pj).map { it.name }
        } ?: emptyList()

        return BackupPreview(
            manifestJson = manifestJson,
            formatVersion = root.optInt("version", 0),
            exportedAt = root.optLong("exportedAt", 0L).takeIf { it > 0 },
            encrypted = true,
            passwordProtected = needsPassword,
            notes = notesArr?.length() ?: 0,
            archivedNotes = archived,
            trashedNotes = trashedNotes,
            tasks = tasksArr?.length() ?: 0,
            completedTasks = completed,
            trashedTasks = trashedTasks,
            noteVersions = root.optJSONArray("noteVersions")?.length() ?: 0,
            conversations = root.optJSONArray("conversations")?.length() ?: 0,
            chatMessages = root.optJSONArray("chats")?.length() ?: 0,
            attachments = attachments,
            hasSettings = root.optJSONObject("settings") != null,
            modules = root.optJSONArray("modules")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { BackupModule.valueOf(arr.optString(i)) }.getOrNull()
                }.toSet()
            } ?: emptySet(),
            modelFiles = scan.modelCount,
            modelBytes = scan.modelBytes,
            fontFiles = scan.fontCount,
            fontBytes = scan.fontBytes,
            hasBlobs = scan.framed,
            password = password,
            conversationList = convList,
            apiProfileNames = profileNames
        )
    }

    /**
     * Compatibility wrapper for callers that already hold the whole file in memory (small,
     * model-less backups; tests). Delegates to the streaming implementation — the array is simply
     * a source that can be re-opened for free.
     */
    suspend fun inspect(context: Context, bytes: ByteArray, password: String? = null): BackupPreview =
        inspect(context, BackupSource { bytes.inputStream() }, password)

    /**
     * Actually restore a previously [inspect]ed backup. This is the only call that writes.
     *
     * Splitting it out is the point: nothing touches the database until the user has seen what is in
     * the file and said yes.
     */
    suspend fun commit(
        context: Context,
        db: AppDatabase,
        settings: SettingsRepository,
        preview: BackupPreview,
        modules: Set<BackupModule> = BackupModule.entries.toSet(),
        // Per-item restore choices (task F2), same convention as the export selection: null = restore
        // everything in that module, a non-null set is an explicit subset. Chats by conversation id,
        // API by profile name — the same handles the export side and the preview lists use.
        conversationIds: Set<Long>? = null,
        apiProfileNames: Set<String>? = null,
        // The same source [inspect] read. Needed only when the file carries blob frames and one of
        // the blob-bearing modules was chosen; commit re-opens it and streams the blobs straight to
        // disk. Last-with-a-default so every existing positional call still compiles unchanged.
        source: BackupSource? = null
    ): String {
        // Blobs first, and deliberately so: the model slot manifest and the font library manifest
        // are each adopted only for files actually present on disk (see the two restoreFromBackup
        // implementations), so the payloads have to land before the settings block is read or
        // every restored slot would be discarded as dangling. Model files restore under their own
        // opt-in module; fonts restore with the SETTINGS module they travelled with.
        //
        // This is the second streaming pass over the file (inspect made the first). The decrypted
        // bytes go from the cipher straight to each blob's temp file in 64 KB pieces — the preview
        // no longer smuggles a multi-gigabyte payload across the confirm dialog, which is the
        // memory shape that used to kill the import of any backup containing a local model.
        // Cooperative cancel: honoured through the blob phase (where the gigabytes and the
        // time are), and checked once more before the database restore begins — see the
        // comment above importJson below for why it stops being honoured after that point.
        val commitJob = coroutineContext[Job]
        val cancelled: () -> Boolean = { commitJob?.isActive == false }
        var restoredModels = 0
        var restoredFonts = 0
        if (preview.hasBlobs && source != null) {
            val wantModels = BackupModule.LOCAL_MODEL_FILES in modules
            val wantFonts = BackupModule.SETTINGS in modules
            if (wantModels || wantFonts) {
                val scratch = ByteArray(1 shl 16)
                var plain: java.io.InputStream? = null
                try {
                    plain = BackupFrames.openDecrypted(source, preview.password)
                    BackupFrames.scanPayload(plain, cancelled) { name, dataLen, data ->
                        val (m, f) = BackupFrames.restoreOneBlob(
                            context, name, dataLen, data, wantModels, wantFonts, scratch, cancelled
                        )
                        restoredModels += m
                        restoredFonts += f
                    }
                } catch (t: Throwable) {
                    // A user cancel must abort the entire restore, not fall through to the
                    // database phase as if the blob pass had merely hiccuped.
                    if (t is CancellationException) throw t
                    // Best-effort by design, exactly as the old in-memory walk was: whatever blobs
                    // already landed stay landed, and the manifest restore below — the user's
                    // actual notes and tasks — still runs. The report will honestly show how many
                    // model files made it.
                } finally {
                    try { plain?.close() } catch (_: Throwable) {}
                }
            }
        }
        // The manifest restore below is a stream of individual DB writes with no wrapping
        // transaction, so a cancellation landing in the middle of it would leave a half-restored
        // database — strictly worse than either outcome the user could have meant. Cancellation
        // is therefore honoured only up to this point (the blob phase above, where the time
        // actually goes); once the database restore starts it runs to completion, shielded by
        // NonCancellable so a late Cancel press simply lets it finish.
        BackupFrames.throwIfCancelled(cancelled)
        val summary = withContext(NonCancellable) {
            importJson(
                context, db, settings, preview.manifestJson, modules, conversationIds, apiProfileNames
            )
        }
        // Diagnostic breadcrumb (task F4): what a restore touched, counts and flags only. A no-op
        // unless logging is on.
        StartupLog.event(
            context,
            "Backup restored: modules=${modules.joinToString(",") { it.name }}" +
                (conversationIds?.let { "; chats=${it.size}" } ?: "") +
                (apiProfileNames?.let { "; apiProfiles=${it.size}" } ?: "") +
                "; models=$restoredModels; fonts=$restoredFonts"
        )
        var report = summary
        if (restoredModels > 0) report += com.lucent.app.i18n.S.backupModelFilesRestored(restoredModels)
        if (restoredFonts > 0) report += com.lucent.app.i18n.S.backupFontsRestored(restoredFonts)
        return report
    }

    /**
     * Look at a file *before* importing it, so the UI knows whether to ask for a password.
     *
     * Import has to be able to answer "does this need a password?" without a password, or the only
     * way to find out would be to demand one and see if it worked — a miserable thing to do to
     * someone who is already anxious because they are restoring a backup. The envelope's header is
     * plaintext for exactly this reason.
     *
     * Returns null for anything that isn't one of our `.lcb` envelopes; the caller treats that as
     * "not a restorable file" (legacy ZIP/JSON support has been removed — task 5).
     */
    fun peekPasswordRequirement(bytes: ByteArray): BackupCrypto.Header? = BackupCrypto.readHeader(bytes)

    /**
     * The streaming twin of the ByteArray peek: reads only the first few dozen bytes of [source] —
     * the plaintext envelope header — instead of requiring the whole file in memory first, which
     * for a backup carrying model files is the difference between a 64-byte read and an OOM.
     * Returns null when the file can't be read at all or isn't one of ours.
     */
    fun peekPasswordRequirement(source: BackupSource): BackupCrypto.Header? = try {
        source.open().use { input ->
            val head = ByteArray(64)
            var read = 0
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n < 0) break
                read += n
            }
            if (read == 0) null else BackupCrypto.readHeader(head.copyOf(read))
        }
    } catch (_: Throwable) {
        null
    }

    suspend fun importJson(
        context: Context,
        db: AppDatabase,
        settings: SettingsRepository,
        json: String,
        modules: Set<BackupModule> = BackupModule.entries.toSet(),
        conversationIds: Set<Long>? = null,
        apiProfileNames: Set<String>? = null,
        mode: ImportMode = ImportMode.DEFAULT
    ): String = BackupImporter.import(
        context, db, settings, json, modules, conversationIds, apiProfileNames, mode
    )

}
