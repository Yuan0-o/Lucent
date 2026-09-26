package com.lucent.app.data

import android.content.Context
import com.lucent.app.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first
import org.json.JSONObject

internal object BackupImporter {

    suspend fun import(
        context: Context,
        db: AppDatabase,
        settings: SettingsRepository,
        json: String,
        modules: Set<BackupManager.BackupModule> = BackupManager.BackupModule.entries.toSet(),
        conversationIds: Set<Long>? = null,
        apiProfileNames: Set<String>? = null,
        mode: ImportMode = ImportMode.DEFAULT
    ): String {
        val root = JSONObject(json)
        var importedNotes = 0
        var importedTasks = 0
        var importedChats = 0
        var skipped = 0
        var replacedNotes = 0
        var replacedTasks = 0

        val existingNotes = db.noteDao().getAllOnce()
        val existingTasks = db.taskDao().getAllOnce()
        val existingChats = db.chatDao().getAll().first()

        val noteIdByKey = HashMap<String, Long>()
        val taskIdByKey = HashMap<String, Long>()
        var importedVersions = 0

        val convIdRemap = HashMap<Long, Long>()
        val wantNotes = BackupManager.BackupModule.NOTES in modules
        val wantTasks = BackupManager.BackupModule.TASKS in modules
        val wantChats = BackupManager.BackupModule.CHATS in modules
        (if (wantChats) root.optJSONArray("conversations") else null)?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val oldId = o.optLong("id", 0)
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
                val archived = o.optBoolean("archived", false)
                val archivedAt = if (o.isNull("archivedAt")) null else o.optLong("archivedAt")
                val pinned = o.optBoolean("pinned", false)
                val color = o.optString("color", "")
                val isChecklist = o.optBoolean("isChecklist", false)
                val checklist = o.optString("checklist", "[]")
                val trashedAt = if (o.isNull("trashedAt")) null else o.optLong("trashedAt")
                val manualOrder = o.optInt("manualOrder", 0)
                val isDraft = o.optBoolean("isDraft", false)
                val draftSavedAt = if (o.isNull("draftSavedAt")) null else o.optLong("draftSavedAt")
                val hidden = o.optBoolean("hidden", false)
                val isDoodle = o.optBoolean("isDoodle", false)
                val doodle = o.optString("doodle", "")
                val bodySpans = o.optString("bodySpans", "")
                val formatOverride = if (o.isNull("formatOverride")) null else o.optString("formatOverride").takeIf { it.isNotBlank() }
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
                    manualOrder = manualOrder, isDraft = isDraft,
                    draftSavedAt = draftSavedAt, hidden = hidden,
                    isDoodle = isDoodle, doodle = doodle, bodySpans = bodySpans,
                    formatOverride = formatOverride
                )
                val newNoteId = if (action == ImportAction.REPLACE && local != null) {
                    db.noteDao().update(incoming.copy(id = local.id))
                    replacedNotes++
                    local.id
                } else {
                    db.noteDao().insert(incoming)
                }
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
                val taskFormatOverride = if (o.isNull("formatOverride")) null else o.optString("formatOverride").takeIf { it.isNotBlank() }
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
                    manualOrder = taskManualOrder, isDraft = taskIsDraft,
                    draftSavedAt = taskDraftSavedAt, hidden = taskHidden,
                    notesSpans = taskNotesSpans,
                    formatOverride = taskFormatOverride
                )
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
            taskIdByKey.values.distinct().forEach { id ->
                db.taskVersionDao().trimTo(id, TaskHistory.MAX_VERSIONS_PER_TASK)
            }
        }

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
            noteIdByKey.values.distinct().forEach { id ->
                db.noteVersionDao().trimTo(id, NoteHistory.MAX_VERSIONS_PER_NOTE)
            }
        }

        (if (wantChats) root.optJSONArray("chats") else null)?.let { arr ->
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
                        tokens = o.optInt("tokens", 0),
                        agentTrace = if (o.isNull("agentTrace")) null else o.optString("agentTrace"),
                        reasoningBlocks = if (o.isNull("reasoningBlocks")) null else o.optString("reasoningBlocks"),
                        reasoningText = if (o.isNull("reasoningText")) null else o.optString("reasoningText")
                    )
                )
                importedChats++
            }
        }

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
                        Notebook(
                            title = title,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                            color = o.optString("color", ""),
                            manualOrder = o.optInt("manualOrder", 0),
                            pinned = o.optBoolean("pinned", false)
                        )
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
            val restoreApi = BackupManager.BackupModule.API in modules
            val restoreGeneral = BackupManager.BackupModule.SETTINGS in modules
            val restoreLocal = BackupManager.BackupModule.LOCAL_ASSISTANT in modules
            settingsRestored = restoreApi || restoreGeneral || restoreLocal
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
            if (restoreGeneral && s.has("dynamicColorEnabled")) {
                settings.setDynamicColorEnabled(s.optBoolean("dynamicColorEnabled"))
            }
            if (restoreGeneral && s.has("fontLibrary")) {
                try {
                    FontStore.restoreFromBackup(context, s.optString("fontLibrary"))
                } catch (_: Throwable) {
                }
            }
            if (restoreGeneral && s.has("font")) {
                val wantFont = s.optString("font")
                val resolvable = wantFont == "system" ||
                    runCatching { FontStore.fontFile(context, wantFont) }.getOrNull() != null
                settings.setFont(if (resolvable) wantFont else "system")
            }
            if (restoreGeneral && s.has("assistantName")) settings.setAssistantName(s.optString("assistantName"))
            if (restoreGeneral && s.has("assistantStyle")) settings.setAssistantStyle(s.optString("assistantStyle"))
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

            if (restoreGeneral) {
                if (s.has("memoryTier")) settings.setMemoryTier(s.optString("memoryTier"))
                if (s.has("memoryTierLocal")) settings.setMemoryTierLocal(s.optString("memoryTierLocal"))
                if (s.has("agentMode")) settings.setAgentMode(s.optBoolean("agentMode", true))
                if (s.has("cloudAgentMode")) settings.setAgentMode(s.optBoolean("cloudAgentMode", true))
                if (s.has("reasoning")) settings.setReasoning(s.optString("reasoning"))
                if (s.has("webSearchEngine")) settings.setWebSearchEngine(s.optString("webSearchEngine"))
                if (s.has("webSearchEnabled")) settings.setWebSearchEnabled(s.optBoolean("webSearchEnabled"))
                if (s.has("typingHaptics")) settings.setTypingHapticsEnabled(s.optBoolean("typingHaptics", true))
                if (s.has("markdownEnabled")) settings.setMarkdownEnabled(s.optBoolean("markdownEnabled"))
                if (s.has("linksEnabled")) settings.setLinksEnabled(s.optBoolean("linksEnabled"))
                if (s.has("backgroundAnimationEnabled")) {
                    settings.setBackgroundAnimationEnabled(s.optBoolean("backgroundAnimationEnabled", true))
                }
                if (s.has("splashEnabled")) settings.setSplashEnabled(s.optBoolean("splashEnabled", true))
                if (s.has("splashStyle")) settings.setSplashStyle(s.optString("splashStyle"))
                if (s.has("appLanguage")) settings.setAppLanguage(s.optString("appLanguage"))
                if (s.has("notesSort")) settings.setNotesSort(s.optString("notesSort"))
                if (s.has("tasksSort")) settings.setTasksSort(s.optString("tasksSort"))
                if (s.has("notebooksSort")) settings.setNotebooksSort(s.optString("notebooksSort"))
                if (s.has("savedSearches")) settings.setSavedSearches(s.optString("savedSearches"))
                if (restoreGeneral && s.has("harnessConfig")) settings.setHarnessConfig(s.optString("harnessConfig"))
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
                if (s.has("autoUpdateEnabled")) settings.setAutoUpdateEnabled(s.optBoolean("autoUpdateEnabled"))
                if (s.has("privilegedEnabled")) settings.setPrivilegedEnabled(s.optBoolean("privilegedEnabled"))
            }
            if (restoreLocal && s.has("localBackgroundReply")) {
                settings.setLocalBackgroundReplyEnabled(s.optBoolean("localBackgroundReply"))
            }

            if (restoreGeneral && s.has("systemIntegrationEnabled")) {
                val shareOn = s.optBoolean("systemIntegrationEnabled")
                settings.setSystemIntegrationEnabled(shareOn)
                ShareIntegration.setEnabled(context, shareOn)
            }
            if (restoreGeneral && s.has("startupLoggingEnabled")) {
                val loggingOn = s.optBoolean("startupLoggingEnabled")
                settings.setStartupLoggingEnabled(loggingOn)
                StartupLog.setEnabled(loggingOn)
            }

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
                if (wantLocal && hasModel) {
                    if (s.has("localToolsEnabled")) settings.setLocalToolsEnabled(s.optBoolean("localToolsEnabled"))
                    if (s.has("localGpuEnabled")) settings.setLocalGpuEnabled(s.optBoolean("localGpuEnabled"))
                }
            }
        }

        AttachmentMigration.pruneOrphans(context)
        db.noteVersionDao().pruneOrphaned()

        if (importedTasks > 0) {
            ReminderScheduler.rescheduleAll(context)
        }

        val settingsNote = if (settingsRestored) com.lucent.app.i18n.S.importSettingsRestored else ""
        val historyNote = if (importedVersions > 0) com.lucent.app.i18n.S.importVersionsRestored(importedVersions) else ""
        val dedupNote = if (skipped > 0) com.lucent.app.i18n.S.importDuplicatesSkipped(skipped) else ""
        val replacedNote =
            if (replacedNotes > 0 || replacedTasks > 0)
                "\n" + com.lucent.app.i18n.S.importReplacedSummary(replacedNotes, replacedTasks)
            else ""

        if (importedNotes > 0 || replacedNotes > 0) {
            try { db.noteDao().rebuildFts() } catch (_: Exception) {  }
        }
        if (importedTasks > 0 || replacedTasks > 0) {
            try { db.taskDao().rebuildFts() } catch (_: Exception) {  }
        }

        return com.lucent.app.i18n.S.importSummary(importedNotes, importedTasks, importedChats) +
            settingsNote + historyNote + dedupNote + replacedNote
    }

    internal data class HarnessRestoreResult(
        val restored: Int = 0,
        val problems: List<String> = emptyList()
    )

    internal suspend fun restoreHarness(
        context: Context,
        source: BackupManager.BackupSource,
        password: String?,
        cancelled: () -> Boolean
    ): HarnessRestoreResult {
        val problems = mutableListOf<String>()
        var restored = 0
        var plain: java.io.InputStream? = null
        try {
            plain = BackupFrames.openDecrypted(source, password)
            val scratch = ByteArray(1 shl 16)
            HarnessBackup.useHome {
                BackupFrames.scanPayload(plain, cancelled) { name, dataLen, data ->
                    if (BackupFrames.blobKind(name) != BackupFrames.BlobKind.HARNESS) {
                        BackupFrames.skipFully(data, dataLen, scratch, cancelled)
                        return@scanPayload
                    }
                    if (HarnessBackup.writeEntry(context, name, dataLen, data, scratch, cancelled)) {
                        restored++
                    } else {
                        problems += HarnessBackup.relativePath(name) ?: name
                    }
                }
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            problems += t.message ?: t::class.simpleName ?: "error"
        } finally {
            try { plain?.close() } catch (_: Throwable) {
            }
        }
        return HarnessRestoreResult(restored, problems)
    }

    private fun migrateInlineAttachmentsIfNeeded(context: Context, attachmentsJson: String): String {
        val list = Attachments.parse(attachmentsJson)
        if (list.isEmpty()) return attachmentsJson
        var changed = false
        val migrated = list.map { att ->
            if (AttachmentStore.looksLikeId(att.data)) return@map att
            val bytes = try {
                android.util.Base64.decode(att.data, android.util.Base64.DEFAULT)
            } catch (t: Throwable) {
                return@map att
            }
            val id = AttachmentStore.importBytes(context, bytes)
            if (id != null) { changed = true; att.copy(data = id) } else att
        }
        return if (changed) Attachments.serialize(migrated) else attachmentsJson
    }

}
