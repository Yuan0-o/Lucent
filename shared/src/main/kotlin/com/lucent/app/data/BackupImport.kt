package com.lucent.app.data

import android.content.Context
import com.lucent.app.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * The `.lcb` JSON import path (extracted from BackupManager, P0-7): restores notes, tasks,
 * revision histories, chats, notebooks, settings, API profiles and local-assistant state from a
 * decrypted manifest, re-linking rows to fresh ids and decoding inline attachments to disk as it
 * goes. BackupManager's importJson() delegates here so the public API is unchanged.
 */
internal object BackupImporter {

    /**
     * Restore from a decrypted manifest string (the JSON sealed inside a `.lcb`). Attachments are
     * inline Base64; each blob is decoded to disk and the row rewritten with a disk id as it goes.
     * Rows already carrying disk ids (a manifest that was somehow hand-built) are left as-is.
     */
    suspend fun import(
        context: Context,
        db: AppDatabase,
        settings: SettingsRepository,
        json: String,
        modules: Set<BackupManager.BackupModule> = BackupManager.BackupModule.entries.toSet(),
        // Per-item restore choices (task F2): null = everything in that module, a non-null set is an
        // explicit subset. Chats by conversation id, API by profile name.
        conversationIds: Set<Long>? = null,
        apiProfileNames: Set<String>? = null,
        /**
         * C-group task 16 — parallel vs overwrite. Defaults to [ImportMode.PARALLEL], which is the
         * behaviour every existing call site already had, so adding this parameter changes nothing
         * for a caller that does not pass it.
         */
        mode: ImportMode = ImportMode.DEFAULT
    ): String {
        val root = JSONObject(json)
        var importedNotes = 0
        var importedTasks = 0
        var importedChats = 0
        var skipped = 0
        // C-group task 16: replacements are counted separately from inserts. "Imported 12" when
        // four of them silently replaced existing notes is a report that hides the only part of
        // the operation the user might want to undo.
        var replacedNotes = 0
        var replacedTasks = 0

        val existingNotes = db.noteDao().getAllOnce()
        val existingTasks = db.taskDao().getAllOnce()
        val existingChats = db.chatDao().getAll().first()

        // Maps a backed-up note's (title, updatedAt) to the id it was given on *this* device. Note
        // ids are not stable across an import — Room assigns fresh ones — so version history can't
        // travel by id and has to be re-linked through something that survives the trip.
        val noteIdByKey = HashMap<String, Long>()
        // Task A19 — same re-linking table for tasks; see the export comment.
        val taskIdByKey = HashMap<String, Long>()
        var importedVersions = 0

        // Restore conversations first so chat messages can be repointed at them. Backups store
        // each conversation's original id; because the local DB may already have conversations
        // with those ids, we insert fresh rows and remember old-id -> new-id so message rows can
        // be remapped. Backups predating multi-conversation support have no "conversations"
        // array — those messages keep conversationId 1, and we make sure a conversation with a
        // usable id exists for them below.
        val convIdRemap = HashMap<Long, Long>()
        val wantNotes = BackupManager.BackupModule.NOTES in modules
        val wantTasks = BackupManager.BackupModule.TASKS in modules
        val wantChats = BackupManager.BackupModule.CHATS in modules
        (if (wantChats) root.optJSONArray("conversations") else null)?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val oldId = o.optLong("id", 0)
                // Selective restore (task F2): skip a conversation the user didn't tick. Its messages
                // are dropped below too, since they'd otherwise fall through to the "unknown
                // conversation" bucket and reappear under a fresh thread the user didn't ask for.
                if (conversationIds != null && oldId !in conversationIds) continue
                val title = o.optString("title", "Conversation")
                val createdAt = o.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = o.optLong("updatedAt", createdAt)
                val newId = db.chatConversationDao().insert(
                    ChatConversation(title = title, createdAt = createdAt, updatedAt = updatedAt)
                )
                if (oldId != 0L) convIdRemap[oldId] = newId
            }
        }

        (if (wantNotes) root.optJSONArray("notes") else null)?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val title = o.optString("title")
                val body = o.optString("body")
                val updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                val tags = o.optString("tags", "")
                val rawAttachments = o.optString("attachments", "[]")
                val attachments = migrateInlineAttachmentsIfNeeded(context, rawAttachments)
                // Archive state — absent in pre-archive backups, so it defaults to not-archived
                // with a null timestamp, which is exactly right for those older files.
                val archived = o.optBoolean("archived", false)
                val archivedAt = if (o.isNull("archivedAt")) null else o.optLong("archivedAt")
                // Pin / colour / checklist / trash — absent from older backups, so each defaults to
                // exactly the inert value a never-touched pre-existing row would have had.
                val pinned = o.optBoolean("pinned", false)
                val color = o.optString("color", "")
                val isChecklist = o.optBoolean("isChecklist", false)
                val checklist = o.optString("checklist", "[]")
                val trashedAt = if (o.isNull("trashedAt")) null else o.optLong("trashedAt")
                // 1.1.0 group A. Every default here is the value a pre-1.1.0 row already had, so an
                // older backup restores as "not a draft, not hidden, no manual position" — which is
                // precisely what those notes were.
                val manualOrder = o.optInt("manualOrder", 0)
                val isDraft = o.optBoolean("isDraft", false)
                val draftSavedAt = if (o.isNull("draftSavedAt")) null else o.optLong("draftSavedAt")
                val hidden = o.optBoolean("hidden", false)
                val isDoodle = o.optBoolean("isDoodle", false)
                val doodle = o.optString("doodle", "")
                val bodySpans = o.optString("bodySpans", "")
                // C-group task 16. Identity is the trimmed, case-insensitive title — the same rule
                // NoteLinks resolves [[wiki]] links with, so a restore can never split a note away
                // from the links pointing at it. See ImportMode for the full reasoning.
                val key = ImportDecision.noteKey(title)
                val local = existingNotes.firstOrNull { ImportDecision.noteKey(it.title) == key }
                val isDuplicate = existingNotes.any {
                    it.title == title && it.body == body && it.updatedAt == updatedAt
                }
                val action = ImportDecision.forNote(mode, local?.updatedAt, updatedAt, isDuplicate)
                if (action == ImportAction.SKIP) { skipped++; continue }
                val incoming = Note(
                    title = title, body = body, updatedAt = updatedAt, tags = tags,
                    attachments = attachments, archived = archived, archivedAt = archivedAt,
                    pinned = pinned, color = color, isChecklist = isChecklist,
                    checklist = checklist, trashedAt = trashedAt,
                    // INTEGRATION: group A's 1.1.0 columns travel through C's import-mode path too.
                    // In OVERWRITE mode the incoming row replaces the local one wholesale, so these
                    // must be carried here — leaving them off would silently reset a replaced note's
                    // draft/hidden/order state to the defaults instead of to what the backup held.
                    manualOrder = manualOrder, isDraft = isDraft,
                    draftSavedAt = draftSavedAt, hidden = hidden,
                    isDoodle = isDoodle, doodle = doodle, bodySpans = bodySpans
                )
                val newNoteId = if (action == ImportAction.REPLACE && local != null) {
                    // Keep the local row's id so anything already pointing at it — a widget, an
                    // open editor, this device's version history — keeps pointing at it.
                    db.noteDao().update(incoming.copy(id = local.id))
                    replacedNotes++
                    local.id
                } else {
                    db.noteDao().insert(incoming)
                }
                // Remember where this note landed so its revision history can be re-linked to the
                // id Room just handed it. Keyed on the same (title, updatedAt) pair the export used.
                noteIdByKey["$title\u0000$updatedAt"] = newNoteId
                if (action == ImportAction.INSERT) importedNotes++
            }
        }
        (if (wantTasks) root.optJSONArray("tasks") else null)?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val title = o.optString("title")
                val isDone = o.optBoolean("isDone", false)
                val createdAt = o.optLong("createdAt", System.currentTimeMillis())
                val rawAttachments = o.optString("attachments", "[]")
                val attachments = migrateInlineAttachmentsIfNeeded(context, rawAttachments)
                val dueAt = if (o.isNull("dueAt")) null else o.optLong("dueAt")
                val taskNotes = o.optString("notes", "")
                val completedAt = if (o.isNull("completedAt")) null else o.optLong("completedAt")
                // Priority / pin / subtasks / repeat / reminder / trash — absent from older backups,
                // so each defaults to the inert value, same reasoning as the note fields above.
                val priority = o.optInt("priority", 0)
                val taskPinned = o.optBoolean("pinned", false)
                val subtasks = o.optString("subtasks", "[]")
                val repeatRule = o.optString("repeatRule", "NONE")
                val reminderEnabled = o.optBoolean("reminderEnabled", false)
                val taskTrashedAt = if (o.isNull("trashedAt")) null else o.optLong("trashedAt")
                val taskManualOrder = o.optInt("manualOrder", 0)
                val taskIsDraft = o.optBoolean("isDraft", false)
                val taskDraftSavedAt = if (o.isNull("draftSavedAt")) null else o.optLong("draftSavedAt")
                val taskHidden = o.optBoolean("hidden", false)
                val taskNotesSpans = o.optString("notesSpans", "")
                // C-group task 16. Tasks match on title + createdAt: a title alone is a bad key
                // ("Buy milk" recurs), while the creation instant is stable and travels in the
                // backup. Tasks carry NO modification timestamp, so OVERWRITE cannot ask which copy
                // is newer and instead does what the mode plainly says — see ImportMode.
                val taskKey = ImportDecision.taskKey(title, createdAt)
                val localTask = existingTasks.firstOrNull {
                    ImportDecision.taskKey(it.title, it.createdAt) == taskKey
                }
                val isDuplicate = localTask != null &&
                    localTask.isDone == isDone && localTask.notes == taskNotes &&
                    localTask.dueAt == dueAt && localTask.priority == priority &&
                    localTask.subtasks == subtasks && localTask.trashedAt == taskTrashedAt
                val taskAction = ImportDecision.forTask(mode, localTask != null, isDuplicate)
                if (taskAction == ImportAction.SKIP) { skipped++; continue }
                val incomingTask = Task(
                    title = title, isDone = isDone, createdAt = createdAt,
                    attachments = attachments, dueAt = dueAt, notes = taskNotes,
                    completedAt = completedAt, priority = priority, pinned = taskPinned,
                    subtasks = subtasks, repeatRule = repeatRule,
                    reminderEnabled = reminderEnabled, trashedAt = taskTrashedAt,
                    // INTEGRATION: same reasoning as the note block above.
                    manualOrder = taskManualOrder, isDraft = taskIsDraft,
                    draftSavedAt = taskDraftSavedAt, hidden = taskHidden,
                    notesSpans = taskNotesSpans
                )
                // INTEGRATION: group A recorded the landing id so task revision history can be
                // re-linked; group C introduced the REPLACE branch, where the row keeps the LOCAL
                // id rather than getting a new one. Both are needed, so the id is captured from
                // whichever branch ran — a REPLACE that recorded the insert id instead would
                // re-link every restored revision to a task that does not exist.
                val newTaskId = if (taskAction == ImportAction.REPLACE && localTask != null) {
                    db.taskDao().update(incomingTask.copy(id = localTask.id))
                    replacedTasks++
                    localTask.id
                } else {
                    val inserted = db.taskDao().insert(incomingTask)
                    importedTasks++
                    inserted
                }
                taskIdByKey["$title\u0000$createdAt"] = newTaskId
            }
        }
        // Task revision history — the task twin of the note block below, including the same rule
        // about dropping revisions whose owner wasn't imported: an orphaned version row would be
        // invisible history attached to nothing, which is strictly worse than no history.
        (if (wantTasks) root.optJSONArray("taskVersions") else null)?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ownerTitle = o.optString("taskTitle", "")
                val ownerCreatedAt = o.optLong("taskCreatedAt", -1L)
                val taskId = taskIdByKey["$ownerTitle\u0000$ownerCreatedAt"] ?: continue
                db.taskVersionDao().insert(
                    TaskVersion(
                        taskId = taskId,
                        title = o.optString("title", ""),
                        notes = o.optString("notes", ""),
                        subtasks = o.optString("subtasks", "[]"),
                        priority = o.optInt("priority", 0),
                        dueAt = if (o.isNull("dueAt")) null else o.optLong("dueAt"),
                        savedAt = o.optLong("savedAt", System.currentTimeMillis())
                    )
                )
                importedVersions++
            }
            // The per-task cap is enforced on the way in as well as on the way out, so a
            // hand-edited backup carrying a thousand revisions of one task can't blow past it.
            taskIdByKey.values.distinct().forEach { id ->
                db.taskVersionDao().trimTo(id, TaskHistory.MAX_VERSIONS_PER_TASK)
            }
        }

        // Note revision history. Re-linked to whichever local id each note actually landed on (see
        // noteIdByKey). Versions whose note wasn't imported — because it was a duplicate and got
        // skipped, or because the file was hand-edited — are simply dropped: an orphaned version row
        // would be invisible history attached to nothing, which is strictly worse than no history.
        (if (wantNotes) root.optJSONArray("noteVersions") else null)?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ownerTitle = o.optString("noteTitle", "")
                val ownerUpdatedAt = o.optLong("noteUpdatedAt", -1L)
                val noteId = noteIdByKey["$ownerTitle\u0000$ownerUpdatedAt"] ?: continue
                db.noteVersionDao().insert(
                    NoteVersion(
                        noteId = noteId,
                        title = o.optString("title", ""),
                        body = o.optString("body", ""),
                        tags = o.optString("tags", ""),
                        isChecklist = o.optBoolean("isChecklist", false),
                        checklist = o.optString("checklist", "[]"),
                        savedAt = o.optLong("savedAt", System.currentTimeMillis())
                    )
                )
                importedVersions++
            }
            // The per-note cap is enforced on the way in as well as on the way out, so a
            // hand-edited backup carrying a thousand revisions of one note can't blow past it.
            noteIdByKey.values.distinct().forEach { id ->
                db.noteVersionDao().trimTo(id, NoteHistory.MAX_VERSIONS_PER_NOTE)
            }
        }

        (if (wantChats) root.optJSONArray("chats") else null)?.let { arr ->
            // A conversation to hold any messages whose original conversation wasn't in the
            // backup (legacy backups, or hand-edited files). Created lazily on first need so a
            // backup with no such messages doesn't add an empty conversation.
            var fallbackConvId: Long? = null
            suspend fun fallbackConversation(): Long {
                fallbackConvId?.let { return it }
                val id = db.chatConversationDao().insert(ChatConversation(title = com.lucent.app.i18n.S.importedConversationTitle))
                fallbackConvId = id
                return id
            }
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val role = o.optString("role")
                val content = o.optString("content")
                val timestamp = o.optLong("timestamp", System.currentTimeMillis())
                val oldConvId = if (o.has("conversationId")) o.optLong("conversationId", 1) else 1L
                // Selective restore (task F2): drop a message whose conversation the user didn't tick.
                if (conversationIds != null && oldConvId !in conversationIds) continue
                val newConvId = convIdRemap[oldConvId] ?: fallbackConversation()
                val isDuplicate = existingChats.any { it.role == role && it.content == content && it.timestamp == timestamp }
                if (isDuplicate) { skipped++; continue }
                db.chatDao().insert(
                    ChatMessage(
                        role = role,
                        content = content,
                        timestamp = timestamp,
                        attachmentMime = if (o.isNull("attachmentMime")) null else o.optString("attachmentMime"),
                        attachmentData = if (o.isNull("attachmentData")) null else o.optString("attachmentData"),
                        attachmentName = if (o.isNull("attachmentName")) null else o.optString("attachmentName"),
                        attachmentList = if (o.isNull("attachmentList")) null else o.optString("attachmentList"),
                        conversationId = newConvId,
                        tokens = o.optInt("tokens", 0)
                        // replyToId (B-group task 12) is deliberately NOT carried across a
                        // restore. It is a raw row id, and a restore assigns brand-new ids —
                        // conversations are already remapped through convIdRemap for exactly that
                        // reason. Copying the old value verbatim would leave every restored reply
                        // pointing at whatever message happens to hold that id now, which is a
                        // dangling pointer into unrelated content: the chat would group answers
                        // that have nothing to do with each other.
                        //
                        // Leaving it 0 means restored replies are simply ungrouped — no 1/2
                        // switcher, every answer shown in order, exactly as conversations rendered
                        // before this feature existed. Losing which answers were siblings is a
                        // small, honest loss; showing the wrong ones as siblings would not be.
                        //
                        // Preserving it properly would need a message-id remap alongside the
                        // conversation one, built in the same pass. Worth doing if variants ever
                        // become load-bearing; not worth the risk today.
                    )
                )
                importedChats++
            }
        }

        // Notebooks and their membership. Notebooks organize notes and tasks, so they restore
        // alongside the NOTES+TASKS module. The export stored membership by the note/task *title*
        // (ids are not stable across a restore), so we re-link each membership row to the note/task
        // that actually landed on this device. We re-query the DB *after* the notes/tasks import so
        // freshly-imported rows are included; rows whose target didn't land are dropped.
        if (wantNotes || wantTasks) {
            val restoredNotebooks = root.optJSONArray("notebooks")
            val restoredItems = root.optJSONArray("notebookItems")
            if (restoredNotebooks != null && restoredItems != null) {
                val liveNotes = db.noteDao().getAllOnce()
                val liveTasks = db.taskDao().getAllOnce()
                val notebookIdByTitle = HashMap<String, Long>()
                for (i in 0 until restoredNotebooks.length()) {
                    val o = restoredNotebooks.getJSONObject(i)
                    val title = o.optString("title", "")
                    val createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    val updatedAt = o.optLong("updatedAt", createdAt)
                    val newId = db.notebookDao().insert(
                        Notebook(title = title, createdAt = createdAt, updatedAt = updatedAt)
                    )
                    notebookIdByTitle[title] = newId
                }
                for (i in 0 until restoredItems.length()) {
                    val o = restoredItems.getJSONObject(i)
                    val notebookId = notebookIdByTitle[o.optString("notebookTitle", "")] ?: continue
                    val kind = o.optString("itemKind", "")
                    val itemTitle = o.optString("itemTitle", "")
                    val itemCreatedAt = o.optLong("itemCreatedAt", -1L)
                    val targetId = when (kind) {
                        NotebookItem.KIND_NOTE -> {
                            val key = ImportDecision.noteKey(itemTitle)
                            liveNotes.firstOrNull { ImportDecision.noteKey(it.title) == key }?.id
                        }
                        NotebookItem.KIND_TASK -> {
                            liveTasks.firstOrNull {
                                it.title == itemTitle && it.createdAt == itemCreatedAt
                            }?.id
                        }
                        else -> null
                    }
                    if (targetId != null) {
                        db.notebookDao().insertItem(
                            NotebookItem(notebookId = notebookId, itemKind = kind, itemId = targetId)
                        )
                    }
                }
            }
        }

        var settingsRestored = false
        root.optJSONObject("settings")?.let { s ->
            // Each half of the settings block is gated on its own module (task 9), so "restore my
            // preferences but not the API keys from this shared backup" is a real option rather
            // than something the user has to achieve by editing the file.
            val restoreApi = BackupManager.BackupModule.API in modules
            val restoreGeneral = BackupManager.BackupModule.SETTINGS in modules
            val restoreLocal = BackupManager.BackupModule.LOCAL_ASSISTANT in modules
            settingsRestored = restoreApi || restoreGeneral || restoreLocal
            // The flat connection keys are the legacy single-API mirror. They're restored only on a
            // WHOLE-API restore (apiProfileNames == null). On a selective one they're deliberately
            // skipped: the file's flat keys mirror whichever profile was active at export, which may
            // be a profile the user didn't pick — writing it would leak an unselected key. The kept
            // profiles below re-mirror the flat keys correctly instead.
            val wholeApi = restoreApi && apiProfileNames == null
            if (wholeApi && s.has("baseUrl")) settings.setBaseUrl(s.optString("baseUrl"))
            if (wholeApi && s.has("apiSpec")) settings.setApiSpec(s.optString("apiSpec"))
            if (wholeApi && s.has("apiKeyEncrypted")) {
                val decrypted = CryptoUtil.decrypt(s.optString("apiKeyEncrypted"))
                if (decrypted.isNotEmpty()) settings.setApiKey(decrypted)
            }
            if (wholeApi && s.has("model")) settings.setModel(s.optString("model"))
            if (restoreGeneral && s.has("themeMode")) settings.setThemeMode(s.optString("themeMode"))
            if (restoreGeneral && s.has("palette")) settings.setPalette(s.optString("palette"))
            // The Material You flag, next to the choices it suspends (backup v13). Older files
            // simply lack the key and leave the current value alone.
            if (restoreGeneral && s.has("dynamicColorEnabled")) {
                settings.setDynamicColorEnabled(s.optBoolean("dynamicColorEnabled"))
            }
            // The imported font library. Adopted BEFORE the font key below, and only for files this
            // restore actually delivered (see FontStore.restoreFromBackup — the blobs were written
            // before importJson was even called), so the key can be validated against what really
            // exists on this device.
            if (restoreGeneral && s.has("fontLibrary")) {
                try {
                    FontStore.restoreFromBackup(context, s.optString("fontLibrary"))
                } catch (_: Throwable) {
                }
            }
            if (restoreGeneral && s.has("font")) {
                // Never restore the font key into a lie: a backup made where a font existed may be
                // restored where its file did not land. "system" and any id that resolves are kept;
                // anything else becomes "system", so the picker never shows a selection that cannot
                // render. The same confirmed-against-reality shape as localModelEnabled below.
                val wantFont = s.optString("font")
                val resolvable = wantFont == "system" ||
                    runCatching { FontStore.fontFile(context, wantFont) }.getOrNull() != null
                settings.setFont(if (resolvable) wantFont else "system")
            }
            if (restoreGeneral && s.has("assistantName")) settings.setAssistantName(s.optString("assistantName"))
            if (restoreGeneral && s.has("assistantStyle")) settings.setAssistantStyle(s.optString("assistantStyle"))
            // API profiles (keys already encrypted inside the JSON).
            //
            //  - WHOLE restore (apiProfileNames == null): unchanged — the file's profiles replace the
            //    current set and the selected one is re-mirrored into the flat keys.
            //  - SELECTIVE restore (task F2): MERGE the chosen profiles into the current set rather
            //    than replacing it — appending only names not already present, capped at the max — so
            //    pulling one API out of a shared backup never wipes the APIs already on this device.
            if (restoreApi && s.has("apiProfiles")) {
                val parsed = com.lucent.app.data.ApiProfiles.parse(s.optString("apiProfiles"))
                if (apiProfileNames == null) {
                    if (parsed.isNotEmpty()) {
                        settings.saveApiProfiles(parsed, s.optInt("apiProfileSelected", 0))
                    }
                } else {
                    val chosen = parsed.filter { it.name in apiProfileNames }
                    if (chosen.isNotEmpty()) {
                        val current = com.lucent.app.data.ApiProfiles.parse(settings.apiProfilesJson.first())
                        val existingNames = current.map { it.name }.toHashSet()
                        val merged = (current + chosen.filter { it.name !in existingNames })
                            .take(com.lucent.app.data.ApiProfiles.MAX)
                        val sel = settings.apiProfileSelected.first()
                            .coerceIn(0, (merged.size - 1).coerceAtLeast(0))
                        settings.saveApiProfiles(merged, sel)
                    }
                }
            }

            // ---- The rest of the preferences (task 17) ----
            //
            // Each is guarded by has(): an OLDER backup simply doesn't carry these keys, and a
            // restore from one must leave the current value alone rather than stamping a default
            // over it. That is what makes this change safe in both directions — a new app reading
            // an old file changes nothing it wasn't told about.
            if (restoreGeneral) {
                if (s.has("memoryTier")) settings.setMemoryTier(s.optString("memoryTier"))
                if (s.has("webSearchEnabled")) settings.setWebSearchEnabled(s.optBoolean("webSearchEnabled"))
                if (s.has("typingHaptics")) settings.setTypingHapticsEnabled(s.optBoolean("typingHaptics", true))
                if (s.has("markdownEnabled")) settings.setMarkdownEnabled(s.optBoolean("markdownEnabled"))
                if (s.has("linksEnabled")) settings.setLinksEnabled(s.optBoolean("linksEnabled"))
                if (s.has("backgroundAnimationEnabled")) {
                    settings.setBackgroundAnimationEnabled(s.optBoolean("backgroundAnimationEnabled", true))
                }
                if (s.has("appLanguage")) settings.setAppLanguage(s.optString("appLanguage"))
                if (s.has("notesSort")) settings.setNotesSort(s.optString("notesSort"))
                if (s.has("tasksSort")) settings.setTasksSort(s.optString("tasksSort"))
                // Backup v12 (audit G1/G2): the preferences added after "task 17" claimed
                // completeness. Same has()-guard as the block above, so a v11 file restores
                // exactly as it always did and a v12 file restores fully. Crash shield and
                // blackout are restored here — BEFORE the system-integration / logging keys
                // below, whose file-side values re-assert whatever their setters force (blackout
                // forces share integration off; crash shield forces logging on).
                if (s.has("savedSearches")) settings.setSavedSearches(s.optString("savedSearches"))
                // v2.7.2 (backup v13): user-defined note templates + the interrupted-authoring
                // draft, both settings JSON (see data/CustomTemplates.kt). Old files simply lack
                // the keys, so this restore leaves the current values alone.
                if (restoreGeneral && s.has("customTemplates")) settings.setCustomTemplatesJson(s.optString("customTemplates"))
                if (restoreGeneral && s.has("templateDraft")) settings.setTemplateDraftJson(s.optString("templateDraft"))
                if (restoreGeneral && s.has("hiddenTemplates")) settings.setHiddenTemplatesJson(s.optString("hiddenTemplates"))
                if (restoreGeneral && s.has("cloudEnabled")) settings.setCloudEnabled(s.optBoolean("cloudEnabled"))
                if (restoreGeneral && s.has("cloudProvider")) settings.setCloudProvider(s.optString("cloudProvider"))
                if (restoreGeneral && s.has("cloudUrl")) settings.setCloudUrl(s.optString("cloudUrl"))
                if (restoreGeneral && s.has("cloudUser")) settings.setCloudUser(s.optString("cloudUser"))
                if (restoreGeneral && s.has("cloudPasswordEnc")) settings.setCloudPasswordEnc(s.optString("cloudPasswordEnc"))
                if (restoreGeneral && s.has("cloudFolder")) settings.setCloudFolder(s.optString("cloudFolder"))
                if (restoreGeneral && s.has("cloudAutoBackup")) settings.setCloudAutoBackup(s.optBoolean("cloudAutoBackup"))
                if (s.has("noteHistoryEnabled")) settings.setNoteHistoryEnabled(s.optBoolean("noteHistoryEnabled", true))
                if (s.has("taskHistoryEnabled")) settings.setTaskHistoryEnabled(s.optBoolean("taskHistoryEnabled", true))
                if (s.has("pwFirstRoundLimit")) {
                    settings.setPwFirstRoundLimit(s.optInt("pwFirstRoundLimit", PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT))
                }
                if (s.has("pwLaterRoundLimit")) {
                    settings.setPwLaterRoundLimit(s.optInt("pwLaterRoundLimit", PasswordAttempts.DEFAULT_LATER_ROUND_LIMIT))
                }
                if (s.has("pwSelfDestructEnabled")) settings.setPwSelfDestructEnabled(s.optBoolean("pwSelfDestructEnabled"))
                if (s.has("pwSelfDestructThreshold")) {
                    settings.setPwSelfDestructThreshold(s.optInt("pwSelfDestructThreshold", PasswordAttempts.DEFAULT_SELF_DESTRUCT_THRESHOLD))
                }
                if (s.has("crashShieldEnabled")) settings.setCrashShieldEnabled(s.optBoolean("crashShieldEnabled"))
                if (s.has("blackoutEnabled")) settings.setBlackoutEnabled(s.optBoolean("blackoutEnabled"))
                if (s.has("richTextEnabled")) settings.setRichTextEnabled(s.optBoolean("richTextEnabled"))
                if (s.has("openLinksExternally")) settings.setOpenLinksExternally(s.optBoolean("openLinksExternally"))
                if (s.has("assistantConfirmToolsEnabled")) settings.setAssistantConfirmTools(s.optBoolean("assistantConfirmToolsEnabled", true))
                if (s.has("smallModelModeEnabled")) settings.setSmallModelModeEnabled(s.optBoolean("smallModelModeEnabled"))
            }
            if (restoreLocal && s.has("localBackgroundReply")) {
                settings.setLocalBackgroundReplyEnabled(s.optBoolean("localBackgroundReply"))
            }

            // System integration is a preference AND an OS-level component state; restoring the flag
            // without flipping the manifest component would leave the app claiming a share-sheet
            // entry it doesn't have. Both move together, exactly as the Privacy toggle does.
            if (restoreGeneral && s.has("systemIntegrationEnabled")) {
                val shareOn = s.optBoolean("systemIntegrationEnabled")
                settings.setSystemIntegrationEnabled(shareOn)
                ShareIntegration.setEnabled(context, shareOn)
            }
            // Same shape for logging: the flag and the live logger are one decision.
            if (restoreGeneral && s.has("startupLoggingEnabled")) {
                val loggingOn = s.optBoolean("startupLoggingEnabled")
                settings.setStartupLoggingEnabled(loggingOn)
                StartupLog.setEnabled(loggingOn)
            }

            // Local model: restore the intent, but never restore it into a lie. The GGUF file MAY
            // now travel with the backup (the LOCAL_MODEL_FILES module, task 9) but usually will
            // not, and either way what matters here is the same check: on a phone with no model
            // actually on disk, honouring a stored "local model on" would hand the user an
            // assistant that cannot answer anything and an API page frozen shut with no visible
            // cause. So the flag is confirmed against reality rather than trusted. When there is no
            // model, local mode stays off and the cloud API keeps working — the honest state for
            // that device. Note the ordering dependency: the slot manifest above, and the model
            // blobs written before importJson was even called, both run first precisely so that
            // hasModel() here sees what this restore just delivered.
            // Adopt the backed-up slot manifest before the enable flag is decided, so hasModel()
            // below sees any model files this restore just put on disk. Slots whose file is absent
            // are dropped inside restoreFromBackup — a restore without the model-files module puts
            // the switches back but honestly reports no models.
            if (restoreLocal && s.has("localModelManifest")) {
                try {
                    com.lucent.app.local.LocalModelStore.restoreFromBackup(
                        context, s.optString("localModelManifest")
                    )
                } catch (_: Throwable) {
                }
            }
            if (restoreLocal && s.has("localModelEnabled")) {
                val wantLocal = s.optBoolean("localModelEnabled")
                val hasModel = try {
                    com.lucent.app.local.LocalModelStore.hasModel(context)
                } catch (t: Throwable) {
                    false
                }
                settings.setLocalModelEnabled(wantLocal && hasModel)
                // Tools/GPU are only meaningful with local mode actually on. setLocalModelEnabled
                // has just reset both to off (task 1), so re-applying the backed-up values here
                // would fight that rule; they are restored only when local mode really came back.
                if (wantLocal && hasModel) {
                    if (s.has("localToolsEnabled")) settings.setLocalToolsEnabled(s.optBoolean("localToolsEnabled"))
                    if (s.has("localGpuEnabled")) settings.setLocalGpuEnabled(s.optBoolean("localGpuEnabled"))
                }
            }
        }

        // After a JSON import, some rows might reference ids that don't exist on disk (unlikely
        // but not impossible if the backup was hand-edited). And after a ZIP import there may be
        // extra staged attachments the manifest doesn't reference (e.g. duplicates that got
        // skipped by the row-level dedup). Sweep the orphans either way.
        AttachmentMigration.pruneOrphans(context)
        db.noteVersionDao().pruneOrphaned()

        // Alarms are OS state, not data: they are not in the backup and cannot be, so a restored
        // task that wants a reminder has nothing scheduled for it on this device. Re-arm everything
        // now, or a restored reminder would sit silently dead until the task happened to be edited —
        // which is precisely the sort of quiet, invisible failure a restore must not have.
        if (importedTasks > 0) {
            ReminderScheduler.rescheduleAll(context)
        }

        val settingsNote = if (settingsRestored) com.lucent.app.i18n.S.importSettingsRestored else ""
        val historyNote = if (importedVersions > 0) com.lucent.app.i18n.S.importVersionsRestored(importedVersions) else ""
        val dedupNote = if (skipped > 0) com.lucent.app.i18n.S.importDuplicatesSkipped(skipped) else ""
        // C-group task 16: replacements get their own line, and only when there were any. An
        // overwrite restore that silently reports "imported 12" is hiding the one part of the
        // operation the user might want to reverse.
        val replacedNote =
            if (replacedNotes > 0 || replacedTasks > 0)
                "\n" + com.lucent.app.i18n.S.importReplacedSummary(replacedNotes, replacedTasks)
            else ""
        return com.lucent.app.i18n.S.importSummary(importedNotes, importedTasks, importedChats) +
            settingsNote + historyNote + dedupNote + replacedNote
    }

    /**
     * If this attachment blob still uses inline Base64 (v7 self-contained JSON, or legacy v5),
     * decode each blob to disk and rewrite it with a disk id. Blobs that already use disk ids (a
     * ZIP manifest, or a row that was already migrated) are returned unchanged.
     */
    private fun migrateInlineAttachmentsIfNeeded(context: Context, attachmentsJson: String): String {
        val list = Attachments.parse(attachmentsJson)
        if (list.isEmpty()) return attachmentsJson
        var changed = false
        val migrated = list.map { att ->
            if (AttachmentStore.looksLikeId(att.data)) return@map att
            val bytes = try {
                android.util.Base64.decode(att.data, android.util.Base64.DEFAULT)
            } catch (t: Throwable) {
                // Undecodable — best we can do is leave the row untouched so the startup
                // migration can retry, rather than replacing it with a UUID that points at
                // nothing on disk. The row simply stays in legacy form.
                return@map att
            }
            // importBytes encrypts on the way to disk.
            val id = AttachmentStore.importBytes(context, bytes)
            if (id != null) { changed = true; att.copy(data = id) } else att
        }
        return if (changed) Attachments.serialize(migrated) else attachmentsJson
    }

}
