package com.lucent.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * The `.lcb` manifest builder (extracted from BackupManager, P0-7): turns in-memory entities into
 * the self-contained manifest JSON that gets sealed inside the backup — notes, tasks, revision
 * histories, chats, conversations, notebooks and the selectable settings/API/local-assistant
 * blocks. Every field added here is read back with a default by the importer, so older builds
 * restore newer files partially rather than failing, and older files keep restoring fully.
 */
internal object BackupManifestBuilder {

    /**
     * Build the manifest JSON.
     *
     * When [inlineAttachments] is true, each note/task's attachment list is rewritten so every
     * `data` field holds the Base64 of the file's bytes (a fully self-contained backup). When
     * false, the list is left as stored — disk ids — which is what the ZIP path wants, since the
     * bytes travel as separate archive entries.
     *
     * Notes always carry their `archived` / `archivedAt` state so archiving survives a round-trip.
     */
    suspend fun build(
        context: Context,
        notes: List<Note>,
        tasks: List<Task>,
        noteVersions: List<NoteVersion>,
        // First-compile fix (CI 2026-07-26): this list was assembled by the caller (module-filtered,
        // like everything else here) and then never handed over — the A19 export block below read a
        // name that did not exist in this scope, on both platforms. The parameter is the fix; the
        // caller now passes what it already computed.
        taskVersions: List<TaskVersion>,
        chats: List<ChatMessage>,
        conversations: List<ChatConversation>,
        settings: SettingsRepository,
        notebooks: List<Notebook> = emptyList(),
        notebookItems: List<NotebookItem> = emptyList(),
        inlineAttachments: Boolean,
        modules: Set<BackupManager.BackupModule> = BackupManager.DEFAULT_MODULES,
        // Null = every saved API profile travels (unchanged). A non-null set of profile NAMES narrows
        // the API section to just those profiles (task F1); see the API block below.
        apiProfileNames: Set<String>? = null
    ): JSONObject {
        val notesArray = JSONArray()
        notes.forEach {
            val attachments = if (inlineAttachments) inlineAttachmentBytes(context, it.attachments) else it.attachments
            notesArray.put(
                JSONObject()
                    .put("title", it.title)
                    .put("body", it.body)
                    .put("updatedAt", it.updatedAt)
                    .put("tags", it.tags)
                    .put("attachments", attachments)
                    // Archive state. Older readers simply ignore unknown keys, and older *files*
                    // simply don't have them — every field added here is read back with a default
                    // that reproduces the old behaviour exactly, which is what lets a v5 backup from
                    // two years ago still restore cleanly into this build.
                    .put("archived", it.archived)
                    .put("archivedAt", it.archivedAt ?: JSONObject.NULL)
                    .put("pinned", it.pinned)
                    .put("color", it.color)
                    .put("isChecklist", it.isChecklist)
                    .put("checklist", it.checklist)
                    .put("trashedAt", it.trashedAt ?: JSONObject.NULL)
                    // 1.1.0 group A. Same additive contract as the archive keys above: an older
                    // reader ignores them, an older *file* doesn't carry them, and the importer
                    // defaults each one to the value that reproduces pre-1.1.0 behaviour — so a
                    // backup written today restores into an old build, and a backup written two
                    // years ago restores into this one.
                    .put("manualOrder", it.manualOrder)
                    .put("isDraft", it.isDraft)
                    .put("draftSavedAt", it.draftSavedAt ?: JSONObject.NULL)
                    .put("hidden", it.hidden)
                    // Task A22 — additive, same contract as every key added before it.
                    .put("isDoodle", it.isDoodle)
                    // INTEGRATION (C task 20): the rich-text sidecar travels with the body it
                    // describes. Absent from older backups, where it restores as "" = unstyled.
                    .put("bodySpans", it.bodySpans)
                    .put("doodle", it.doodle)
            )
        }

        val tasksArray = JSONArray()
        tasks.forEach {
            val attachments = if (inlineAttachments) inlineAttachmentBytes(context, it.attachments) else it.attachments
            tasksArray.put(
                JSONObject()
                    .put("title", it.title)
                    .put("isDone", it.isDone)
                    .put("createdAt", it.createdAt)
                    .put("attachments", attachments)
                    .put("dueAt", it.dueAt ?: JSONObject.NULL)
                    .put("notes", it.notes)
                    .put("completedAt", it.completedAt ?: JSONObject.NULL)
                    .put("priority", it.priority)
                    .put("pinned", it.pinned)
                    .put("subtasks", it.subtasks)
                    .put("repeatRule", it.repeatRule)
                    .put("reminderEnabled", it.reminderEnabled)
                    .put("trashedAt", it.trashedAt ?: JSONObject.NULL)
                    .put("manualOrder", it.manualOrder)
                    .put("isDraft", it.isDraft)
                    .put("draftSavedAt", it.draftSavedAt ?: JSONObject.NULL)
                    .put("hidden", it.hidden)
                    .put("notesSpans", it.notesSpans)
            )
        }

        // Note revision history. It travels by *note title + updatedAt* rather than by noteId,
        // because import inserts notes as new rows and Room hands them brand-new ids — a stored
        // noteId would point at whatever note happened to land on that id, which is worse than
        // useless. The importer re-links each version to the note it actually belongs to.
        val versionsArray = JSONArray()
        val noteById = notes.associateBy { it.id }
        noteVersions.forEach { version ->
            val owner = noteById[version.noteId] ?: return@forEach
            versionsArray.put(
                JSONObject()
                    .put("noteTitle", owner.title)
                    .put("noteUpdatedAt", owner.updatedAt)
                    .put("title", version.title)
                    .put("body", version.body)
                    .put("tags", version.tags)
                    .put("isChecklist", version.isChecklist)
                    .put("checklist", version.checklist)
                    .put("savedAt", version.savedAt)
            )
        }

        // Task A19 — task revision history, carried exactly as note history is: by *task title +
        // createdAt*, never by taskId. Import inserts tasks as new rows and the database hands them
        // brand-new ids, so a stored id would point at whatever task happened to land there — worse
        // than useless. The importer re-links each revision to the task it actually belongs to.
        val taskVersionsArray = JSONArray()
        val taskById = tasks.associateBy { it.id }
        taskVersions.forEach { version ->
            val owner = taskById[version.taskId] ?: return@forEach
            taskVersionsArray.put(
                JSONObject()
                    .put("taskTitle", owner.title)
                    .put("taskCreatedAt", owner.createdAt)
                    .put("title", version.title)
                    .put("notes", version.notes)
                    .put("subtasks", version.subtasks)
                    .put("priority", version.priority)
                    .put("dueAt", version.dueAt ?: JSONObject.NULL)
                    .put("savedAt", version.savedAt)
            )
        }

        val chatsArray = JSONArray()
        chats.forEach {
            chatsArray.put(
                JSONObject()
                    .put("role", it.role)
                    .put("content", it.content)
                    .put("timestamp", it.timestamp)
                    .put("attachmentMime", it.attachmentMime ?: JSONObject.NULL)
                    .put("attachmentData", it.attachmentData ?: JSONObject.NULL)
                    .put("attachmentName", it.attachmentName ?: JSONObject.NULL)
                    // The multi-attachment JSON column (backup v13). A chat message can carry N
                    // files (the legacy trio above holds the first); without this key a restore
                    // kept only that first file. The payload is plain JSON text — the same base64
                    // data the trio carries — so nothing new leaves the envelope.
                    .put("attachmentList", it.attachmentList ?: JSONObject.NULL)
                    .put("conversationId", it.conversationId)
                    // The per-turn token estimate (task F3 completeness). Absent from older files, so
                    // import reads it back with a 0 default — exactly the value a pre-token row had.
                    .put("tokens", it.tokens)
            )
        }

        val conversationsArray = JSONArray()
        conversations.forEach {
            conversationsArray.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("createdAt", it.createdAt)
                    .put("updatedAt", it.updatedAt)
            )
        }

        // The settings block is assembled in three independently selectable parts (task 9): the
        // connection/credentials (API), everything about how the app looks and behaves (SETTINGS),
        // and the on-device assistant's own switches plus its slot manifest (LOCAL_ASSISTANT).
        // Each key is written only when its module is in, and every key is read back guarded by
        // has() — so a file missing a section leaves those preferences untouched rather than
        // stamping defaults over them.
        val wantApi = BackupManager.BackupModule.API in modules
        val wantSettings = BackupManager.BackupModule.SETTINGS in modules
        val wantLocal = BackupManager.BackupModule.LOCAL_ASSISTANT in modules

        val settingsObj = JSONObject()
        // The API section, optionally narrowed to a chosen subset of profiles (task F1).
        //
        // When no per-profile choice was made ([apiProfileNames] == null) or there are no saved
        // profiles to narrow, the whole API state travels exactly as it always did: the flat
        // connection keys mirror the active profile and every saved profile is included.
        //
        // When a subset was chosen, only those profiles travel; the selected index is remapped into
        // the kept list, and the flat mirror keys are taken from whichever profile ends up selected
        // there — so a profile the user deliberately excluded can never leak out through the legacy
        // flat fields. The profile JSON already stores each key encrypted (see ApiProfiles.serialize),
        // and the flat key is sealed with CryptoUtil the same way it always was.
        if (wantApi) {
            val rawProfilesJson = settings.apiProfilesJson.first()
            val allProfiles = ApiProfiles.parse(rawProfilesJson)
            if (apiProfileNames == null || allProfiles.isEmpty()) {
                settingsObj
                    .put("baseUrl", settings.baseUrl.first())
                    .put("apiSpec", settings.apiSpec.first())
                    .put("apiKeyEncrypted", CryptoUtil.encrypt(settings.apiKey.first()))
                    .put("model", settings.model.first())
                    .put("apiProfiles", rawProfilesJson)
                    .put("apiProfileSelected", settings.apiProfileSelected.first())
            } else {
                val origSelected = settings.apiProfileSelected.first()
                val keptIndexed = allProfiles.withIndex().filter { it.value.name in apiProfileNames }
                val keptProfiles = keptIndexed.map { it.value }
                val newSelected =
                    keptIndexed.indexOfFirst { it.index == origSelected }.let { if (it >= 0) it else 0 }
                val mirror = keptProfiles.getOrNull(newSelected)
                settingsObj
                    .put("baseUrl", mirror?.baseUrl ?: "")
                    .put("apiSpec", mirror?.spec ?: "openai")
                    .put("apiKeyEncrypted", CryptoUtil.encrypt(mirror?.apiKey ?: ""))
                    .put("model", mirror?.model ?: "")
                    .put("apiProfiles", ApiProfiles.serializeForBackup(keptProfiles))
                    .put("apiProfileSelected", newSelected)
            }
        }
        if (wantSettings) settingsObj
            .put("themeMode", settings.themeMode.first())
            .put("palette", settings.palette.first())
            // The Material You (dynamic colour) flag (v2.7.0). Written next to the choices it
            // suspends, so a restore puts back not only the stored theme/palette but also the
            // fact that the wallpaper was overriding them.
            .put("dynamicColorEnabled", settings.dynamicColorEnabled.first())
            .put("font", settings.font.first())
            // The imported font library's manifest: which fonts exist and what the user named
            // them. The font *files* ride as framed blobs (see exportEncrypted); this is the
            // labelling half, kept in the settings block the same way the model slot manifest is
            // kept in the local-assistant block — names without files restore as an honest nothing,
            // files without names would restore as anonymous blobs.
            .put("fontLibrary", FontStore.exportManifestJson(context))
            .put("assistantName", settings.assistantName.first())
            .put("assistantStyle", settings.assistantStyle.first())
        // (The multi-API profile JSON is written in the consolidated API block above, task F1.)
        if (wantSettings) settingsObj
            // ---- Everything else the app remembers about how it should behave (task 17) ----
            //
            // The block above is the original backup, written when settings *were* the API and the
            // theme. Several releases have added preferences since, and each one silently widened
            // the gap between "a backup holds everything" — which the Data page promises in those
            // words — and what a restore actually put back. A user who restored onto a new phone got
            // their notes and their API key and then found the app in a language they hadn't chosen,
            // with Markdown off, links off, their sort orders reset and the assistant's memory tier
            // back to default. Nothing was lost that could not be re-set by hand, which is precisely
            // why it went unnoticed for so long, and precisely why it was worth fixing: a backup you
            // have to spend twenty minutes correcting is not a backup, it is a starting point.
            //
            // So every user-visible preference the app stores now travels with the file. Three
            // things are deliberately still excluded, and each for a reason that would survive the
            // question "why isn't this in my backup?":
            //
            //  - **App-lock credentials.** The hashes are sealed with a key that lives in THIS
            //    device's hardware Keystore and cannot leave it. Putting them in a portable file
            //    would ship material that is unusable on the restoring device at best, and at worst
            //    would lock someone out of the app on a phone where the recovery answer can't be
            //    verified. The lock is re-set after a restore, on purpose.
            //  - **The backup password.** Storing the password for a file inside that same file is
            //    not encryption, it is theatre.
            //  - **attachments_migrated.** A one-shot marker about *this* install's disk layout. The
            //    migrator re-derives it correctly on first launch; carrying a stale one across
            //    devices could skip a migration that the new device still needs.
            .put("memoryTier", settings.memoryTier.first())
            .put("webSearchEnabled", settings.webSearchEnabled.first())
            .put("typingHaptics", settings.typingHapticsEnabled.first())
            .put("markdownEnabled", settings.markdownEnabled.first())
            .put("linksEnabled", settings.linksEnabled.first())
            .put("backgroundAnimationEnabled", settings.backgroundAnimationEnabled.first())
            .put("appLanguage", settings.appLanguage.first())
            .put("notesSort", settings.notesSort.first())
            .put("tasksSort", settings.tasksSort.first())
            .put("systemIntegrationEnabled", settings.systemIntegrationEnabled.first())
            .put("startupLoggingEnabled", settings.startupLoggingEnabled.first())
            // ---- Backup v12: keys that drifted in after "task 17" (audit G1/G2) ----
            //
            // The completeness promise above was quietly broken by later additions: the saved
            // searches, the note/task history capture toggles, the unlock attempts-ladder
            // configuration, crash shield, blackout, the rich-text editor toggle,
            // open-links-externally, assistant tool confirmation and small-model mode are all
            // persisted, user-visible choices, and none of them travelled — a restore silently
            // reset each one. Every key is written below and read back guarded by has(), so a v11
            // file restores exactly as it always did. What still stays out is transient or
            // device-local, not a durable user choice: the lockout record (pw_attempt_state), the
            // parking keys local-mode/blackout park values into (*_prelocal / *_preblackout), the
            // remembered tab (last_screen), the crash-recovery snapshot (session_snapshot), the
            // model quick-switcher recency list (model_recents — no restore accessor exists on
            // either platform) and the auto-backup configuration. The desktop-only keys
            // (close_to_tray, app_lock_hello_enabled) stay out too: this file is compiled
            // verbatim on both platforms, and neither accessor exists on the Android twin, so
            // naming one here would break the shared build.
            .put("savedSearches", settings.savedSearches.first())
            // v2.7.2: user-defined note templates and the interrupted-authoring draft (both ride
            // the SETTINGS module like saved searches; see data/CustomTemplates.kt).
            .put("customTemplates", settings.customTemplatesJson.first())
            .put("templateDraft", settings.templateDraftJson.first())
            .put("hiddenTemplates", settings.hiddenTemplatesJson.first())
            // v2.7.5: the cloud storage module's configuration (password riding encrypted).
            .put("cloudEnabled", settings.cloudEnabled.first())
            .put("cloudProvider", settings.cloudProvider.first())
            .put("cloudUrl", settings.cloudUrl.first())
            .put("cloudUser", settings.cloudUser.first())
            .put("cloudPasswordEnc", settings.cloudPasswordEnc.first())
            .put("cloudFolder", settings.cloudFolder.first())
            .put("cloudAutoBackup", settings.cloudAutoBackup.first())
            .put("noteHistoryEnabled", settings.noteHistoryEnabled.first())
            .put("taskHistoryEnabled", settings.taskHistoryEnabled.first())
            .put("pwFirstRoundLimit", settings.pwFirstRoundLimit.first())
            .put("pwLaterRoundLimit", settings.pwLaterRoundLimit.first())
            .put("pwSelfDestructEnabled", settings.pwSelfDestructEnabled.first())
            .put("pwSelfDestructThreshold", settings.pwSelfDestructThreshold.first())
            .put("richTextEnabled", settings.richTextEnabled.first())
            .put("openLinksExternally", settings.openLinksExternally.first())
            .put("assistantConfirmToolsEnabled", settings.assistantConfirmToolsEnabled.first())
            .put("smallModelModeEnabled", settings.smallModelModeEnabled.first())
            .put("crashShieldEnabled", settings.crashShieldEnabled.first())
            .put("blackoutEnabled", settings.blackoutEnabled.first())

        // The local-assistant switches, plus — new in v10 — the model SLOT MANIFEST: the names the
        // user gave their models and which one was active.
        //
        // The manifest was the quiet omission behind "backup doesn't cover the local assistant".
        // Even a user who accepted that a 4 GB file cannot live in a backup lost the labelling of
        // their models on restore, which is the part that made three interchangeable-looking blobs
        // tell-apart-able. It is a few hundred bytes; there was never a reason for it to be absent.
        if (wantLocal) settingsObj
            .put("localModelEnabled", settings.localModelEnabled.first())
            .put("localToolsEnabled", settings.localToolsEnabled.first())
            .put("localGpuEnabled", settings.localGpuEnabled.first())
            .put("localBackgroundReply", settings.localBackgroundReplyEnabled.first())
            .put("localModelManifest", com.lucent.app.local.LocalModelStore.exportManifestJson(context))

        // Notebooks and their membership. Notebooks organize notes and tasks, so they travel with
        // the NOTES+TASKS module; membership rows simply name the notebook and the note/task id,
        // and the importer remaps both to the ids the restored rows actually got.
        val notebooksArray = JSONArray()
        notebooks.forEach { nb ->
            notebooksArray.put(
                JSONObject()
                    .put("title", nb.title)
                    .put("createdAt", nb.createdAt)
                    .put("updatedAt", nb.updatedAt)
            )
        }
        val notebookItemsArray = JSONArray()
        notebookItems.forEach { item ->
            notebookItemsArray.put(
                JSONObject()
                    .put("notebookTitle", notebooks.firstOrNull { it.id == item.notebookId }?.title ?: "")
                    .put("itemKind", item.itemKind)
                    .put("itemTitle", when (item.itemKind) {
                        NotebookItem.KIND_NOTE -> notes.firstOrNull { it.id == item.itemId }?.title ?: ""
                        NotebookItem.KIND_TASK -> tasks.firstOrNull { it.id == item.itemId }?.title ?: ""
                        else -> ""
                    })
                    .put("itemCreatedAt", when (item.itemKind) {
                        NotebookItem.KIND_NOTE -> notes.firstOrNull { it.id == item.itemId }?.updatedAt ?: -1L
                        NotebookItem.KIND_TASK -> tasks.firstOrNull { it.id == item.itemId }?.createdAt ?: -1L
                        else -> -1L
                    })
            )
        }

        val root = JSONObject()
            .put("version", BACKUP_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            // Which modules this file actually claims to carry. Import shows it, so a restore can
            // say "this backup has no tasks in it" instead of silently restoring nothing and
            // leaving the user to work out whether that was the file or the app.
            .put("modules", JSONArray().apply { modules.forEach { put(it.name) } })
        if (BackupManager.BackupModule.NOTES in modules) root.put("notes", notesArray).put("noteVersions", versionsArray)
        if (BackupManager.BackupModule.TASKS in modules) root.put("tasks", tasksArray).put("taskVersions", taskVersionsArray)
        if (BackupManager.BackupModule.CHATS in modules) root.put("chats", chatsArray).put("conversations", conversationsArray)
        if (notebooksArray.length() > 0) {
            root.put("notebooks", notebooksArray).put("notebookItems", notebookItemsArray)
        }
        if (settingsObj.length() > 0) root.put("settings", settingsObj)
        return root
    }

    /**
     * Rewrite an attachment-list JSON so every disk-backed entry carries its bytes inline as
     * Base64 (for the self-contained JSON export). Entries that are already Base64 (legacy rows
     * that haven't been migrated to disk yet) are left as-is, and a disk id whose file is missing
     * is left untouched too — best effort, so a single unreadable attachment never breaks the whole
     * export. An empty list short-circuits.
     */
    private fun inlineAttachmentBytes(context: Context, attachmentsJson: String): String {
        val list = Attachments.parse(attachmentsJson)
        if (list.isEmpty()) return attachmentsJson
        val inlined = list.mapNotNull { att ->
            // Not a disk id → it's already an inline Base64 payload; leave it.
            if (!AttachmentStore.looksLikeId(att.data)) return@mapNotNull att
            // Through the store, so the bytes are decrypted. Reading the File directly would inline
            // ciphertext into the backup — which would round-trip on this device (the same key would
            // "decrypt" it again) and be permanently unreadable on any other. Exactly the kind of bug
            // that only surfaces the day someone actually needs their backup.
            //
            // Self-containment fix (from the first settings variant): an attachment whose bytes
            // can't be read is DROPPED from the backup rather than embedded as its on-disk id. A
            // bare id resolves on the phone that minted it (masking the problem on same-device
            // restores) and points at nothing on any other device — the exact per-attachment loss
            // that surfaced only cross-device. The file is already unreadable on this device, so
            // nothing recoverable is lost; what's guaranteed instead is that no .lcb ever
            // references bytes it doesn't actually contain.
            val plain = AttachmentStore.readBytes(context, att.data, maxBytes = Long.MAX_VALUE)
                ?: return@mapNotNull null
            val encoded = try {
                Base64.encodeToString(plain, Base64.NO_WRAP)
            } catch (t: Throwable) {
                return@mapNotNull null
            }
            att.copy(data = encoded)
        }
        return Attachments.serialize(inlined)
    }
}
