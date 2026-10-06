package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import kotlin.io.encoding.Base64
import com.lucent.app.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

private class ImportState {
    var importedNotes = 0
    var importedTasks = 0
    var importedChats = 0
    var skipped = 0
    var replacedNotes = 0
    var replacedTasks = 0
    var importedVersions = 0
    val noteIdByKey = HashMap<String, Long>()
    val taskIdByKey = HashMap<String, Long>()
    val convIdRemap = HashMap<Long, Long>()
}

object BackupImporter {

    suspend fun import(
        context: PlatformContext,
        db: AppDatabase,
        settings: SettingsRepository,
        json: String,
        modules: Set<BackupManager.BackupModule> = BackupManager.BackupModule.entries.toSet(),
        conversationIds: Set<Long>? = null,
        apiProfileNames: Set<String>? = null,
        mode: ImportMode = ImportMode.DEFAULT
    ): String {
        val root = Json.parseToJsonElement(json).jsonObject
        val state = ImportState()
        val existingNotes = db.noteDao.getAllOnce()
        val existingTasks = db.taskDao.getAllOnce()
        val existingChats = db.chatDao.getAll().first()
        val wantNotes = BackupManager.BackupModule.NOTES in modules
        val wantTasks = BackupManager.BackupModule.TASKS in modules
        val wantChats = BackupManager.BackupModule.CHATS in modules
        importConversations(db, root, wantChats, conversationIds, state)
        importNotes(context, db, root, wantNotes, mode, existingNotes, state)
        importTasks(context, db, root, wantTasks, mode, existingTasks, state)
        importTaskVersions(db, root, wantTasks, state)
        importNoteVersions(db, root, wantNotes, state)
        importChats(db, root, wantChats, conversationIds, existingChats, state)
        importNotebooks(context, db, root, wantNotes, wantTasks)
        val settingsRestored = importSettings(context, settings, root, modules, apiProfileNames)

        AttachmentMigration.pruneOrphans(context)
        db.noteVersionDao.pruneOrphaned()

        if (state.importedTasks > 0) {
            ReminderScheduler.rescheduleAll(context)
        }

        val settingsNote = if (settingsRestored) com.lucent.app.i18n.S.importSettingsRestored else ""
        val historyNote = if (state.importedVersions > 0) com.lucent.app.i18n.S.importVersionsRestored(state.importedVersions) else ""
        val dedupNote = if (state.skipped > 0) com.lucent.app.i18n.S.importDuplicatesSkipped(state.skipped) else ""
        val replacedNote =
            if (state.replacedNotes > 0 || state.replacedTasks > 0)
                "\n" + com.lucent.app.i18n.S.importReplacedSummary(state.replacedNotes, state.replacedTasks)
            else ""

        if (state.importedNotes > 0 || state.replacedNotes > 0) {
            try { db.noteDao.rebuildFts() } catch (_: Exception) {  }
        }
        if (state.importedTasks > 0 || state.replacedTasks > 0) {
            try { db.taskDao.rebuildFts() } catch (_: Exception) {  }
        }

        return com.lucent.app.i18n.S.importSummary(state.importedNotes, state.importedTasks, state.importedChats) +
            settingsNote + historyNote + dedupNote + replacedNote
    }

    private suspend fun importConversations(
        db: AppDatabase,
        root: JsonObject,
        wantChats: Boolean,
        conversationIds: Set<Long>?,
        state: ImportState
    ) {
        (if (wantChats) root["conversations"]?.jsonArray else null)?.let { arr ->
            for (i in 0 until arr.size) {
                val o = arr[i].jsonObject
                val oldId = o["id"]?.jsonPrimitive?.longOrNull ?: 0
                if (conversationIds != null && oldId !in conversationIds) continue
                val title = o["title"]?.jsonPrimitive?.content ?: "Conversation"
                val createdAt = o["createdAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                val updatedAt = o["updatedAt"]?.jsonPrimitive?.longOrNull ?: createdAt
                val newId = db.chatConversationDao.insert(
                    ChatConversation(title = title, createdAt = createdAt, updatedAt = updatedAt)
                )
                if (oldId != 0L) state.convIdRemap[oldId] = newId
            }
        }
    }

    private suspend fun importNotes(
        context: PlatformContext,
        db: AppDatabase,
        root: JsonObject,
        wantNotes: Boolean,
        mode: ImportMode,
        existingNotes: List<Note>,
        state: ImportState
    ) {
        (if (wantNotes) root["notes"]?.jsonArray else null)?.let { arr ->
            for (i in 0 until arr.size) {
                val o = arr[i].jsonObject
                val title = o["title"]?.jsonPrimitive?.content ?: ""
                val body = o["body"]?.jsonPrimitive?.content ?: ""
                val updatedAt = o["updatedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                val tags = o["tags"]?.jsonPrimitive?.content ?: ""
                val rawAttachments = o["attachments"]?.jsonPrimitive?.content ?: "[]"
                val attachments = migrateInlineAttachmentsIfNeeded(context, rawAttachments)
                val archived = o["archived"]?.jsonPrimitive?.booleanOrNull ?: false
                val archivedAt = if ((o["archivedAt"] == null || o["archivedAt"] is JsonNull)) null else o["archivedAt"]?.jsonPrimitive?.longOrNull ?: 0L
                val pinned = o["pinned"]?.jsonPrimitive?.booleanOrNull ?: false
                val color = o["color"]?.jsonPrimitive?.content ?: ""
                val isChecklist = o["isChecklist"]?.jsonPrimitive?.booleanOrNull ?: false
                val checklist = o["checklist"]?.jsonPrimitive?.content ?: "[]"
                val trashedAt = if ((o["trashedAt"] == null || o["trashedAt"] is JsonNull)) null else o["trashedAt"]?.jsonPrimitive?.longOrNull ?: 0L
                val manualOrder = o["manualOrder"]?.jsonPrimitive?.intOrNull ?: 0
                val isDraft = o["isDraft"]?.jsonPrimitive?.booleanOrNull ?: false
                val draftSavedAt = if ((o["draftSavedAt"] == null || o["draftSavedAt"] is JsonNull)) null else o["draftSavedAt"]?.jsonPrimitive?.longOrNull ?: 0L
                val hidden = o["hidden"]?.jsonPrimitive?.booleanOrNull ?: false
                val isDoodle = o["isDoodle"]?.jsonPrimitive?.booleanOrNull ?: false
                val doodle = o["doodle"]?.jsonPrimitive?.content ?: ""
                val bodySpans = o["bodySpans"]?.jsonPrimitive?.content ?: ""
                val formatOverride = if ((o["formatOverride"] == null || o["formatOverride"] is JsonNull)) null else o["formatOverride"]?.jsonPrimitive?.content ?: "".takeIf { it.isNotBlank() }
                val key = ImportDecision.noteKey(title)
                val local = existingNotes.firstOrNull { ImportDecision.noteKey(it.title) == key }
                val isDuplicate = existingNotes.any {
                    it.title == title && it.body == body && it.updatedAt == updatedAt
                }
                val action = ImportDecision.forNote(mode, local?.updatedAt, updatedAt, isDuplicate)
                if (action == ImportAction.SKIP) { state.skipped++; continue }
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
                    db.noteDao.update(incoming.copy(id = local.id))
                    state.replacedNotes++
                    local.id
                } else {
                    db.noteDao.insert(incoming)
                }
                state.noteIdByKey["$title\u0000$updatedAt"] = newNoteId
                if (action == ImportAction.INSERT) state.importedNotes++
            }
        }
    }

    private suspend fun importTasks(
        context: PlatformContext,
        db: AppDatabase,
        root: JsonObject,
        wantTasks: Boolean,
        mode: ImportMode,
        existingTasks: List<Task>,
        state: ImportState
    ) {
        (if (wantTasks) root["tasks"]?.jsonArray else null)?.let { arr ->
            for (i in 0 until arr.size) {
                val o = arr[i].jsonObject
                val title = o["title"]?.jsonPrimitive?.content ?: ""
                val isDone = o["isDone"]?.jsonPrimitive?.booleanOrNull ?: false
                val createdAt = o["createdAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                val rawAttachments = o["attachments"]?.jsonPrimitive?.content ?: "[]"
                val attachments = migrateInlineAttachmentsIfNeeded(context, rawAttachments)
                val dueAt = if ((o["dueAt"] == null || o["dueAt"] is JsonNull)) null else o["dueAt"]?.jsonPrimitive?.longOrNull ?: 0L
                val taskNotes = o["notes"]?.jsonPrimitive?.content ?: ""
                val completedAt = if ((o["completedAt"] == null || o["completedAt"] is JsonNull)) null else o["completedAt"]?.jsonPrimitive?.longOrNull ?: 0L
                val priority = o["priority"]?.jsonPrimitive?.intOrNull ?: 0
                val taskPinned = o["pinned"]?.jsonPrimitive?.booleanOrNull ?: false
                val subtasks = o["subtasks"]?.jsonPrimitive?.content ?: "[]"
                val repeatRule = o["repeatRule"]?.jsonPrimitive?.content ?: "NONE"
                val reminderEnabled = o["reminderEnabled"]?.jsonPrimitive?.booleanOrNull ?: false
                val taskTrashedAt = if ((o["trashedAt"] == null || o["trashedAt"] is JsonNull)) null else o["trashedAt"]?.jsonPrimitive?.longOrNull ?: 0L
                val taskManualOrder = o["manualOrder"]?.jsonPrimitive?.intOrNull ?: 0
                val taskIsDraft = o["isDraft"]?.jsonPrimitive?.booleanOrNull ?: false
                val taskDraftSavedAt = if ((o["draftSavedAt"] == null || o["draftSavedAt"] is JsonNull)) null else o["draftSavedAt"]?.jsonPrimitive?.longOrNull ?: 0L
                val taskHidden = o["hidden"]?.jsonPrimitive?.booleanOrNull ?: false
                val taskNotesSpans = o["notesSpans"]?.jsonPrimitive?.content ?: ""
                val taskFormatOverride = if ((o["formatOverride"] == null || o["formatOverride"] is JsonNull)) null else o["formatOverride"]?.jsonPrimitive?.content ?: "".takeIf { it.isNotBlank() }
                val taskKey = ImportDecision.taskKey(title, createdAt)
                val localTask = existingTasks.firstOrNull {
                    ImportDecision.taskKey(it.title, it.createdAt) == taskKey
                }
                val isDuplicate = localTask != null &&
                    localTask.isDone == isDone && localTask.notes == taskNotes &&
                    localTask.dueAt == dueAt && localTask.priority == priority &&
                    localTask.subtasks == subtasks && localTask.trashedAt == taskTrashedAt
                val taskAction = ImportDecision.forTask(mode, localTask != null, isDuplicate)
                if (taskAction == ImportAction.SKIP) { state.skipped++; continue }
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
                    db.taskDao.update(incomingTask.copy(id = localTask.id))
                    state.replacedTasks++
                    localTask.id
                } else {
                    val inserted = db.taskDao.insert(incomingTask)
                    state.importedTasks++
                    inserted
                }
                state.taskIdByKey["$title\u0000$createdAt"] = newTaskId
            }
        }
    }

    private suspend fun importTaskVersions(
        db: AppDatabase,
        root: JsonObject,
        wantTasks: Boolean,
        state: ImportState
    ) {
        (if (wantTasks) root["taskVersions"]?.jsonArray else null)?.let { arr ->
            for (i in 0 until arr.size) {
                val o = arr[i].jsonObject
                val ownerTitle = o["taskTitle"]?.jsonPrimitive?.content ?: ""
                val ownerCreatedAt = o["taskCreatedAt"]?.jsonPrimitive?.longOrNull ?: -1L
                val taskId = state.taskIdByKey["$ownerTitle\u0000$ownerCreatedAt"] ?: continue
                db.taskVersionDao.insert(
                    TaskVersion(
                        taskId = taskId,
                        title = o["title"]?.jsonPrimitive?.content ?: "",
                        notes = o["notes"]?.jsonPrimitive?.content ?: "",
                        subtasks = o["subtasks"]?.jsonPrimitive?.content ?: "[]",
                        priority = o["priority"]?.jsonPrimitive?.intOrNull ?: 0,
                        dueAt = if ((o["dueAt"] == null || o["dueAt"] is JsonNull)) null else o["dueAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                        savedAt = o["savedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                    )
                )
                state.importedVersions++
            }
            state.taskIdByKey.values.distinct().forEach { id ->
                db.taskVersionDao.trimTo(id, TaskHistory.MAX_VERSIONS_PER_TASK)
            }
        }
    }

    private suspend fun importNoteVersions(
        db: AppDatabase,
        root: JsonObject,
        wantNotes: Boolean,
        state: ImportState
    ) {
        (if (wantNotes) root["noteVersions"]?.jsonArray else null)?.let { arr ->
            for (i in 0 until arr.size) {
                val o = arr[i].jsonObject
                val ownerTitle = o["noteTitle"]?.jsonPrimitive?.content ?: ""
                val ownerUpdatedAt = o["noteUpdatedAt"]?.jsonPrimitive?.longOrNull ?: -1L
                val noteId = state.noteIdByKey["$ownerTitle\u0000$ownerUpdatedAt"] ?: continue
                db.noteVersionDao.insert(
                    NoteVersion(
                        noteId = noteId,
                        title = o["title"]?.jsonPrimitive?.content ?: "",
                        body = o["body"]?.jsonPrimitive?.content ?: "",
                        tags = o["tags"]?.jsonPrimitive?.content ?: "",
                        isChecklist = o["isChecklist"]?.jsonPrimitive?.booleanOrNull ?: false,
                        checklist = o["checklist"]?.jsonPrimitive?.content ?: "[]",
                        savedAt = o["savedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                    )
                )
                state.importedVersions++
            }
            state.noteIdByKey.values.distinct().forEach { id ->
                db.noteVersionDao.trimTo(id, NoteHistory.MAX_VERSIONS_PER_NOTE)
            }
        }
    }

    private suspend fun importChats(
        db: AppDatabase,
        root: JsonObject,
        wantChats: Boolean,
        conversationIds: Set<Long>?,
        existingChats: List<ChatMessage>,
        state: ImportState
    ) {
        (if (wantChats) root["chats"]?.jsonArray else null)?.let { arr ->
            var fallbackConvId: Long? = null
            suspend fun fallbackConversation(): Long {
                fallbackConvId?.let { return it }
                val id = db.chatConversationDao.insert(ChatConversation(title = com.lucent.app.i18n.S.importedConversationTitle))
                fallbackConvId = id
                return id
            }
            for (i in 0 until arr.size) {
                val o = arr[i].jsonObject
                val role = o["role"]?.jsonPrimitive?.content ?: ""
                val content = o["content"]?.jsonPrimitive?.content ?: ""
                val timestamp = o["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                val oldConvId = if (o.containsKey("conversationId")) o["conversationId"]?.jsonPrimitive?.longOrNull ?: 1 else 1L
                if (conversationIds != null && oldConvId !in conversationIds) continue
                val newConvId = state.convIdRemap[oldConvId] ?: fallbackConversation()
                val isDuplicate = existingChats.any { it.role == role && it.content == content && it.timestamp == timestamp }
                if (isDuplicate) { state.skipped++; continue }
                db.chatDao.insert(
                    ChatMessage(
                        role = role,
                        content = content,
                        timestamp = timestamp,
                        attachmentMime = if ((o["attachmentMime"] == null || o["attachmentMime"] is JsonNull)) null else o["attachmentMime"]?.jsonPrimitive?.content ?: "",
                        attachmentData = if ((o["attachmentData"] == null || o["attachmentData"] is JsonNull)) null else o["attachmentData"]?.jsonPrimitive?.content ?: "",
                        attachmentName = if ((o["attachmentName"] == null || o["attachmentName"] is JsonNull)) null else o["attachmentName"]?.jsonPrimitive?.content ?: "",
                        attachmentList = if ((o["attachmentList"] == null || o["attachmentList"] is JsonNull)) null else o["attachmentList"]?.jsonPrimitive?.content ?: "",
                        conversationId = newConvId,
                        tokens = o["tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                        agentTrace = if ((o["agentTrace"] == null || o["agentTrace"] is JsonNull)) null else o["agentTrace"]?.jsonPrimitive?.content ?: "",
                        reasoningBlocks = if ((o["reasoningBlocks"] == null || o["reasoningBlocks"] is JsonNull)) null else o["reasoningBlocks"]?.jsonPrimitive?.content ?: "",
                        reasoningText = if ((o["reasoningText"] == null || o["reasoningText"] is JsonNull)) null else o["reasoningText"]?.jsonPrimitive?.content ?: "",
                        quotedRole = if ((o["quotedRole"] == null || o["quotedRole"] is JsonNull)) null else o["quotedRole"]?.jsonPrimitive?.content ?: "",
                        quotedText = if ((o["quotedText"] == null || o["quotedText"] is JsonNull)) null else o["quotedText"]?.jsonPrimitive?.content ?: ""
                    )
                )
                state.importedChats++
            }
        }
    }

    private suspend fun importNotebooks(
        context: PlatformContext,
        db: AppDatabase,
        root: JsonObject,
        wantNotes: Boolean,
        wantTasks: Boolean
    ) {
        if (wantNotes || wantTasks) {
            val restoredNotebooks = root["notebooks"]?.jsonArray
            val restoredItems = root["notebookItems"]?.jsonArray
            if (restoredNotebooks != null && restoredItems != null) {
                val liveNotes = db.noteDao.getAllOnce()
                val liveTasks = db.taskDao.getAllOnce()
                val notebookIdByTitle = HashMap<String, Long>()
                for (i in 0 until restoredNotebooks.length()) {
                    val o = restoredNotebooks[i].jsonObject
                    val title = o["title"]?.jsonPrimitive?.content ?: ""
                    val createdAt = o["createdAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                    val updatedAt = o["updatedAt"]?.jsonPrimitive?.longOrNull ?: createdAt
                    val storedColor = o["color"]?.jsonPrimitive?.content ?: ""
                    val coverData = o["coverData"]?.jsonPrimitive?.content ?: ""
                    val restoredColor = if (storedColor.startsWith("photo:") && coverData.isNotEmpty()) {
                        runCatching {
                            val bytes = Base64.Mime.decode(coverData)
                            AttachmentStore.importBytes(context, bytes)?.let { "photo:$it" }
                        }.getOrNull() ?: storedColor
                    } else storedColor
                    val newId = db.notebookDao.insert(
                        Notebook(
                            title = title,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                            color = restoredColor,
                            manualOrder = o["manualOrder"]?.jsonPrimitive?.intOrNull ?: 0,
                            pinned = o["pinned"]?.jsonPrimitive?.booleanOrNull ?: false
                        )
                    )
                    notebookIdByTitle[title] = newId
                }
                for (i in 0 until restoredItems.length()) {
                    val o = restoredItems[i].jsonObject
                    val notebookId = notebookIdByTitle[o["notebookTitle"]?.jsonPrimitive?.content ?: ""] ?: continue
                    val kind = o["itemKind"]?.jsonPrimitive?.content ?: ""
                    val itemTitle = o["itemTitle"]?.jsonPrimitive?.content ?: ""
                    val itemCreatedAt = o["itemCreatedAt"]?.jsonPrimitive?.longOrNull ?: -1L
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
                        db.notebookDao.insertItem(
                            NotebookItem(notebookId = notebookId, itemKind = kind, itemId = targetId)
                        )
                    }
                }
            }
        }
    }

    private suspend fun importSettings(
        context: PlatformContext,
        settings: SettingsRepository,
        root: JsonObject,
        modules: Set<BackupManager.BackupModule>,
        apiProfileNames: Set<String>?
    ): Boolean {
        var settingsRestored = false
        root["settings"]?.jsonObject?.let { s ->
            val restoreApi = BackupManager.BackupModule.API in modules
            val restoreGeneral = BackupManager.BackupModule.SETTINGS in modules
            val restoreLocal = BackupManager.BackupModule.LOCAL_ASSISTANT in modules
            settingsRestored = restoreApi || restoreGeneral || restoreLocal
            val wholeApi = restoreApi && apiProfileNames == null
            if (wholeApi && s.containsKey("baseUrl")) settings.setBaseUrl(s["baseUrl"]?.jsonPrimitive?.content ?: "")
            if (wholeApi && s.containsKey("apiSpec")) settings.setApiSpec(s["apiSpec"]?.jsonPrimitive?.content ?: "")
            if (wholeApi && s.containsKey("apiKeyEncrypted")) {
                val decrypted = CryptoUtil.decrypt(s["apiKeyEncrypted"]?.jsonPrimitive?.content ?: "")
                if (decrypted.isNotEmpty()) settings.setApiKey(decrypted)
            }
            if (wholeApi && s.containsKey("model")) settings.setModel(s["model"]?.jsonPrimitive?.content ?: "")
            if (restoreGeneral && s.containsKey("themeMode")) settings.setThemeMode(s["themeMode"]?.jsonPrimitive?.content ?: "")
            if (restoreGeneral && s.containsKey("palette")) settings.setPalette(s["palette"]?.jsonPrimitive?.content ?: "")
            if (restoreGeneral && s.containsKey("dynamicColorEnabled")) {
                settings.setDynamicColorEnabled(s["dynamicColorEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
            }
            if (restoreGeneral && s.containsKey("fontLibrary")) {
                try {
                    FontStore.restoreFromBackup(context, s["fontLibrary"]?.jsonPrimitive?.content ?: "")
                } catch (_: Throwable) {
                }
            }
            if (restoreGeneral && s.containsKey("font")) {
                val wantFont = s["font"]?.jsonPrimitive?.content ?: ""
                val resolvable = wantFont == "system" ||
                    runCatching { FontStore.fontFile(context, wantFont) }.getOrNull() != null
                settings.setFont(if (resolvable) wantFont else "system")
            }
            if (restoreGeneral && s.containsKey("assistantName")) settings.setAssistantName(s["assistantName"]?.jsonPrimitive?.content ?: "")
            if (restoreGeneral && s.containsKey("assistantStyle")) settings.setAssistantStyle(s["assistantStyle"]?.jsonPrimitive?.content ?: "")
            if (restoreApi && s.containsKey("apiProfiles")) {
                val parsed = com.lucent.app.data.ApiProfiles.parse(s["apiProfiles"]?.jsonPrimitive?.content ?: "")
                if (apiProfileNames == null) {
                    if (parsed.isNotEmpty()) {
                        settings.saveApiProfiles(parsed, s["apiProfileSelected"]?.jsonPrimitive?.intOrNull ?: 0)
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
                if (s.containsKey("memoryTier")) settings.setMemoryTier(s["memoryTier"]?.jsonPrimitive?.content ?: "")
                if (s.containsKey("memoryTierLocal")) settings.setMemoryTierLocal(s["memoryTierLocal"]?.jsonPrimitive?.content ?: "")
                if (s.containsKey("agentMode")) settings.setAgentMode(s["agentMode"]?.jsonPrimitive?.booleanOrNull ?: true)
                if (s.containsKey("cloudAgentMode")) settings.setAgentMode(s["cloudAgentMode"]?.jsonPrimitive?.booleanOrNull ?: true)
                if (s.containsKey("reasoning")) settings.setReasoning(s["reasoning"]?.jsonPrimitive?.content ?: "")
                if (s.containsKey("webSearchEngine")) settings.setWebSearchEngine(s["webSearchEngine"]?.jsonPrimitive?.content ?: "")
                if (s.containsKey("webSearchEnabled")) settings.setWebSearchEnabled(s["webSearchEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                if (s.containsKey("typingHaptics")) settings.setTypingHapticsEnabled(s["typingHaptics"]?.jsonPrimitive?.booleanOrNull ?: true)
                if (s.containsKey("markdownEnabled")) settings.setMarkdownEnabled(s["markdownEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                if (s.containsKey("linksEnabled")) settings.setLinksEnabled(s["linksEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                if (s.containsKey("backgroundAnimationEnabled")) {
                    settings.setBackgroundAnimationEnabled(s["backgroundAnimationEnabled"]?.jsonPrimitive?.booleanOrNull ?: true)
                }
                if (s.containsKey("splashEnabled")) settings.setSplashEnabled(s["splashEnabled"]?.jsonPrimitive?.booleanOrNull ?: true)
                if (s.containsKey("splashStyle")) settings.setSplashStyle(s["splashStyle"]?.jsonPrimitive?.content ?: "")
                if (s.containsKey("appLanguage")) settings.setAppLanguage(s["appLanguage"]?.jsonPrimitive?.content ?: "")
                if (s.containsKey("notesSort")) settings.setNotesSort(s["notesSort"]?.jsonPrimitive?.content ?: "")
                if (s.containsKey("tasksSort")) settings.setTasksSort(s["tasksSort"]?.jsonPrimitive?.content ?: "")
                if (s.containsKey("notebooksSort")) settings.setNotebooksSort(s["notebooksSort"]?.jsonPrimitive?.content ?: "")
                if (restoreGeneral && s.containsKey("notebookOpens")) settings.setNotebookOpens(s["notebookOpens"]?.jsonPrimitive?.content ?: "{}")
                if (s.containsKey("savedSearches")) settings.setSavedSearches(s["savedSearches"]?.jsonPrimitive?.content ?: "")
                if (restoreGeneral && s.containsKey("harnessConfig")) {
                    val incomingHcStr = s["harnessConfig"]?.jsonPrimitive?.content ?: ""
                    val incomingHc = com.lucent.app.harness.HarnessConfig.parse(incomingHcStr)
                    val currentHcStr = settings.harnessConfig.first()
                    val currentHc = com.lucent.app.harness.HarnessConfig.parse(currentHcStr)

                    val backupPlugins = incomingHc.installedPlugins()
                    val currentPlugins = currentHc.installedPlugins()
                    val pending = (backupPlugins - currentPlugins).toList()

                    val finalHc = incomingHc.copy(
                        plugins = currentHc.plugins,
                        pluginPendingReinstall = (currentHc.pluginPendingReinstall + pending).distinct()
                    )
                    settings.setHarnessConfig(finalHc.toJson())
                }
                if (restoreGeneral && s.containsKey("customTemplates")) settings.setCustomTemplatesJson(s["customTemplates"]?.jsonPrimitive?.content ?: "")
                if (restoreGeneral && s.containsKey("templateDraft")) settings.setTemplateDraftJson(s["templateDraft"]?.jsonPrimitive?.content ?: "")
                if (restoreGeneral && s.containsKey("hiddenTemplates")) settings.setHiddenTemplatesJson(s["hiddenTemplates"]?.jsonPrimitive?.content ?: "")
                if (restoreGeneral && s.containsKey("cloudEnabled")) settings.setCloudEnabled(s["cloudEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                if (restoreGeneral && s.containsKey("cloudProvider")) settings.setCloudProvider(s["cloudProvider"]?.jsonPrimitive?.content ?: "")
                if (restoreGeneral && s.containsKey("cloudUrl")) settings.setCloudUrl(s["cloudUrl"]?.jsonPrimitive?.content ?: "")
                if (restoreGeneral && s.containsKey("cloudUser")) settings.setCloudUser(s["cloudUser"]?.jsonPrimitive?.content ?: "")
                if (restoreGeneral && s.containsKey("cloudPasswordEnc")) settings.setCloudPasswordEnc(s["cloudPasswordEnc"]?.jsonPrimitive?.content ?: "")
                if (restoreGeneral && s.containsKey("terminalFontSize")) settings.setTerminalFontSize(s["terminalFontSize"]?.jsonPrimitive?.doubleOrNull ?: 14.0.toFloat())
                if (restoreGeneral && s.containsKey("terminalKeyBarVisible")) settings.setTerminalKeyBarVisible(s["terminalKeyBarVisible"]?.jsonPrimitive?.booleanOrNull ?: true)
                if (restoreGeneral && s.containsKey("globalTextSelectionEnabled")) settings.setGlobalTextSelectionEnabled(s["globalTextSelectionEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                if (restoreGeneral && s.containsKey("cloudFolder")) settings.setCloudFolder(s["cloudFolder"]?.jsonPrimitive?.content ?: "")
                if (restoreGeneral && s.containsKey("cloudAutoBackup")) settings.setCloudAutoBackup(s["cloudAutoBackup"]?.jsonPrimitive?.booleanOrNull ?: false)
                if (s.containsKey("noteHistoryEnabled")) settings.setNoteHistoryEnabled(s["noteHistoryEnabled"]?.jsonPrimitive?.booleanOrNull ?: true)
                if (s.containsKey("taskHistoryEnabled")) settings.setTaskHistoryEnabled(s["taskHistoryEnabled"]?.jsonPrimitive?.booleanOrNull ?: true)
                if (s.containsKey("pwFirstRoundLimit")) {
                    settings.setPwFirstRoundLimit(s["pwFirstRoundLimit"]?.jsonPrimitive?.intOrNull ?: PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT)
                }
                if (s.containsKey("pwLaterRoundLimit")) {
                    settings.setPwLaterRoundLimit(s["pwLaterRoundLimit"]?.jsonPrimitive?.intOrNull ?: PasswordAttempts.DEFAULT_LATER_ROUND_LIMIT)
                }
                if (s.containsKey("pwSelfDestructEnabled")) settings.setPwSelfDestructEnabled(s["pwSelfDestructEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                if (s.containsKey("pwSelfDestructThreshold")) {
                    settings.setPwSelfDestructThreshold(s["pwSelfDestructThreshold"]?.jsonPrimitive?.intOrNull ?: PasswordAttempts.DEFAULT_SELF_DESTRUCT_THRESHOLD)
                }
                if (s.containsKey("crashShieldEnabled")) settings.setCrashShieldEnabled(s["crashShieldEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                if (s.containsKey("blackoutEnabled")) settings.setBlackoutEnabled(s["blackoutEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                if (s.containsKey("richTextEnabled")) settings.setRichTextEnabled(s["richTextEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                if (s.containsKey("openLinksExternally")) settings.setOpenLinksExternally(s["openLinksExternally"]?.jsonPrimitive?.booleanOrNull ?: false)
                if (s.containsKey("assistantConfirmToolsEnabled")) settings.setAssistantConfirmTools(s["assistantConfirmToolsEnabled"]?.jsonPrimitive?.booleanOrNull ?: true)
                if (s.containsKey("smallModelModeEnabled")) settings.setSmallModelModeEnabled(s["smallModelModeEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                if (s.containsKey("updateChannel")) settings.setUpdateChannel(s["updateChannel"]?.jsonPrimitive?.content ?: "stable")
                if (s.containsKey("autoUpdateEnabled")) settings.setAutoUpdateEnabled(s["autoUpdateEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                if (s.containsKey("privilegedEnabled")) settings.setPrivilegedEnabled(s["privilegedEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
            }
            if (restoreLocal && s.containsKey("localBackgroundReply")) {
                settings.setLocalBackgroundReplyEnabled(s["localBackgroundReply"]?.jsonPrimitive?.booleanOrNull ?: false)
            }

            if (restoreGeneral && s.containsKey("systemIntegrationEnabled")) {
                val shareOn = s["systemIntegrationEnabled"]?.jsonPrimitive?.booleanOrNull ?: false
                settings.setSystemIntegrationEnabled(shareOn)
                ShareIntegration.setEnabled(context, shareOn)
            }
            if (restoreGeneral && s.containsKey("startupLoggingEnabled")) {
                val loggingOn = s["startupLoggingEnabled"]?.jsonPrimitive?.booleanOrNull ?: false
                settings.setStartupLoggingEnabled(loggingOn)
                StartupLog.setEnabled(loggingOn)
            }

            if (restoreLocal && s.containsKey("localModelManifest")) {
                try {
                    com.lucent.app.local.LocalModelStore.restoreFromBackup(
                        context, s["localModelManifest"]?.jsonPrimitive?.content ?: ""
                    )
                } catch (_: Throwable) {
                }
            }
            if (restoreLocal && s.containsKey("localModelEnabled")) {
                val wantLocal = s["localModelEnabled"]?.jsonPrimitive?.booleanOrNull ?: false
                val hasModel = try {
                    com.lucent.app.local.LocalModelStore.hasModel(context)
                } catch (t: Throwable) {
                    false
                }
                settings.setLocalModelEnabled(wantLocal && hasModel)
                if (wantLocal && hasModel) {
                    if (s.containsKey("localToolsEnabled")) settings.setLocalToolsEnabled(s["localToolsEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                    if (s.containsKey("localGpuEnabled")) settings.setLocalGpuEnabled(s["localGpuEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                }
            }
        }
        return settingsRestored
    }

    internal data class HarnessRestoreResult(
        val restored: Int = 0,
        val problems: List<String> = emptyList()
    )

    internal suspend fun restoreHarness(
        context: PlatformContext,
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

    private fun migrateInlineAttachmentsIfNeeded(context: PlatformContext, attachmentsJson: String): String {
        val list = Attachments.parse(attachmentsJson)
        if (list.isEmpty()) return attachmentsJson
        var changed = false
        val migrated = list.map { att ->
            if (AttachmentStore.looksLikeId(att.data)) return@map att
            val bytes = try {
                Base64.Mime.decode(att.data)
            } catch (t: Throwable) {
                return@map att
            }
            val id = AttachmentStore.importBytes(context, bytes)
            if (id != null) { changed = true; att.copy(data = id) } else att
        }
        return if (changed) Attachments.serialize(migrated) else attachmentsJson
    }

}
